package com.lzq.shortlink.workspace;

/** 请求的工作空间角色不受支持。 */
public class InvalidWorkspaceRoleException extends RuntimeException {

    public InvalidWorkspaceRoleException() {
        super("角色仅支持 ADMIN 或 MEMBER");
    }
}
