package com.sandook.ledger.user;

import jakarta.validation.constraints.Size;

/**
 * Partial update for a user. Null fields are left unchanged; a non-blank
 * password resets the password.
 */
public record UpdateUserRequest(
        Role role,
        Boolean active,
        @Size(min = 8, max = 100, message = "Password must be at least 8 characters")
        String password) {
}
