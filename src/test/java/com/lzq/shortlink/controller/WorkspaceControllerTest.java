package com.lzq.shortlink.controller;

import com.lzq.shortlink.entity.ShortLink;
import com.lzq.shortlink.service.ShortLinkService;
import com.lzq.shortlink.service.ShortLinkPageResult;
import com.lzq.shortlink.workspace.Workspace;
import com.lzq.shortlink.workspace.WorkspaceAccessDeniedException;
import com.lzq.shortlink.workspace.WorkspaceAccessService;
import com.lzq.shortlink.workspace.WorkspaceResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 工作空间和工作空间短链接接口测试。 */
@ExtendWith(MockitoExtension.class)
class WorkspaceControllerTest {

    @Mock
    private WorkspaceAccessService workspaceAccessService;

    @Mock
    private ShortLinkService shortLinkService;

    @Test
    void shouldListCurrentUserWorkspaces() {
        Workspace workspace = workspace(100L, "Demo 的工作空间");
        when(workspaceAccessService.listCurrentUserWorkspaces())
                .thenReturn(List.of(workspace));

        WorkspaceController controller = new WorkspaceController(
                workspaceAccessService
        );

        List<WorkspaceResponse> response = controller.list();

        assertEquals(1, response.size());
        assertEquals(100L, response.get(0).getId());
        assertEquals("Demo 的工作空间", response.get(0).getName());
    }

    @Test
    void shouldRejectWorkspaceDetailsWhenUserIsNotMember() {
        when(workspaceAccessService.requireAccessibleWorkspace(100L))
                .thenThrow(new WorkspaceAccessDeniedException());

        WorkspaceController controller = new WorkspaceController(
                workspaceAccessService
        );

        assertThrows(
                WorkspaceAccessDeniedException.class,
                () -> controller.get(100L)
        );
    }

    @Test
    void shouldCreateShortLinkInsideAccessibleWorkspace() {
        Workspace workspace = workspace(100L, "Demo 的工作空间");
        when(workspaceAccessService.requireAccessibleWorkspace(100L))
                .thenReturn(workspace);

        ShortLink shortLink = new ShortLink();
        shortLink.setId(10L);
        shortLink.setWorkspaceId(100L);
        shortLink.setShortCode("abc12345");
        shortLink.setOriginalUrl("https://example.com");
        when(shortLinkService.createShortLink(
                100L,
                "https://example.com",
                null
        )).thenReturn(shortLink);

        com.lzq.shortlink.dto.CreateShortLinkRequest request =
                new com.lzq.shortlink.dto.CreateShortLinkRequest();
        request.setOriginalUrl("https://example.com");

        WorkspaceLinkController controller = new WorkspaceLinkController(
                workspaceAccessService,
                shortLinkService
        );

        com.lzq.shortlink.dto.CreateShortLinkResponse response =
                controller.create(100L, request);

        assertEquals(10L, response.getId());
        assertEquals(100L, response.getWorkspaceId());
        verify(workspaceAccessService).requireAccessibleWorkspace(100L);
    }

    @Test
    void shouldCreateShortLinkWithCustomShortCode() {
        Workspace workspace = workspace(100L, "Demo 的工作空间");
        when(workspaceAccessService.requireAccessibleWorkspace(100L))
                .thenReturn(workspace);

        ShortLink shortLink = new ShortLink();
        shortLink.setId(11L);
        shortLink.setWorkspaceId(100L);
        shortLink.setShortCode("promo2026");
        shortLink.setOriginalUrl("https://example.com/custom");
        when(shortLinkService.createShortLink(
                100L,
                "https://example.com/custom",
                null,
                "promo2026"
        )).thenReturn(shortLink);

        com.lzq.shortlink.dto.CreateShortLinkRequest request =
                new com.lzq.shortlink.dto.CreateShortLinkRequest();
        request.setOriginalUrl("https://example.com/custom");
        request.setShortCode("promo2026");

        WorkspaceLinkController controller = new WorkspaceLinkController(
                workspaceAccessService,
                shortLinkService
        );

        com.lzq.shortlink.dto.CreateShortLinkResponse response =
                controller.create(100L, request);

        assertEquals("promo2026", response.getShortCode());
        verify(shortLinkService).createShortLink(
                100L,
                "https://example.com/custom",
                null,
                "promo2026"
        );
    }

    @Test
    void shouldListShortLinksInsideWorkspaceWithPagination() {
        when(workspaceAccessService.requireAccessibleWorkspace(100L))
                .thenReturn(workspace(100L, "Demo 的工作空间"));

        ShortLink shortLink = new ShortLink();
        shortLink.setId(10L);
        shortLink.setWorkspaceId(100L);
        shortLink.setShortCode("abc12345");
        shortLink.setOriginalUrl("https://example.com");
        when(shortLinkService.listShortLinks(100L, 1, 20))
                .thenReturn(new ShortLinkPageResult(
                        List.of(shortLink),
                        1L,
                        1,
                        20
                ));

        WorkspaceLinkController controller = new WorkspaceLinkController(
                workspaceAccessService,
                shortLinkService
        );

        com.lzq.shortlink.dto.ShortLinkPageResponse response = controller.list(
                100L,
                1,
                20
        );

        assertEquals(1L, response.getTotal());
        assertEquals(1, response.getItems().size());
        assertEquals("abc12345", response.getItems().get(0).getShortCode());
        verify(shortLinkService).listShortLinks(100L, 1, 20);
    }

    @Test
    void shouldPassStatusAndKeywordFiltersToService() {
        when(workspaceAccessService.requireAccessibleWorkspace(100L))
                .thenReturn(workspace(100L, "Demo 的工作空间"));
        when(shortLinkService.listShortLinks(
                100L,
                1,
                20,
                "DISABLED",
                "disabled"
        )).thenReturn(new ShortLinkPageResult(
                List.of(),
                0L,
                1,
                20
        ));

        WorkspaceLinkController controller = new WorkspaceLinkController(
                workspaceAccessService,
                shortLinkService
        );

        com.lzq.shortlink.dto.ShortLinkPageResponse response = controller.list(
                100L,
                1,
                20,
                "DISABLED",
                "disabled"
        );

        assertEquals(0L, response.getTotal());
        verify(shortLinkService).listShortLinks(
                100L,
                1,
                20,
                "DISABLED",
                "disabled"
        );
    }

    @Test
    void shouldReturnShortLinkDetailInsideWorkspace() {
        when(workspaceAccessService.requireAccessibleWorkspace(100L))
                .thenReturn(workspace(100L, "Demo 的工作空间"));

        ShortLink shortLink = new ShortLink();
        shortLink.setId(10L);
        shortLink.setWorkspaceId(100L);
        shortLink.setShortCode("abc12345");
        shortLink.setOriginalUrl("https://example.com");
        when(shortLinkService.findShortLinkById(100L, 10L))
                .thenReturn(shortLink);

        WorkspaceLinkController controller = new WorkspaceLinkController(
                workspaceAccessService,
                shortLinkService
        );

        com.lzq.shortlink.dto.ShortLinkListItemResponse response = controller.get(
                100L,
                10L
        );

        assertEquals(10L, response.getId());
        assertEquals("https://example.com", response.getOriginalUrl());
    }

    @Test
    void shouldRejectInvalidPageSize() {
        WorkspaceLinkController controller = new WorkspaceLinkController(
                workspaceAccessService,
                shortLinkService
        );

        assertThrows(
                com.lzq.shortlink.exception.InvalidPaginationException.class,
                () -> controller.list(100L, 1, 101)
        );
    }

    @Test
    void shouldUpdateShortLinkInsideWorkspace() {
        when(workspaceAccessService.requireAccessibleWorkspace(100L))
                .thenReturn(workspace(100L, "Demo 的工作空间"));
        ShortLink shortLink = new ShortLink();
        shortLink.setId(10L);
        shortLink.setWorkspaceId(100L);
        shortLink.setShortCode("abc12345");
        shortLink.setOriginalUrl("https://example.com/new");
        shortLink.setStatus("ACTIVE");
        when(shortLinkService.updateShortLink(
                100L,
                10L,
                "https://example.com/new",
                null
        )).thenReturn(shortLink);

        WorkspaceLinkController controller = new WorkspaceLinkController(
                workspaceAccessService,
                shortLinkService
        );
        com.lzq.shortlink.dto.CreateShortLinkRequest request =
                new com.lzq.shortlink.dto.CreateShortLinkRequest();
        request.setOriginalUrl("https://example.com/new");

        com.lzq.shortlink.dto.ShortLinkListItemResponse response =
                controller.update(100L, 10L, request);

        assertEquals("https://example.com/new", response.getOriginalUrl());
        assertEquals("ACTIVE", response.getStatus());
    }

    @Test
    void shouldChangeShortLinkStatusInsideWorkspace() {
        when(workspaceAccessService.requireAccessibleWorkspace(100L))
                .thenReturn(workspace(100L, "Demo 的工作空间"));
        ShortLink shortLink = new ShortLink();
        shortLink.setId(10L);
        shortLink.setWorkspaceId(100L);
        shortLink.setShortCode("abc12345");
        shortLink.setStatus("DISABLED");
        when(shortLinkService.changeShortLinkStatus(
                100L,
                10L,
                "DISABLED"
        )).thenReturn(shortLink);

        WorkspaceLinkController controller = new WorkspaceLinkController(
                workspaceAccessService,
                shortLinkService
        );
        com.lzq.shortlink.dto.UpdateShortLinkStatusRequest request =
                new com.lzq.shortlink.dto.UpdateShortLinkStatusRequest();
        request.setStatus("DISABLED");

        com.lzq.shortlink.dto.ShortLinkListItemResponse response =
                controller.changeStatus(100L, 10L, request);

        assertEquals("DISABLED", response.getStatus());
    }

    @Test
    void shouldSoftDeleteShortLinkInsideWorkspace() {
        when(workspaceAccessService.requireAccessibleWorkspace(100L))
                .thenReturn(workspace(100L, "Demo 的工作空间"));
        when(shortLinkService.deleteShortLink(100L, 10L)).thenReturn(true);

        WorkspaceLinkController controller = new WorkspaceLinkController(
                workspaceAccessService,
                shortLinkService
        );

        controller.delete(100L, 10L);

        verify(shortLinkService).deleteShortLink(100L, 10L);
    }

    private Workspace workspace(Long id, String name) {
        Workspace workspace = new Workspace();
        workspace.setId(id);
        workspace.setName(name);
        workspace.setStatus("ACTIVE");
        return workspace;
    }
}
