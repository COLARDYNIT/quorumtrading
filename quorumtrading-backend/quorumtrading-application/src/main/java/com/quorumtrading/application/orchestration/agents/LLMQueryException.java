package com.quorumtrading.application.orchestration.agents;

/**
 * Exception thrown when LLM query operations fail.
 */
public class LLMQueryException extends RuntimeException {

    /**
     * True when the failure was the router's per-call cap firing, not the provider erroring.
     * A timeout used to be distinguishable only by string-matching the message, so nothing
     * could count timeouts separately from real provider errors.
     */
    private final boolean timedOut;

    public LLMQueryException(String message) {
        this(message, null, false);
    }

    public LLMQueryException(String message, Throwable cause) {
        this(message, cause, false);
    }

    public LLMQueryException(String message, Throwable cause, boolean timedOut) {
        super(message, cause);
        this.timedOut = timedOut;
    }

    public boolean isTimedOut() {
        return timedOut;
    }
}
