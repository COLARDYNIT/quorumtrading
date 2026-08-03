package com.quorumtrading.adapter.rest.dto.response;

import org.eclipse.microprofile.openapi.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;

@Schema(description = "Result of liquidating all positions")
public record LiquidateAllResponse(
    @Schema(description = "Number of positions successfully liquidated") int positionsLiquidated,
    @Schema(description = "Number of positions that failed to liquidate") int positionsFailed,
    @Schema(description = "Error messages for failed positions") List<String> errors,
    @Schema(description = "Cash balance after liquidation") BigDecimal cashAfterLiquidation
) {}
