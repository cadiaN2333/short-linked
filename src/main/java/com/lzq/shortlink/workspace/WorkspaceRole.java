package com.lzq.shortlink.workspace;

import java.util.Locale;

/** 工作空间内允许使用的固定角色。 */
public enum WorkspaceRole {
    OWNER,
    ADMIN,
    MEMBER;

    /** 将数据库中的角色字符串安全解析为已知角色。 */
    public static WorkspaceRole from(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    /** 解析可以授予普通成员的角色，不允许通过成员接口授予 OWNER。 */
    public static WorkspaceRole assignable(String value) {
        WorkspaceRole role = from(value);
        if (role == null || role == OWNER) {
            throw new InvalidWorkspaceRoleException();
        }
        return role;
    }

    /** 角色是否可以管理工作空间成员及短链接。 */
    public boolean canManage() {
        return this == OWNER || this == ADMIN;
    }
}
