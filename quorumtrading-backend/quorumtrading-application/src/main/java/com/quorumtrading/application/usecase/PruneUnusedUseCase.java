package com.quorumtrading.application.usecase;

import com.quorumtrading.application.usecase.command.PruneUnusedCommand;
import com.quorumtrading.application.usecase.result.PruneUnusedResult;

public interface PruneUnusedUseCase {
    PruneUnusedResult execute(PruneUnusedCommand command);
}
