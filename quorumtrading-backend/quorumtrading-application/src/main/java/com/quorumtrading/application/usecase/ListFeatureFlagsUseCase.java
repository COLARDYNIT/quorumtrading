package com.quorumtrading.application.usecase;

import com.quorumtrading.model.entity.FeatureFlag;
import com.quorumtrading.model.valueobject.UserId;

import java.util.List;

/**
 * Lists every registered flag with its current state, for the admin UI.
 *
 * <p>Administrators only — the list exposes which identities a flag is targeted at.</p>
 */
public interface ListFeatureFlagsUseCase {

    List<FeatureFlag> execute(UserId requestingUserId);
}
