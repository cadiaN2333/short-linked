package com.lzq.shortlink.service.impl;

import com.lzq.shortlink.dto.DailyPvResponse;
import com.lzq.shortlink.exception.InvalidStatisticsRangeException;
import com.lzq.shortlink.mapper.ShortLinkDailyStatMapper;
import com.lzq.shortlink.mapper.ShortLinkDailyStatRow;
import com.lzq.shortlink.service.ShortLinkAnalyticsService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 短链接访问分析服务实现。 */
@Service
public class ShortLinkAnalyticsServiceImpl
        implements ShortLinkAnalyticsService {

    private final ShortLinkDailyStatMapper mapper;

    public ShortLinkAnalyticsServiceImpl(ShortLinkDailyStatMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<DailyPvResponse> queryDailyPv(
            Long shortLinkId,
            LocalDate from,
            LocalDate to
    ) {
        if (from == null || to == null) {
            throw new InvalidStatisticsRangeException(
                    "统计开始日期和结束日期不能为空"
            );
        }
        if (from.isAfter(to)) {
            throw new InvalidStatisticsRangeException(
                    "开始日期不能晚于结束日期"
            );
        }

        long days = ChronoUnit.DAYS.between(from, to) + 1;
        if (days > 90) {
            throw new InvalidStatisticsRangeException(
                    "统计范围不能超过 90 天"
            );
        }

        Map<LocalDate, Long> pvByDate = mapper
                .selectByShortLinkIdAndDateBetween(shortLinkId, from, to)
                .stream()
                .collect(Collectors.toMap(
                        ShortLinkDailyStatRow::statDate,
                        row -> row.pv() == null ? 0L : row.pv(),
                        (first, second) -> {
                            throw new IllegalStateException(
                                    "同一短链接存在重复统计日期"
                            );
                        }
                ));

        List<DailyPvResponse> result = new ArrayList<>();
        for (LocalDate date = from;
             !date.isAfter(to);
             date = date.plusDays(1)) {
            result.add(new DailyPvResponse(
                    date,
                    pvByDate.getOrDefault(date, 0L)
            ));
        }
        return result;
    }
}
