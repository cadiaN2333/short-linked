package com.lzq.shortlink.workspace;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class WorkspaceMember {

    private Long workspaceId;

    private Long userId;

    private String role;

    private LocalDateTime createdAt;
}