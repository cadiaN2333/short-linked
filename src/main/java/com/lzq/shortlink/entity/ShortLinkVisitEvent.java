package com.lzq.shortlink.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 短链接访问事件去重实体。
 */
@Data
@TableName("short_link_visit_event")
public class ShortLinkVisitEvent {

    /** 访问事件唯一标识，由生产者生成。 */
    @TableId(value = "event_id", type = IdType.INPUT)
    private String eventId;

    /** 短链接主键。 */
    private Long shortLinkId;

    /** 访问发生时间。 */
    private LocalDateTime visitedAt;

    /** 事件写入数据库的时间。 */
    private LocalDateTime createdAt;
}