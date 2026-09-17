package com.lzq.shortlink.workspace;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

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
}