package com.quorumtrading.application.usecase.command;

import com.quorumtrading.model.enums.Granularity;
import com.quorumtrading.model.enums.InvestmentType;
import com.quorumtrading.model.valueobject.TickerForm;
import java.time.Instant;

/**
 * Command for backfilling historical price data for a specific ticker.
 *
 * @param ticker the ticker symbol (e.g., 'BTC', 'ETH', 'AAPL', 'TSLA')
 * @param assetType the type of asset - 'CRYPTO' or 'STOCK'
 * @param startDate start of the historical data range (inclusive)
 * @param endDate end of the historical data range (inclusive)
 * @param granularity the time interval for bars (e.g., Granularity.ONE_DAY, Granularity.ONE_HOUR). Defaults to ONE_DAY if null.
 */
public record BackFillTickerPriceDataCommand(
    String ticker,
    String assetType,
    Instant startDate,
    Instant endDate,
    Granularity granularity
) {
    /**
     * Validate the command parameters.
     *
     * @throws IllegalArgumentException if any required field is invalid
     */
    public void validate() {
        validateRequiredFields();
        validateDateRange();
        validateTickerForm();
        validateAssetClassMatchesTicker();
    }

    private void validateRequiredFields() {
        if (ticker == null || ticker.isBlank()) {
            throw new IllegalArgumentException("Ticker is required");
        }
        if (assetType == null || assetType.isBlank()) {
            throw new IllegalArgumentException("Asset type is required");
        }
        if (!isCrypto() && !isStock()) {
            throw new IllegalArgumentException("Asset type must be CRYPTO or STOCK");
        }
    }

    private void validateDateRange() {
        if (startDate == null) {
            throw new IllegalArgumentException("Start date is required");
        }
        if (endDate == null) {
            throw new IllegalArgumentException("End date is required");
        }
        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("End date must be after start date");
        }
    }

    /**
     * Form before dispatch.
     *
     * <p>A pair form used to reach the provider loop, get three live HTTP calls spent on it, and
     * come back as "most likely not covered by them … or the range predates its listing" — a
     * diagnosis about coverage, for a string no provider will accept in any range. This is the one
     * entry point that knew, and it only said so afterwards; {@code get_price_coverage} did not
     * know at all. Validating here means every driving adapter and the auto-backfill path inherit
     * the same answer.</p>
     */
    private void validateTickerForm() {
        TickerForm.Verdict form = TickerForm.check(
            ticker, isCrypto() ? InvestmentType.CRYPTO : InvestmentType.STOCK);
        if (!form.acceptable()) {
            throw new IllegalArgumentException(form.message());
        }
    }

    /**
     * Price bars are keyed by bare symbol, so a stock feed answering a crypto ticker writes a
     * different instrument into the same series: "BTC" as STOCK returns the NYSE Arca bitcoin ETF
     * (~$28), which then re-values every position held in crypto BTC (~$60k).
     *
     * <p>Only the certain direction is enforced — {@link InvestmentType} falls back to STOCK for
     * any ticker it does not recognise, so rejecting "unknown ticker as CRYPTO" would block every
     * coin missing from that list.</p>
     */
    private void validateAssetClassMatchesTicker() {
        if (isStock() && InvestmentType.fromTicker(ticker) == InvestmentType.CRYPTO) {
            throw new IllegalArgumentException(String.format(
                "%s is a crypto ticker — backfill it with assetType CRYPTO. Requesting it as STOCK"
                    + " fetches whichever equity or ETF happens to share the symbol and stores it"
                    + " under the same series, corrupting the crypto price history.",
                ticker.toUpperCase(java.util.Locale.ROOT)));
        }
    }

    /**
     * Check if this is a crypto ticker.
     */
    public boolean isCrypto() {
        return "CRYPTO".equalsIgnoreCase(assetType);
    }

    /**
     * Check if this is a stock ticker.
     */
    public boolean isStock() {
        return "STOCK".equalsIgnoreCase(assetType);
    }

    /**
     * Get the granularity to use for backfill.
     * Returns the provided granularity, or defaults to ONE_DAY if null.
     *
     * Default changed from ONE_HOUR to ONE_DAY to maximize API efficiency:
     * - Alpha Vantage free tier: 25 API calls/day
     * - Daily data allows backfilling all 11 tickers in 1 day (11 calls)
     * - Hourly data requires 2 calls per ticker = 22 calls (need 2 days)
     */
    public Granularity getGranularity() {
        if (granularity == null) {
            return Granularity.ONE_DAY;  // Default to daily data (changed from ONE_HOUR for API efficiency)
        }
        return granularity;
    }
}
