package com.lzq.shortlink.controller;

import com.lzq.shortlink.dto.DailyPvResponse;
import com.lzq.shortlink.dto.ShortLinkStatisticsResponse;
import com.lzq.shortlink.entity.ShortLink;
import com.lzq.shortlink.exception.InvalidStatisticsRangeException;
import com.lzq.shortlink.exception.ShortLinkNotFoundException;
import com.lzq.shortlink.service.ShortLinkAnalyticsService;
import com.lzq.shortlink.service.ShortLinkService;
import com.lzq.shortlink.workspace.WorkspaceAccessService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/** 工作空间范围内的短链接统计接口。 */
@RestController
@RequestMapping(
        "/api/v1/workspaces/{workspaceId}/links/{linkId}/analytics"
)
public class WorkspaceAnalyticsController {

    private final WorkspaceAccessService workspaceAccessService;
    private final ShortLinkService shortLinkService;
    private final ShortLinkAnalyticsService analyticsService;

    public WorkspaceAnalyticsController(
            WorkspaceAccessService workspaceAccessService,
            ShortLinkService shortLinkService,
            ShortLinkAnalyticsService analyticsService
    ) {
        this.workspaceAccessService = workspaceAccessService;
        this.shortLinkService = shortLinkService;
        this.analyticsService = analyticsService;
    }

    /** 查询工作空间短链接的每日 PV 趋势，默认最近 7 天。 */
    @GetMapping
    public ShortLinkStatisticsResponse getAnalytics(
            @PathVariable Long workspaceId,
            @PathVariable Long linkId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate to,
            @RequestParam(defaultValue = "day") String granularity
    ) {
        workspaceAccessService.requireAccessibleWorkspace(workspaceId);

        if (!"day".equals(granularity)) {
            throw new InvalidStatisticsRangeException("当前仅支持 day 粒度");
        }

        ShortLink shortLink = shortLinkService.findShortLinkById(
                workspaceId,
                linkId
        );
        if (shortLink == null) {
            throw new ShortLinkNotFoundException();
        }

        StatisticsRange range = resolveStatisticsRange(from, to);
        List<DailyPvResponse> trend = analyticsService.queryDailyPv(
                shortLink.getId(),
                range.from(),
                range.to()
        );

        ShortLinkStatisticsResponse response = new ShortLinkStatisticsResponse();
        response.setShortCode(shortLink.getShortCode());
        response.setOriginalUrl(shortLink.getOriginalUrl());
        response.setVisitCount(shortLink.getVisitCount());
        response.setLastVisitedAt(shortLink.getLastVisitedAt());
        response.setTrendFrom(range.from());
        response.setTrendTo(range.to());
        response.setGranularity(granularity);
        response.setPvTrend(trend);
        return response;
    }

    /** 解析并校验统计日期范围，最大允许查询 90 天。 */
    private StatisticsRange resolveStatisticsRange(
            LocalDate from,
            LocalDate to
    ) {
        StatisticsRange range;
        if (from == null && to == null) {
            LocalDate end = LocalDate.now();
            range = new StatisticsRange(end.minusDays(6), end);
        } else if (from != null && to == null) {
            range = new StatisticsRange(from, from.plusDays(6));
        } else if (from == null) {
            range = new StatisticsRange(to.minusDays(6), to);
        } else {
            range = new StatisticsRange(from, to);
        }

        if (range.from().isAfter(range.to())) {
            throw new InvalidStatisticsRangeException("开始日期不能晚于结束日期");
        }

        long days = ChronoUnit.DAYS.between(
                range.from(),
                range.to()
        ) + 1;
        if (days > 90) {
            throw new InvalidStatisticsRangeException(
                    "统计范围不能超过 90 天"
            );
        }
        return range;
    }

    /** 统计查询的闭区间日期。 */
    private record StatisticsRange(LocalDate from, LocalDate to) {
    }
}
