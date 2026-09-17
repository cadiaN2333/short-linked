package com.lzq.shortlink.auth;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtConfigTest {

    private static final String VALID_SECRET =
            "0123456789abcdef0123456789abcdef";

    @Test
    void shouldEncodeAndDecodeHs256Token() {
        JwtProperties properties = new JwtProperties();
        properties.setIssuer("https://short-link.local");
        properties.setSecret(VALID_SECRET);

        JwtConfig config = new JwtConfig();

        JwtEncoder encoder = config.jwtEncoder(properties);
        JwtDecoder decoder = config.jwtDecoder(properties);

        Instant now = Instant.now();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.getIssuer())
                .subject("42")
                .issuedAt(now)
                .expiresAt(now.plus(Duration.ofMinutes(15)))
                .build();

        Jwt token = encoder.encode(
                JwtEncoderParameters.from(claims)
        );

        Jwt decoded = decoder.decode(token.getTokenValue());

        assertEquals("42", decoded.getSubject());
        assertEquals(
                "https://short-link.local",
                decoded.getIssuer().toString()
        );
        assertEquals(
                MacAlgorithm.HS256.getName(),
                decoded.getHeaders().get("alg")
        );
    }

    @Test
    void shouldRejectShortSecret() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("short-secret");

        JwtConfig config = new JwtConfig();

        assertThrows(
                IllegalStateException.class,
                () -> config.jwtEncoder(properties)
        );
    }
}