package com.quorumtrading.adapter.rest.dto.request;

import jakarta.validation.constraints.NotNull;

public record ToggleVisibilityRequest(
    @NotNull Boolean publicVisible
) {}
