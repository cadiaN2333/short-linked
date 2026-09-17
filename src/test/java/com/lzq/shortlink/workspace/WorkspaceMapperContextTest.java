package com.lzq.shortlink.workspace;

import com.lzq.shortlink.ShortLinkApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/** 工作空间 Mapper 应被 Spring 扫描并注册为 Bean。 */
@SpringBootTest(classes = ShortLinkApplication.class)
class WorkspaceMapperContextTest {

    @Autowired
    private WorkspaceMapper workspaceMapper;

    @Autowired
    private WorkspaceMemberMapper workspaceMemberMapper;

    @Test
    void shouldRegisterWorkspaceMappers() {
        assertNotNull(workspaceMapper);
        assertNotNull(workspaceMemberMapper);
    }
}
