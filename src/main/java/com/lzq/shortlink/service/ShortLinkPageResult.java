package com.lzq.shortlink.service;

import com.lzq.shortlink.entity.ShortLink;

import java.util.List;

/** 工作空间短链接分页查询结果。 */
public record ShortLinkPageResult(
        List<ShortLink> items,
        long total,
        int page,
        int pageSize
) {
}
