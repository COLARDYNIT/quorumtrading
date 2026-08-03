package com.quorumtrading.application.usecase;

import com.quorumtrading.application.usecase.result.SellPositionResult;
import com.quorumtrading.model.valueobject.SimulationId;
import com.quorumtrading.model.valueobject.Symbol;
import com.quorumtrading.model.valueobject.UserId;

public interface SellPositionUseCase {

    SellPositionResult execute(SimulationId simulationId, Symbol symbol, UserId requestingUserId);

    /** Backward-compatible overload for internal/system callers. */
    default SellPositionResult execute(SimulationId simulationId, Symbol symbol) {
        return execute(simulationId, symbol, null);
    }
}
