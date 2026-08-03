package com.quorumtrading.adapter.rest.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/**
 * Request DTO for estimating the AI budget cost of a backtest or benchmark.
 */
@Schema(description = "Request to estimate backtest/benchmark AI budget cost")
public record CostEstimateRequest(
    @NotEmpty(message = "Strategy IDs are required")
    @Schema(description = "List of strategy UUIDs to estimate cost for",
            example = "[\"550e8400-e29b-41d4-a716-446655440000\"]",
            required = true)
    List<String> strategyIds,

    @NotNull(message = "Start date is required")
    @Schema(description = "Backtest start date (ISO 8601)", example = "2025-01-01T00:00:00Z", required = true)
    Instant startDate,

    @NotNull(message = "End date is required")
    @Schema(description = "Backtest end date (ISO 8601)", example = "2025-06-30T23:59:59Z", required = true)
    Instant endDate
) {}
