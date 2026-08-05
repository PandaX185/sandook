package com.sandook.ledger.user;

public record UserResponse(Long id, String username, Role role, boolean active) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getUsername(), user.getRole(), user.isActive());
    }
}
