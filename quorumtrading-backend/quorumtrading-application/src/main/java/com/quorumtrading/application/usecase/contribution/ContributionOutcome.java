package com.quorumtrading.application.usecase.contribution;

import com.quorumtrading.model.entity.Contribution;

import java.util.Objects;

/**
 * The result of recording a contribution, and whether it actually recorded one.
 *
 * <p>Recording is idempotent on {@code (sourcePlanId, contributionDate)} — that is what makes a
 * retried scheduler tick safe. But the operation has two genuinely different outcomes, and
 * returning a bare {@link Contribution} collapsed them: the caller received the same object
 * whether it had just moved money or had matched an existing row and moved nothing. The plan
 * executor, unable to tell, reported every call as a funded execution.</p>
 *
 * @param contribution    the contribution — newly created, or the existing one it matched
 * @param alreadyRecorded {@code true} when this call was a no-op against an existing row
 */
public record ContributionOutcome(Contribution contribution, boolean alreadyRecorded) {

    public ContributionOutcome {
        Objects.requireNonNull(contribution, "contribution is required");
    }

    /** Money moved: this call created the contribution. */
    public static ContributionOutcome created(Contribution contribution) {
        return new ContributionOutcome(contribution, false);
    }

    /** No money moved: an equivalent contribution already existed for this plan and date. */
    public static ContributionOutcome alreadyRecorded(Contribution existing) {
        return new ContributionOutcome(existing, true);
    }
}
