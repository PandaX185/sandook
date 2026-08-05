package com.sandook.ledger.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "sandook.jwt")
public record JwtProperties(String secret, int accessTtlMinutes, int refreshTtlDays) {
}
