package com.lzq.shortlink.service;

import com.lzq.shortlink.entity.ShortLink;

import java.time.LocalDateTime;

/**
 * 短链接业务接口。
 */
public interface ShortLinkService {

    /**
     * 在指定工作空间内创建短链接。
     *
     * @param workspaceId 工作空间主键
     * @param originalUrl 原始长链接
     * @param expireAt 过期时间，null 表示永久有效
     * @return 已保存的短链接
     */
    ShortLink createShortLink(
            Long workspaceId,
            String originalUrl,
            LocalDateTime expireAt
    );

    /** 在工作空间内创建短链接，可选指定自定义短码。 */
    ShortLink createShortLink(
            Long workspaceId,
            String originalUrl,
            LocalDateTime expireAt,
            String requestedShortCode
    );

    /** 查询工作空间内的短链接分页结果。 */
    ShortLinkPageResult listShortLinks(
            Long workspaceId,
            int page,
            int pageSize
    );

    /** 按状态和关键词查询工作空间短链接。 */
    ShortLinkPageResult listShortLinks(
            Long workspaceId,
            int page,
            int pageSize,
            String status,
            String keyword
    );

    /** 按工作空间边界查询单条短链接。 */
    ShortLink findShortLinkById(Long workspaceId, Long linkId);

    /** 修改工作空间内短链接的目标地址和过期时间。 */
    ShortLink updateShortLink(
            Long workspaceId,
            Long linkId,
            String originalUrl,
            LocalDateTime expireAt
    );

    /** 修改工作空间内短链接状态。 */
    ShortLink changeShortLinkStatus(
            Long workspaceId,
            Long linkId,
            String status
    );

    /** 对工作空间内短链接执行软删除。 */
    boolean deleteShortLink(Long workspaceId, Long linkId);

    /**
     * 查询存在且未过期的短链接。
     *
     * @param shortCode 短码
     * @return 有效短链接；不存在或过期时返回 null
     */
    ShortLink findAvailableShortLink(String shortCode);

    /**
     * 使用短码和管理凭证查询短链接统计。
     * 已过期短链接仍可被查询。
     *
     * @param shortCode 短码
     * @param manageToken 管理凭证
     * @return 匹配的短链接；短码或凭证不匹配时返回 null
     */
    ShortLink findShortLinkForStatistics(String shortCode, String manageToken);

    /**
     * 记录一次有效短链接访问。
     *
     * @param shortLink 被访问的有效短链接
     */
    void recordVisit(ShortLink shortLink);
}
