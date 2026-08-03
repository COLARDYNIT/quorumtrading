package com.quorumtrading.model.valueobject;

/**
 * The vocabulary of {@code simulations.external_pause_reason} — every reason the system (never a
 * user) may pause a run with.
 *
 * <p>One vocabulary, one home. These strings are written by the circuit breaker, the executor's
 * budget guard and the stall detector, and they are read back by the health read model to render
 * an explanation; when the writers and the reader each kept their own literal, a reason could be
 * persisted that no surface knew how to explain, and the run showed "Paused by the system." with
 * nothing after it.</p>
 *
 * <p>A pause is always <em>recoverable</em>: whatever the reason, one resume continues the run
 * from its last decision. That is the whole point of the vocabulary — see
 * {@code docs/adr/0004-watchdogs-pause-on-evidence-never-fabricate-completion.md}, which forbids
 * a watchdog from writing a terminal state instead.</p>
 */
public final class ExternalPauseReason {

    /** The owner's AI budget (or daily spend cap) is exhausted; top it up and resume. */
    public static final String BUDGET_DEPLETED = "BUDGET_DEPLETED";

    /** Repeated rounds skipped for want of market data; a re-backfill was requested. */
    public static final String DATA_GAP_DETECTED = "DATA_GAP_DETECTED";

    /** Repeated rounds in which the LLM engine failed or timed out; no backfill can fix that. */
    public static final String LLM_FAILURE_DETECTED = "LLM_FAILURE_DETECTED";

    /**
     * The run stopped making progress and no executor holds its lease — a hung or vanished
     * evaluation. Written only by the stall detector, and only on execution evidence (a dead
     * lease plus no new decision), never on how long the run has existed.
     */
    public static final String EXECUTION_STALLED = "EXECUTION_STALLED";

    private ExternalPauseReason() {
        // constants holder
    }
}
