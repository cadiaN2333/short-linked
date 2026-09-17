package com.lzq.shortlink.auth;

import com.lzq.shortlink.auth.dto.AuthTokenResponse;
import com.lzq.shortlink.auth.dto.LoginRequest;
import com.lzq.shortlink.auth.dto.RefreshTokenRequest;
import com.lzq.shortlink.auth.dto.RegisterRequest;
import com.lzq.shortlink.auth.exception.EmailAlreadyRegisteredException;
import com.lzq.shortlink.auth.exception.InvalidCredentialsException;
import com.lzq.shortlink.auth.exception.InvalidRefreshTokenException;
import com.lzq.shortlink.entity.AppUser;
import com.lzq.shortlink.entity.RefreshToken;
import com.lzq.shortlink.mapper.AppUserMapper;
import com.lzq.shortlink.mapper.RefreshTokenMapper;
import com.lzq.shortlink.workspace.Workspace;
import com.lzq.shortlink.workspace.WorkspaceMapper;
import com.lzq.shortlink.workspace.WorkspaceMemberMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class AuthServiceImpl implements AuthService {

    private static final String ACTIVE = "ACTIVE";

    private final WorkspaceMapper workspaceMapper;
    private final WorkspaceMemberMapper workspaceMemberMapper;
    private final AppUserMapper appUserMapper;
    private final RefreshTokenMapper refreshTokenMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private final JwtProperties jwtProperties;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthServiceImpl(
            AppUserMapper appUserMapper,
            RefreshTokenMapper refreshTokenMapper,
            PasswordEncoder passwordEncoder,
            JwtEncoder jwtEncoder,
            JwtProperties jwtProperties,
            WorkspaceMapper workspaceMapper,
            WorkspaceMemberMapper workspaceMemberMapper
    ) {
        this.appUserMapper = appUserMapper;
        this.refreshTokenMapper = refreshTokenMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtEncoder = jwtEncoder;
        this.jwtProperties = jwtProperties;
        this.workspaceMapper = workspaceMapper;
        this.workspaceMemberMapper = workspaceMemberMapper;
    }

    @Override
    @Transactional
    public AppUser register(RegisterRequest request) {
        String displayEmail = request.getEmail().trim();
        String email = normalizeEmail(displayEmail);

        if (appUserMapper.selectByEmail(email) != null) {
            throw new EmailAlreadyRegisteredException();
        }

        AppUser user = new AppUser();
        user.setEmail(email);
        user.setPasswordHash(
                passwordEncoder.encode(request.getPassword())
        );
        user.setStatus(ACTIVE);

        try {
            appUserMapper.insert(user);
        } catch (DuplicateKeyException exception) {
            throw new EmailAlreadyRegisteredException();
        }

        Workspace workspace = new Workspace();
        workspace.setName(buildWorkspaceName(displayEmail));
        workspace.setOwnerUserId(user.getId());
        workspace.setStatus(ACTIVE);

        workspaceMapper.insert(workspace);
        workspaceMemberMapper.insertOwner(
                workspace.getId(),
                user.getId()
        );

        return user;
    }

    @Override
    @Transactional
    public AuthTokenResponse login(LoginRequest request) {
        String email = normalizeEmail(request.getEmail());
        AppUser user = appUserMapper.selectByEmail(email);

        if (user == null
                || !ACTIVE.equals(user.getStatus())
                || !passwordEncoder.matches(
                request.getPassword(),
                user.getPasswordHash()
        )) {
            throw new InvalidCredentialsException();
        }

        user.setLastLoginAt(LocalDateTime.now());
        appUserMapper.updateById(user);

        return issueTokens(user);
    }

    @Override
    @Transactional
    public AuthTokenResponse refresh(RefreshTokenRequest request) {
        String rawToken = request.getRefreshToken().trim();
        String tokenHash = sha256Hex(rawToken);

        RefreshToken storedToken =
                refreshTokenMapper.selectByTokenHash(tokenHash);

        LocalDateTime now = LocalDateTime.now();

        if (storedToken == null
                || storedToken.getRevokedAt() != null
                || storedToken.getExpiresAt() == null
                || !storedToken.getExpiresAt().isAfter(now)) {
            throw new InvalidRefreshTokenException();
        }

        AppUser user = appUserMapper.selectById(
                storedToken.getUserId()
        );

        if (user == null || !ACTIVE.equals(user.getStatus())) {
            throw new InvalidRefreshTokenException();
        }

        refreshTokenMapper.revokeById(storedToken.getId());

        return issueTokens(user);
    }

    private AuthTokenResponse issueTokens(AppUser user) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(
                jwtProperties.getAccessTokenTtl()
        );

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(jwtProperties.getIssuer())
                .subject(String.valueOf(user.getId()))
                .claim("email", user.getEmail())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .build();

        String accessToken = jwtEncoder.encode(
                JwtEncoderParameters.from(claims)
        ).getTokenValue();

        String refreshToken = generateRefreshToken();

        RefreshToken entity = new RefreshToken();
        entity.setUserId(user.getId());
        entity.setTokenHash(sha256Hex(refreshToken));
        entity.setExpiresAt(
                LocalDateTime.now().plus(
                        jwtProperties.getRefreshTokenTtl()
                )
        );

        refreshTokenMapper.insert(entity);

        return new AuthTokenResponse(
                "Bearer",
                accessToken,
                refreshToken,
                jwtProperties.getAccessTokenTtl().toSeconds()
        );
    }

    private String generateRefreshToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);

        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(bytes);
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest =
                    MessageDigest.getInstance("SHA-256");

            byte[] hash = digest.digest(
                    value.getBytes(StandardCharsets.UTF_8)
            );

            return HexFormat.of().formatHex(hash);
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "刷新令牌哈希计算失败",
                    exception
            );
        }
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }

    private String buildWorkspaceName(String email) {
        int separator = email.indexOf('@');

        String prefix = separator > 0
                ? email.substring(0, separator)
                : "个人";

        if (prefix.length() > 80) {
            prefix = prefix.substring(0, 80);
        }

        return prefix + " 的工作空间";
    }
}
