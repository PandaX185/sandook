package com.sandook.ledger.auth;

import com.sandook.ledger.user.RefreshToken;
import com.sandook.ledger.user.RefreshTokenRepository;
import com.sandook.ledger.user.User;
import com.sandook.ledger.user.UserRepository;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final JwtProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(UserRepository userRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       JwtProperties properties) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.properties = properties;
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.username())
                .filter(User::isActive)
                .orElseThrow(() -> new BadCredentialsException("Invalid username or password"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid username or password");
        }

        return buildResponse(user, issueRefreshToken(user));
    }

    @Transactional
    public AuthResponse refresh(RefreshRequest request) {
        String hash = sha256(request.refreshToken());
        RefreshToken token = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new RefreshTokenException("Invalid refresh token"));

        if (token.isRevoked() || token.getExpiresAt().isBefore(Instant.now())) {
            throw new RefreshTokenException("Refresh token expired or revoked");
        }

        User user = token.getUser();
        if (!user.isActive()) {
            throw new RefreshTokenException("User is deactivated");
        }

        // Rotation: revoke the presented token, issue a fresh one
        token.setRevoked(true);
        String newRefresh = issueRefreshToken(user);
        token.setReplacedByHash(sha256(newRefresh));

        return buildResponse(user, newRefresh);
    }

    @Transactional
    public void logout(RefreshRequest request) {
        refreshTokenRepository.findByTokenHash(sha256(request.refreshToken()))
                .ifPresent(token -> token.setRevoked(true));
    }

    private String issueRefreshToken(User user) {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        RefreshToken token = new RefreshToken();
        token.setUser(user);
        token.setTokenHash(sha256(raw));
        token.setExpiresAt(Instant.now().plus(properties.refreshTtlDays(), ChronoUnit.DAYS));
        refreshTokenRepository.save(token);
        return raw;
    }

    private AuthResponse buildResponse(User user, String refreshToken) {
        String accessToken = jwtService.issueAccessToken(user);
        return new AuthResponse(
                accessToken,
                refreshToken,
                properties.accessTtlMinutes() * 60L,
                "Bearer",
                user.getUsername(),
                user.getRole().name());
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
