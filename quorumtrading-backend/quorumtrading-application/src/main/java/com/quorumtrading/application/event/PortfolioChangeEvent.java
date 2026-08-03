package com.quorumtrading.application.event;

import com.quorumtrading.model.entity.Position;
import com.quorumtrading.model.valueobject.SimulationId;
import com.quorumtrading.model.valueobject.Symbol;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * Application event fired when a portfolio snapshot is created.
 */
public record PortfolioChangeEvent(
        SimulationId simulationId,
        BigDecimal totalValueUsd,
        BigDecimal cashAvailable,
        Instant timestamp,
        Map<Symbol, Position> positions) // Domain positions Map
{
}
