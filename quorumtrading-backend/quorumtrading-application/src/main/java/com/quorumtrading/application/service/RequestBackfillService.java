package com.quorumtrading.application.service;

import com.quorumtrading.application.event.BackfillJobCreatedEvent;
import com.quorumtrading.application.port.out.BackfillJobRepository;
import com.quorumtrading.application.usecase.AnalyzeDataAvailabilityUseCase;
import com.quorumtrading.application.usecase.RequestBackfillUseCase;
import com.quorumtrading.application.usecase.command.AnalyzeDataAvailabilityCommand;
import com.quorumtrading.application.usecase.command.RequestBackfillCommand;
import com.quorumtrading.application.usecase.result.RequestBackfillResult;
import com.quorumtrading.model.marketdata.BackfillJob;
import com.quorumtrading.model.marketdata.DataAvailability;
import com.quorumtrading.model.marketdata.MarketDataGap;
import jakarta.enterprise.event.Event;

import java.util.List;

/**
 * Application service implementing the RequestBackfillUseCase.
 *
 * <p>This service follows the hexagonal architecture pattern:</p>
 * <ul>
 *   <li>Implements the inbound port (use case interface)</li>
 *   <li>Coordinates between domain logic and outbound ports</li>
 *   <li>Delegates data availability analysis to AnalyzeDataAvailabilityUseCase</li>
 *   <li>Uses BackfillJobRepository outbound port to persist backfill requests</li>
 * </ul>
 *
 * <p><b>Business Logic:</b></p>
 * <ol>
 *   <li>Analyze current data availability for requested symbols/types/timerange</li>
 *   <li>If data is already complete (no gaps), return "no backfill needed" result</li>
 *   <li>If gaps exist, create BackfillJob domain entity with identified gaps</li>
 *   <li>Persist the backfill request via repository</li>
 *   <li>Return result with backfill job ID</li>
 * </ol>
 */
public class RequestBackfillService implements RequestBackfillUseCase {

    private final BackfillJobRepository backfillJobRepository;
    private final AnalyzeDataAvailabilityUseCase analyzeDataAvailabilityUseCase;
    private final Event<BackfillJobCreatedEvent> jobCreatedEvent;

    public RequestBackfillService(
        BackfillJobRepository backfillJobRepository,
        AnalyzeDataAvailabilityUseCase analyzeDataAvailabilityUseCase,
        Event<BackfillJobCreatedEvent> jobCreatedEvent
    ) {
        this.backfillJobRepository = backfillJobRepository;
        this.analyzeDataAvailabilityUseCase = analyzeDataAvailabilityUseCase;
        this.jobCreatedEvent = jobCreatedEvent;
    }

    @Override
    public RequestBackfillResult execute(RequestBackfillCommand command) {
        // First analyze current data availability (unless forceBackfill is true)
        var analysisCommand = new AnalyzeDataAvailabilityCommand(
            command.timeRange(),
            command.symbols(),
            command.dataTypes()
        );

        DataAvailability availability = analyzeDataAvailabilityUseCase.execute(analysisCommand);

        // Check if backfill is needed by identifying gaps
        List<MarketDataGap> gaps = availability.identifyGaps();

        // If forceBackfill is false and no gaps exist, return early
        if (!command.forceBackfill() && gaps.isEmpty()) {
            return RequestBackfillResult.noBackfillNeeded();
        }

        // If forceBackfill is true but no gaps exist, create gaps for entire range
        if (command.forceBackfill() && gaps.isEmpty()) {
            gaps = createForcedGaps(command);
        }

        // Create and save backfill request with identified gaps
        BackfillJob backfillRequest = command.linkedSimulationId() != null
            ? new BackfillJob(gaps, command.linkedSimulationId(), command.granularity())
            : new BackfillJob(gaps);
        backfillJobRepository.save(backfillRequest);

        // Fire event to notify WebSocket clients immediately
        jobCreatedEvent.fire(new BackfillJobCreatedEvent(
            backfillRequest.getId(),
            gaps.size()
        ));

        return RequestBackfillResult.backfillStarted(
            backfillRequest.getId(),
            java.time.Instant.now()
        );
    }

    /**
     * Create gaps for forced backfill when data is already complete.
     * Forces re-fetching of all data for the requested symbols/types/range.
     */
    private List<MarketDataGap> createForcedGaps(RequestBackfillCommand command) {
        return command.symbols().stream()
            .flatMap(symbol -> command.dataTypes().stream()
                .map(type -> new MarketDataGap(symbol, type, command.timeRange())))
            .toList();
    }
}
