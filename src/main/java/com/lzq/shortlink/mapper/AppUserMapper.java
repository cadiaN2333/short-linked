package com.lzq.shortlink.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lzq.shortlink.entity.AppUser;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface AppUserMapper extends BaseMapper<AppUser> {

    @Select("""
            SELECT id, email, password_hash, status,
                   created_at, updated_at, last_login_at
            FROM app_user
            WHERE email = #{email}
            LIMIT 1
            """)
    AppUser selectByEmail(@Param("email") String email);

    /** 只更新最近登录时间，避免登录时回写用户全字段。 */
    @Update("""
            UPDATE app_user
            SET last_login_at = #{lastLoginAt}
            WHERE id = #{userId}
            """)
    int updateLastLoginAt(
            @Param("userId") Long userId,
            @Param("lastLoginAt") java.time.LocalDateTime lastLoginAt
    );
}
