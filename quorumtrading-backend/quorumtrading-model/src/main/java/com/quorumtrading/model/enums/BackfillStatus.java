package com.quorumtrading.model.enums;

/**
 * Status of a backfill job.
 * Tracks the lifecycle of market data backfill requests.
 */
public enum BackfillStatus {
    /**
     * Job is queued and waiting to be processed.
     */
    PENDING,

    /**
     * Job is currently being processed.
     */
    IN_PROGRESS,

    /**
     * Job completed successfully.
     */
    COMPLETED,

    /**
     * Job is waiting to retry after a transient error (e.g., rate limit).
     */
    RETRY_PENDING,

    /**
     * Job failed due to an error.
     */
    FAILED
}
