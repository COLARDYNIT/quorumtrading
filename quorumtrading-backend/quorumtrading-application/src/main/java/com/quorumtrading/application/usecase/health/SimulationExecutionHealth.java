package com.quorumtrading.application.usecase.health;

import java.time.Instant;
import java.util.List;

/**
 * Live execution health of a simulation — the answer to "why is this run not progressing?",
 * available while it is running.
 *
 * <p>The platform already records why every round produced nothing (SKIP decision rows with
 * reason notes), but the headline surfaces refused to look until a run was COMPLETED: the
 * post-mortem {@code noDecisionsReason} field is hard-null for anything in flight, so a
 * stalling backtest reported a normal status, zero counters and four empty panels. This read
 * model is computed on demand from the recent decision rows plus the simulation's counters,
 * and is rendered by every driving adapter (MCP {@code get_simulation}, REST
 * {@code /simulations/{id}/status}, the web UI) so they cannot drift apart.</p>
 *
 * <p>Terminology: an <b>evaluation</b> is one scheduled consensus round (the simulation's
 * counters count those); a <b>row</b> is one persisted consensus decision — one per ticker
 * per round, so for a single-ticker strategy the two coincide. Recent-window numbers count
 * rows because that is what is persisted; instants are the rows' decision timestamps, which
 * run on simulation time during a backtest.</p>
 *
 * @param status                      lifecycle status the verdict was computed for (enum name)
 * @param verdict                     coarse classification, see {@link Verdict}
 * @param detail                      one human-readable sentence explaining the verdict
 * @param lastDecisionAt              decision timestamp of the newest non-SKIP row, null when
 *                                    the run has not decided anything yet
 * @param lastRoundAt                 decision timestamp of the newest row of any kind (SKIPs
 *                                    included), null when no round has ever been recorded
 * @param recentRows                  rows inspected (newest first, capped at the window size)
 * @param recentSkippedRows           how many of those rows are skips
 * @param consecutiveSkippedRows      length of the skip streak at the head of the window
 * @param totalEvaluations            lifetime evaluations attempted (simulation counter)
 * @param totalSkippedEvaluations     lifetime evaluations skipped (simulation counter)
 * @param topSkipReasons              most frequent skip reasons in the window, normalised
 *                                    (embedded timestamps collapsed) and counted, largest first
 * @param externalPauseReason         why the system paused the run (BUDGET_DEPLETED,
 *                                    DATA_GAP_DETECTED, LLM_FAILURE_DETECTED, …), null otherwise
 * @param failureReason               why the run failed, null unless status is FAILED
 */
public record SimulationExecutionHealth(
        String status,
        Verdict verdict,
        String detail,
        Instant lastDecisionAt,
        Instant lastRoundAt,
        int recentRows,
        int recentSkippedRows,
        int consecutiveSkippedRows,
        int totalEvaluations,
        int totalSkippedEvaluations,
        List<SkipReason> topSkipReasons,
        String externalPauseReason,
        String failureReason
) {

    /** Coarse execution-health classification, ordered roughly from "fine" to "broken". */
    public enum Verdict {
        /** Recent rounds are producing decisions. */
        HEALTHY,
        /** Running, but no decision yet — first rounds pending or too few rows to judge. */
        IDLE,
        /** Running, and the recent rounds are all being skipped — the run burns time without deciding. */
        STALLED,
        /** Waiting for a historical price backfill before evaluating. */
        WAITING_FOR_DATA,
        /** Paused by the user; resuming it continues the run. */
        PAUSED_BY_USER,
        /** Paused by the system — see {@link #externalPauseReason()}. */
        PAUSED_EXTERNAL,
        /** Created but not started. */
        NOT_STARTED,
        /** Finished. */
        COMPLETED,
        /** Failed — see {@link #failureReason()}. */
        FAILED
    }

    /** One normalised skip reason and how often it occurred in the inspected window. */
    public record SkipReason(String reason, int count) {
    }
}
