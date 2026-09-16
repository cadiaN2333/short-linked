package com.lzq.shortlink.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lzq.shortlink.entity.ShortLinkVisitEvent;
import org.apache.ibatis.annotations.Insert;

/**
 * 短链接访问事件去重数据访问层。
 */
public interface ShortLinkVisitEventMapper
        extends BaseMapper<ShortLinkVisitEvent> {

    /**
     * 写入访问事件；相同事件 ID 已存在时不重复写入。
     *
     * @param visitEvent 访问事件
     * @return 新事件返回 1，重复事件返回 0
     */
    @Insert("""
            INSERT IGNORE INTO short_link_visit_event
            (event_id, short_link_id, visited_at)
            VALUES (#{eventId}, #{shortLinkId}, #{visitedAt})
            """)
    int insertIgnore(ShortLinkVisitEvent visitEvent);
}