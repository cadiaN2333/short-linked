// 替换：src/main/java/com/lzq/shortlink/controller/RedirectController.java
package com.lzq.shortlink.controller;

import com.lzq.shortlink.entity.ShortLink;
import com.lzq.shortlink.exception.InvalidTargetUrlException;
import com.lzq.shortlink.exception.ShortLinkNotFoundException;
import com.lzq.shortlink.service.ShortLinkService;
import com.lzq.shortlink.validation.TargetUrlValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/** 短链接公开跳转接口。 */
@Slf4j
@RestController
public class RedirectController {

    private final ShortLinkService shortLinkService;

    public RedirectController(ShortLinkService shortLinkService) {
        this.shortLinkService = shortLinkService;
    }

    @GetMapping("/{shortCode:[0-9A-Za-z_-]{3,16}}")
    public ResponseEntity<Void> redirect(@PathVariable String shortCode) {
        ShortLink shortLink = shortLinkService.findAvailableShortLink(shortCode);

        if (shortLink == null) {
            throw new ShortLinkNotFoundException();
        }

        URI target;
        try {
            target = TargetUrlValidator.parse(shortLink.getOriginalUrl());
        } catch (InvalidTargetUrlException exception) {
            // 历史非法目标返回不可用，不记录有效访问，也不打印目标 URL。
            log.warn(
                    "短链接目标地址无效，拒绝跳转，shortCode={}, linkId={}",
                    shortCode,
                    shortLink.getId()
            );
            throw new ShortLinkNotFoundException();
        }

        shortLinkService.recordVisit(shortLink);

        return ResponseEntity.status(HttpStatus.FOUND)
                .location(target)
                .build();
    }
}