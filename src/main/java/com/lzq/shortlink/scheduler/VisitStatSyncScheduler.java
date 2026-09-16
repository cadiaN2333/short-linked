package com.lzq.shortlink.scheduler;

import com.lzq.shortlink.mapper.ShortLinkMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 将 Redis 中的短链接访问增量定时同步到 MySQL。
 */
@Slf4j
@Component
public class VisitStatSyncScheduler {

    private static final String VISIT_COUNT_KEY_PREFIX = "short-link:visit:";

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
                String visitCountKey = cursor.next();

                String incrementValue = stringRedisTemplate.opsForValue()
                        .getAndDelete(visitCountKey);

                if (incrementValue == null) {
                    continue;
                }

                long increment = Long.parseLong(incrementValue);

                if (increment <= 0) {
                    continue;
                }

                String shortCode = visitCountKey.substring(
                        VISIT_COUNT_KEY_PREFIX.length()
                );

                int updatedRows = shortLinkMapper.incrementVisitStatistics(
                        shortCode,
                        increment
                );
                log.info(
                        "访问统计已落库，shortCode={}, increment={}, updatedRows={}",
                        shortCode,
                        increment,
                        updatedRows
                );

                if (updatedRows == 0) {
                    log.warn("访问统计落库失败，短码不存在，shortCode={}", shortCode);
                }
            }
        } catch (RedisConnectionFailureException exception) {
            log.warn("Redis 访问统计同步失败，将在下一轮重试", exception);
        }
    }
}
