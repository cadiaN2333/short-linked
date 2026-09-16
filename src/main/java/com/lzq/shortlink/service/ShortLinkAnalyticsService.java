package com.lzq.shortlink.service;

import com.lzq.shortlink.dto.DailyPvResponse;

import java.time.LocalDate;
import java.util.List;

/** 短链接访问分析服务。 */
public interface ShortLinkAnalyticsService {

    /** 查询指定日期范围的每日 PV，并补齐没有记录的日期。 */
    List<DailyPvResponse> queryDailyPv(
            Long shortLinkId,
            LocalDate from,
            LocalDate to
    );
}
