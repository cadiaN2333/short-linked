package com.lzq.shortlink.migration;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Flyway 基线迁移脚本契约测试。 */
class FlywayMigrationScriptTest {

    @Test
    void shouldDeclareIdempotentBaselineForCoreTables() throws IOException {
        String script;
        try (InputStream input = getClass().getClassLoader()
                .getResourceAsStream("db/migration/V1__baseline_schema.sql")) {
            assertTrue(input != null, "Flyway 基线迁移脚本不存在");
            script = new String(input.readAllBytes(), StandardCharsets.UTF_8)
                    .toLowerCase();
        }

        assertTrue(script.contains("create table if not exists short_link"));
        assertTrue(script.contains(
                "create table if not exists short_link_visit_event"
        ));
        assertTrue(script.contains(
                "create table if not exists short_link_daily_stat"
        ));
    }
}
