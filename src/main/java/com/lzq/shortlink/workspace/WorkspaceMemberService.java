package com.lzq.shortlink.workspace;

import com.lzq.shortlink.entity.AppUser;
import com.lzq.shortlink.mapper.AppUserMapper;
import com.lzq.shortlink.workspace.dto.WorkspaceMemberResponse;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** 工作空间成员及角色管理。 */
@Service
public class WorkspaceMemberService {

    private final WorkspaceAccessService workspaceAccessService;
    private final WorkspaceMemberMapper workspaceMemberMapper;
    private final AppUserMapper appUserMapper;

    public WorkspaceMemberService(
            WorkspaceAccessService workspaceAccessService,
            WorkspaceMemberMapper workspaceMemberMapper,
            AppUserMapper appUserMapper
    ) {
        this.workspaceAccessService = workspaceAccessService;
        this.workspaceMemberMapper = workspaceMemberMapper;
        this.appUserMapper = appUserMapper;
    }

    /** 任意有效成员可查看成员列表。 */
    @Transactional(readOnly = true)
    public List<WorkspaceMemberResponse> listMembers(Long workspaceId) {
        workspaceAccessService.requireAccessibleWorkspace(workspaceId);
        return workspaceMemberMapper.selectMembersByWorkspaceId(workspaceId)
                .stream()
                .map(WorkspaceMemberResponse::from)
                .toList();
    }

    /** 将已有的有效用户添加到工作空间。 */
    @Transactional
    public WorkspaceMemberResponse addMember(
            Long workspaceId,
            String email,
            String requestedRole
    ) {
        workspaceAccessService.requireManager(workspaceId);
        WorkspaceRole role = WorkspaceRole.assignable(requestedRole);
        String normalizedEmail = email == null
                ? ""
                : email.trim().toLowerCase(Locale.ROOT);
        AppUser user = appUserMapper.selectByEmail(normalizedEmail);
        if (user == null || !"ACTIVE".equals(user.getStatus())) {
            throw new WorkspaceUserNotFoundException();
        }

        try {
            workspaceMemberMapper.insertMember(
                    workspaceId,
                    user.getId(),
                    role.name()
            );
        } catch (DuplicateKeyException exception) {
            throw new WorkspaceMemberAlreadyExistsException();
        }

        return WorkspaceMemberResponse.from(
                workspaceMemberMapper.selectMemberByWorkspaceAndUser(
                        workspaceId,
                        user.getId()
                )
        );
    }

    /** 修改非 OWNER 成员的角色。 */
    @Transactional
    public WorkspaceMemberResponse updateRole(
            Long workspaceId,
            Long userId,
            String requestedRole
    ) {
        workspaceAccessService.requireManager(workspaceId);
        WorkspaceRole role = WorkspaceRole.assignable(requestedRole);
        String existingRole = workspaceMemberMapper
                .selectRoleByWorkspaceAndUser(workspaceId, userId);
        if (existingRole == null) {
            throw new WorkspaceMemberNotFoundException();
        }
        WorkspaceRole currentRole = WorkspaceRole.from(existingRole);
        if (currentRole == null) {
            throw new WorkspaceMemberOperationDeniedException(
                    "目标成员角色无效，禁止修改"
            );
        }
        if (currentRole == WorkspaceRole.OWNER) {
            throw new WorkspaceMemberOperationDeniedException(
                    "不允许修改工作空间所有者角色"
            );
        }
        if (currentRole == role) {
            return WorkspaceMemberResponse.from(
                    workspaceMemberMapper.selectMemberByWorkspaceAndUser(
                            workspaceId,
                            userId
                    )
            );
        }

        int updated = workspaceMemberMapper.updateRoleByWorkspaceAndUser(
                workspaceId,
                userId,
                role.name()
        );
        if (updated != 1) {
            throw new WorkspaceMemberNotFoundException();
        }
        return WorkspaceMemberResponse.from(
                workspaceMemberMapper.selectMemberByWorkspaceAndUser(
                        workspaceId,
                        userId
                )
        );
    }

    /** 移除非 OWNER 成员，不允许请求者自行移除自己。 */
    @Transactional
    public void removeMember(Long workspaceId, Long userId) {
        workspaceAccessService.requireManager(workspaceId);
        if (Objects.equals(workspaceAccessService.currentUserId(), userId)) {
            throw new WorkspaceMemberOperationDeniedException(
                    "不能通过成员管理接口移除自己"
            );
        }

        String existingRole = workspaceMemberMapper
                .selectRoleByWorkspaceAndUser(workspaceId, userId);
        if (existingRole == null) {
            throw new WorkspaceMemberNotFoundException();
        }
        WorkspaceRole currentRole = WorkspaceRole.from(existingRole);
        if (currentRole == null) {
            throw new WorkspaceMemberOperationDeniedException(
                    "目标成员角色无效，禁止移除"
            );
        }
        if (currentRole == WorkspaceRole.OWNER) {
            throw new WorkspaceMemberOperationDeniedException(
                    "不允许移除工作空间所有者"
            );
        }

        int removed = workspaceMemberMapper.deleteNonOwnerMember(
                workspaceId,
                userId
        );
        if (removed != 1) {
            throw new WorkspaceMemberNotFoundException();
        }
    }
}
