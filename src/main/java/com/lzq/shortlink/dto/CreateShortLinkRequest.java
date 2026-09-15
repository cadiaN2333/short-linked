package com.lzq.shortlink.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDateTime;

/**
 * 创建短链接请求参数。
 */
@Getter
@Setter
public class CreateShortLinkRequest {

    private static final int MAX_URL_LENGTH = 2048;

    @NotBlank(message = "原始链接不能为空")
    @Size(
            max = MAX_URL_LENGTH,
            message = "原始链接长度不能超过 " + MAX_URL_LENGTH + " 个字符"
    )
    @Pattern(
            regexp = "^https?://\\S+$",
            message = "原始链接必须是 HTTP 或 HTTPS 地址"
    )
    private String originalUrl;

    @Future(message = "过期时间必须晚于当前时间")
    private LocalDateTime expireAt;
}