-- 为短链接增加可运营的生命周期状态。
-- ACTIVE 可公开跳转，DISABLED 暂停跳转，DELETED 为软删除。

ALTER TABLE short_link
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
        COMMENT '短链接状态：ACTIVE、DISABLED、DELETED';

ALTER TABLE short_link
    ADD KEY idx_short_link_workspace_status (workspace_id, status, id);
