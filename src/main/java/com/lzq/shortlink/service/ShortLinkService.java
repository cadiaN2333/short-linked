package com.lzq.shortlink.service;

import com.lzq.shortlink.entity.ShortLink;

import java.time.LocalDateTime;

/**
 * 短链接业务接口。
 */
public interface ShortLinkService {

    /**
     * 创建并保存短链接。
     *
     * @param originalUrl 原始长链接
     * @param expireAt 过期时间，null 表示永久有效
     * @return 已保存的短链接
     */
    ShortLink createShortLink(String originalUrl, LocalDateTime expireAt);

    /**
     * 查询存在且未过期的短链接。
     *
     * @param shortCode 短码
     * @return 有效短链接；不存在或过期时返回 null
     */
    ShortLink findAvailableShortLink(String shortCode);

    /**
     * 记录一次有效短链接访问。
     *
     * @param shortCode 被访问的有效短码
     */
    void recordVisit(String shortCode);
}
