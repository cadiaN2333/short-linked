package com.lzq.shortlink.mapper;

import java.time.LocalDate;

/** 每日统计查询结果。 */
public record ShortLinkDailyStatRow(LocalDate statDate, Long pv) {
}
