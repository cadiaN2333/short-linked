package com.lzq.shortlink.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 短链接数据库实体。
 */
@Data
@TableName("short_link")
public class ShortLink {

    /** 主键，由 MySQL 自增生成。 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 短码。 */
    private String shortCode;

    /** 原始长链接。 */
    private String originalUrl;

    /** 创建时间。 */
    private LocalDateTime createdAt;

    /** 过期时间，为 null 表示永久有效。 */
    private LocalDateTime expireAt;
}
