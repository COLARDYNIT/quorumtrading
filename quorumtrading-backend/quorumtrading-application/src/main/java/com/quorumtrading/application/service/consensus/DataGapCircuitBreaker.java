package com.quorumtrading.application.service.consensus;

import com.quorumtrading.application.event.ConsensusSkippedEvent;
import com.quorumtrading.application.event.DataGapDetectedEvent;
import com.quorumtrading.application.port.out.EventPublisher;
import com.quorumtrading.application.port.out.SimulationRepository;
import com.quorumtrading.model.entity.Simulation;
import com.quorumtrading.model.valueobject.ExternalPauseReason;
import com.quorumtrading.model.valueobject.SimulationId;
import com.quorumtrading.model.valueobject.Symbol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Set;

/**
 * Tracks skipped consensus evaluations and pauses a simulation once the configured
 * consecutive / percentage thresholds are exceeded. Two skip families share the same
 * counters and thresholds but pause with different reasons, because the operator's fix
 * differs: a data gap ({@link ExternalPauseReason#DATA_GAP_DETECTED}) additionally fires a
 * {@link DataGapDetectedEvent} to trigger re-backfill, while an LLM-engine failure
 * ({@link ExternalPauseReason#LLM_FAILURE_DETECTED}) does not — no amount of price data revives a
 * dead engine. Also owns the {@link ConsensusSkippedEvent} publication for every skip path.
 *
 * <p>Plain POJO wired via {@code ApplicationConfig} (CDI convention: application
 * services are produced, not annotated).</p>
 */
public class DataGapCircuitBreaker {

    private static final Logger logger = LoggerFactory.getLogger(DataGapCircuitBreaker.class);

    private final SimulationRepository simulationRepository;
    private final EventPublisher<DataGapDetectedEvent> dataGapDetectedEventPublisher;
    private final EventPublisher<ConsensusSkippedEvent> consensusSkippedEventPublisher;
    private final int consecutiveSkipThreshold;
    private final int percentageSkipThreshold;

    public DataGapCircuitBreaker(
            SimulationRepository simulationRepository,
            EventPublisher<DataGapDetectedEvent> dataGapDetectedEventPublisher,
            EventPublisher<ConsensusSkippedEvent> consensusSkippedEventPublisher,
            int consecutiveSkipThreshold,
            int percentageSkipThreshold) {
        this.simulationRepository = simulationRepository;
        this.dataGapDetectedEventPublisher = dataGapDetectedEventPublisher;
        this.consensusSkippedEventPublisher = consensusSkippedEventPublisher;
        this.consecutiveSkipThreshold = consecutiveSkipThreshold;
        this.percentageSkipThreshold = percentageSkipThreshold;
        logger.info("DataGapCircuitBreaker initialized: {} consecutive / {}% total",
            consecutiveSkipThreshold, percentageSkipThreshold);
    }

    /** Publish a ConsensusSkippedEvent (used by every consensus skip path). */
    public void publishSkipped(SimulationId simulationId, String consensusRequestId,
                               Instant analysisTimestamp, String reason) {
        consensusSkippedEventPublisher.publish(new ConsensusSkippedEvent(
            simulationId, consensusRequestId, analysisTimestamp, reason));
    }

    /**
     * CIRCUIT BREAKER: Track a no-market-data skip and pause the simulation if the
     * threshold is exceeded; fires DataGapDetectedEvent to trigger re-backfill and
     * publishes the ConsensusSkippedEvent.
     */
    public void recordDataGapSkip(Simulation simulation, Instant analysisTimestamp,
                                  Set<Symbol> allTickerSymbols, String consensusRequestId) {
        SimulationId simulationId = simulation.getSimulationId();

        simulation.incrementSkippedEvaluation();

        if (simulation.shouldPauseForDataGap(consecutiveSkipThreshold, percentageSkipThreshold)) {
            logger.warn("Circuit breaker triggered for simulation {} - {} consecutive skips, {}% total skipped",
                simulationId.value().toString().substring(0, 8),
                simulation.getSkippedEvaluationCount(),
                simulation.getSkipPercentage());

            simulationRepository.updatePauseState(simulationId, simulation.isPaused(), true,
                ExternalPauseReason.DATA_GAP_DETECTED);

            // Fire event to trigger re-backfill with delay
            DataGapDetectedEvent dataGapEvent = new DataGapDetectedEvent(
                simulationId,
                analysisTimestamp,
                simulation.getEndDate(),
                allTickerSymbols,
                simulation.getSkippedEvaluationCount(),
                simulation.getSkipPercentage()
            );
            dataGapDetectedEventPublisher.publish(dataGapEvent);

            logger.info("Fired DataGapDetectedEvent for simulation {} - will trigger re-backfill",
                simulationId.value().toString().substring(0, 8));
        }

        simulationRepository.updateEvaluationCounters(simulation.getSimulationId(),
                simulation.getTotalEvaluationCount(), simulation.getSkippedEvaluationCount(),
                simulation.getTotalSkippedEvaluationCount());
        publishSkipped(simulationId, consensusRequestId, analysisTimestamp,
            "No market data available for tickers at " + analysisTimestamp);
    }

    /**
     * CIRCUIT BREAKER: Track a round in which every evaluated ticker was skipped because the
     * LLM engine failed or timed out. Shares the counters and thresholds with the data-gap
     * path so a stalled engine is as visible as missing data, but pauses with
     * {@link ExternalPauseReason#LLM_FAILURE_DETECTED} and deliberately fires no {@link DataGapDetectedEvent}
     * — re-backfilling cannot fix an engine that is not answering.
     */
    public void recordLlmFailureSkip(Simulation simulation, Instant analysisTimestamp,
                                     String consensusRequestId, String reason) {
        SimulationId simulationId = simulation.getSimulationId();

        simulation.incrementSkippedEvaluation();

        if (simulation.shouldPauseForDataGap(consecutiveSkipThreshold, percentageSkipThreshold)) {
            logger.warn("Circuit breaker triggered for simulation {} - {} consecutive skips, {}% total skipped; pausing with {}",
                simulationId.value().toString().substring(0, 8),
                simulation.getSkippedEvaluationCount(),
                simulation.getSkipPercentage(),
                ExternalPauseReason.LLM_FAILURE_DETECTED);
            simulationRepository.updatePauseState(simulationId, simulation.isPaused(), true,
                ExternalPauseReason.LLM_FAILURE_DETECTED);
        }

        simulationRepository.updateEvaluationCounters(simulation.getSimulationId(),
                simulation.getTotalEvaluationCount(), simulation.getSkippedEvaluationCount(),
                simulation.getTotalSkippedEvaluationCount());
        publishSkipped(simulationId, consensusRequestId, analysisTimestamp, reason);
    }

    /** Record a successful evaluation - resets the consecutive skip counter. */
    public void recordSuccess(Simulation simulation) {
        simulation.recordSuccessfulEvaluation();
        simulationRepository.updateEvaluationCounters(simulation.getSimulationId(),
                simulation.getTotalEvaluationCount(), simulation.getSkippedEvaluationCount(),
                simulation.getTotalSkippedEvaluationCount());
        logger.debug("Recorded successful evaluation for simulation {}, total: {}",
            simulation.getSimulationId().value().toString().substring(0, 8),
            simulation.getTotalEvaluationCount());
    }
}
