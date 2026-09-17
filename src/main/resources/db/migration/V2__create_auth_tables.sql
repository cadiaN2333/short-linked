-- 用户认证表
CREATE TABLE IF NOT EXISTS app_user (
                                        id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '用户主键',
                                        email VARCHAR(320) NOT NULL COMMENT '登录邮箱',
                                        password_hash VARCHAR(100) NOT NULL COMMENT 'BCrypt 密码哈希',
                                        status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '用户状态',
                                        created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                        updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
                                            ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                        last_login_at DATETIME NULL COMMENT '最近登录时间',
                                        PRIMARY KEY (id),
                                        UNIQUE KEY uk_app_user_email (email)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
    COMMENT = '用户认证表';

-- 刷新令牌表
CREATE TABLE IF NOT EXISTS refresh_token (
                                             id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '令牌主键',
                                             user_id BIGINT UNSIGNED NOT NULL COMMENT '用户主键',
                                             token_hash CHAR(64) NOT NULL COMMENT '刷新令牌 SHA-256 哈希',
                                             expires_at DATETIME NOT NULL COMMENT '过期时间',
                                             revoked_at DATETIME NULL COMMENT '撤销时间',
                                             created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                             PRIMARY KEY (id),
                                             UNIQUE KEY uk_refresh_token_hash (token_hash),
                                             KEY idx_refresh_token_user_id (user_id),
                                             KEY idx_refresh_token_expires_at (expires_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
    COMMENT = '刷新令牌表';