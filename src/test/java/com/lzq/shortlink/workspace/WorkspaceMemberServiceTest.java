package com.lzq.shortlink.workspace;

import com.lzq.shortlink.entity.AppUser;
import com.lzq.shortlink.mapper.AppUserMapper;
import com.lzq.shortlink.workspace.dto.WorkspaceMemberResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 工作空间成员管理业务测试。 */
@ExtendWith(MockitoExtension.class)
class WorkspaceMemberServiceTest {

    @Mock
    private WorkspaceAccessService workspaceAccessService;

    @Mock
    private WorkspaceMemberMapper workspaceMemberMapper;

    @Mock
    private AppUserMapper appUserMapper;

    @Test
    void shouldListMembersAfterCheckingWorkspaceAccess() {
        WorkspaceMember owner = member(100L, 42L, "OWNER", "owner@example.com");
        when(workspaceMemberMapper.selectMembersByWorkspaceId(100L))
                .thenReturn(List.of(owner));

        List<WorkspaceMemberResponse> members = service().listMembers(100L);

        assertEquals(1, members.size());
        assertEquals("owner@example.com", members.get(0).getEmail());
        verify(workspaceAccessService).requireAccessibleWorkspace(100L);
    }

    @Test
    void shouldAddExistingUserWithAllowedRole() {
        AppUser user = user(77L, "member@example.com");
        when(appUserMapper.selectByEmail("member@example.com")).thenReturn(user);
        when(workspaceMemberMapper.selectMemberByWorkspaceAndUser(100L, 77L))
                .thenReturn(member(
                        100L,
                        77L,
                        "MEMBER",
                        "member@example.com"
                ));

        service().addMember(100L, " MEMBER@example.com ", "MEMBER");

        verify(workspaceAccessService).requireManager(100L);
        verify(workspaceMemberMapper).insertMember(100L, 77L, "MEMBER");
    }

    @Test
    void shouldRejectGrantingOwnerRole() {
        assertThrows(
                InvalidWorkspaceRoleException.class,
                () -> service().addMember(100L, "member@example.com", "OWNER")
        );
        verify(workspaceMemberMapper, never())
                .insertMember(100L, 77L, "OWNER");
    }

    @Test
    void shouldRejectUnknownRole() {
        assertThrows(
                InvalidWorkspaceRoleException.class,
                () -> service().addMember(100L, "member@example.com", "CUSTOM")
        );
        verify(appUserMapper, never()).selectByEmail("member@example.com");
    }

    @Test
    void shouldRejectUnknownUserEmail() {
        when(appUserMapper.selectByEmail("missing@example.com"))
                .thenReturn(null);

        assertThrows(
                WorkspaceUserNotFoundException.class,
                () -> service().addMember(
                        100L,
                        "missing@example.com",
                        "MEMBER"
                )
        );
        verify(workspaceMemberMapper, never())
                .insertMember(100L, 77L, "MEMBER");
    }

    @Test
    void shouldTranslateDuplicateMembership() {
        AppUser user = user(77L, "member@example.com");
        when(appUserMapper.selectByEmail("member@example.com")).thenReturn(user);
        when(workspaceMemberMapper.insertMember(100L, 77L, "MEMBER"))
                .thenThrow(new DuplicateKeyException("duplicate"));

        assertThrows(
                WorkspaceMemberAlreadyExistsException.class,
                () -> service().addMember(100L, "member@example.com", "MEMBER")
        );
    }

    @Test
    void shouldNotChangeOwnerRole() {
        when(workspaceMemberMapper.selectRoleByWorkspaceAndUser(100L, 42L))
                .thenReturn("OWNER");

        assertThrows(
                WorkspaceMemberOperationDeniedException.class,
                () -> service().updateRole(100L, 42L, "ADMIN")
        );
        verify(workspaceMemberMapper, never())
                .updateRoleByWorkspaceAndUser(100L, 42L, "ADMIN");
    }

    @Test
    void shouldChangeRoleOfNonOwnerMember() {
        when(workspaceMemberMapper.selectRoleByWorkspaceAndUser(100L, 77L))
                .thenReturn("MEMBER");
        when(workspaceMemberMapper.updateRoleByWorkspaceAndUser(
                100L,
                77L,
                "ADMIN"
        )).thenReturn(1);
        when(workspaceMemberMapper.selectMemberByWorkspaceAndUser(100L, 77L))
                .thenReturn(member(
                        100L,
                        77L,
                        "ADMIN",
                        "member@example.com"
                ));

        var response = service().updateRole(100L, 77L, "ADMIN");

        assertEquals("ADMIN", response.getRole());
        verify(workspaceAccessService).requireManager(100L);
        verify(workspaceMemberMapper).updateRoleByWorkspaceAndUser(
                100L,
                77L,
                "ADMIN"
        );
    }

    @Test
    void shouldRejectChangingMissingMemberRole() {
        when(workspaceMemberMapper.selectRoleByWorkspaceAndUser(100L, 77L))
                .thenReturn(null);

        assertThrows(
                WorkspaceMemberNotFoundException.class,
                () -> service().updateRole(100L, 77L, "ADMIN")
        );
        verify(workspaceMemberMapper, never())
                .updateRoleByWorkspaceAndUser(100L, 77L, "ADMIN");
    }

    @Test
    void shouldTreatReapplyingSameRoleAsSuccessful() {
        when(workspaceMemberMapper.selectRoleByWorkspaceAndUser(100L, 77L))
                .thenReturn("MEMBER");
        when(workspaceMemberMapper.selectMemberByWorkspaceAndUser(100L, 77L))
                .thenReturn(member(
                        100L,
                        77L,
                        "MEMBER",
                        "member@example.com"
                ));

        var response = service().updateRole(100L, 77L, "MEMBER");

        assertEquals("MEMBER", response.getRole());
        verify(workspaceMemberMapper, never())
                .updateRoleByWorkspaceAndUser(100L, 77L, "MEMBER");
    }

    @Test
    void shouldRejectOperatingOnMemberWithUnknownRole() {
        when(workspaceMemberMapper.selectRoleByWorkspaceAndUser(100L, 77L))
                .thenReturn("CUSTOM");

        assertThrows(
                WorkspaceMemberOperationDeniedException.class,
                () -> service().updateRole(100L, 77L, "ADMIN")
        );
        verify(workspaceMemberMapper, never())
                .updateRoleByWorkspaceAndUser(100L, 77L, "ADMIN");
    }

    @Test
    void shouldRemoveNonOwnerMember() {
        when(workspaceAccessService.currentUserId()).thenReturn(42L);
        when(workspaceMemberMapper.selectRoleByWorkspaceAndUser(100L, 77L))
                .thenReturn("MEMBER");
        when(workspaceMemberMapper.deleteNonOwnerMember(100L, 77L))
                .thenReturn(1);

        service().removeMember(100L, 77L);

        verify(workspaceAccessService).requireManager(100L);
        verify(workspaceMemberMapper).deleteNonOwnerMember(100L, 77L);
    }

    @Test
    void shouldNotRemoveCurrentUser() {
        when(workspaceAccessService.currentUserId()).thenReturn(42L);

        assertThrows(
                WorkspaceMemberOperationDeniedException.class,
                () -> service().removeMember(100L, 42L)
        );
        verify(workspaceMemberMapper, never())
                .deleteNonOwnerMember(100L, 42L);
    }

    private WorkspaceMemberService service() {
        return new WorkspaceMemberService(
                workspaceAccessService,
                workspaceMemberMapper,
                appUserMapper
        );
    }

    private AppUser user(Long id, String email) {
        AppUser user = new AppUser();
        user.setId(id);
        user.setEmail(email);
        user.setStatus("ACTIVE");
        return user;
    }

    private WorkspaceMember member(
            Long workspaceId,
            Long userId,
            String role,
            String email
    ) {
        WorkspaceMember member = new WorkspaceMember();
        member.setWorkspaceId(workspaceId);
        member.setUserId(userId);
        member.setRole(role);
        member.setEmail(email);
        return member;
    }
}
