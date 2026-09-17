package com.lzq.shortlink.workspace;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

/** 工作空间成员访问校验测试。 */
@ExtendWith(MockitoExtension.class)
class WorkspaceAccessServiceTest {

    @Mock
    private WorkspaceMapper workspaceMapper;

    private WorkspaceAccessService workspaceAccessService;

    @BeforeEach
    void setUp() {
        workspaceAccessService = new WorkspaceAccessService(workspaceMapper);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldReturnWorkspaceWhenCurrentUserIsMember() {
        authenticateAs("42");

        Workspace workspace = new Workspace();
        workspace.setId(100L);
        when(workspaceMapper.selectAccessibleById(100L, 42L))
                .thenReturn(workspace);

        Workspace result = workspaceAccessService
                .requireAccessibleWorkspace(100L);

        assertEquals(100L, result.getId());
    }

    @Test
    void shouldRejectWorkspaceOwnedByAnotherUser() {
        authenticateAs("42");
        when(workspaceMapper.selectAccessibleById(100L, 42L))
                .thenReturn(null);

        assertThrows(
                WorkspaceAccessDeniedException.class,
                () -> workspaceAccessService.requireAccessibleWorkspace(100L)
        );
    }

    @Test
    void shouldRejectInvalidJwtSubject() {
        authenticateAs("not-a-number");

        assertThrows(
                WorkspaceAccessDeniedException.class,
                () -> workspaceAccessService.requireAccessibleWorkspace(100L)
        );
    }

    private void authenticateAs(String subject) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(subject, null, List.of())
        );
    }
}
