-- 管理凭证查询接口已下线，统计权限改由 JWT 和工作空间成员关系控制。
-- 删除不再使用的明文凭证及其唯一索引。
ALTER TABLE short_link
    DROP INDEX uk_manage_token,
    DROP COLUMN manage_token;
