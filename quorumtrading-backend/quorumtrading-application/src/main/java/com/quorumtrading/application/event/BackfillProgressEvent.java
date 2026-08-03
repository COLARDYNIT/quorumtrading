package com.quorumtrading.application.event;

import com.quorumtrading.model.marketdata.GapProgress;

import java.util.List;
import java.util.UUID;

/**
 * Domain event fired when backfill job makes progress.
 * This event is observed by adapters (e.g., WebSocket adapter) to broadcast updates.
 *
 * <p>Contains per-gap status information to enable the frontend to show detailed
 * progress with success/failure reasons for each gap.</p>
 */
public record BackfillProgressEvent(
    UUID jobId,
    int progressPercentage,
    int processedRecords,
    int totalRecords,
    /** The gap that was just processed (null for initial/final events) */
    GapProgress currentGap,
    /** All gap progress so far (for cumulative tracking) */
    List<GapProgress> allGapProgress
) {
    /**
     * Create a progress event without per-gap details (backward compatibility).
     */
    public BackfillProgressEvent(UUID jobId, int progressPercentage, int processedRecords, int totalRecords) {
        this(jobId, progressPercentage, processedRecords, totalRecords, null, List.of());
    }
}
