package com.lzq.shortlink.auth;

import com.lzq.shortlink.auth.dto.AuthTokenResponse;
import com.lzq.shortlink.auth.dto.LoginRequest;
import com.lzq.shortlink.auth.dto.RefreshTokenRequest;
import com.lzq.shortlink.auth.dto.RegisterRequest;
import com.lzq.shortlink.entity.AppUser;

public interface AuthService {
    AppUser register(RegisterRequest request);

    AuthTokenResponse login(LoginRequest request);

    AuthTokenResponse refresh(RefreshTokenRequest request);
}
