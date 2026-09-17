package com.lzq.shortlink.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lzq.shortlink.entity.AppUser;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface AppUserMapper extends BaseMapper<AppUser> {

    @Select("""
            SELECT id, email, password_hash, status,
                   created_at, updated_at, last_login_at
            FROM app_user
            WHERE email = #{email}
            LIMIT 1
            """)
    AppUser selectByEmail(@Param("email") String email);
}