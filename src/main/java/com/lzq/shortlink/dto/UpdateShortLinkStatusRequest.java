package com.lzq.shortlink.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

/** 更新短链接启用状态请求。 */
@Getter
@Setter
public class UpdateShortLinkStatusRequest {

    /** 只允许启用或禁用，删除必须使用 DELETE 接口。 */
    @NotBlank(message = "短链接状态不能为空")
    @Pattern(
            regexp = "ACTIVE|DISABLED",
            message = "短链接状态只能是 ACTIVE 或 DISABLED"
    )
    private String status;
}
