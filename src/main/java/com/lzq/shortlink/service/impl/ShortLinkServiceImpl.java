package com.lzq.shortlink.service.impl;

import com.lzq.shortlink.validation.TargetUrlValidator;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.lzq.shortlink.cache.ShortLinkLocalCacheValue;
import com.lzq.shortlink.cache.ShortLinkRedirectCache;
import com.lzq.shortlink.entity.ShortLink;
import com.lzq.shortlink.mapper.ShortLinkMapper;
import com.lzq.shortlink.message.VisitEventPublisher;
import com.lzq.shortlink.service.ShortLinkService;
import com.lzq.shortlink.service.ShortLinkPageResult;
import com.lzq.shortlink.exception.InvalidShortLinkStatusException;
import com.lzq.shortlink.exception.InvalidShortLinkFilterException;
import com.lzq.shortlink.exception.ReservedShortCodeException;
import com.lzq.shortlink.exception.ShortCodeAlreadyExistsException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.stereotype.Service;
import com.lzq.shortlink.cache.ShortCodeBloomFilter;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 短链接业务接口实现。
 */
@Slf4j
@Service
public class ShortLinkServiceImpl implements ShortLinkService {

    private static final int MAX_PAGE_SIZE = 100;

    private static final String STATUS_ACTIVE = "ACTIVE";

    private static final String STATUS_DISABLED = "DISABLED";

    private static final String STATUS_DELETED = "DELETED";

    private static final Set<String> RESERVED_SHORT_CODES = Set.of(
            "api",
            "actuator",
            "error",
            "swagger-ui",
            "v3",
            "favicon.ico"
    );

    // 短码字符集
    private static final String SHORT_CODE_CHARACTERS =
            "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";

    // 短码长度
    private static final int SHORT_CODE_LENGTH = 8;

    // 最大重试次数
    private static final int MAX_RETRY_COUNT = 3;

    private static final String VISIT_COUNT_KEY_PREFIX = "short-link:visit:";

    private final StringRedisTemplate stringRedisTemplate;

    private final ShortLinkMapper shortLinkMapper;

    private final VisitEventPublisher visitEventPublisher;

    // 短码布隆过滤器，用于快速检查短码是否存在
    private final ShortCodeBloomFilter shortCodeBloomFilter;

    private final ShortLinkRedirectCache redirectCache;

    @Autowired
    public ShortLinkServiceImpl(
            StringRedisTemplate stringRedisTemplate,
            ShortLinkMapper shortLinkMapper,
            VisitEventPublisher visitEventPublisher,
            @Qualifier("shortLinkLocalCache")
            Cache<String, ShortLinkLocalCacheValue> localCache,
            @Qualifier("shortLinkLocalMissingCache")
            Cache<String, Boolean> localMissingCache,
            ShortCodeBloomFilter shortCodeBloomFilter
    ) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.shortLinkMapper = shortLinkMapper;
        this.visitEventPublisher = visitEventPublisher;
        this.shortCodeBloomFilter = shortCodeBloomFilter;
        this.redirectCache = new ShortLinkRedirectCache(stringRedisTemplate, localCache, localMissingCache);
    }

    /** 使用默认本地缓存，保留给单元测试和兼容调用。 */
    public ShortLinkServiceImpl(
            StringRedisTemplate stringRedisTemplate,
            ShortLinkMapper shortLinkMapper,
            VisitEventPublisher visitEventPublisher
    ) {
        this(
                stringRedisTemplate,
                shortLinkMapper,
                visitEventPublisher,
                com.github.benmanes.caffeine.cache.Caffeine.newBuilder()
                        .maximumSize(10_000)
                        .expireAfterWrite(Duration.ofSeconds(30))
                        .recordStats()
                        .build(),
                com.github.benmanes.caffeine.cache.Caffeine.newBuilder()
                        .maximumSize(10_000)
                        .expireAfterWrite(Duration.ofSeconds(5))
                        .recordStats()
                        .build()
        );
    }

    /** 保留原有手动构造方式，默认关闭布隆过滤器。 */
    public ShortLinkServiceImpl(
            StringRedisTemplate stringRedisTemplate,
            ShortLinkMapper shortLinkMapper,
            VisitEventPublisher visitEventPublisher,
            Cache<String, ShortLinkLocalCacheValue> localCache,
            Cache<String, Boolean> localMissingCache
    ) {
        this(
                stringRedisTemplate,
                shortLinkMapper,
                visitEventPublisher,
                localCache,
                localMissingCache,
                ShortCodeBloomFilter.disabled()
        );
    }


    @Override
    public ShortLink createShortLink(
            Long workspaceId,
            String originalUrl,
            LocalDateTime expireAt
    ) {
        return createShortLink(
                workspaceId,
                originalUrl,
                expireAt,
                null
        );
    }

    @Override
    public ShortLink createShortLink(
            Long workspaceId,
            String originalUrl,
            LocalDateTime expireAt,
            String requestedShortCode
    ) {
        return shortCodeBloomFilter.duringCreation(
                () -> doCreateShortLink(
                        workspaceId, originalUrl, expireAt, requestedShortCode
                )
        );
    }

    /** 创建与初始化协调，输入校验仍先于候选短码登记。 */
    private ShortLink doCreateShortLink(
            Long workspaceId,
            String originalUrl,
            LocalDateTime expireAt,
            String requestedShortCode
    ) {
        if (workspaceId == null) {
            throw new IllegalArgumentException("工作空间不能为空");
        }

        String normalizedShortCode = normalizeRequestedShortCode(
                requestedShortCode
        );

        if (normalizedShortCode != null) {
            ShortLink shortLink = buildShortLink(
                    workspaceId,
                    originalUrl,
                    expireAt,
                    normalizedShortCode
            );
            try {
                shortCodeBloomFilter.beforeInsert(shortLink.getShortCode());
                insertAndEvict(shortLink);
                return shortLink;
            } catch (DuplicateKeyException exception) {
                throw new ShortCodeAlreadyExistsException(
                        "自定义短码已被占用"
                );
            }
        }

        for (int attempt = 0; attempt < MAX_RETRY_COUNT; attempt++) {
            ShortLink shortLink = buildShortLink(
                    workspaceId,
                    originalUrl,
                    expireAt,
                    generateShortCode()
            );

            try {
                shortCodeBloomFilter.beforeInsert(shortLink.getShortCode());
                insertAndEvict(shortLink);
                log.info(
                        "短链接创建成功，shortCode={}，expireAt={}",
                        shortLink.getShortCode(),
                        shortLink.getExpireAt()
                );
                return shortLink;
            } catch (DuplicateKeyException exception) {
                if (attempt == MAX_RETRY_COUNT - 1) {
                    throw new IllegalStateException("短码生成失败，请稍后重试", exception);
                }
            }
        }

        throw new IllegalStateException("短码生成失败，请稍后重试");
    }

    /** 新建与同短码负缓存回填互斥；事务内创建在完成回调中再次清理。 */
    private void insertAndEvict(ShortLink shortLink) {
        redirectCache.mutate(shortLink.getShortCode(), () -> {
            shortLinkMapper.insert(shortLink);
            return null;
        });
    }

    /** 组装待持久化的短链接实体。 */
    private ShortLink buildShortLink(
            Long workspaceId,
            String originalUrl,
            LocalDateTime expireAt,
            String shortCode
    ) {
        String normalizedUrl =
                TargetUrlValidator.normalize(originalUrl);
        ShortLink shortLink = new ShortLink();
        shortLink.setShortCode(shortCode);
        shortLink.setOriginalUrl(normalizedUrl);
        shortLink.setWorkspaceId(workspaceId);
        shortLink.setStatus(STATUS_ACTIVE);
        shortLink.setExpireAt(expireAt);
        return shortLink;
    }

    /** 校验自定义短码格式并拦截系统保留路径。 */
    private String normalizeRequestedShortCode(String requestedShortCode) {
        if (requestedShortCode == null || requestedShortCode.isBlank()) {
            return null;
        }

        String normalized = requestedShortCode.trim();
        if (normalized.length() < 3 || normalized.length() > 16
                || !normalized.matches("[0-9A-Za-z_-]+")) {
            throw new InvalidShortLinkFilterException(
                    "自定义短码只能包含数字、字母、下划线和连字符，长度为 3 到 16 个字符"
            );
        }

        if (RESERVED_SHORT_CODES.contains(
                normalized.toLowerCase(Locale.ROOT)
        )) {
            throw new ReservedShortCodeException(
                    "自定义短码命中系统保留路径"
            );
        }
        return normalized;
    }

    @Override
    public ShortLinkPageResult listShortLinks(
            Long workspaceId,
            int page,
            int pageSize
    ) {
        return listShortLinks(workspaceId, page, pageSize, null, null);
    }

    @Override
    public ShortLinkPageResult listShortLinks(
            Long workspaceId,
            int page,
            int pageSize,
            String status,
            String keyword
    ) {
        if (workspaceId == null) {
            throw new IllegalArgumentException("工作空间不能为空");
        }
        if (page < 1) {
            throw new IllegalArgumentException("页码必须从 1 开始");
        }
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException(
                "每页数量必须在 1 到 " + MAX_PAGE_SIZE + " 之间"
            );
        }

        String normalizedStatus = normalizeStatusFilter(status);
        String normalizedKeyword = normalizeKeywordFilter(keyword);

        long offset = (long) (page - 1) * pageSize;
        if (normalizedStatus == null && normalizedKeyword == null) {
            return new ShortLinkPageResult(
                    shortLinkMapper.selectPageByWorkspaceId(
                            workspaceId,
                            pageSize,
                            offset
                    ),
                    shortLinkMapper.countByWorkspaceId(workspaceId),
                    page,
                    pageSize
            );
        }

        return new ShortLinkPageResult(
                shortLinkMapper.selectPageByWorkspaceIdAndFilter(
                        workspaceId,
                        normalizedStatus,
                        normalizedKeyword,
                        pageSize,
                        offset
                ),
                shortLinkMapper.countByWorkspaceIdAndFilter(
                        workspaceId,
                        normalizedStatus,
                        normalizedKeyword
                ),
                page,
                pageSize
        );
    }

    /** 规范化状态筛选，只允许查询可见的两个生命周期状态。 */
    private String normalizeStatusFilter(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }

        String normalized = status.trim().toUpperCase(Locale.ROOT);
        if (!STATUS_ACTIVE.equals(normalized)
                && !STATUS_DISABLED.equals(normalized)) {
            throw new InvalidShortLinkFilterException(
                    "状态筛选只能是 ACTIVE 或 DISABLED"
            );
        }
        return normalized;
    }

    /** 规范化关键词并限制查询条件长度，避免过大的模糊查询请求。 */
    private String normalizeKeywordFilter(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }

        String normalized = keyword.trim();
        if (normalized.length() > 100) {
            throw new InvalidShortLinkFilterException(
                    "关键词长度不能超过 100 个字符"
            );
        }
        return normalized;
    }

    @Override
    public ShortLink findShortLinkById(Long workspaceId, Long linkId) {
        if (workspaceId == null || linkId == null) {
            return null;
        }
        return shortLinkMapper.selectByWorkspaceIdAndId(workspaceId, linkId);
    }

    @Override
    public ShortLink updateShortLink(
            Long workspaceId,
            Long linkId,
            String originalUrl,
            LocalDateTime expireAt
    ) {
        ShortLink existing = findShortLinkById(workspaceId, linkId);
        if (existing == null) {
            return null;
        }

        String normalizedUrl = TargetUrlValidator.normalize(originalUrl);

        return redirectCache.mutate(existing.getShortCode(), () -> {
            shortLinkMapper.updateContentByWorkspaceIdAndId(
                    workspaceId, linkId, normalizedUrl, expireAt
            );
            return shortLinkMapper.selectByWorkspaceIdAndId(workspaceId, linkId);
        });
    }

    @Override
    public ShortLink changeShortLinkStatus(
            Long workspaceId,
            Long linkId,
            String status
    ) {
        if (!STATUS_ACTIVE.equals(status)
                && !STATUS_DISABLED.equals(status)) {
            throw new InvalidShortLinkStatusException(
                    "短链接状态只能是 ACTIVE 或 DISABLED"
            );
        }

        ShortLink existing = findShortLinkById(workspaceId, linkId);
        if (existing == null) {
            return null;
        }

        return redirectCache.mutate(existing.getShortCode(), () -> {
            shortLinkMapper.updateStatusByWorkspaceIdAndId(workspaceId, linkId, status);
            return shortLinkMapper.selectByWorkspaceIdAndId(workspaceId, linkId);
        });
    }

    @Override
    public boolean deleteShortLink(Long workspaceId, Long linkId) {
        ShortLink existing = findShortLinkById(workspaceId, linkId);
        if (existing == null) {
            return false;
        }

        return redirectCache.mutate(existing.getShortCode(), () -> {
            int affectedRows = shortLinkMapper.updateStatusByWorkspaceIdAndId(
                    workspaceId, linkId, STATUS_DELETED
            );
            return affectedRows > 0;
        });
    }

    /**
     * 查询存在且未过期的短链接。
     *
     * @param shortCode 短码
     * @return 有效短链接；不存在或过期时返回 null
     */
    @Override
    public ShortLink findAvailableShortLink(String shortCode) {
        if (shortCode == null || !shortCode.matches("[0-9A-Za-z_-]{3,16}")) return null;
        // 事务读取也可能等待数据库锁，不在事务内持缓存锁执行数据库查询。
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()) {
            return findAvailableLocked(shortCode);
        }
        return redirectCache.locked(shortCode, () -> findAvailableLocked(shortCode));
    }

    private ShortLink findAvailableLocked(String shortCode) {
        if (redirectCache.missing(shortCode)) return null;
        ShortLink cached = redirectCache.get(shortCode);
        if (cached != null) {
            return STATUS_ACTIVE.equals(cached.getStatus()) ? cached : null;
        }
        if (!shortCodeBloomFilter.mightContain(shortCode)) return null;
        log.debug("短链接缓存未命中，查询数据库，shortCode={}", shortCode);
        ShortLink row = shortLinkMapper.selectOne(
                new LambdaQueryWrapper<ShortLink>()
                        .eq(ShortLink::getShortCode, shortCode)
                        .eq(ShortLink::getStatus, STATUS_ACTIVE)
        );
        // 迁移前或数据库比较规则异常时，也不能让错误大小写命中。
        if (row == null || !shortCode.equals(row.getShortCode())
                || !STATUS_ACTIVE.equals(row.getStatus())
                || (row.getExpireAt() != null && !LocalDateTime.now().isBefore(row.getExpireAt()))) {
            redirectCache.putMissing(shortCode);
            return null;
        }
        redirectCache.put(row);
        return row;
    }

    /**
     * 记录一次有效短链接访问。
     *
     * @param shortLink 被访问的有效短链接
     */
    @Override
    public void recordVisit(ShortLink shortLink) {
        String shortCode = shortLink.getShortCode();
        String visitCountKey = VISIT_COUNT_KEY_PREFIX + shortCode;

        try {
            stringRedisTemplate.opsForValue().increment(visitCountKey);
            log.debug("短链接访问次数增加，shortCode={}", shortCode);
        } catch (RedisConnectionFailureException exception) {
            log.warn(
                    "Redis 写入失败，本次请求仍继续发布访问事件，shortCode={}",
                    shortCode,
                    exception
            );
        }

        visitEventPublisher.publish(shortLink);
    }

    /**
     * 生成随机短码。
     *
     * @return 随机短码
     */
    protected String generateShortCode() {
        StringBuilder shortCode = new StringBuilder(SHORT_CODE_LENGTH);

        for (int index = 0; index < SHORT_CODE_LENGTH; index++) {
            int randomIndex = ThreadLocalRandom.current()
                    .nextInt(SHORT_CODE_CHARACTERS.length());
            shortCode.append(SHORT_CODE_CHARACTERS.charAt(randomIndex));
        }

        return shortCode.toString();
    }
}
