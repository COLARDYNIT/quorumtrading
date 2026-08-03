package com.quorumtrading.adapter.rest.dto.response;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/**
 * Live execution health of a simulation — the answer to "why is this run not progressing?"
 * while it is still running. Returned by {@code GET /simulations/{id}/status}.
 *
 * <p>Numbers count persisted consensus decision rows (one per ticker per round); instants are
 * the rows' decision timestamps, which run on simulation time during a backtest.</p>
 */
@Schema(description = "Live execution health of a simulation")
public record SimulationHealthResponse(

    @Schema(description = "Lifecycle status the verdict was computed for",
            example = "SIMULATING")
    String status,

    @Schema(description = "Coarse health classification",
            example = "STALLED",
            enumeration = {"HEALTHY", "IDLE", "STALLED", "WAITING_FOR_DATA", "PAUSED_BY_USER",
                           "PAUSED_EXTERNAL", "NOT_STARTED", "COMPLETED", "FAILED"})
    String verdict,

    @Schema(description = "One sentence explaining the verdict, safe to show verbatim",
            example = "The engine is not producing decisions: the last 12 consensus row(s) are all skips.")
    String detail,

    @Schema(description = "When the run last produced a real decision (BUY/SELL/HOLD); null when it never has",
            nullable = true)
    Instant lastDecisionAt,

    @Schema(description = "When the run last recorded any consensus row, skips included; null when none exist",
            nullable = true)
    Instant lastRoundAt,

    @Schema(description = "Rows inspected for the verdict (newest first, capped window)")
    int recentRows,

    @Schema(description = "How many of the inspected rows are skips")
    int recentSkippedRows,

    @Schema(description = "Skip streak at the head of the window — the 'how stuck is it' number")
    int consecutiveSkippedRows,

    @Schema(description = "Lifetime evaluations attempted")
    int totalEvaluations,

    @Schema(description = "Lifetime evaluations skipped")
    int totalSkippedEvaluations,

    @Schema(description = "Most frequent skip reasons in the window, counted, largest first (max 3)")
    List<SkipReasonResponse> topSkipReasons,

    @Schema(description = "Why the system paused the run (BUDGET_DEPLETED, DATA_GAP_DETECTED, "
                        + "LLM_FAILURE_DETECTED); null when not system-paused",
            nullable = true)
    String externalPauseReason,

    @Schema(description = "Why the run failed; null unless status is FAILED", nullable = true)
    String failureReason
) {

    /** One normalised skip reason and how often it occurred in the inspected window. */
    @Schema(description = "A skip reason and its occurrence count in the inspected window")
    public record SkipReasonResponse(
        @Schema(description = "Normalised reason text (embedded timestamps collapsed)")
        String reason,
        @Schema(description = "Occurrences in the window")
        int count
    ) {
    }
}
