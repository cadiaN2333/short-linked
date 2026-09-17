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

    /** 可选自定义短码；为空时由系统生成随机短码。 */
    @Size(
            min = 3,
            max = 16,
            message = "自定义短码长度必须在 3 到 16 个字符之间"
    )
    @Pattern(
            regexp = "^[0-9A-Za-z_-]+$",
            message = "自定义短码只能包含数字、字母、下划线和连字符"
    )
    private String shortCode;

    @Future(message = "过期时间必须晚于当前时间")
    private LocalDateTime expireAt;
}
