package com.lzq.shortlink.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 短链接每日访问统计实体。
 */
@Data
@TableName("short_link_daily_stat")
public class ShortLinkDailyStat {

    /** 主键，由 MySQL 自增生成。 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 短链接主键。 */
    private Long shortLinkId;

    /** 统计日期。 */
    private LocalDate statDate;

    /** 当天累计访问次数。 */
    private Long pv;

    /** 创建时间。 */
    private LocalDateTime createdAt;

    /** 更新时间。 */
    private LocalDateTime updatedAt;
}