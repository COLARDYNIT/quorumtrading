package com.quorumtrading.application.usecase;

import com.quorumtrading.application.usecase.command.ToggleStrategyVisibilityCommand;

public interface ToggleStrategyVisibilityUseCase {
    void execute(ToggleStrategyVisibilityCommand command);
}
