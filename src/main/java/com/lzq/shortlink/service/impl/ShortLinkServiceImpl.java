package com.lzq.shortlink.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lzq.shortlink.entity.ShortLink;
import com.lzq.shortlink.mapper.ShortLinkMapper;
import com.lzq.shortlink.message.VisitEventPublisher;
import com.lzq.shortlink.service.ShortLinkService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 短链接业务接口实现。
 */
@Slf4j
@Service
public class ShortLinkServiceImpl implements ShortLinkService {

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


    /**
     * 创建并保存短链接。
     *
     * @param originalUrl 原始长链接
     * @param expireAt 过期时间，null 表示永久有效
     * @return 已保存的短链接
     */
    @Override
    public ShortLink createShortLink(String originalUrl, LocalDateTime expireAt) {
        for (int attempt = 0; attempt < MAX_RETRY_COUNT; attempt++) {
            ShortLink shortLink = new ShortLink();
            shortLink.setShortCode(generateShortCode());
            shortLink.setOriginalUrl(originalUrl);
            shortLink.setManageToken(generateManageToken());
            shortLink.setExpireAt(expireAt);

            try {
                shortLinkMapper.insert(shortLink);
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

        // 1. 先查 Redis
        String originalUrl = null;
        String shortLinkId = null;

        try {
            originalUrl = stringRedisTemplate.opsForValue().get(cacheKey);
            shortLinkId = stringRedisTemplate.opsForValue()
                    .get(shortLinkIdCacheKey);
        } catch (RedisConnectionFailureException exception) {
            log.warn(
                    "Redis 读取失败，已降级查询 MySQL，shortCode={}",
                    shortCode,
                    exception
            );
        }

        if (originalUrl != null && shortLinkId != null) {
            log.info("短链接缓存命中，shortCode={}", shortCode);
            ShortLink cachedShortLink = new ShortLink();
            cachedShortLink.setShortCode(shortCode);
            cachedShortLink.setOriginalUrl(originalUrl);
            cachedShortLink.setId(Long.parseLong(shortLinkId));

            return cachedShortLink;
        }

        // 2. Redis 未命中，再查 MySQL
        log.info("短链接缓存未命中，查询数据库，shortCode={}", shortCode);
        ShortLink shortLink = shortLinkMapper.selectOne(
                new LambdaQueryWrapper<ShortLink>()
                        .eq(ShortLink::getShortCode, shortCode)
        );

        if (shortLink == null) {
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
                log.info(
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
                log.info("短链接缓存永久有效期，shortCode={}", shortCode);
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
            log.info("短链接访问次数增加，shortCode={}", shortCode);
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
