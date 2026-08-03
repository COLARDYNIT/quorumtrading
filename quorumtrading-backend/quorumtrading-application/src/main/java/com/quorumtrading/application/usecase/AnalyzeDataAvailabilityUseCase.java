package com.quorumtrading.application.usecase;

import com.quorumtrading.application.port.in.DataAvailabilityPort;
import com.quorumtrading.application.usecase.command.AnalyzeDataAvailabilityCommand;
import com.quorumtrading.model.enums.EventType;
import com.quorumtrading.model.marketdata.DataAvailability;
import com.quorumtrading.model.valueobject.Symbol;
import com.quorumtrading.model.valueobject.TimeRange;

import java.util.Set;

/**
 * Use case for analyzing market data availability.
 * Implements the DataAvailabilityPort inbound port.
 *
 * <p>This use case:</p>
 * <ul>
 *   <li>Analyzes data coverage for a given time range, symbols, and data types</li>
 *   <li>Calculates daily and overall coverage metrics</li>
 *   <li>Identifies gaps in data that may require backfilling</li>
 * </ul>
 */
public interface AnalyzeDataAvailabilityUseCase extends DataAvailabilityPort {
    /**
     * Execute the data availability analysis.
     *
     * @param command the analysis command containing time range, symbols, and data types
     * @return DataAvailability aggregate with coverage metrics and gap information
     */
    DataAvailability execute(AnalyzeDataAvailabilityCommand command);

    @Override
    default DataAvailability analyzeAvailability(TimeRange range, Set<Symbol> symbols, Set<EventType> types) {
        return execute(new AnalyzeDataAvailabilityCommand(range, symbols, types));
    }
}
