package com.lzq.shortlink.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * JWT 令牌配置。
 */
@ConfigurationProperties(prefix = "app.security.jwt")
public class JwtProperties {

    /** JWT 签发方。 */
    private String issuer = "https://short-link.local";

    /** Access Token 有效期。 */
    private Duration accessTokenTtl = Duration.ofMinutes(15);

    /** Refresh Token 有效期。 */
    private Duration refreshTokenTtl = Duration.ofDays(30);

    /** JWT 签名密钥，下一步从本地配置读取。 */
    private String secret;

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public Duration getAccessTokenTtl() {
        return accessTokenTtl;
    }

    public void setAccessTokenTtl(Duration accessTokenTtl) {
        this.accessTokenTtl = accessTokenTtl;
    }

    public Duration getRefreshTokenTtl() {
        return refreshTokenTtl;
    }

    public void setRefreshTokenTtl(Duration refreshTokenTtl) {
        this.refreshTokenTtl = refreshTokenTtl;
    }

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }
}