package com.quorumtrading.application.usecase.result;

import java.math.BigDecimal;
import java.util.List;

public record LiquidateAllResult(
    int positionsLiquidated,
    int positionsFailed,
    List<String> errors,
    BigDecimal cashAfterLiquidation
) {

    public static LiquidateAllResult empty() {
        return new LiquidateAllResult(0, 0, List.of(), null);
    }
}
