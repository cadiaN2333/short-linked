package com.lzq.shortlink.workspace;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;

/** 集中处理当前用户与工作空间之间的访问边界。 */
@Service
public class WorkspaceAccessService {

    private final WorkspaceMapper workspaceMapper;

    public WorkspaceAccessService(WorkspaceMapper workspaceMapper) {
        this.workspaceMapper = workspaceMapper;
    }

    /** 查询当前用户可以访问的全部启用工作空间。 */
    public List<Workspace> listCurrentUserWorkspaces() {
        return workspaceMapper.selectActiveByUserId(currentUserId());
    }

    /** 校验当前用户可访问指定工作空间，并返回工作空间实体。 */
    public Workspace requireAccessibleWorkspace(Long workspaceId) {
        if (workspaceId == null) {
            throw new WorkspaceAccessDeniedException();
        }

        Workspace workspace = workspaceMapper.selectAccessibleById(
                workspaceId,
                currentUserId()
        );

        if (workspace == null) {
            throw new WorkspaceAccessDeniedException();
        }

        return workspace;
    }

    /** 读取 JWT subject 中的当前用户 ID。 */
    public Long currentUserId() {
        Authentication authentication = SecurityContextHolder
                .getContext()
                .getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication.getName() == null) {
            throw new WorkspaceAccessDeniedException();
        }

        try {
            return Long.valueOf(authentication.getName());
        } catch (NumberFormatException exception) {
            throw new WorkspaceAccessDeniedException();
        }
    }
}
