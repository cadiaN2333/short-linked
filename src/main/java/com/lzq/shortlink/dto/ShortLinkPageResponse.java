package com.lzq.shortlink.dto;

import lombok.Data;

import java.util.List;

/** 工作空间短链接分页响应。 */
@Data
public class ShortLinkPageResponse {

    /** 当前页数据。 */
    private List<ShortLinkListItemResponse> items;

    /** 当前页码，从 1 开始。 */
    private int page;

    /** 每页数量。 */
    private int pageSize;

    /** 符合条件的总数量。 */
    private long total;

    /** 总页数。 */
    private long totalPages;
}
