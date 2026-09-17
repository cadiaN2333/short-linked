package com.lzq.shortlink.workspace;

/** 当前用户无权访问目标工作空间。 */
public class WorkspaceAccessDeniedException extends RuntimeException {

    public WorkspaceAccessDeniedException() {
        super("当前用户无权访问该工作空间");
    }
}
