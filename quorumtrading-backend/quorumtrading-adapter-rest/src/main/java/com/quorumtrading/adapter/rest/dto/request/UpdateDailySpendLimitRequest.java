package com.quorumtrading.adapter.rest.dto.request;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * Request DTO for updating the daily spend limit.
 * Send null to clear the limit.
 */
@Schema(description = "Request to update daily spend limit")
public record UpdateDailySpendLimitRequest(
    @Schema(description = "Daily spend limit in USD. Null to clear the limit.", example = "5.00")
    BigDecimal dailySpendLimit
) {}
