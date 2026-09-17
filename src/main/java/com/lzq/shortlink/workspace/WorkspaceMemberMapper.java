package com.lzq.shortlink.workspace;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

public interface WorkspaceMemberMapper {

    @Insert("""
            INSERT INTO workspace_member (
                workspace_id, user_id, role
            ) VALUES (
                #{workspaceId}, #{userId}, 'OWNER'
            )
            """)
    int insertOwner(
            @Param("workspaceId") Long workspaceId,
            @Param("userId") Long userId
    );
}