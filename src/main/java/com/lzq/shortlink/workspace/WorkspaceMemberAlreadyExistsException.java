package com.lzq.shortlink.workspace;

/** 用户已加入该工作空间。 */
public class WorkspaceMemberAlreadyExistsException extends RuntimeException {

    public WorkspaceMemberAlreadyExistsException() {
        super("该用户已是工作空间成员");
    }
}
