package com.quorumtrading.application.usecase.command;

import com.quorumtrading.model.valueobject.StrategyId;
import com.quorumtrading.model.valueobject.UserId;

public record CloneStrategyCommand(
    StrategyId sourceStrategyId,
    UserId requestingUserId
) {}
