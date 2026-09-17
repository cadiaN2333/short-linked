package com.lzq.shortlink.scheduler;

import com.lzq.shortlink.mapper.ShortLinkMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * 将 Redis 中的短链接访问增量定时同步到 MySQL。
 */
@Slf4j
@Component
public class VisitStatSyncScheduler {

    private static final String VISIT_COUNT_KEY_PREFIX = "short-link:visit:";

    private static final String PROCESSING_KEY_PREFIX =
            VISIT_COUNT_KEY_PREFIX + "processing:";

    private static final String CORRUPT_KEY_PREFIX =
            VISIT_COUNT_KEY_PREFIX + "corrupt:";

    private static final String ORPHAN_KEY_PREFIX =
            VISIT_COUNT_KEY_PREFIX + "orphan:";

    /** 处理中键超过该时间后，下一轮允许重新入队。 */
    private static final long PROCESSING_RECOVERY_DELAY_MILLIS = 5 * 60 * 1000L;

    /** 原子领取待同步增量，领取后新的访问继续写入原始 key。 */
    private static final RedisScript<String> CLAIM_SCRIPT =
            new DefaultRedisScript<>("""
                    local value = redis.call('GET', KEYS[1])
                    if not value then
                        return ''
                    end
                    redis.call('DEL', KEYS[1])
                    redis.call('SET', KEYS[2], value)
                    return value
                    """, String.class);

    /** 将处理中增量与领取期间新产生的增量原子合并回待同步 key。 */
    private static final RedisScript<String> REQUEUE_SCRIPT =
            new DefaultRedisScript<>("""
                    local value = redis.call('GET', KEYS[1])
                    if not value then
                        return 'EMPTY'
                    end
                    redis.call('INCRBY', KEYS[2], value)
                    redis.call('DEL', KEYS[1])
                    return 'OK'
                    """, String.class);

    private final StringRedisTemplate stringRedisTemplate;
    private final ShortLinkMapper shortLinkMapper;

    public VisitStatSyncScheduler(
            StringRedisTemplate stringRedisTemplate,
            ShortLinkMapper shortLinkMapper
    ) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.shortLinkMapper = shortLinkMapper;
    }

    /** 每分钟同步一次待落库的访问增量。 */
    @Scheduled(fixedDelay = 60_000)
    public void syncVisitStatistics() {
        ScanOptions scanOptions = ScanOptions.scanOptions()
                .match(VISIT_COUNT_KEY_PREFIX + "*")
                .count(100)
                .build();

        try (Cursor<String> cursor = stringRedisTemplate.scan(scanOptions)) {
            while (cursor.hasNext()) {
                String key = cursor.next();

                if (key.startsWith(PROCESSING_KEY_PREFIX)) {
                    recoverStaleProcessingKey(key);
                    continue;
                }

                if (!key.startsWith(VISIT_COUNT_KEY_PREFIX)
                        || key.startsWith(CORRUPT_KEY_PREFIX)) {
                    continue;
                }

                syncPendingKey(key);
            }
        } catch (RedisConnectionFailureException exception) {
            log.warn("Redis 访问统计同步失败，将在下一轮重试", exception);
        }
    }

    /** 领取单个待同步 key，并在数据库失败时重新入队。 */
    private void syncPendingKey(String visitCountKey) {
        String shortCode = visitCountKey.substring(
                VISIT_COUNT_KEY_PREFIX.length()
        );
        if (shortCode.isBlank()) {
            return;
        }

        String processingKey = PROCESSING_KEY_PREFIX
                + System.currentTimeMillis()
                + ":"
                + UUID.randomUUID().toString().replace("-", "")
                + ":"
                + shortCode;

        String incrementValue = stringRedisTemplate.execute(
                CLAIM_SCRIPT,
                List.of(visitCountKey, processingKey)
        );

        if (incrementValue == null || incrementValue.isBlank()) {
            return;
        }

        long increment;
        try {
            increment = Long.parseLong(incrementValue);
        } catch (NumberFormatException exception) {
            quarantineCorruptKey(processingKey, shortCode, incrementValue);
            return;
        }

        if (increment <= 0) {
            stringRedisTemplate.delete(processingKey);
            return;
        }

        int updatedRows;
        try {
            updatedRows = shortLinkMapper.incrementVisitStatistics(
                    shortCode,
                    increment
            );
        } catch (RuntimeException exception) {
            requeue(processingKey, visitCountKey);
            log.warn(
                    "访问统计落库异常，已重新入队，shortCode={}",
                    shortCode,
                    exception
            );
            return;
        }

        log.info(
                "访问统计已落库，shortCode={}, increment={}, updatedRows={}",
                shortCode,
                increment,
                updatedRows
        );

        if (updatedRows == 0) {
            quarantineOrphanKey(processingKey, shortCode);
            log.warn("访问统计落库失败，短码不存在，已隔离待人工处理，shortCode={}", shortCode);
            return;
        }

        // 数据库写入成功后确认删除处理中键；删除失败时保留处理中键，后续恢复会再次尝试。
        try {
            stringRedisTemplate.delete(processingKey);
        } catch (RedisConnectionFailureException exception) {
            log.warn(
                    "访问统计已写入数据库，但处理中键确认删除失败，将在后续恢复，shortCode={}",
                    shortCode,
                    exception
            );
        }
    }

    /** 将旧的处理中键重新合并到待同步 key。 */
    private void recoverStaleProcessingKey(String processingKey) {
        Long claimedAt = parseClaimedAt(processingKey);
        if (claimedAt == null
                || System.currentTimeMillis() - claimedAt
                < PROCESSING_RECOVERY_DELAY_MILLIS) {
            return;
        }

        String shortCode = processingKey.substring(
                processingKey.lastIndexOf(':') + 1
        );
        if (shortCode.isBlank()) {
            return;
        }

        requeue(
                processingKey,
                VISIT_COUNT_KEY_PREFIX + shortCode
        );
        log.warn("发现超时处理中访问增量，已重新入队，shortCode={}", shortCode);
    }

    /** 解析处理中键中的领取时间。 */
    private Long parseClaimedAt(String processingKey) {
        String suffix = processingKey.substring(PROCESSING_KEY_PREFIX.length());
        int separatorIndex = suffix.indexOf(':');
        if (separatorIndex <= 0) {
            return null;
        }
        try {
            return Long.parseLong(suffix, 0, separatorIndex, 10);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    /** 原子合并处理中增量并删除处理中键。 */
    private void requeue(String processingKey, String visitCountKey) {
        stringRedisTemplate.execute(
                REQUEUE_SCRIPT,
                List.of(processingKey, visitCountKey)
        );
    }

    /** 隔离无法解析的 Redis 计数，避免污染值导致调度器 500。 */
    private void quarantineCorruptKey(
            String processingKey,
            String shortCode,
            String incrementValue
    ) {
        String corruptKey = CORRUPT_KEY_PREFIX
                + System.currentTimeMillis()
                + ":"
                + UUID.randomUUID().toString().replace("-", "")
                + ":"
                + shortCode;
        try {
            stringRedisTemplate.rename(processingKey, corruptKey);
            log.error(
                    "访问统计 Redis 增量格式非法，已隔离，shortCode={}, value={}",
                    shortCode,
                    incrementValue
            );
        } catch (RuntimeException exception) {
            log.error(
                    "访问统计 Redis 增量格式非法且隔离失败，shortCode={}, value={}",
                    shortCode,
                    incrementValue,
                    exception
            );
        }
    }

    /** 隔离短码已不存在的访问增量，避免任务无限重试。 */
    private void quarantineOrphanKey(String processingKey, String shortCode) {
        String orphanKey = ORPHAN_KEY_PREFIX
                + System.currentTimeMillis()
                + ":"
                + UUID.randomUUID().toString().replace("-", "")
                + ":"
                + shortCode;
        try {
            stringRedisTemplate.rename(processingKey, orphanKey);
        } catch (RuntimeException exception) {
            log.error(
                    "访问统计短码不存在且隔离失败，shortCode={}",
                    shortCode,
                    exception
            );
        }
    }
}
