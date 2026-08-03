package com.quorumtrading.adapter.rest.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public class DecisionQueryParams {

    @NotNull(message = "Simulation ID is required")
    public UUID simulationId;

    public Instant from;

    public Instant to;

    @Size(max = 255, message = "Agent ID must be max 255 characters")
    public String agentId;

    public Boolean executed;

    @Min(value = 1, message = "Limit must be at least 1")
    @Max(value = 1000, message = "Limit must not exceed 1000")
    public Integer limit = 100;

    public DecisionQueryParams() {}

    public DecisionQueryParams(UUID simulationId, Instant from, Instant to,
                               String agentId, Boolean executed, Integer limit) {
        this.simulationId = simulationId;
        this.from = from;
        this.to = to;
        this.agentId = agentId;
        this.executed = executed;
        this.limit = limit != null ? limit : 100;
    }
}
