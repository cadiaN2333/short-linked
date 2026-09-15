package com.lzq.shortlink.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 创建短链接响应结果。
 */
@Data
public class CreateShortLinkResponse {

    /** 数据库主键。 */
    private Long id;

    /** 生成后的短码。 */
    private String shortCode;

    /** 原始长链接。 */
    private String originalUrl;

    /** 过期时间，为 null 表示永久有效。 */
    private LocalDateTime expireAt;

    /** 完整短链接地址。 */
    private String shortUrl;
}