package com.lzq.shortlink.message;

import java.time.LocalDateTime;

/**
 * 短链接访问事件。
 *
 * @param eventId 事件唯一标识，用于消费幂等
 * @param shortLinkId 短链接主键
 * @param shortCode 短码
 * @param visitedAt 访问发生时间
 */
public record VisitEvent(
        String eventId,
        Long shortLinkId,
        String shortCode,
        LocalDateTime visitedAt
) {
}