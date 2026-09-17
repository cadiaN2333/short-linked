package com.lzq.shortlink.workspace;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class Workspace {

    private Long id;

    private String name;

    private Long ownerUserId;

    private String status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}