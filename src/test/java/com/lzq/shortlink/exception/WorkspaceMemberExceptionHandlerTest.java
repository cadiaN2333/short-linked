package com.lzq.shortlink.exception;

import com.lzq.shortlink.workspace.InvalidWorkspaceRoleException;
import com.lzq.shortlink.workspace.WorkspaceMemberAlreadyExistsException;
import com.lzq.shortlink.workspace.WorkspaceMemberNotFoundException;
import com.lzq.shortlink.workspace.WorkspaceMemberOperationDeniedException;
import com.lzq.shortlink.workspace.WorkspaceUserNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 工作空间成员业务错误响应测试。 */
class WorkspaceMemberExceptionHandlerTest {

    private final GlobalExceptionHandler handler =
            new GlobalExceptionHandler();

    @Test
    void shouldReturn400ForUnsupportedRole() {
        var response = handler.handleInvalidWorkspaceRole(
                new InvalidWorkspaceRoleException()
        );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("INVALID_WORKSPACE_ROLE", response.getBody().getCode());
    }

    @Test
    void shouldReturn403ForProtectedMemberOperation() {
        var response = handler.handleWorkspaceMemberOperationDenied(
                new WorkspaceMemberOperationDeniedException("禁止操作")
        );

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals(
                "WORKSPACE_MEMBER_OPERATION_DENIED",
                response.getBody().getCode()
        );
    }

    @Test
    void shouldReturn404ForMissingUserOrMember() {
        assertEquals(
                HttpStatus.NOT_FOUND,
                handler.handleWorkspaceUserNotFound(
                        new WorkspaceUserNotFoundException()
                ).getStatusCode()
        );
        assertEquals(
                HttpStatus.NOT_FOUND,
                handler.handleWorkspaceMemberNotFound(
                        new WorkspaceMemberNotFoundException()
                ).getStatusCode()
        );
    }

    @Test
    void shouldReturn409ForDuplicateMembership() {
        var response = handler.handleWorkspaceMemberAlreadyExists(
                new WorkspaceMemberAlreadyExistsException()
        );

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals(
                "WORKSPACE_MEMBER_ALREADY_EXISTS",
                response.getBody().getCode()
        );
    }
}
