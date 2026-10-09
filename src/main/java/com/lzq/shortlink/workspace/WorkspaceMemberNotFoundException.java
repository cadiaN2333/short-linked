package com.lzq.shortlink.workspace;

/** 工作空间成员不存在。 */
public class WorkspaceMemberNotFoundException extends RuntimeException {

    public WorkspaceMemberNotFoundException() {
        super("工作空间成员不存在");
    }
}
