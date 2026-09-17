package com.lzq.shortlink.auth;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * JWT 签发和验证配置。
 */
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class JwtConfig {

    /**
     * 创建 JWT 签发器。
     */
    @Bean
    JwtEncoder jwtEncoder(JwtProperties properties) {
        SecretKey secretKey = createSecretKey(properties);

        return NimbusJwtEncoder
                .withSecretKey(secretKey)
                .algorithm(MacAlgorithm.HS256)
                .build();
    }

    /**
     * 创建 JWT 验证器。
     */
    @Bean
    JwtDecoder jwtDecoder(JwtProperties properties) {
        SecretKey secretKey = createSecretKey(properties);

        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withSecretKey(secretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();

        decoder.setJwtValidator(
                JwtValidators.createDefaultWithIssuer(
                        properties.getIssuer()
                )
        );

        return decoder;
    }

    /**
     * 根据配置创建签名密钥。
     */
    private SecretKey createSecretKey(JwtProperties properties) {
        String secret = properties.getSecret();

        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "JWT 密钥不能为空，请配置 app.security.jwt.secret"
            );
        }

        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);

        if (secretBytes.length < 32) {
            throw new IllegalStateException(
                    "JWT 密钥长度至少为 32 字节"
            );
        }

        return new SecretKeySpec(secretBytes, "HmacSHA256");
    }
}