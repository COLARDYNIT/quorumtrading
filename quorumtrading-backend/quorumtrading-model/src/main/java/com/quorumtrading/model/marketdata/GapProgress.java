package com.quorumtrading.model.marketdata;

import com.quorumtrading.model.enums.EventType;
import com.quorumtrading.model.valueobject.Symbol;
import com.quorumtrading.model.valueobject.TimeRange;

/**
 * Value object representing the progress of filling a single market data gap.
 * Provides visibility into which gaps succeeded, failed, or were skipped.
 *
 * <p>This enables the frontend to show per-gap status with detailed error messages,
 * improving backfill UX by showing exactly what worked and what didn't.</p>
 */
public record GapProgress(
    /** The symbol (null for market-wide data like NEWS) */
    Symbol symbol,
    /** The data type of this gap */
    EventType dataType,
    /** The time period this gap covers */
    TimeRange period,
    /** Current status of this gap */
    GapStatus status,
    /** Provider used to fill this gap (null if skipped/failed before provider selection) */
    String providerName,
    /** Error message if failed/skipped (null if successful/pending) */
    String errorMessage,
    /** Number of events fetched (null if not yet processed) */
    Integer eventsFetched
) {
    /**
     * Status of a single gap during backfill.
     */
    public enum GapStatus {
        /** Gap is queued and not yet processed */
        PENDING,
        /** Gap is currently being processed */
        IN_PROGRESS,
        /** Gap was successfully filled with data */
        SUCCESS,
        /** Gap failed to fill (provider error, timeout, etc.) */
        FAILED,
        /** Gap was skipped (no provider available for this data type) */
        SKIPPED,
        /**
         * A provider ran successfully but returned no data for this range.
         * This is not an error and not a fill — the provider simply has no data
         * for the requested symbol/period (e.g. crypto history before the
         * provider's earliest coverage). Distinct from SUCCESS so the operator
         * is not told data was filled when nothing was stored.
         */
        NO_DATA
    }

    /**
     * Create a pending gap progress from a MarketDataGap.
     */
    public static GapProgress pending(MarketDataGap gap) {
        return new GapProgress(
            gap.symbol(),
            gap.dataType(),
            gap.period(),
            GapStatus.PENDING,
            null,
            null,
            null
        );
    }

    /**
     * Create an in-progress gap progress.
     */
    public static GapProgress inProgress(MarketDataGap gap, String providerName) {
        return new GapProgress(
            gap.symbol(),
            gap.dataType(),
            gap.period(),
            GapStatus.IN_PROGRESS,
            providerName,
            null,
            null
        );
    }

    /**
     * Create a successful gap progress.
     */
    public static GapProgress success(MarketDataGap gap, String providerName, int eventsFetched) {
        return new GapProgress(
            gap.symbol(),
            gap.dataType(),
            gap.period(),
            GapStatus.SUCCESS,
            providerName,
            null,
            eventsFetched
        );
    }

    /**
     * Create a failed gap progress.
     */
    public static GapProgress failed(MarketDataGap gap, String providerName, String errorMessage) {
        return new GapProgress(
            gap.symbol(),
            gap.dataType(),
            gap.period(),
            GapStatus.FAILED,
            providerName,
            errorMessage,
            null
        );
    }

    /**
     * Create a no-data gap progress: a provider ran without error but returned
     * no data for this range. Records 0 events fetched.
     */
    public static GapProgress noData(MarketDataGap gap, String providerName) {
        return new GapProgress(
            gap.symbol(),
            gap.dataType(),
            gap.period(),
            GapStatus.NO_DATA,
            providerName,
            "No data available from provider for this range",
            0
        );
    }

    /**
     * Create a skipped gap progress (no provider available).
     */
    public static GapProgress skipped(MarketDataGap gap, String reason) {
        return new GapProgress(
            gap.symbol(),
            gap.dataType(),
            gap.period(),
            GapStatus.SKIPPED,
            null,
            reason,
            null
        );
    }

    /**
     * Check if this gap is for market-wide data (NEWS/SOCIAL).
     */
    public boolean isMarketWide() {
        return symbol == null;
    }

    /**
     * Get display name for the symbol (or "MARKET" for market-wide).
     */
    public String getSymbolDisplay() {
        return symbol != null ? symbol.ticker() : "MARKET";
    }
}
