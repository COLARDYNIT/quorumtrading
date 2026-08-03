package com.quorumtrading.adapter.rest.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotEmpty;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import java.time.LocalDate;
import java.util.Set;

/**
 * Request DTO for requesting market data backfill.
 */
@Schema(description = "Request to backfill missing market data")
public record BackfillRequest(
    @NotEmpty(message = "Symbols cannot be empty")
    @Schema(description = "Set of ticker symbols to backfill",
            example = "[\"BTC\", \"ETH\"]",
            required = true)
    Set<String> symbols,

    @NotEmpty(message = "Data types cannot be empty")
    @Schema(description = "Set of data types to backfill",
            example = "[\"PRICE\", \"NEWS\"]",
            required = true,
            enumeration = {"PRICE", "NEWS", "SOCIAL"})
    Set<String> dataTypes,

    @NotNull(message = "Start date cannot be null")
    @Schema(description = "Backfill start date",
            example = "2024-01-01",
            required = true)
    LocalDate from,

    @NotNull(message = "End date cannot be null")
    @Schema(description = "Backfill end date",
            example = "2024-01-31",
            required = true)
    LocalDate to,

    @Schema(description = "Force backfill even if data is sufficient (skips coverage check)",
            example = "false",
            defaultValue = "false")
    boolean forceBackfill
) {}
