package com.lzq.shortlink.auth;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthMigrationScriptTest {

    @Test
    void shouldDeclareAuthTablesWithoutDestructiveSql()
            throws IOException {
        String script;

        try (InputStream input = getClass()
                .getClassLoader()
                .getResourceAsStream(
                        "db/migration/V2__create_auth_tables.sql"
                )) {
            assertNotNull(input, "认证迁移脚本不存在");

            script = new String(
                    input.readAllBytes(),
                    StandardCharsets.UTF_8
            ).toLowerCase(Locale.ROOT);
        }

        assertTrue(
                script.contains(
                        "create table if not exists app_user"
                )
        );
        assertTrue(
                script.contains(
                        "create table if not exists refresh_token"
                )
        );
        assertTrue(
                script.contains("unique key uk_app_user_email")
        );
        assertTrue(script.contains("token_hash"));
        assertFalse(script.contains("drop table"));
        assertFalse(script.contains("truncate table"));
    }
}