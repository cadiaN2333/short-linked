package com.lzq.shortlink.workspace;

import lombok.Data;

import java.time.LocalDateTime;

/** 工作空间接口响应。 */
@Data
public class WorkspaceResponse {

    private Long id;

    private String name;

    private Long ownerUserId;

    private String status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public static WorkspaceResponse from(Workspace workspace) {
        WorkspaceResponse response = new WorkspaceResponse();
        response.setId(workspace.getId());
        response.setName(workspace.getName());
        response.setOwnerUserId(workspace.getOwnerUserId());
        response.setStatus(workspace.getStatus());
        response.setCreatedAt(workspace.getCreatedAt());
        response.setUpdatedAt(workspace.getUpdatedAt());
        return response;
    }
}
