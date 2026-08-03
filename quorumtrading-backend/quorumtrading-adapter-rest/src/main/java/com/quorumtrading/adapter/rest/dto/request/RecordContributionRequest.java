package com.quorumtrading.adapter.rest.dto.request;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

@Schema(description = "Record a cash contribution into a LIVE simulation (ops/dev endpoint; the recurring-plan executor goes through the use case directly).")
public record RecordContributionRequest(
        @Schema(description = "Cash amount to inject (must be positive)", required = true, example = "200.00")
        BigDecimal amount,

        @Schema(description = "Currency code; defaults to USD", example = "USD")
        String currency,

        @Schema(description = "As-of date for the contribution; defaults to now if omitted", example = "2026-05-11T00:00:00Z", nullable = true)
        Instant contributionDate,

        @Schema(description = "Optional plan ID — pairs with contributionDate as the idempotency key", nullable = true)
        String sourcePlanId,

        @Schema(description = "Optional allocation ID linking back to the plan execution audit row", nullable = true)
        String sourceAllocationId
) {}
