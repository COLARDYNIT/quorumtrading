package com.quorumtrading.application.usecase;

import com.quorumtrading.application.usecase.command.CloneStrategyCommand;
import com.quorumtrading.model.valueobject.StrategyId;

public interface CloneStrategyUseCase {
    StrategyId execute(CloneStrategyCommand command);
}
