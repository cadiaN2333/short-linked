package com.lzq.shortlink.auth;

import com.lzq.shortlink.auth.dto.LoginRequest;
import com.lzq.shortlink.auth.exception.InvalidCredentialsException;
import com.lzq.shortlink.auth.exception.LoginAttemptRateLimitedException;
import com.lzq.shortlink.entity.AppUser;
import com.lzq.shortlink.mapper.AppUserMapper;
import com.lzq.shortlink.mapper.RefreshTokenMapper;
import com.lzq.shortlink.workspace.WorkspaceMapper;
import com.lzq.shortlink.workspace.WorkspaceMemberMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtEncoder;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceLoginRateLimitTest {

    @Mock
    private AppUserMapper appUserMapper;

    @Mock
    private RefreshTokenMapper refreshTokenMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtEncoder jwtEncoder;

    @Mock
    private WorkspaceMapper workspaceMapper;

    @Mock
    private WorkspaceMemberMapper workspaceMemberMapper;

    @Mock
    private LoginAttemptRateLimiter loginAttemptRateLimiter;

    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        JwtProperties jwtProperties = new JwtProperties();
        jwtProperties.setIssuer("https://short-link.local");
        authService = new AuthServiceImpl(
                appUserMapper,
                refreshTokenMapper,
                passwordEncoder,
                jwtEncoder,
                jwtProperties,
                workspaceMapper,
                workspaceMemberMapper,
                loginAttemptRateLimiter
        );
    }

    @Test
    void shouldRecordFailureForInvalidCredentials() {
        when(appUserMapper.selectByEmail("demo@example.com"))
                .thenReturn(null);

        assertThrows(
                InvalidCredentialsException.class,
                () -> authService.login(request(), "192.0.2.20")
        );

        verify(loginAttemptRateLimiter)
                .recordFailure("demo@example.com", "192.0.2.20");
    }

    @Test
    void shouldRejectBlockedLoginBeforeLookingUpUser() {
        doThrow(new LoginAttemptRateLimitedException(45))
                .when(loginAttemptRateLimiter)
                .checkAllowed("demo@example.com", "192.0.2.20");

        assertThrows(
                LoginAttemptRateLimitedException.class,
                () -> authService.login(request(), "192.0.2.20")
        );

        verify(appUserMapper, never()).selectByEmail(anyString());
    }

    @Test
    void shouldClearAccountFailuresAfterSuccessfulLogin() {
        AppUser user = new AppUser();
        user.setId(7L);
        user.setEmail("demo@example.com");
        user.setPasswordHash("encoded-password");
        user.setStatus("ACTIVE");
        when(appUserMapper.selectByEmail("demo@example.com")).thenReturn(user);
        when(passwordEncoder.matches("Demo@123456", "encoded-password"))
                .thenReturn(true);
        when(jwtEncoder.encode(any()))
                .thenReturn(Jwt.withTokenValue("access-token")
                        .header("alg", "HS256")
                        .claim("sub", "7")
                        .build());

        authService.login(request(), "192.0.2.20");

        verify(loginAttemptRateLimiter)
                .clearAccountFailures("demo@example.com");
    }

    private LoginRequest request() {
        LoginRequest request = new LoginRequest();
        request.setEmail("demo@example.com");
        request.setPassword("Demo@123456");
        return request;
    }
}
