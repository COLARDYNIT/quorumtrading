package com.quorumtrading.application.usecase.command;

import com.quorumtrading.model.valueobject.StrategyId;
import com.quorumtrading.model.valueobject.UserId;

public record ToggleStrategyVisibilityCommand(
    StrategyId strategyId,
    boolean makePublic,
    UserId requestingUserId
) {}
