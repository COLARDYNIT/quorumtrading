package com.quorumtrading.application.event;

import java.util.UUID;

/**
 * Domain event fired when a new backfill job is created and saved.
 * This event is observed by adapters (e.g., WebSocket adapter) to broadcast
 * immediate notification to clients that a backfill job has been queued.
 *
 * <p>This allows the frontend to show the job immediately after creation,
 * rather than waiting for the scheduler to pick it up and fire IN_PROGRESS.</p>
 */
public record BackfillJobCreatedEvent(
    UUID jobId,
    int totalGaps
) {
}
