package com.quorumtrading.application.port.in;

import com.quorumtrading.model.enums.EventType;
import com.quorumtrading.model.marketdata.DataAvailability;
import com.quorumtrading.model.valueobject.Symbol;
import com.quorumtrading.model.valueobject.TimeRange;
import java.util.Set;

public interface DataAvailabilityPort {
    DataAvailability analyzeAvailability(TimeRange range, Set<Symbol> symbols, Set<EventType> types);
}
