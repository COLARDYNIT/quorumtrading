package com.quorumtrading.adapter.rest.dto.response;

import io.quarkus.runtime.annotations.RegisterForReflection;
import java.time.Instant;
import java.util.List;

@RegisterForReflection
public record UserResponse(
    String id,
    String email,
    String displayName,
    String avatarUrl,
    String role,
    List<String> providers,
    Instant createdAt,
    Instant lastLoginAt,
    boolean welcomeBudgetCredited,
    boolean returningUser
) {}
