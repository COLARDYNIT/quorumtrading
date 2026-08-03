package com.quorumtrading.model.valueobject;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record Percentage(BigDecimal value) {

    public static final Percentage ZERO = new Percentage(BigDecimal.ZERO);
    public static final Percentage ONE_HUNDRED = new Percentage(BigDecimal.ONE);

    public Percentage {
        if (value == null) {
            throw new IllegalArgumentException("Percentage cannot be null");
        }
        if (value.compareTo(BigDecimal.ZERO) < 0 || value.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("Percentage must be between 0 and 1, got: " + value);
        }
    }

    public static Percentage fromDecimal(BigDecimal decimal) {
        return new Percentage(decimal);
    }

    public static Percentage fromPercentage(BigDecimal percentage) {
        return new Percentage(percentage.divide(new BigDecimal("100"), 10, RoundingMode.HALF_UP));
    }

    public BigDecimal asDecimal() {
        return value;
    }

    public BigDecimal asPercentage() {
        return value.multiply(new BigDecimal("100"));
    }

    public Money of(Money amount) {
        return amount.multiply(value);
    }
}
