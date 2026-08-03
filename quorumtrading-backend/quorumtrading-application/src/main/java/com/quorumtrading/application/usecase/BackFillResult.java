package com.quorumtrading.application.usecase;

/**
 * Result of backfilling historical price data.
 *
 * A backfill fetch can return more than price bars (providers bundle news events for the
 * Fundamental/Sentiment agents); the price and news counts are reported separately so
 * "recordsInserted" always means what it says: price bars.
 *
 * @param ticker the ticker symbol that was backfilled
 * @param assetType the type of asset (CRYPTO or STOCK)
 * @param recordsInserted number of new PRICE bars inserted
 * @param recordsSkipped number of duplicate PRICE bars skipped
 * @param recordsFailed number of PRICE bars that failed to insert
 * @param newsRecordsInserted number of non-price records (news/sentiment events) inserted
 * @param newsRecordsSkipped number of non-price records skipped as duplicates or failed
 * @param success whether the backfill completed successfully
 * @param errorMessage error message if backfill failed, null otherwise
 */
public record BackFillResult(
    String ticker,
    String assetType,
    int recordsInserted,
    int recordsSkipped,
    int recordsFailed,
    int newsRecordsInserted,
    int newsRecordsSkipped,
    boolean success,
    String errorMessage
) {
    /**
     * Create a successful backfill result with price-bar counts only (no news records).
     */
    public static BackFillResult success(String ticker, String assetType, int inserted, int skipped, int failed) {
        return new BackFillResult(ticker, assetType, inserted, skipped, failed, 0, 0, true, null);
    }

    /**
     * Create a successful backfill result with separate price-bar and news counts.
     */
    public static BackFillResult success(String ticker, String assetType, int inserted, int skipped, int failed,
                                         int newsInserted, int newsSkipped) {
        return new BackFillResult(ticker, assetType, inserted, skipped, failed, newsInserted, newsSkipped, true, null);
    }

    /**
     * Create a failed backfill result.
     */
    public static BackFillResult failure(String ticker, String assetType, String errorMessage) {
        return new BackFillResult(ticker, assetType, 0, 0, 0, 0, 0, false, errorMessage);
    }

    /**
     * Get total price records processed (inserted + skipped + failed).
     */
    public int totalProcessed() {
        return recordsInserted + recordsSkipped + recordsFailed;
    }
}
