package com.quorumtrading.model.marketdata;

import java.math.BigDecimal;

/**
 * A single quarter of earnings data: actual EPS vs analyst estimate.
 */
public record EarningsRecord(
    String period,
    int quarter,
    int year,
    BigDecimal actualEps,
    BigDecimal estimateEps,
    BigDecimal surprise,
    BigDecimal surprisePercent
) {

    public boolean beat() {
        return surprise != null && surprise.signum() > 0;
    }

    public boolean missed() {
        return surprise != null && surprise.signum() < 0;
    }
}
