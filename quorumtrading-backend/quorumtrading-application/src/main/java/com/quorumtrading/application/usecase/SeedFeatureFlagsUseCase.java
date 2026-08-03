package com.quorumtrading.application.usecase;

/**
 * Creates rows for any registered feature that has none yet, using the profile defaults.
 *
 * <p>Runs at startup. Insert-only: an existing row is never touched, so a flag an administrator
 * switched on does not revert on the next deploy.</p>
 */
public interface SeedFeatureFlagsUseCase {

    /** @return the number of flags created */
    int execute();
}
