package com.quorumtrading.application.event;

import com.quorumtrading.model.enums.BackfillStatus;

import java.util.UUID;

/**
 * Domain event fired when backfill job status changes.
 * This event is observed by adapters (e.g., WebSocket adapter) to broadcast updates.
 */
public record BackfillStatusChangedEvent(
    UUID jobId,
    BackfillStatus oldStatus,
    BackfillStatus newStatus,
    String errorMessage,
    Integer totalRecords
) {
}
