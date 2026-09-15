package com.lzq.shortlink.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 统一错误响应。
 */
@Getter
@AllArgsConstructor
public class ApiErrorResponse {

    /** 业务错误码。 */
    private final String code;

    /** 面向调用方的错误说明。 */
    private final String message;

    /** 错误发生时间。 */
    private final LocalDateTime timestamp;
}
