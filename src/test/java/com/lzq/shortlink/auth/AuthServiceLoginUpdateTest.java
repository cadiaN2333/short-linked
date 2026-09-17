package com.lzq.shortlink.auth;

import com.lzq.shortlink.auth.dto.LoginRequest;
import com.lzq.shortlink.entity.AppUser;
import com.lzq.shortlink.mapper.AppUserMapper;
import com.lzq.shortlink.mapper.RefreshTokenMapper;
import com.lzq.shortlink.workspace.WorkspaceMapper;
import com.lzq.shortlink.workspace.WorkspaceMemberMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtEncoder;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 登录只更新最近登录时间，不能回写用户全字段。 */
@ExtendWith(MockitoExtension.class)
class AuthServiceLoginUpdateTest {

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

    @Test
    void shouldUpdateOnlyLastLoginAtWhenLoginSucceeds() {
        AppUser user = new AppUser();
        user.setId(1L);
        user.setEmail("demo@example.com");
        user.setPasswordHash("encoded");
        user.setStatus("ACTIVE");
        when(appUserMapper.selectByEmail("demo@example.com"))
                .thenReturn(user);
        when(passwordEncoder.matches("Demo@123456", "encoded"))
                .thenReturn(true);
        when(jwtEncoder.encode(any()))
                .thenReturn(Jwt.withTokenValue("access-token")
                        .header("alg", "HS256")
                        .claim("sub", "1")
                        .build());

        JwtProperties jwtProperties = new JwtProperties();
        jwtProperties.setIssuer("https://short-link.local");
        AuthServiceImpl authService = new AuthServiceImpl(
                appUserMapper,
                refreshTokenMapper,
                passwordEncoder,
                jwtEncoder,
                jwtProperties,
                workspaceMapper,
                workspaceMemberMapper
        );

        LoginRequest request = new LoginRequest();
        request.setEmail("demo@example.com");
        request.setPassword("Demo@123456");

        authService.login(request);

        verify(appUserMapper, never()).updateById(any(AppUser.class));
        verify(appUserMapper).updateLastLoginAt(any(Long.class), any());
    }
}
