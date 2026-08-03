package com.quorumtrading.application.usecase.result;

import java.math.BigDecimal;

public record SellPositionResult(
    boolean success,
    String message,
    BigDecimal filledQuantity,
    BigDecimal executionPrice
) {

    public static SellPositionResult success(BigDecimal filledQuantity, BigDecimal executionPrice) {
        return new SellPositionResult(true,
            String.format("Sold %s at $%s", filledQuantity.toPlainString(), executionPrice.toPlainString()),
            filledQuantity, executionPrice);
    }

    public static SellPositionResult failure(String message) {
        return new SellPositionResult(false, message, null, null);
    }
}
