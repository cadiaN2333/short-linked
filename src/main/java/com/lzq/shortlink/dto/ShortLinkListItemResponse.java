package com.lzq.shortlink.dto;

import lombok.Data;

import java.time.LocalDateTime;

/** 工作空间短链接列表项，不返回管理凭证。 */
@Data
public class ShortLinkListItemResponse {

    /** 短链接主键。 */
    private Long id;

    /** 所属工作空间主键。 */
    private Long workspaceId;

    /** 短码。 */
    private String shortCode;

    /** 原始长链接。 */
    private String originalUrl;

    /** 生命周期状态。 */
    private String status;

    /** 创建时间。 */
    private LocalDateTime createdAt;

    /** 过期时间，为 null 表示永久有效。 */
    private LocalDateTime expireAt;

    /** 已落库的累计访问次数。 */
    private Long visitCount;

    /** 最近一次落库访问时间。 */
    private LocalDateTime lastVisitedAt;

    /** 完整短链接地址。 */
    private String shortUrl;
}
