package com.lzq.shortlink.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lzq.shortlink.entity.ShortLink;
import com.lzq.shortlink.mapper.ShortLinkMapper;
import com.lzq.shortlink.service.ShortLinkService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.dao.DuplicateKeyException;

/**
 * 短链接业务接口实现。
 */
@Service
public class ShortLinkServiceImpl implements ShortLinkService {

    // 短码字符集
    private static final String SHORT_CODE_CHARACTERS =
            "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";

    // 短码长度
    private static final int SHORT_CODE_LENGTH = 8;

    // 最大重试次数
    private static final int MAX_RETRY_COUNT = 3;

    private final ShortLinkMapper shortLinkMapper;

    public ShortLinkServiceImpl(ShortLinkMapper shortLinkMapper) {
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
        ShortLink shortLink = shortLinkMapper.selectOne(
                new LambdaQueryWrapper<ShortLink>()
                        .eq(ShortLink::getShortCode, shortCode)
        );

        if (shortLink == null) {
            return null;
        }

        if (shortLink.getExpireAt() != null
                && !shortLink.getExpireAt().isAfter(LocalDateTime.now())) {
            return null;
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