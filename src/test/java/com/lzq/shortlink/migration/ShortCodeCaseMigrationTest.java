package com.lzq.shortlink.migration;

import com.lzq.shortlink.message.VisitEventPublisher;
import com.lzq.shortlink.scheduler.VisitStatSyncScheduler;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import javax.sql.DataSource;
import static org.junit.jupiter.api.Assertions.*;

/** 显式执行 V7；迁移前后比对全部原有主键和短码，不启动消息消费。 */
@SpringBootTest(properties = {
        "spring.flyway.enabled=false",
        "spring.rabbitmq.listener.simple.auto-startup=false",
        "spring.rabbitmq.listener.direct.auto-startup=false",
        "spring.rabbitmq.dynamic=false",
        "app.short-link.bloom.enabled=false",
        "app.security.jwt.secret=case-migration-tests-only-not-for-production-2026"
})
class ShortCodeCaseMigrationTest {
    @Autowired DataSource dataSource;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean StringRedisTemplate redis;
    @MockitoBean VisitEventPublisher publisher;
    @MockitoBean VisitStatSyncScheduler scheduler;

    @Test
    void migratesComparisonWithoutChangingExistingCodes() {
        String sql = "SELECT id, short_code FROM short_link ORDER BY id";
        var before = jdbc.queryForList(sql);
        String oldCollation = jdbc.queryForObject("SELECT COLLATION_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='short_link' AND COLUMN_NAME='short_code'", String.class);
        assertEquals("varchar", jdbc.queryForObject("SELECT DATA_TYPE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='short_link' AND COLUMN_NAME='short_code'", String.class));
        assertEquals(16, jdbc.queryForObject("SELECT CHARACTER_MAXIMUM_LENGTH FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='short_link' AND COLUMN_NAME='short_code'", Integer.class));
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        var after = jdbc.queryForList(sql);
        assertEquals(before, after, "迁移不得转换、删除或重写历史短码");
        String newCollation = jdbc.queryForObject("SELECT COLLATION_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='short_link' AND COLUMN_NAME='short_code'", String.class);
        assertEquals("utf8mb4_bin", newCollation);
        System.out.printf("短码迁移校验：记录数=%d，迁移前=%s，迁移后=%s，原始短码全部保持不变%n", before.size(), oldCollation, newCollation);
    }
}
