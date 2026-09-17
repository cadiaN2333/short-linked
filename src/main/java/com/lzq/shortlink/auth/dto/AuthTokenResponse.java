package com.lzq.shortlink.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AuthTokenResponse {

    private String tokenType;

    private String accessToken;

    private String refreshToken;

    private long expiresIn;
}