package com.lzq.shortlink.auth;

import com.lzq.shortlink.auth.dto.RefreshTokenRequest;
import com.lzq.shortlink.auth.exception.InvalidRefreshTokenException;
import com.lzq.shortlink.entity.AppUser;
import com.lzq.shortlink.entity.RefreshToken;
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

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceRefreshRaceTest {

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
    void shouldRejectRefreshWhenAnotherRequestAlreadyRevokedToken() {
        RefreshToken storedToken = new RefreshToken();
        storedToken.setId(10L);
        storedToken.setUserId(7L);
        storedToken.setExpiresAt(LocalDateTime.now().plusDays(1));

        AppUser user = new AppUser();
        user.setId(7L);
        user.setEmail("demo@example.com");
        user.setStatus("ACTIVE");

        when(refreshTokenMapper.selectByTokenHash(any()))
                .thenReturn(storedToken);
        when(appUserMapper.selectById(7L)).thenReturn(user);

        // 模拟并发中的另一个请求已经先撤销了该令牌。
        when(refreshTokenMapper.revokeById(10L)).thenReturn(0);

        // 让旧实现继续走到签发步骤，从而明确验证它必须在此之前失败。
        lenient().when(jwtEncoder.encode(any()))
                .thenReturn(Jwt.withTokenValue("new-access-token")
                        .header("alg", "HS256")
                        .claim("sub", "7")
                        .build());

        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken("reused-refresh-token");

        assertThrows(
                InvalidRefreshTokenException.class,
                () -> authService.refresh(request)
        );

        verify(refreshTokenMapper, never())
                .insert(any(RefreshToken.class));
    }
}
