package com.quorumtrading.application.usecase.contribution;

import com.quorumtrading.application.usecase.command.RecordSimulationContributionCommand;

public interface RecordSimulationContributionUseCase {
    /**
     * Records a contribution. Idempotent on {@code (sourcePlanId, contributionDate)} when both
     * are present — repeated calls with the same key return the existing contribution rather
     * than creating a duplicate.
     *
     * <p>The result says which of the two happened. Callers that report the outcome to a user
     * must branch on it: a deduplicated call moved no money, and describing it as a funded
     * execution is a false statement about someone's money.
     *
     * @throws java.util.NoSuchElementException if the target simulation does not exist
     * @throws IllegalStateException            if the simulation is not in LIVE state
     */
    ContributionOutcome execute(RecordSimulationContributionCommand command);
}
