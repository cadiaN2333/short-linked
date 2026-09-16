package com.lzq.shortlink.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lzq.shortlink.entity.ShortLinkDailyStat;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

/**
 * 短链接每日访问统计数据访问层。
 */
public interface ShortLinkDailyStatMapper
        extends BaseMapper<ShortLinkDailyStat> {

    /**
     * 原子增加指定短链接当天的 PV。
     * 不存在当天统计时插入一条记录，存在时直接累加。
     *
     * @param shortLinkId 短链接主键
     * @param statDate 统计日期
     * @return 受影响的记录数
     */
    @Insert("""
            INSERT INTO short_link_daily_stat (short_link_id, stat_date, pv)
            VALUES (#{shortLinkId}, #{statDate}, 1)
            ON DUPLICATE KEY UPDATE
                pv = pv + 1,
                updated_at = NOW()
            """)
    int incrementPv(
            @Param("shortLinkId") Long shortLinkId,
            @Param("statDate") LocalDate statDate
    );

    /** 查询指定短链接日期范围内已经落库的每日 PV。 */
    @Select("""
            SELECT stat_date, pv
            FROM short_link_daily_stat
            WHERE short_link_id = #{shortLinkId}
              AND stat_date BETWEEN #{from} AND #{to}
            ORDER BY stat_date ASC
            """)
    List<ShortLinkDailyStatRow> selectByShortLinkIdAndDateBetween(
            @Param("shortLinkId") Long shortLinkId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to
    );
}
