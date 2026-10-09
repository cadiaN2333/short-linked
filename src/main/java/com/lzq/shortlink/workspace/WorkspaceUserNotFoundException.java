package com.lzq.shortlink.workspace;

/** 未找到可加入工作空间的有效用户。 */
public class WorkspaceUserNotFoundException extends RuntimeException {

    public WorkspaceUserNotFoundException() {
        super("未找到有效用户");
    }
}
