package com.quorumtrading.application.usecase.command;

import com.quorumtrading.model.enums.EventType;
import com.quorumtrading.model.enums.Granularity;
import com.quorumtrading.model.valueobject.SimulationId;
import com.quorumtrading.model.valueobject.Symbol;
import com.quorumtrading.model.valueobject.TimeRange;
import java.util.Set;

public record RequestBackfillCommand(
    Set<Symbol> symbols,
    Set<EventType> dataTypes,
    TimeRange timeRange,
    boolean forceBackfill,
    SimulationId linkedSimulationId,
    Granularity granularity
) {
    public RequestBackfillCommand {
        if (symbols == null || symbols.isEmpty()) throw new IllegalArgumentException("Symbols cannot be empty");
        if (dataTypes == null || dataTypes.isEmpty()) throw new IllegalArgumentException("DataTypes cannot be empty");
        if (timeRange == null) throw new IllegalArgumentException("TimeRange cannot be null");
    }

    /**
     * Convenience constructor without granularity (defaults to ONE_DAY).
     */
    public RequestBackfillCommand(Set<Symbol> symbols, Set<EventType> dataTypes, TimeRange timeRange, boolean forceBackfill, SimulationId linkedSimulationId) {
        this(symbols, dataTypes, timeRange, forceBackfill, linkedSimulationId, Granularity.ONE_DAY);
    }

    /**
     * Convenience constructor without linkedSimulationId or granularity.
     */
    public RequestBackfillCommand(Set<Symbol> symbols, Set<EventType> dataTypes, TimeRange timeRange, boolean forceBackfill) {
        this(symbols, dataTypes, timeRange, forceBackfill, null, Granularity.ONE_DAY);
    }
}
