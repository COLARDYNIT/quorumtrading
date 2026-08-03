package com.quorumtrading.application.usecase.command;

import com.quorumtrading.model.enums.OAuthProvider;

public record AuthenticateUserCommand(
    String email,
    String displayName,
    String avatarUrl,
    OAuthProvider provider,
    String providerUserId
) {
    public AuthenticateUserCommand {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email cannot be blank");
        }
        if (provider == null) {
            throw new IllegalArgumentException("Provider cannot be null");
        }
    }
}
