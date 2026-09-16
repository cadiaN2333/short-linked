package com.lzq.shortlink.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

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

    /** 趋势起始日期。 */
    private LocalDate trendFrom;

    /** 趋势结束日期。 */
    private LocalDate trendTo;

    /** 当前统计粒度。 */
    private String granularity;

    /** 按日期升序排列的每日 PV。 */
    private List<DailyPvResponse> pvTrend;
}
