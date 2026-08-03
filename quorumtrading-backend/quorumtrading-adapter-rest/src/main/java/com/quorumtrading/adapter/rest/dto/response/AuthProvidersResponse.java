package com.quorumtrading.adapter.rest.dto.response;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.List;

@RegisterForReflection
public record AuthProvidersResponse(
    List<AuthProvider> providers,
    boolean devLoginEnabled
) {
    @RegisterForReflection
    public record AuthProvider(
        String id,
        String name,
        String icon
    ) {}
}
