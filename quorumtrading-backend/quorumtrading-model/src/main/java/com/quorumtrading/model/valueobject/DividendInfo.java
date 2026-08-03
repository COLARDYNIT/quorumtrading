package com.quorumtrading.model.valueobject;

import java.math.BigDecimal;

/**
 * Value object representing dividend information for a ticker.
 * Used to surface dividend awareness in backtests, strategies, and benchmark comparisons.
 *
 * <p><b>Unknown is not the same as "pays nothing".</b> {@code paysDividends} and
 * {@code dividendCountLastYear} are nullable, and null means the dividend history
 * could not be determined. They were previously primitives, so a failed lookup
 * produced {@code (false, 0)} — a record byte-identical to a genuine
 * no-dividend stock. Coca-Cola and AstraZeneca, both long-standing dividend
 * payers, were reported as paying none, and the trading-concepts guide tells
 * users to rely on this field. Absence of evidence must not serialise as a
 * positive claim; callers should omit the field entirely when it is null.</p>
 *
 * @param ticker                 the ticker symbol (e.g., "AAPL")
 * @param paysDividends          whether this ticker pays dividends; null when not determined
 * @param annualYieldEstimate    estimated annual dividend yield as a percentage (e.g., 0.5 for 0.5%), nullable
 * @param dividendCountLastYear  dividend payments in the trailing 12 months; null when not determined
 * @param assetType              "STOCK" or "CRYPTO"
 */
public record DividendInfo(
    String ticker,
    Boolean paysDividends,
    BigDecimal annualYieldEstimate,
    Integer dividendCountLastYear,
    String assetType
) {

    public DividendInfo {
        if (ticker == null || ticker.isBlank()) {
            throw new IllegalArgumentException("Ticker must not be null or blank");
        }
        if (assetType == null || assetType.isBlank()) {
            throw new IllegalArgumentException("Asset type must not be null or blank");
        }
        if (dividendCountLastYear != null && dividendCountLastYear < 0) {
            throw new IllegalArgumentException("Dividend count must not be negative");
        }
    }

    /**
     * Factory for a ticker that pays no dividends (stock with no recent dividend history).
     */
    public static DividendInfo noDividends(String ticker) {
        return new DividendInfo(ticker, false, null, 0, "STOCK");
    }

    /**
     * Factory for a cryptocurrency (never pays dividends).
     */
    public static DividendInfo crypto(String ticker) {
        return new DividendInfo(ticker, false, null, 0, "CRYPTO");
    }

    /**
     * Factory for a stock that pays dividends with known yield.
     */
    public static DividendInfo withDividends(String ticker, BigDecimal annualYieldEstimate, int dividendCount) {
        return new DividendInfo(ticker, true, annualYieldEstimate, dividendCount, "STOCK");
    }

    /**
     * Factory for when dividend info could not be determined (API failure, etc.).
     */
    public static DividendInfo unknown(String ticker, String assetType) {
        return new DividendInfo(ticker, null, null, null, assetType);
    }

    /**
     * @return {@code true} when the dividend history was actually established.
     *     Callers must check this before presenting the answer as fact, and omit
     *     the field rather than rendering "unknown" as "no".
     */
    public boolean isKnown() {
        return paysDividends != null;
    }

    /** @return {@code true} only when this ticker is known to pay dividends. */
    public boolean paysDividendsKnown() {
        return Boolean.TRUE.equals(paysDividends);
    }
}
