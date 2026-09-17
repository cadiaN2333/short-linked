package com.lzq.shortlink.auth;

import com.lzq.shortlink.auth.dto.AuthTokenResponse;
import com.lzq.shortlink.auth.dto.LoginRequest;
import com.lzq.shortlink.auth.dto.RefreshTokenRequest;
import com.lzq.shortlink.auth.dto.RegisterRequest;
import com.lzq.shortlink.auth.dto.RegisterResponse;
import com.lzq.shortlink.entity.AppUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public RegisterResponse register(
            @Valid @RequestBody RegisterRequest request
    ) {
        AppUser user = authService.register(request);

        return new RegisterResponse(
                user.getId(),
                user.getEmail()
        );
    }

    @PostMapping("/login")
    public AuthTokenResponse login(
            @Valid @RequestBody LoginRequest request
    ) {
        return authService.login(request);
    }

    @PostMapping("/refresh")
    public AuthTokenResponse refresh(
            @Valid @RequestBody RefreshTokenRequest request
    ) {
        return authService.refresh(request);
    }
}