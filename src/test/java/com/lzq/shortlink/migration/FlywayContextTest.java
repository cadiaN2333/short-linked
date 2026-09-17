package com.lzq.shortlink.migration;

import com.lzq.shortlink.ShortLinkApplication;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Flyway 应用上下文集成测试。 */
@SpringBootTest(classes = ShortLinkApplication.class)
class FlywayContextTest {

    @Autowired
    private Flyway flyway;

    @Test
    void shouldExposeSuccessfulFlywayMigration() {
        assertNotNull(flyway.info().current());
        assertEquals("4", flyway.info().current().getVersion().toString());
    }
}
