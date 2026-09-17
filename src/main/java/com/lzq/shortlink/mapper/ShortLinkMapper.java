package com.lzq.shortlink.mapper;


import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lzq.shortlink.entity.ShortLink;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface ShortLinkMapper extends BaseMapper<ShortLink> {

    /** 按工作空间倒序分页查询短链接。 */
    @Select("""
        SELECT id, workspace_id, short_code, original_url,
               manage_token, status, created_at, expire_at,
               visit_count, last_visited_at
        FROM short_link
        WHERE workspace_id = #{workspaceId}
          AND status <> 'DELETED'
        ORDER BY id DESC
        LIMIT #{limit} OFFSET #{offset}
        """)
    List<ShortLink> selectPageByWorkspaceId(
            @Param("workspaceId") Long workspaceId,
            @Param("limit") long limit,
            @Param("offset") long offset
    );

    /** 按状态和关键词筛选工作空间短链接并分页。 */
    @Select("""
        <script>
        SELECT id, workspace_id, short_code, original_url,
               manage_token, status, created_at, expire_at,
               visit_count, last_visited_at
        FROM short_link
        WHERE workspace_id = #{workspaceId}
          AND status &lt;&gt; 'DELETED'
        <if test="status != null and status != ''">
          AND status = #{status}
        </if>
        <if test="keyword != null and keyword != ''">
          AND (short_code LIKE CONCAT('%', #{keyword}, '%')
               OR original_url LIKE CONCAT('%', #{keyword}, '%'))
        </if>
        ORDER BY id DESC
        LIMIT #{limit} OFFSET #{offset}
        </script>
        """)
    List<ShortLink> selectPageByWorkspaceIdAndFilter(
            @Param("workspaceId") Long workspaceId,
            @Param("status") String status,
            @Param("keyword") String keyword,
            @Param("limit") long limit,
            @Param("offset") long offset
    );

    /** 统计工作空间内的短链接总数。 */
    @Select("""
        SELECT COUNT(*)
        FROM short_link
        WHERE workspace_id = #{workspaceId}
          AND status <> 'DELETED'
        """)
    long countByWorkspaceId(@Param("workspaceId") Long workspaceId);

    /** 统计筛选条件下工作空间短链接总数。 */
    @Select("""
        <script>
        SELECT COUNT(*)
        FROM short_link
        WHERE workspace_id = #{workspaceId}
          AND status &lt;&gt; 'DELETED'
        <if test="status != null and status != ''">
          AND status = #{status}
        </if>
        <if test="keyword != null and keyword != ''">
          AND (short_code LIKE CONCAT('%', #{keyword}, '%')
               OR original_url LIKE CONCAT('%', #{keyword}, '%'))
        </if>
        </script>
        """)
    long countByWorkspaceIdAndFilter(
            @Param("workspaceId") Long workspaceId,
            @Param("status") String status,
            @Param("keyword") String keyword
    );

    /** 按工作空间和主键查询短链接，避免越权读取其他工作空间数据。 */
    @Select("""
        SELECT id, workspace_id, short_code, original_url,
               manage_token, status, created_at, expire_at,
               visit_count, last_visited_at
        FROM short_link
        WHERE workspace_id = #{workspaceId}
          AND id = #{linkId}
          AND status <> 'DELETED'
        LIMIT 1
        """)
    ShortLink selectByWorkspaceIdAndId(
            @Param("workspaceId") Long workspaceId,
            @Param("linkId") Long linkId
    );

    /** 更新工作空间内短链接的目标地址和过期时间。 */
    @Update("""
        UPDATE short_link
        SET original_url = #{originalUrl},
            expire_at = #{expireAt}
        WHERE workspace_id = #{workspaceId}
          AND id = #{linkId}
          AND status <> 'DELETED'
        """)
    int updateContentByWorkspaceIdAndId(
            @Param("workspaceId") Long workspaceId,
            @Param("linkId") Long linkId,
            @Param("originalUrl") String originalUrl,
            @Param("expireAt") java.time.LocalDateTime expireAt
    );

    /** 更新工作空间内短链接的生命周期状态。 */
    @Update("""
        UPDATE short_link
        SET status = #{status}
        WHERE workspace_id = #{workspaceId}
          AND id = #{linkId}
          AND status <> 'DELETED'
        """)
    int updateStatusByWorkspaceIdAndId(
            @Param("workspaceId") Long workspaceId,
            @Param("linkId") Long linkId,
            @Param("status") String status
    );

    /**
     * 原子累加访问次数，并更新最近访问时间。
     *
     * @param shortCode 短码
     * @param increment 本批次访问增量
     * @return 更新的记录数
     */
    @Update("""
        UPDATE short_link
        SET visit_count = visit_count + #{increment},
            last_visited_at = NOW()
        WHERE short_code = #{shortCode}
        """)
    int incrementVisitStatistics(
            @Param("shortCode") String shortCode,
            @Param("increment") long increment
    );
}
