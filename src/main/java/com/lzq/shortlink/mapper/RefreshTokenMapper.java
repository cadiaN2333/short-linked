package com.lzq.shortlink.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lzq.shortlink.entity.RefreshToken;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface RefreshTokenMapper extends BaseMapper<RefreshToken> {

    @Select("""
            SELECT id, user_id, token_hash,
                   expires_at, revoked_at, created_at
            FROM refresh_token
            WHERE token_hash = #{tokenHash}
            LIMIT 1
            """)
    RefreshToken selectByTokenHash(
            @Param("tokenHash") String tokenHash
    );

    @Update("""
            UPDATE refresh_token
            SET revoked_at = NOW()
            WHERE id = #{id}
              AND revoked_at IS NULL
            """)
    int revokeById(@Param("id") Long id);
}