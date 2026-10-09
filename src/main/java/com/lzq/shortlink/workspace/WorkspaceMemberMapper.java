package com.lzq.shortlink.workspace;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Delete;

public interface WorkspaceMemberMapper {

    @Select("""
            SELECT wm.workspace_id AS workspaceId,
                   wm.user_id AS userId,
                   u.email AS email,
                   wm.role AS role,
                   wm.created_at AS createdAt
            FROM workspace_member wm
            INNER JOIN app_user u ON u.id = wm.user_id
            WHERE wm.workspace_id = #{workspaceId}
            ORDER BY wm.created_at, wm.user_id
            """)
    java.util.List<WorkspaceMember> selectMembersByWorkspaceId(
            @Param("workspaceId") Long workspaceId
    );

    @Select("""
            SELECT wm.workspace_id AS workspaceId,
                   wm.user_id AS userId,
                   u.email AS email,
                   wm.role AS role,
                   wm.created_at AS createdAt
            FROM workspace_member wm
            INNER JOIN app_user u ON u.id = wm.user_id
            WHERE wm.workspace_id = #{workspaceId}
              AND wm.user_id = #{userId}
            LIMIT 1
            """)
    WorkspaceMember selectMemberByWorkspaceAndUser(
            @Param("workspaceId") Long workspaceId,
            @Param("userId") Long userId
    );

    @Select("""
            SELECT wm.role
            FROM workspace_member wm
            INNER JOIN workspace w ON w.id = wm.workspace_id
            WHERE wm.workspace_id = #{workspaceId}
              AND wm.user_id = #{userId}
              AND w.status = 'ACTIVE'
            LIMIT 1
            """)
    String selectRoleByWorkspaceAndUser(
            @Param("workspaceId") Long workspaceId,
            @Param("userId") Long userId
    );

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

    @Insert("""
            INSERT INTO workspace_member (
                workspace_id, user_id, role
            ) VALUES (
                #{workspaceId}, #{userId}, #{role}
            )
            """)
    int insertMember(
            @Param("workspaceId") Long workspaceId,
            @Param("userId") Long userId,
            @Param("role") String role
    );

    @Update("""
            UPDATE workspace_member
            SET role = #{role}
            WHERE workspace_id = #{workspaceId}
              AND user_id = #{userId}
              AND role <> 'OWNER'
            """)
    int updateRoleByWorkspaceAndUser(
            @Param("workspaceId") Long workspaceId,
            @Param("userId") Long userId,
            @Param("role") String role
    );

    @Delete("""
            DELETE FROM workspace_member
            WHERE workspace_id = #{workspaceId}
              AND user_id = #{userId}
              AND role <> 'OWNER'
            """)
    int deleteNonOwnerMember(
            @Param("workspaceId") Long workspaceId,
            @Param("userId") Long userId
    );
}
