package com.quorumtrading.application.usecase.result;

import com.quorumtrading.model.enums.BackfillStatus;
import java.time.Instant;
import java.util.UUID;

public record RequestBackfillResult(
    boolean backfillTriggered,
    UUID jobId,
    String message,
    BackfillStatus status,
    Instant createdAt
) {
    public static RequestBackfillResult noBackfillNeeded() {
        return new RequestBackfillResult(
            false,
            null,
            "Data is already complete",
            null,
            null
        );
    }

    public static RequestBackfillResult backfillStarted(UUID jobId, Instant createdAt) {
        return new RequestBackfillResult(
            true,
            jobId,
            "Backfill job queued",
            BackfillStatus.PENDING,
            createdAt
        );
    }
}
