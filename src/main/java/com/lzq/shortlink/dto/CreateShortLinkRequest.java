// 替换：src/main/java/com/lzq/shortlink/dto/CreateShortLinkRequest.java
package com.lzq.shortlink.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 创建或编辑短链接的请求参数。 */
@Getter
@Setter
public class CreateShortLinkRequest {

    @NotBlank(message = "原始链接不能为空")
    @Size(max = 2048, message = "原始链接长度不能超过 2048 个字符")
    private String originalUrl;

    /** 可选自定义短码；省略时生成随机短码。 */
    @Size(min = 3, max = 16, message = "自定义短码长度必须在 3 到 16 个字符之间")
    @Pattern(
            regexp = "^[0-9A-Za-z_-]+$",
            message = "自定义短码只能包含数字、字母、下划线和连字符"
    )
    private String shortCode;

    @Future(message = "过期时间必须晚于当前时间")
    private LocalDateTime expireAt;
}