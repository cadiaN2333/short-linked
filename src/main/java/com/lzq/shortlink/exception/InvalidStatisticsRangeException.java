package com.lzq.shortlink.exception;

/** 统计查询日期或粒度不合法。 */
public class InvalidStatisticsRangeException extends RuntimeException {

    public InvalidStatisticsRangeException(String message) {
        super(message);
    }
}
