package com.sandook.ledger.auth;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        long expiresIn,
        String tokenType,
        String username,
        String role) {
}
