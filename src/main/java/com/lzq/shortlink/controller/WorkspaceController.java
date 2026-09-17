package com.lzq.shortlink.controller;

import com.lzq.shortlink.workspace.Workspace;
import com.lzq.shortlink.workspace.WorkspaceAccessService;
import com.lzq.shortlink.workspace.WorkspaceResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 工作空间查询接口。 */
@RestController
@RequestMapping("/api/v1/workspaces")
public class WorkspaceController {

    private final WorkspaceAccessService workspaceAccessService;

    public WorkspaceController(WorkspaceAccessService workspaceAccessService) {
        this.workspaceAccessService = workspaceAccessService;
    }

    @GetMapping
    public List<WorkspaceResponse> list() {
        return workspaceAccessService.listCurrentUserWorkspaces()
                .stream()
                .map(WorkspaceResponse::from)
                .toList();
    }

    @GetMapping("/{workspaceId}")
    public WorkspaceResponse get(@PathVariable Long workspaceId) {
        Workspace workspace = workspaceAccessService
                .requireAccessibleWorkspace(workspaceId);
        return WorkspaceResponse.from(workspace);
    }
}
