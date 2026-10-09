package com.lzq.shortlink.workspace;

/** 当前角色不允许执行该成员管理操作。 */
public class WorkspaceMemberOperationDeniedException extends RuntimeException {

    public WorkspaceMemberOperationDeniedException(String message) {
        super(message);
    }
}
