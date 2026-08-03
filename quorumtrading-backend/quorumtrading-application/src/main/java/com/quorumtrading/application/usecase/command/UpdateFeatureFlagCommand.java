package com.quorumtrading.application.usecase.command;

import com.quorumtrading.model.enums.Feature;
import com.quorumtrading.model.valueobject.FeatureFlagStrategy;
import com.quorumtrading.model.valueobject.UserId;

import java.util.List;

/**
 * @param strategies who the flag applies to when enabled; empty means everyone
 */
public record UpdateFeatureFlagCommand(
    Feature feature,
    boolean enabled,
    List<FeatureFlagStrategy> strategies,
    UserId requestingUserId
) {
    public UpdateFeatureFlagCommand {
        strategies = strategies == null ? List.of() : List.copyOf(strategies);
    }
}
