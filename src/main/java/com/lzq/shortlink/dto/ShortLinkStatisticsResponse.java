package com.lzq.shortlink.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 短链接统计查询响应。
 */
@Data
public class ShortLinkStatisticsResponse {

    /** 短码。 */
    private String shortCode;

    /** 原始长链接。 */
    private String originalUrl;

    /** 已落库的累计访问次数。 */
    private Long visitCount;

    /** 最近一次落库的访问时间。 */
    private LocalDateTime lastVisitedAt;
}
