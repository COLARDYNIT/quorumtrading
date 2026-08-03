package com.quorumtrading.adapter.mcp.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.List;

/**
 * Live execution health of a simulation — the in-flight answer to "why is this run not
 * progressing?". Unlike {@link McpSimulationDto#noDecisionsReason()}, which is a post-mortem
 * for COMPLETED runs, this block is populated while the run is SIMULATING/LIVE, where the
 * question is actually asked.
 *
 * <p>Numbers count persisted decision rows (one per ticker per round); instants are the
 * rows' decision timestamps, which run on simulation time during a backtest.</p>
 */
@RegisterForReflection
public record McpSimulationHealthDto(
        /** HEALTHY, IDLE, STALLED, WAITING_FOR_DATA, PAUSED_BY_USER, PAUSED_EXTERNAL, NOT_STARTED, COMPLETED, FAILED. */
        String verdict,
        /** One sentence explaining the verdict — safe to show a user verbatim. */
        String detail,
        /** When the run last produced a real decision (BUY/SELL/HOLD); null when it never has. */
        String lastDecisionAt,
        /** When the run last recorded any round, skips included; null when no round ever ran. */
        String lastRoundAt,
        int recentRows,
        int recentSkippedRows,
        /** Skip streak at the head of the recent window — the "how stuck is it" number. */
        int consecutiveSkippedRows,
        int totalEvaluations,
        int totalSkippedEvaluations,
        /** Most frequent skip reasons in the window, counted, largest first (max 3). */
        List<McpSkipReasonDto> topSkipReasons
) {

    /** One normalised skip reason and how often it occurred in the inspected window. */
    @RegisterForReflection
    public record McpSkipReasonDto(String reason, int count) {
    }
}
