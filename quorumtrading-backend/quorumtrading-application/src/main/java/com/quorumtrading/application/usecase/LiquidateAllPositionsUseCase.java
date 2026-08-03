package com.quorumtrading.application.usecase;

import com.quorumtrading.application.usecase.result.LiquidateAllResult;
import com.quorumtrading.model.valueobject.SimulationId;
import com.quorumtrading.model.valueobject.UserId;

public interface LiquidateAllPositionsUseCase {

    LiquidateAllResult execute(SimulationId simulationId, UserId requestingUserId);

    /** Backward-compatible overload for internal/system callers. */
    default LiquidateAllResult execute(SimulationId simulationId) {
        return execute(simulationId, null);
    }
}
