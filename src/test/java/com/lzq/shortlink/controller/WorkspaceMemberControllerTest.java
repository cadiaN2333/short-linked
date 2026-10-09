package com.lzq.shortlink.controller;

import com.lzq.shortlink.workspace.WorkspaceMemberService;
import com.lzq.shortlink.workspace.dto.AddWorkspaceMemberRequest;
import com.lzq.shortlink.workspace.dto.ChangeWorkspaceMemberRoleRequest;
import com.lzq.shortlink.workspace.dto.WorkspaceMemberResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 工作空间成员 API 控制器测试。 */
@ExtendWith(MockitoExtension.class)
class WorkspaceMemberControllerTest {

    @Mock
    private WorkspaceMemberService workspaceMemberService;

    @Test
    void shouldReturnWorkspaceMembers() {
        WorkspaceMemberResponse member = member(100L, 42L, "OWNER");
        when(workspaceMemberService.listMembers(100L))
                .thenReturn(List.of(member));

        var response = new WorkspaceMemberController(workspaceMemberService)
                .list(100L);

        assertEquals(1, response.size());
        assertEquals("OWNER", response.get(0).getRole());
    }

    @Test
    void shouldAddExistingUserWithRequestedRole() {
        AddWorkspaceMemberRequest request = new AddWorkspaceMemberRequest();
        request.setEmail("member@example.com");
        request.setRole("MEMBER");
        WorkspaceMemberResponse member = member(100L, 77L, "MEMBER");
        when(workspaceMemberService.addMember(
                100L,
                "member@example.com",
                "MEMBER"
        )).thenReturn(member);

        var response = new WorkspaceMemberController(workspaceMemberService)
                .add(100L, request);

        assertEquals(77L, response.getUserId());
        assertEquals("MEMBER", response.getRole());
    }

    @Test
    void shouldUpdateMemberRole() {
        ChangeWorkspaceMemberRoleRequest request =
                new ChangeWorkspaceMemberRoleRequest();
        request.setRole("ADMIN");
        WorkspaceMemberResponse member = member(100L, 77L, "ADMIN");
        when(workspaceMemberService.updateRole(100L, 77L, "ADMIN"))
                .thenReturn(member);

        var response = new WorkspaceMemberController(workspaceMemberService)
                .updateRole(100L, 77L, request);

        assertEquals("ADMIN", response.getRole());
    }

    @Test
    void shouldRemoveMember() {
        WorkspaceMemberController controller =
                new WorkspaceMemberController(workspaceMemberService);

        controller.remove(100L, 77L);

        verify(workspaceMemberService).removeMember(100L, 77L);
    }

    private WorkspaceMemberResponse member(
            Long workspaceId,
            Long userId,
            String role
    ) {
        WorkspaceMemberResponse response = new WorkspaceMemberResponse();
        response.setWorkspaceId(workspaceId);
        response.setUserId(userId);
        response.setEmail("person@example.com");
        response.setRole(role);
        return response;
    }
}
