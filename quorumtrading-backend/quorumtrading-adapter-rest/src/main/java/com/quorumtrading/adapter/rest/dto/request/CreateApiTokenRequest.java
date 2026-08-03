package com.quorumtrading.adapter.rest.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateApiTokenRequest(
        @NotBlank(message = "Token name is required")
        @Size(max = 100, message = "Token name must be at most 100 characters")
        String name,

        Integer expiresInDays
) {
}
