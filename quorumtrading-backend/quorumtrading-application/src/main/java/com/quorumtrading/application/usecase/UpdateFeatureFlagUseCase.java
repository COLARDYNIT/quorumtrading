package com.quorumtrading.application.usecase;

import com.quorumtrading.application.usecase.command.UpdateFeatureFlagCommand;
import com.quorumtrading.model.entity.FeatureFlag;

/**
 * Changes a flag's state. Administrators only.
 *
 * <p>The admin check lives here rather than in the REST resource so that any future driving
 * adapter — MCP, a CLI, a scheduled job — inherits it automatically.</p>
 */
public interface UpdateFeatureFlagUseCase {

    FeatureFlag execute(UpdateFeatureFlagCommand command);
}
