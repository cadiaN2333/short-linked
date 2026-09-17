package com.lzq.shortlink.auth;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JwtPropertiesTest {

    @Test
    void shouldUseExpectedTokenDurations() {
        JwtProperties properties = new JwtProperties();

        assertEquals("https://short-link.local", properties.getIssuer());
        assertEquals(
                Duration.ofMinutes(15),
                properties.getAccessTokenTtl()
        );
        assertEquals(
                Duration.ofDays(30),
                properties.getRefreshTokenTtl()
        );
    }
}