-- 保留短码原始字符，只调整比较规则，使唯一索引和等值查询严格区分大小写。
ALTER TABLE short_link
    MODIFY COLUMN short_code VARCHAR(16)
    CHARACTER SET utf8mb4 COLLATE utf8mb4_bin
    NOT NULL COMMENT '短码';
