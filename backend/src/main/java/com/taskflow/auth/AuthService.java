package com.taskflow.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Registration, login and refresh-token rotation. Refresh tokens are random
 * opaque strings stored SHA-256 hashed; each use revokes the presented token
 * and issues a fresh pair, so a stolen token is usable at most once.
 */
@Service
public class AuthService {
    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final JwtService jwt;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private final long refreshDays;

    public AuthService(UserRepository users, RefreshTokenRepository refreshTokens, JwtService jwt,
                       @Value("${taskflow.jwt.refresh-days:7}") long refreshDays) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.jwt = jwt;
        this.refreshDays = refreshDays;
    }

    @Transactional
    public AuthDtos.AuthResponse register(AuthDtos.RegisterRequest req) {
        String email = req.email().toLowerCase().trim();
        if (users.existsByEmail(email)) {
            throw new IllegalArgumentException("An account with this email already exists.");
        }
        User user = new User(email, encoder.encode(req.password()), req.displayName().trim());
        if (users.count() == 0) {
            user.setRole(Role.ADMIN); // first account bootstraps workspace admin
        }
        users.save(user);
        return issueTokens(user);
    }

    @Transactional
    public AuthDtos.AuthResponse login(AuthDtos.LoginRequest req) {
        User user = users.findByEmail(req.email().toLowerCase().trim())
                .orElseThrow(() -> new IllegalArgumentException("Wrong email or password."));
        if (!encoder.matches(req.password(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Wrong email or password.");
        }
        return issueTokens(user);
    }

    /** Rotation: the presented refresh token is revoked, a new pair is issued. */
    @Transactional
    public AuthDtos.AuthResponse refresh(AuthDtos.RefreshRequest req) {
        RefreshToken stored = refreshTokens.findByTokenHash(sha256(req.refreshToken()))
                .orElseThrow(() -> new IllegalArgumentException("Session expired. Please log in again."));
        if (stored.isRevoked() || stored.isExpired()) {
            throw new IllegalArgumentException("Session expired. Please log in again.");
        }
        stored.setRevoked(true);
        User user = users.findById(stored.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("Session expired. Please log in again."));
        return issueTokens(user);
    }

    private AuthDtos.AuthResponse issueTokens(User user) {
        String refresh = UUID.randomUUID() + "-" + UUID.randomUUID();
        refreshTokens.save(new RefreshToken(user.getId(), sha256(refresh),
                Instant.now().plusSeconds(refreshDays * 86400)));
        return new AuthDtos.AuthResponse(jwt.accessToken(user), refresh, AuthDtos.UserDto.from(user));
    }

    static String sha256(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
