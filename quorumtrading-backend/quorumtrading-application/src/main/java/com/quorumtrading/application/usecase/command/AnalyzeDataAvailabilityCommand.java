package com.quorumtrading.application.usecase.command;

import com.quorumtrading.model.enums.EventType;
import com.quorumtrading.model.valueobject.Symbol;
import com.quorumtrading.model.valueobject.TimeRange;
import java.util.Set;

public record AnalyzeDataAvailabilityCommand(
    TimeRange timeRange,
    Set<Symbol> symbols,
    Set<EventType> dataTypes
) {
    public AnalyzeDataAvailabilityCommand {
        if (timeRange == null) throw new IllegalArgumentException("TimeRange cannot be null");
        if (symbols == null || symbols.isEmpty()) throw new IllegalArgumentException("Symbols cannot be empty");
        if (dataTypes == null || dataTypes.isEmpty()) throw new IllegalArgumentException("DataTypes cannot be empty");
    }
}
