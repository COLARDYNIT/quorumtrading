package com.quorumtrading.application.orchestration;

import com.quorumtrading.model.entity.Portfolio;

import java.time.Instant;

/**
 * Value object representing the resume context for a simulation.
 * Contains information about where to resume execution and the initial portfolio state.
 */
public class ResumeContext {

    private final Instant resumeFrom;
    private final Portfolio initialPortfolio;
    private final boolean isResuming;
    private final boolean isComplete;

    private ResumeContext(Instant resumeFrom, Portfolio initialPortfolio, boolean isResuming, boolean isComplete) {
        this.resumeFrom = resumeFrom;
        this.initialPortfolio = initialPortfolio;
        this.isResuming = isResuming;
        this.isComplete = isComplete;
    }

    /**
     * Create a context for starting a simulation from the beginning.
     */
    public static ResumeContext startFresh(Instant startDate) {
        return new ResumeContext(startDate, null, false, false);
    }

    /**
     * Create a context for resuming from a checkpoint.
     */
    public static ResumeContext resumeFrom(Instant resumePoint, Portfolio portfolio) {
        return new ResumeContext(resumePoint, portfolio, true, false);
    }

    /**
     * Create a context indicating simulation is already complete.
     */
    public static ResumeContext alreadyComplete(Instant lastTimestamp) {
        return new ResumeContext(lastTimestamp, null, false, true);
    }

    public Instant getResumeFrom() {
        return resumeFrom;
    }

    public Portfolio getInitialPortfolio() {
        return initialPortfolio;
    }

    public boolean isResuming() {
        return isResuming;
    }

    public boolean isComplete() {
        return isComplete;
    }
}
