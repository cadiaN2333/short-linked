-- 短链接访问事件去重表
CREATE TABLE IF NOT EXISTS short_link_visit_event (
    event_id CHAR(32) NOT NULL COMMENT '访问事件唯一标识',
    short_link_id BIGINT UNSIGNED NOT NULL COMMENT '短链接主键',
    visited_at DATETIME NOT NULL COMMENT '访问时间',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '入库时间',
    PRIMARY KEY (event_id),
    KEY idx_short_link_id_visited_at (short_link_id, visited_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT = '短链接访问事件去重表';

-- 短链接每日 PV 聚合表
CREATE TABLE IF NOT EXISTS short_link_daily_stat (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    short_link_id BIGINT UNSIGNED NOT NULL COMMENT '短链接主键',
    stat_date DATE NOT NULL COMMENT '统计日期',
    pv BIGINT NOT NULL DEFAULT 0 COMMENT '当天访问次数',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_short_link_id_stat_date (short_link_id, stat_date)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT = '短链接每日访问统计表';
