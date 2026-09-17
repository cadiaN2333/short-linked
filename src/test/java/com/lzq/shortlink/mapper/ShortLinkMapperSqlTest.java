package com.lzq.shortlink.mapper;

import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** 访问统计累加不能继续写入已软删除的短链接。 */
class ShortLinkMapperSqlTest {

    @Test
    void shouldExcludeDeletedLinksWhenIncrementingStatistics() throws Exception {
        Update update = ShortLinkMapper.class
                .getMethod("incrementVisitStatistics", String.class, long.class)
                .getAnnotation(Update.class);

        String sql = String.join(" ", update.value()).toUpperCase();
        assertTrue(sql.contains("STATUS <> 'DELETED'"));
    }
}
