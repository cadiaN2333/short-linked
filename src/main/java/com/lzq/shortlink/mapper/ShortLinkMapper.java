package com.lzq.shortlink.mapper;


import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lzq.shortlink.entity.ShortLink;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface ShortLinkMapper extends BaseMapper<ShortLink> {

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
