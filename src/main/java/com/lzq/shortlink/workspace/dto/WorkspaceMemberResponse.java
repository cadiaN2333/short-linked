package com.lzq.shortlink.workspace.dto;

import com.lzq.shortlink.workspace.WorkspaceMember;
import lombok.Data;

import java.time.LocalDateTime;

/** 工作空间成员响应，不暴露用户凭据等内部字段。 */
@Data
public class WorkspaceMemberResponse {

    private Long workspaceId;

    private Long userId;

    private String email;

    private String role;

    private LocalDateTime createdAt;

    public static WorkspaceMemberResponse from(WorkspaceMember member) {
        WorkspaceMemberResponse response = new WorkspaceMemberResponse();
        response.setWorkspaceId(member.getWorkspaceId());
        response.setUserId(member.getUserId());
        response.setEmail(member.getEmail());
        response.setRole(member.getRole());
        response.setCreatedAt(member.getCreatedAt());
        return response;
    }
}
