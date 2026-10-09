package com.lzq.shortlink.controller;

import com.lzq.shortlink.workspace.WorkspaceMemberService;
import com.lzq.shortlink.workspace.dto.AddWorkspaceMemberRequest;
import com.lzq.shortlink.workspace.dto.ChangeWorkspaceMemberRoleRequest;
import com.lzq.shortlink.workspace.dto.WorkspaceMemberResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 工作空间成员查询与管理接口。 */
@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/members")
public class WorkspaceMemberController {

    private final WorkspaceMemberService workspaceMemberService;

    public WorkspaceMemberController(
            WorkspaceMemberService workspaceMemberService
    ) {
        this.workspaceMemberService = workspaceMemberService;
    }

    @GetMapping
    public List<WorkspaceMemberResponse> list(
            @PathVariable Long workspaceId
    ) {
        return workspaceMemberService.listMembers(workspaceId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WorkspaceMemberResponse add(
            @PathVariable Long workspaceId,
            @Valid @RequestBody AddWorkspaceMemberRequest request
    ) {
        return workspaceMemberService.addMember(
                workspaceId,
                request.getEmail(),
                request.getRole()
        );
    }

    @PatchMapping("/{userId}/role")
    public WorkspaceMemberResponse updateRole(
            @PathVariable Long workspaceId,
            @PathVariable Long userId,
            @Valid @RequestBody ChangeWorkspaceMemberRoleRequest request
    ) {
        return workspaceMemberService.updateRole(
                workspaceId,
                userId,
                request.getRole()
        );
    }

    @DeleteMapping("/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(
            @PathVariable Long workspaceId,
            @PathVariable Long userId
    ) {
        workspaceMemberService.removeMember(workspaceId, userId);
    }
}
