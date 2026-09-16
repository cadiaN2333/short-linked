package com.lzq.shortlink.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lzq.shortlink.entity.ShortLink;
import com.lzq.shortlink.mapper.ShortLinkMapper;
import com.lzq.shortlink.service.ShortLinkService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
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

    private final StringRedisTemplate stringRedisTemplate;

    private final ShortLinkMapper shortLinkMapper;

    public ShortLinkServiceImpl(
            StringRedisTemplate stringRedisTemplate,
            ShortLinkMapper shortLinkMapper
    ) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.shortLinkMapper = shortLinkMapper;
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
            shortLink.setExpireAt(expireAt);

            try {
                shortLinkMapper.insert(shortLink);
                return shortLink;
            } catch (DuplicateKeyException exception) {
                if (attempt == MAX_RETRY_COUNT - 1) {
                    throw new IllegalStateException("短码生成失败，请稍后重试", exception);
                }
            }
        }

        throw new IllegalStateException("短码生成失败，请稍后重试");
    }

    @Override
    public ShortLink findAvailableShortLink(String shortCode) {
        String cacheKey = REDIS_KEY_PREFIX + shortCode;

        // 1. 先查 Redis
        String originalUrl = stringRedisTemplate.opsForValue().get(cacheKey);

        if (originalUrl != null) {
            log.info("短链接缓存命中，shortCode={}", shortCode);
            ShortLink cachedShortLink = new ShortLink();
            cachedShortLink.setShortCode(shortCode);
            cachedShortLink.setOriginalUrl(originalUrl);

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

        // 3. 已过期：不跳转，也不写入缓存
        if (shortLink.getExpireAt() != null) {
            Duration remaining = Duration.between(
                    LocalDateTime.now(),
                    shortLink.getExpireAt()
            );

            if (remaining.isZero() || remaining.isNegative()) {
                return null;
            }

            // 缓存剩余有效期，避免 Redis 中的链接比数据库活得更久
            stringRedisTemplate.opsForValue().set(
                    cacheKey,
                    shortLink.getOriginalUrl(),
                    remaining
            );
            log.info("短链接缓存剩余有效期，shortCode={}, remaining={}",
                    shortCode, remaining);
        } else {
            // 永久链接不设置过期时间
            stringRedisTemplate.opsForValue().set(
                    cacheKey,
                    shortLink.getOriginalUrl()
            );
            log.info("短链接缓存永久有效期，shortCode={}", shortCode);
        }

        return shortLink;
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