package com.quorumtrading.application.service.consensus;

import com.quorumtrading.application.event.ConsensusSkippedEvent;
import com.quorumtrading.application.event.DataGapDetectedEvent;
import com.quorumtrading.application.port.out.EventPublisher;
import com.quorumtrading.application.port.out.SimulationRepository;
import com.quorumtrading.model.entity.Simulation;
import com.quorumtrading.model.enums.InvestmentType;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.SimulationId;
import com.quorumtrading.model.valueobject.StrategyId;
import com.quorumtrading.model.valueobject.Symbol;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link DataGapCircuitBreaker} — skip accounting, threshold-triggered
 * pause + DataGapDetectedEvent, and success bookkeeping. Extracted move-only from
 * ReachConsensusService; these tests pin the moved behavior.
 */
@ExtendWith(MockitoExtension.class)
class DataGapCircuitBreakerTest {

    private static final int CONSECUTIVE_THRESHOLD = 3;
    // Percentage counts skips over ALL evaluations; a first skip is 100%, so keep
    // this unreachable for the consecutive-threshold tests.
    private static final int PERCENTAGE_THRESHOLD = 101;

    @Mock private SimulationRepository simulationRepository;
    @Mock private EventPublisher<DataGapDetectedEvent> dataGapPublisher;
    @Mock private EventPublisher<ConsensusSkippedEvent> skippedPublisher;

    private DataGapCircuitBreaker circuitBreaker;
    private Simulation simulation;
    private Instant now;

    @BeforeEach
    void setUp() {
        circuitBreaker = new DataGapCircuitBreaker(
            simulationRepository, dataGapPublisher, skippedPublisher,
            CONSECUTIVE_THRESHOLD, PERCENTAGE_THRESHOLD);
        simulation = new Simulation(
            new SimulationId(UUID.randomUUID()),
            "Test Sim",
            new StrategyId(UUID.randomUUID()),
            Instant.parse("2024-01-01T00:00:00Z"),
            Instant.parse("2025-01-01T00:00:00Z"),
            Money.usd(new BigDecimal("100000")));
        now = Instant.parse("2024-06-01T10:00:00Z");
    }

    @Test
    void singleSkipBelowThresholdSavesAndPublishesSkipButDoesNotPause() {
        circuitBreaker.recordDataGapSkip(simulation, now,
            Set.of(new Symbol("AAPL", InvestmentType.STOCK)), "req-1");

        assertThat(simulation.getSkippedEvaluationCount()).isEqualTo(1);
        // Counters are persisted with a narrow update rather than save(): the executor holds one
        // Simulation instance for a whole run, so saving it would write back a stale counter.
        verify(simulationRepository).updateEvaluationCounters(
                eq(simulation.getSimulationId()), anyInt(), anyInt(), anyInt());
        verify(simulationRepository, never()).save(any());
        verify(skippedPublisher).publish(any(ConsensusSkippedEvent.class));
        verify(dataGapPublisher, never()).publish(any());
        verify(simulationRepository, never()).updatePauseState(any(), any(Boolean.class), any(Boolean.class), any());
    }

    @Test
    void consecutiveSkipsAtThresholdPauseAndFireDataGapEvent() {
        Set<Symbol> tickers = Set.of(new Symbol("AAPL", InvestmentType.STOCK));
        for (int i = 0; i < CONSECUTIVE_THRESHOLD; i++) {
            circuitBreaker.recordDataGapSkip(simulation, now, tickers, "req-" + i);
        }

        verify(simulationRepository).updatePauseState(
            eq(simulation.getSimulationId()), any(Boolean.class), eq(true), eq("DATA_GAP_DETECTED"));
        verify(dataGapPublisher).publish(any(DataGapDetectedEvent.class));
    }

    @Test
    void recordSuccessResetsConsecutiveSkipCounterAndSaves() {
        circuitBreaker.recordDataGapSkip(simulation, now,
            Set.of(new Symbol("AAPL", InvestmentType.STOCK)), "req-1");

        circuitBreaker.recordSuccess(simulation);

        // A success resets the consecutive counter, so the next skip is 1-of-N again
        circuitBreaker.recordDataGapSkip(simulation, now,
            Set.of(new Symbol("AAPL", InvestmentType.STOCK)), "req-2");
        verify(dataGapPublisher, never()).publish(any());
        verify(simulationRepository, never()).updatePauseState(any(), any(Boolean.class), any(Boolean.class), any());
    }

    @Test
    void percentageThresholdPausesEvenWithoutConsecutiveSkips() {
        DataGapCircuitBreaker percentageBreaker = new DataGapCircuitBreaker(
            simulationRepository, dataGapPublisher, skippedPublisher, 100, 50);

        // 1 success + 1 skip = 50% skipped >= 50% threshold (consecutive count is only 1)
        percentageBreaker.recordSuccess(simulation);
        percentageBreaker.recordDataGapSkip(simulation, now,
            Set.of(new Symbol("AAPL", InvestmentType.STOCK)), "req-1");

        verify(simulationRepository).updatePauseState(
            eq(simulation.getSimulationId()), any(Boolean.class), eq(true), eq("DATA_GAP_DETECTED"));
        verify(dataGapPublisher).publish(any(DataGapDetectedEvent.class));
    }

    @Test
    void publishSkippedForwardsTheReason() {
        circuitBreaker.publishSkipped(simulation.getSimulationId(), "req-9", now, "not active");

        verify(skippedPublisher).publish(any(ConsensusSkippedEvent.class));
    }

    @Test
    void llmFailureSkipCountsAndPublishesButDoesNotPauseBelowThreshold() {
        circuitBreaker.recordLlmFailureSkip(simulation, now, "req-1", "engine timed out");

        assertThat(simulation.getSkippedEvaluationCount()).isEqualTo(1);
        assertThat(simulation.getTotalEvaluationCount()).isEqualTo(1);
        verify(simulationRepository).updateEvaluationCounters(
                eq(simulation.getSimulationId()), anyInt(), anyInt(), anyInt());
        verify(simulationRepository, never()).save(any());
        verify(skippedPublisher).publish(any(ConsensusSkippedEvent.class));
        verify(simulationRepository, never()).updatePauseState(any(), any(Boolean.class), any(Boolean.class), any());
    }

    @Test
    void llmFailureSkipsAtThresholdPauseWithLlmFailureReasonAndFireNoDataGapEvent() {
        for (int i = 0; i < CONSECUTIVE_THRESHOLD; i++) {
            circuitBreaker.recordLlmFailureSkip(simulation, now, "req-" + i, "engine timed out");
        }

        // The pause reason must name the engine, not a data gap — the operator's fix is
        // entirely different (restart/replace the engine vs re-backfill prices), and the
        // data-gap event would trigger a pointless re-backfill.
        verify(simulationRepository).updatePauseState(
            eq(simulation.getSimulationId()), any(Boolean.class), eq(true),
            eq(com.quorumtrading.model.valueobject.ExternalPauseReason.LLM_FAILURE_DETECTED));
        verify(dataGapPublisher, never()).publish(any());
    }

    @Test
    void successResetsTheLlmFailureStreak() {
        for (int i = 0; i < CONSECUTIVE_THRESHOLD - 1; i++) {
            circuitBreaker.recordLlmFailureSkip(simulation, now, "req-" + i, "engine timed out");
        }

        circuitBreaker.recordSuccess(simulation);

        circuitBreaker.recordLlmFailureSkip(simulation, now, "req-x", "engine timed out");
        verify(simulationRepository, never()).updatePauseState(any(), any(Boolean.class), any(Boolean.class), any());
    }
}
