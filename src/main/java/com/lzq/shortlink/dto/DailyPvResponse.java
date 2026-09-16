package com.lzq.shortlink.dto;

import java.time.LocalDate;

/** 单日访问量趋势项。 */
public record DailyPvResponse(LocalDate date, long pv) {
}
