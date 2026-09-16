package com.lzq.shortlink.controller;

import com.lzq.shortlink.dto.CreateShortLinkResponse;
import com.lzq.shortlink.dto.CreateShortLinkRequest;
import com.lzq.shortlink.dto.DailyPvResponse;
import com.lzq.shortlink.dto.ShortLinkStatisticsResponse;
import com.lzq.shortlink.entity.ShortLink;
import com.lzq.shortlink.exception.InvalidStatisticsRangeException;
import com.lzq.shortlink.exception.ShortLinkNotFoundException;
import com.lzq.shortlink.service.ShortLinkAnalyticsService;
import com.lzq.shortlink.service.ShortLinkService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 短链接接口控制器。
 */
@RestController
@RequestMapping("/api/links")
public class ShortLinkController {

    private final ShortLinkService shortLinkService;

    private final ShortLinkAnalyticsService analyticsService;

    public ShortLinkController(
            ShortLinkService shortLinkService,
            ShortLinkAnalyticsService analyticsService
    ) {
        this.shortLinkService = shortLinkService;
        this.analyticsService = analyticsService;
    }

    /**
     * 创建短链接。
     */
    @PostMapping
    public CreateShortLinkResponse createShortLink(
            @Valid @RequestBody CreateShortLinkRequest request
    ) {
        ShortLink shortLink = shortLinkService.createShortLink(
                request.getOriginalUrl(),
                request.getExpireAt()
        );

        CreateShortLinkResponse response = new CreateShortLinkResponse();
        response.setId(shortLink.getId());
        response.setShortCode(shortLink.getShortCode());
        response.setOriginalUrl(shortLink.getOriginalUrl());
        response.setManageToken(shortLink.getManageToken());
        response.setExpireAt(shortLink.getExpireAt());
        response.setShortUrl(
                ServletUriComponentsBuilder.fromCurrentContextPath()
                        .path("/{shortCode}")
                        .buildAndExpand(shortLink.getShortCode())
                        .toUriString()
        );

        return response;
    }

    /**
     * 查询短链接的已落库访问统计。
     */
    @GetMapping("/{shortCode}/stats")
    public ShortLinkStatisticsResponse getStatistics(
            @PathVariable String shortCode,
            @RequestHeader(value = "X-Manage-Token", required = false)
            String manageToken,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate to,
            @RequestParam(defaultValue = "day") String granularity
    ) {
        if (!"day".equals(granularity)) {
            throw new InvalidStatisticsRangeException(
                    "当前仅支持 day 粒度"
            );
        }

        StatisticsRange range = resolveStatisticsRange(from, to);

        ShortLink shortLink = shortLinkService.findShortLinkForStatistics(
                shortCode,
                manageToken
        );

        if (shortLink == null) {
            throw new ShortLinkNotFoundException();
        }

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

    /** 解析统计日期范围并校验最大查询天数。 */
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
            throw new InvalidStatisticsRangeException(
                    "开始日期不能晚于结束日期"
            );
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
