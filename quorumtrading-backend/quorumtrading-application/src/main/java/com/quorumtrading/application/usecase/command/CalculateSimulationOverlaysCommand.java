package com.quorumtrading.application.usecase.command;

import com.quorumtrading.model.valueobject.SimulationId;
import com.quorumtrading.model.valueobject.Symbol;
import com.quorumtrading.model.valueobject.UserId;

import java.util.List;
import java.util.Objects;

/**
 * Command for computing buy-and-hold overlays against a simulation.
 *
 * @param simulationId The simulation whose date range and capital to use
 * @param symbols      Reference assets (e.g., SPY, QQQ, BTC)
 * @param userId       The requesting user
 * @param resolution   Time bucket for downsampling: "1d", "1h", "1w". Null defaults to "1d".
 */
public record CalculateSimulationOverlaysCommand(
    SimulationId simulationId,
    List<Symbol> symbols,
    UserId userId,
    String resolution
) {
    public CalculateSimulationOverlaysCommand {
        Objects.requireNonNull(simulationId, "simulationId is required");
        Objects.requireNonNull(symbols, "symbols is required");
        if (symbols.isEmpty()) {
            throw new IllegalArgumentException("At least one symbol is required");
        }
    }
}
