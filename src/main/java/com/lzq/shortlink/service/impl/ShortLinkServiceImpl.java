package com.lzq.shortlink.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;
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

    private static final String REDIS_KEY_PREFIX = "short-link:";

    private static final String VISIT_COUNT_KEY_PREFIX = "short-link:visit:";

    private static final String SHORT_LINK_ID_KEY_PREFIX = "short-link:id:";

    private static final String SHORT_LINK_STATUS_KEY_PREFIX = "short-link:status:";

    private static final String MISSING_SHORT_LINK_KEY_PREFIX = "short-link:missing:";

    private static final String NEGATIVE_CACHE_VALUE = "1";

    private static final Duration NEGATIVE_CACHE_TTL = Duration.ofSeconds(30);

    private final StringRedisTemplate stringRedisTemplate;

    private final ShortLinkMapper shortLinkMapper;

    private final VisitEventPublisher visitEventPublisher;

    public ShortLinkServiceImpl(
            StringRedisTemplate stringRedisTemplate,
            ShortLinkMapper shortLinkMapper,
            VisitEventPublisher visitEventPublisher
    ) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.shortLinkMapper = shortLinkMapper;
        this.visitEventPublisher = visitEventPublisher;
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
                shortLinkMapper.insert(shortLink);
                evictShortLinkCache(shortLink.getShortCode());
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
                shortLinkMapper.insert(shortLink);
                evictShortLinkCache(shortLink.getShortCode());
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

    /** 组装待持久化的短链接实体。 */
    private ShortLink buildShortLink(
            Long workspaceId,
            String originalUrl,
            LocalDateTime expireAt,
            String shortCode
    ) {
        ShortLink shortLink = new ShortLink();
        shortLink.setShortCode(shortCode);
        shortLink.setOriginalUrl(originalUrl);
        shortLink.setWorkspaceId(workspaceId);
        shortLink.setStatus(STATUS_ACTIVE);
        shortLink.setManageToken(generateManageToken());
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

        shortLinkMapper.updateContentByWorkspaceIdAndId(
                workspaceId,
                linkId,
                originalUrl,
                expireAt
        );
        evictShortLinkCache(existing.getShortCode());
        return shortLinkMapper.selectByWorkspaceIdAndId(workspaceId, linkId);
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

        shortLinkMapper.updateStatusByWorkspaceIdAndId(
                workspaceId,
                linkId,
                status
        );
        evictShortLinkCache(existing.getShortCode());
        return shortLinkMapper.selectByWorkspaceIdAndId(workspaceId, linkId);
    }

    @Override
    public boolean deleteShortLink(Long workspaceId, Long linkId) {
        ShortLink existing = findShortLinkById(workspaceId, linkId);
        if (existing == null) {
            return false;
        }

        int affectedRows = shortLinkMapper.updateStatusByWorkspaceIdAndId(
                workspaceId,
                linkId,
                STATUS_DELETED
        );
        if (affectedRows > 0) {
            evictShortLinkCache(existing.getShortCode());
            return true;
        }
        return false;
    }

    /**
     * 查询存在且未过期的短链接。
     *
     * @param shortCode 短码
     * @return 有效短链接；不存在或过期时返回 null
     */
    @Override
    public ShortLink findAvailableShortLink(String shortCode) {
        String cacheKey = REDIS_KEY_PREFIX + shortCode;
        String shortLinkIdCacheKey = SHORT_LINK_ID_KEY_PREFIX + shortCode;
        String shortLinkStatusCacheKey = SHORT_LINK_STATUS_KEY_PREFIX + shortCode;
        String missingShortLinkCacheKey = MISSING_SHORT_LINK_KEY_PREFIX + shortCode;

        // 1. 先查 Redis
        String originalUrl = null;
        String shortLinkId = null;
        String status = null;
        String negativeCacheValue = null;

        try {
            negativeCacheValue = stringRedisTemplate.opsForValue()
                    .get(missingShortLinkCacheKey);
            if (NEGATIVE_CACHE_VALUE.equals(negativeCacheValue)) {
                log.debug("短链接负缓存命中，shortCode={}", shortCode);
                return null;
            }
            originalUrl = stringRedisTemplate.opsForValue().get(cacheKey);
            shortLinkId = stringRedisTemplate.opsForValue()
                    .get(shortLinkIdCacheKey);
            status = stringRedisTemplate.opsForValue()
                    .get(shortLinkStatusCacheKey);
        } catch (RedisConnectionFailureException exception) {
            log.warn(
                    "Redis 读取失败，已降级查询 MySQL，shortCode={}",
                    shortCode,
                    exception
            );
        }

        if (originalUrl != null && shortLinkId != null && status != null) {
            log.debug("短链接缓存命中，shortCode={}", shortCode);
            if (!STATUS_ACTIVE.equals(status)) {
                return null;
            }

            try {
                ShortLink cachedShortLink = new ShortLink();
                cachedShortLink.setShortCode(shortCode);
                cachedShortLink.setOriginalUrl(originalUrl);
                cachedShortLink.setStatus(status);
                cachedShortLink.setId(Long.parseLong(shortLinkId));
                return cachedShortLink;
            } catch (NumberFormatException exception) {
                log.warn(
                        "短链接缓存 ID 格式非法，清理缓存并回源 MySQL，shortCode={}, value={}",
                        shortCode,
                        shortLinkId
                );
                evictShortLinkCache(shortCode);
            }
        }

        // 2. Redis 未命中，再查 MySQL
        log.debug("短链接缓存未命中，查询数据库，shortCode={}", shortCode);
        ShortLink shortLink = shortLinkMapper.selectOne(
                new LambdaQueryWrapper<ShortLink>()
                        .eq(ShortLink::getShortCode, shortCode)
                        .eq(ShortLink::getStatus, STATUS_ACTIVE)
        );

        if (shortLink == null) {
            cacheMissingShortLink(shortCode);
            return null;
        }

        // 已过期：不跳转，也不写入缓存
        Duration remaining = null;

        if (shortLink.getExpireAt() != null) {
            remaining = Duration.between(
                    LocalDateTime.now(),
                    shortLink.getExpireAt()
            );

            if (remaining.isZero() || remaining.isNegative()) {
                cacheMissingShortLink(shortCode);
                return null;
            }
        }

        try {
            if (remaining != null) {
                // 有过期时间的链接：Redis TTL 与链接剩余有效期一致
                stringRedisTemplate.opsForValue().set(
                        cacheKey,
                        shortLink.getOriginalUrl(),
                        remaining
                );
                stringRedisTemplate.opsForValue().set(
                        shortLinkIdCacheKey,
                        String.valueOf(shortLink.getId()),
                        remaining
                );
                stringRedisTemplate.opsForValue().set(
                        shortLinkStatusCacheKey,
                        shortLink.getStatus(),
                        remaining
                );
                log.debug(
                        "短链接缓存剩余有效期，shortCode={}, remaining={}",
                        shortCode,
                        remaining
                );
            } else {
                // 永久链接：Redis 不设置过期时间
                stringRedisTemplate.opsForValue().set(
                        cacheKey,
                        shortLink.getOriginalUrl()
                );
                stringRedisTemplate.opsForValue().set(
                        shortLinkIdCacheKey,
                        String.valueOf(shortLink.getId())
                );
                stringRedisTemplate.opsForValue().set(
                        shortLinkStatusCacheKey,
                        shortLink.getStatus()
                );
                log.debug("短链接缓存永久有效期，shortCode={}", shortCode);
            }
        } catch (RedisConnectionFailureException exception) {
            log.warn(
                    "Redis 写入失败，本次请求仍使用 MySQL 结果，shortCode={}",
                    shortCode,
                    exception
            );
        }

        return shortLink;
    }

    /** 删除短链接缓存，状态变更后避免继续跳转到旧地址。 */
    private void evictShortLinkCache(String shortCode) {
        if (shortCode == null || shortCode.isBlank()) {
            return;
        }
        try {
            stringRedisTemplate.delete(REDIS_KEY_PREFIX + shortCode);
            stringRedisTemplate.delete(
                    SHORT_LINK_ID_KEY_PREFIX + shortCode
            );
            stringRedisTemplate.delete(
                    SHORT_LINK_STATUS_KEY_PREFIX + shortCode
            );
            stringRedisTemplate.delete(
                    MISSING_SHORT_LINK_KEY_PREFIX + shortCode
            );
        } catch (RedisConnectionFailureException exception) {
            log.warn(
                    "Redis 删除短链接缓存失败，shortCode={}",
                    shortCode,
                    exception
            );
        }
    }

    /** 为不存在或已过期的短码写入短 TTL 负缓存，降低缓存穿透。 */
    private void cacheMissingShortLink(String shortCode) {
        if (shortCode == null
                || !shortCode.matches("[0-9A-Za-z_-]{3,16}")) {
            return;
        }
        try {
            stringRedisTemplate.opsForValue().set(
                    MISSING_SHORT_LINK_KEY_PREFIX + shortCode,
                    NEGATIVE_CACHE_VALUE,
                    NEGATIVE_CACHE_TTL
            );
        } catch (RedisConnectionFailureException exception) {
            log.debug(
                    "Redis 写入短链接负缓存失败，shortCode={}",
                    shortCode,
                    exception
            );
        }
    }

    /**
     * 使用短码和管理凭证查询短链接统计。
     * 已过期短链接仍可被查询。
     *
     * @param shortCode 短码
     * @param manageToken 管理凭证
     * @return 匹配的短链接；短码或凭证不匹配时返回 null
     */
    @Override
    public ShortLink findShortLinkForStatistics(String shortCode, String manageToken) {
        if (manageToken == null || manageToken.isBlank()) {
            return null;
        }

        return shortLinkMapper.selectOne(
                new LambdaQueryWrapper<ShortLink>()
                        .eq(ShortLink::getShortCode, shortCode)
                        .eq(ShortLink::getManageToken, manageToken)
        );
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
     * 生成统计查询使用的管理凭证。
     *
     * @return 32 位随机十六进制字符串
     */
    private String generateManageToken() {
        return UUID.randomUUID().toString().replace("-", "");
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
