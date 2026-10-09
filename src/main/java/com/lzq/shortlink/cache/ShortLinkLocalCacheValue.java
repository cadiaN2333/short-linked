package com.lzq.shortlink.cache;

import com.lzq.shortlink.entity.ShortLink;

import java.time.LocalDateTime;

/** 短链接本地缓存值，合并目标地址、状态和过期时间，减少本地缓存查询次数。 */
public record ShortLinkLocalCacheValue(
        Long id,
        String shortCode,
        String originalUrl,
        String status,
        LocalDateTime expireAt
) {

    /** 判断短链接是否已经过期。 */
    public boolean expiredAt(LocalDateTime now) {
        return expireAt != null && !now.isBefore(expireAt);
    }

    /** 转换为跳转链路使用的实体对象。 */
    public ShortLink toShortLink() {
        ShortLink shortLink = new ShortLink();
        shortLink.setId(id);
        shortLink.setShortCode(shortCode);
        shortLink.setOriginalUrl(originalUrl);
        shortLink.setStatus(status);
        shortLink.setExpireAt(expireAt);
        return shortLink;
    }

    /** 从有效短链接实体创建本地缓存值。 */
    public static ShortLinkLocalCacheValue from(ShortLink shortLink) {
        return new ShortLinkLocalCacheValue(
                shortLink.getId(),
                shortLink.getShortCode(),
                shortLink.getOriginalUrl(),
                shortLink.getStatus(),
                shortLink.getExpireAt()
        );
    }
}
