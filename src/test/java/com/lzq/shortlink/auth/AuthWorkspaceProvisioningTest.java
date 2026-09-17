package com.lzq.shortlink.auth;

import com.lzq.shortlink.auth.dto.RegisterRequest;
import com.lzq.shortlink.entity.AppUser;
import com.lzq.shortlink.mapper.AppUserMapper;
import com.lzq.shortlink.mapper.RefreshTokenMapper;
import com.lzq.shortlink.workspace.Workspace;
import com.lzq.shortlink.workspace.WorkspaceMapper;
import com.lzq.shortlink.workspace.WorkspaceMemberMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthWorkspaceProvisioningTest {

    @Mock
    private AppUserMapper appUserMapper;

    @Mock
    private RefreshTokenMapper refreshTokenMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtEncoder jwtEncoder;

    @Mock
    private JwtProperties jwtProperties;

    @Mock
    private WorkspaceMapper workspaceMapper;

    @Mock
    private WorkspaceMemberMapper workspaceMemberMapper;

    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        authService = new AuthServiceImpl(
                appUserMapper,
                refreshTokenMapper,
                passwordEncoder,
                jwtEncoder,
                jwtProperties,
                workspaceMapper,
                workspaceMemberMapper
        );
    }

    @Test
    void shouldCreatePersonalWorkspaceWhenRegistering() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail(" Demo@example.com ");
        request.setPassword("Demo@123456");

        when(appUserMapper.selectByEmail("demo@example.com"))
                .thenReturn(null);
        when(passwordEncoder.encode("Demo@123456"))
                .thenReturn("encoded-password");

        doAnswer(invocation -> {
            AppUser user = invocation.getArgument(0);
            user.setId(42L);
            return 1;
        }).when(appUserMapper).insert(any(AppUser.class));

        doAnswer(invocation -> {
            Workspace workspace = invocation.getArgument(0);
            workspace.setId(100L);
            return 1;
        }).when(workspaceMapper).insert(any(Workspace.class));

        AppUser user = authService.register(request);

        assertEquals(42L, user.getId());

        ArgumentCaptor<Workspace> workspaceCaptor =
                ArgumentCaptor.forClass(Workspace.class);
        verify(workspaceMapper).insert(workspaceCaptor.capture());

        Workspace workspace = workspaceCaptor.getValue();
        assertEquals("Demo 的工作空间", workspace.getName());
        assertEquals(42L, workspace.getOwnerUserId());
        assertEquals("ACTIVE", workspace.getStatus());

        verify(workspaceMemberMapper).insertOwner(100L, 42L);
    }
}
