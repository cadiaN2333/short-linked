-- 为既有短链接补充管理凭证。每个迁移步骤只执行一次。
ALTER TABLE short_link
    ADD COLUMN manage_token CHAR(32) NULL COMMENT '管理凭证' AFTER original_url;

-- 历史数据也需要满足非空与唯一约束，但旧凭证此前未返回给调用方。
UPDATE short_link
SET manage_token = LOWER(REPLACE(UUID(), '-', ''))
WHERE manage_token IS NULL;

ALTER TABLE short_link
    MODIFY COLUMN manage_token CHAR(32) NOT NULL COMMENT '管理凭证',
    ADD UNIQUE KEY uk_manage_token (manage_token);
