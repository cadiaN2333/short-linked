package com.lzq.shortlink.controller;

import com.lzq.shortlink.dto.CreateShortLinkRequest;
import com.lzq.shortlink.dto.CreateShortLinkResponse;
import com.lzq.shortlink.dto.ShortLinkListItemResponse;
import com.lzq.shortlink.dto.ShortLinkPageResponse;
import com.lzq.shortlink.dto.UpdateShortLinkStatusRequest;
import com.lzq.shortlink.entity.ShortLink;
import com.lzq.shortlink.config.ShortLinkProperties;
import com.lzq.shortlink.exception.InvalidPaginationException;
import com.lzq.shortlink.exception.ShortLinkNotFoundException;
import com.lzq.shortlink.service.ShortLinkPageResult;
import com.lzq.shortlink.service.ShortLinkService;
import com.lzq.shortlink.workspace.WorkspaceAccessService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;

/** 工作空间范围内的短链接创建接口。 */
@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/links")
public class WorkspaceLinkController {

    private static final int MAX_PAGE_SIZE = 100;

    private final WorkspaceAccessService workspaceAccessService;
    private final ShortLinkService shortLinkService;
    private final ShortLinkProperties shortLinkProperties;

    public WorkspaceLinkController(
            WorkspaceAccessService workspaceAccessService,
            ShortLinkService shortLinkService
    ) {
        this(
                workspaceAccessService,
                shortLinkService,
                new ShortLinkProperties()
        );
    }

    public WorkspaceLinkController(
            WorkspaceAccessService workspaceAccessService,
            ShortLinkService shortLinkService,
            ShortLinkProperties shortLinkProperties
    ) {
        this.workspaceAccessService = workspaceAccessService;
        this.shortLinkService = shortLinkService;
        this.shortLinkProperties = shortLinkProperties;
    }

    @PostMapping
    public CreateShortLinkResponse create(
            @PathVariable Long workspaceId,
            @Valid @RequestBody CreateShortLinkRequest request
    ) {
        workspaceAccessService.requireAccessibleWorkspace(workspaceId);

        ShortLink shortLink;
        if (request.getShortCode() == null
                || request.getShortCode().isBlank()) {
            shortLink = shortLinkService.createShortLink(
                    workspaceId,
                    request.getOriginalUrl(),
                    request.getExpireAt()
            );
        } else {
            shortLink = shortLinkService.createShortLink(
                    workspaceId,
                    request.getOriginalUrl(),
                    request.getExpireAt(),
                    request.getShortCode()
            );
        }

        CreateShortLinkResponse response = new CreateShortLinkResponse();
        response.setId(shortLink.getId());
        response.setWorkspaceId(shortLink.getWorkspaceId());
        response.setShortCode(shortLink.getShortCode());
        response.setOriginalUrl(shortLink.getOriginalUrl());
        response.setManageToken(shortLink.getManageToken());
        response.setExpireAt(shortLink.getExpireAt());
        response.setShortUrl(buildShortUrl(shortLink.getShortCode()));
        return response;
    }

    /** 查询当前用户在工作空间内可见的短链接。 */
    @GetMapping
    public ShortLinkPageResponse list(
            @PathVariable Long workspaceId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword
    ) {
        workspaceAccessService.requireAccessibleWorkspace(workspaceId);
        validatePage(page, pageSize);

        ShortLinkPageResult result;
        if ((status == null || status.isBlank())
                && (keyword == null || keyword.isBlank())) {
            result = shortLinkService.listShortLinks(
                    workspaceId,
                    page,
                    pageSize
            );
        } else {
            result = shortLinkService.listShortLinks(
                    workspaceId,
                    page,
                    pageSize,
                    status,
                    keyword
            );
        }

        ShortLinkPageResponse response = new ShortLinkPageResponse();
        response.setItems(result.items().stream()
                .map(this::toListItem)
                .toList());
        response.setPage(result.page());
        response.setPageSize(result.pageSize());
        response.setTotal(result.total());
        response.setTotalPages(result.total() == 0
                ? 0
                : (result.total() + result.pageSize() - 1)
                        / result.pageSize());
        return response;
    }

    /** 供单元测试和内部调用使用的无筛选分页入口。 */
    public ShortLinkPageResponse list(
            Long workspaceId,
            int page,
            int pageSize
    ) {
        return list(workspaceId, page, pageSize, null, null);
    }

    /** 查询工作空间内的一条短链接详情。 */
    @GetMapping("/{linkId}")
    public ShortLinkListItemResponse get(
            @PathVariable Long workspaceId,
            @PathVariable Long linkId
    ) {
        workspaceAccessService.requireAccessibleWorkspace(workspaceId);
        ShortLink shortLink = shortLinkService.findShortLinkById(
                workspaceId,
                linkId
        );
        if (shortLink == null) {
            throw new ShortLinkNotFoundException();
        }
        return toListItem(shortLink);
    }

    /** 修改工作空间内短链接目标地址和过期时间。 */
    @PutMapping("/{linkId}")
    public ShortLinkListItemResponse update(
            @PathVariable Long workspaceId,
            @PathVariable Long linkId,
            @Valid @RequestBody CreateShortLinkRequest request
    ) {
        workspaceAccessService.requireAccessibleWorkspace(workspaceId);
        ShortLink shortLink = shortLinkService.updateShortLink(
                workspaceId,
                linkId,
                request.getOriginalUrl(),
                request.getExpireAt()
        );
        if (shortLink == null) {
            throw new ShortLinkNotFoundException();
        }
        return toListItem(shortLink);
    }

    /** 启用或禁用工作空间内短链接。 */
    @PatchMapping("/{linkId}/status")
    public ShortLinkListItemResponse changeStatus(
            @PathVariable Long workspaceId,
            @PathVariable Long linkId,
            @Valid @RequestBody UpdateShortLinkStatusRequest request
    ) {
        workspaceAccessService.requireAccessibleWorkspace(workspaceId);
        ShortLink shortLink = shortLinkService.changeShortLinkStatus(
                workspaceId,
                linkId,
                request.getStatus()
        );
        if (shortLink == null) {
            throw new ShortLinkNotFoundException();
        }
        return toListItem(shortLink);
    }

    /** 软删除工作空间内短链接。 */
    @DeleteMapping("/{linkId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @PathVariable Long workspaceId,
            @PathVariable Long linkId
    ) {
        workspaceAccessService.requireAccessibleWorkspace(workspaceId);
        if (!shortLinkService.deleteShortLink(workspaceId, linkId)) {
            throw new ShortLinkNotFoundException();
        }
    }

    private void validatePage(int page, int pageSize) {
        if (page < 1) {
            throw new InvalidPaginationException("页码必须从 1 开始");
        }
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new InvalidPaginationException(
                    "每页数量必须在 1 到 " + MAX_PAGE_SIZE + " 之间"
            );
        }
    }

    private ShortLinkListItemResponse toListItem(ShortLink shortLink) {
        ShortLinkListItemResponse response = new ShortLinkListItemResponse();
        response.setId(shortLink.getId());
        response.setWorkspaceId(shortLink.getWorkspaceId());
        response.setShortCode(shortLink.getShortCode());
        response.setOriginalUrl(shortLink.getOriginalUrl());
        response.setStatus(shortLink.getStatus());
        response.setCreatedAt(shortLink.getCreatedAt());
        response.setExpireAt(shortLink.getExpireAt());
        response.setVisitCount(shortLink.getVisitCount());
        response.setLastVisitedAt(shortLink.getLastVisitedAt());
        response.setShortUrl(buildShortUrl(shortLink.getShortCode()));
        return response;
    }

    private String buildShortUrl(String shortCode) {
        String baseUrl = shortLinkProperties.getPublicBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "http://localhost:8080";
        }
        return baseUrl.replaceAll("/+$", "") + "/" + shortCode;
    }
}
