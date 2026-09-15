package com.lzq.shortlink.controller;

import com.lzq.shortlink.dto.CreateShortLinkResponse;
import com.lzq.shortlink.entity.ShortLink;
import com.lzq.shortlink.service.ShortLinkService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.lzq.shortlink.dto.CreateShortLinkRequest;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * 短链接接口控制器。
 */
@RestController
@RequestMapping("/api/links")
public class ShortLinkController {

    private final ShortLinkService shortLinkService;

    public ShortLinkController(ShortLinkService shortLinkService) {
        this.shortLinkService = shortLinkService;
    }

    /**
     * 创建短链接。
     */
    @PostMapping
    public CreateShortLinkResponse createShortLink(
            @Valid @RequestBody CreateShortLinkRequest request
    ) {
        ShortLink shortLink = shortLinkService.createShortLink(
                request.getOriginalUrl(),
                request.getExpireAt()
        );

        CreateShortLinkResponse response = new CreateShortLinkResponse();
        response.setId(shortLink.getId());
        response.setShortCode(shortLink.getShortCode());
        response.setOriginalUrl(shortLink.getOriginalUrl());
        response.setExpireAt(shortLink.getExpireAt());
        response.setShortUrl(
                ServletUriComponentsBuilder.fromCurrentContextPath()
                        .path("/{shortCode}")
                        .buildAndExpand(shortLink.getShortCode())
                        .toUriString()
        );

        return response;
    }
}
