CREATE TABLE workspace (
                           id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '工作空间主键',
                           name VARCHAR(100) NOT NULL COMMENT '工作空间名称',
                           owner_user_id BIGINT UNSIGNED NULL COMMENT '拥有者用户主键',
                           status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '工作空间状态',
                           created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                           updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
                               ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                           PRIMARY KEY (id),
                           UNIQUE KEY uk_workspace_owner (owner_user_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
    COMMENT = '工作空间表';

CREATE TABLE workspace_member (
                                  workspace_id BIGINT UNSIGNED NOT NULL COMMENT '工作空间主键',
                                  user_id BIGINT UNSIGNED NOT NULL COMMENT '用户主键',
                                  role VARCHAR(20) NOT NULL DEFAULT 'OWNER' COMMENT '成员角色',
                                  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间',
                                  PRIMARY KEY (workspace_id, user_id),
                                  KEY idx_workspace_member_user_id (user_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
    COMMENT = '工作空间成员表';

INSERT INTO workspace (name, owner_user_id, status)
SELECT '历史数据工作空间', NULL, 'ACTIVE'
WHERE NOT EXISTS (
    SELECT 1
    FROM workspace
    WHERE owner_user_id IS NULL
      AND name = '历史数据工作空间'
);

ALTER TABLE short_link
    ADD COLUMN workspace_id BIGINT UNSIGNED NULL
        COMMENT '所属工作空间';

UPDATE short_link
SET workspace_id = (
    SELECT id
    FROM workspace
    WHERE owner_user_id IS NULL
      AND name = '历史数据工作空间'
    ORDER BY id
    LIMIT 1
)
WHERE workspace_id IS NULL;

ALTER TABLE short_link
    MODIFY workspace_id BIGINT UNSIGNED NOT NULL
        COMMENT '所属工作空间';

ALTER TABLE short_link
    ADD KEY idx_short_link_workspace_id (workspace_id);