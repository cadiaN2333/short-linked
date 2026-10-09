package com.lzq.shortlink.workspace.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 修改工作空间成员角色的请求。 */
@Data
public class ChangeWorkspaceMemberRoleRequest {

    @NotBlank(message = "角色不能为空")
    private String role;
}
