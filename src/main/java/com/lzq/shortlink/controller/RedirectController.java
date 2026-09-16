package com.lzq.shortlink.controller;

import com.lzq.shortlink.entity.ShortLink;
import com.lzq.shortlink.exception.ShortLinkNotFoundException;
import com.lzq.shortlink.service.ShortLinkService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * 短链接跳转控制器。
 */
@RestController
public class RedirectController {

    private final ShortLinkService shortLinkService;

    public RedirectController(ShortLinkService shortLinkService) {
        this.shortLinkService = shortLinkService;
    }

    /**
     * 根据短码跳转到原始链接。
     */
    @GetMapping("/{shortCode}")
    public ResponseEntity<Void> redirect(@PathVariable String shortCode) {
        ShortLink shortLink = shortLinkService.findAvailableShortLink(shortCode);

        if (shortLink == null) {
            throw new ShortLinkNotFoundException();
        }

        shortLinkService.recordVisit(shortCode);

        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(shortLink.getOriginalUrl()))
                .build();
    }
}