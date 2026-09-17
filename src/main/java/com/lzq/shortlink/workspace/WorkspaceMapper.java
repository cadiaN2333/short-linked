package com.lzq.shortlink.workspace;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface WorkspaceMapper {

    @Insert("""
            INSERT INTO workspace (
                name, owner_user_id, status
            ) VALUES (
                #{name}, #{ownerUserId}, #{status}
            )
            """)
    @Options(
            useGeneratedKeys = true,
            keyProperty = "id",
            keyColumn = "id"
    )
    int insert(Workspace workspace);

    @Select("""
            SELECT id, name, owner_user_id,
                   status, created_at, updated_at
            FROM workspace
            WHERE owner_user_id = #{ownerUserId}
              AND status = 'ACTIVE'
            LIMIT 1
            """)
    Workspace selectActiveByOwnerUserId(
            @Param("ownerUserId") Long ownerUserId
    );

    @Select("""
            SELECT w.id, w.name, w.owner_user_id,
                   w.status, w.created_at, w.updated_at
            FROM workspace w
            INNER JOIN workspace_member wm
                    ON wm.workspace_id = w.id
            WHERE wm.user_id = #{userId}
              AND w.status = 'ACTIVE'
            ORDER BY w.id
            """)
    List<Workspace> selectActiveByUserId(
            @Param("userId") Long userId
    );

    @Select("""
            SELECT w.id, w.name, w.owner_user_id,
                   w.status, w.created_at, w.updated_at
            FROM workspace w
            INNER JOIN workspace_member wm
                    ON wm.workspace_id = w.id
            WHERE w.id = #{workspaceId}
              AND wm.user_id = #{userId}
              AND w.status = 'ACTIVE'
            LIMIT 1
            """)
    Workspace selectAccessibleById(
            @Param("workspaceId") Long workspaceId,
            @Param("userId") Long userId
    );
}
