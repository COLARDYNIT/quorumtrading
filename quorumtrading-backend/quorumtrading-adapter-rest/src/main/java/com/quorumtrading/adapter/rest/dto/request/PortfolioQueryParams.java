package com.quorumtrading.adapter.rest.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

public class PortfolioQueryParams {

    @NotNull(message = "Simulation ID is required")
    public UUID simulationId;

    public Instant from;

    public Instant to;

    @Min(value = 1, message = "Limit must be at least 1")
    @Max(value = 1000, message = "Limit must not exceed 1000")
    public Integer limit = 100;

    public PortfolioQueryParams() {}

    public PortfolioQueryParams(UUID simulationId, Instant from, Instant to, Integer limit) {
        this.simulationId = simulationId;
        this.from = from;
        this.to = to;
        this.limit = limit != null ? limit : 100;
    }
}
