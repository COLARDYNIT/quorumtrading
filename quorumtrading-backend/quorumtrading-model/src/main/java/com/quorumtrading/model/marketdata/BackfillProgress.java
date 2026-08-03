package com.quorumtrading.model.marketdata;

import com.quorumtrading.model.enums.BackfillStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * Value object representing the current progress of a backfill job.
 * Provides a read-only view of backfill status and metrics.
 *
 * <p>This is created from BackfillJob entity for API responses,
 * following the query-side model in CQRS-lite pattern.</p>
 */
public record BackfillProgress(
    UUID jobId,
    BackfillStatus status,
    Integer progressPercentage,
    Instant startedAt,
    Instant completedAt,
    String errorMessage,
    Integer totalRecordsExpected,
    Integer recordsProcessed
) {
    /**
     * Create progress from a backfill request entity.
     *
     * @param request the backfill request entity
     * @return progress value object
     */
    public static BackfillProgress from(BackfillJob request) {
        return new BackfillProgress(
            request.getId(),
            request.getStatus(),
            request.getProgressPercentage(),
            request.getStartedAt(),
            request.getCompletedAt(),
            request.getErrorMessage(),
            request.getTotalRecordsExpected(),
            request.getRecordsProcessed()
        );
    }
}
