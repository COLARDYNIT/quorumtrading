package com.quorumtrading.application.service;

import com.quorumtrading.application.exception.AccessDeniedException;
import com.quorumtrading.application.port.out.ConsensusDecisionRepository;
import com.quorumtrading.application.port.out.ConsensusDecisionRepository.AggregatedLLMCost;
import com.quorumtrading.application.port.out.LLMDecisionRepository;
import com.quorumtrading.application.port.out.PortfolioRepository;
import com.quorumtrading.application.port.out.SimulationRepository;
import com.quorumtrading.application.port.out.StrategyRepository;
import com.quorumtrading.model.entity.ConsensusDecision;
import com.quorumtrading.model.entity.PortfolioSnapshot;
import com.quorumtrading.model.entity.Simulation;
import com.quorumtrading.model.entity.Strategy;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.RestartFromPreview;
import com.quorumtrading.model.valueobject.SimulationId;
import com.quorumtrading.model.valueobject.StrategyId;
import com.quorumtrading.model.valueobject.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RestartSimulationFromServiceTest {

    @Mock private SimulationRepository simulationRepository;
    @Mock private StrategyRepository strategyRepository;
    @Mock private ConsensusDecisionRepository consensusDecisionRepository;
    @Mock private LLMDecisionRepository llmDecisionRepository;
    @Mock private PortfolioRepository portfolioRepository;

    private RestartSimulationFromService service;

    private static final UserId OWNER = new UserId(UUID.randomUUID());
    private static final UserId OTHER = new UserId(UUID.randomUUID());
    private static final SimulationId SIM_ID = SimulationId.generate();
    private static final StrategyId STRATEGY_ID = StrategyId.generate();
    private static final long DECISION_ID = 42L;

    // Backtest: start/end in the past
    private static final Instant SIM_START = Instant.parse("2024-01-01T00:00:00Z");
    private static final Instant SIM_END = Instant.parse("2024-12-31T23:59:59Z");
    private static final Instant CUTOFF = Instant.parse("2024-06-01T00:00:00Z");

    @BeforeEach
    void setUp() {
        AuthorizationService authorizationService = new AuthorizationService();
        service = new RestartSimulationFromService(
            simulationRepository, strategyRepository, consensusDecisionRepository,
            llmDecisionRepository, portfolioRepository, authorizationService);
    }

    @Nested
    @DisplayName("Validation")
    class ValidationTests {

        @Test
        void throwsWhenSimulationNotFound() {
            UUID unknownId = UUID.randomUUID();
            when(simulationRepository.findById(new SimulationId(unknownId))).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.preview(unknownId, DECISION_ID, OWNER))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("Simulation not found");
        }

        @Test
        void throwsWhenNotBacktest() {
            // isBacktest() returns false when endDate is in the future
            Simulation simulation = stubSimulation(SIM_ID, STRATEGY_ID,
                Instant.now().minus(1, ChronoUnit.DAYS),
                Instant.now().plus(365, ChronoUnit.DAYS));
            Strategy strategy = mock(Strategy.class);
            lenient().when(strategy.getOwnerId()).thenReturn(OWNER);
            when(strategyRepository.findById(STRATEGY_ID)).thenReturn(Optional.of(strategy));

            assertThatThrownBy(() -> service.preview(SIM_ID.value(), DECISION_ID, OWNER))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("backtest");
        }

        @Test
        void throwsWhenDecisionNotFound() {
            stubBacktestSimulation();
            when(consensusDecisionRepository.findById(DECISION_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.preview(SIM_ID.value(), DECISION_ID, OWNER))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("Consensus decision not found");
        }

        @Test
        void throwsWhenDecisionBelongsToDifferentSimulation() {
            stubBacktestSimulation();

            ConsensusDecision decision = mock(ConsensusDecision.class);
            SimulationId otherId = SimulationId.generate();
            when(decision.getSimulationId()).thenReturn(otherId);
            when(consensusDecisionRepository.findById(DECISION_ID)).thenReturn(Optional.of(decision));

            assertThatThrownBy(() -> service.preview(SIM_ID.value(), DECISION_ID, OWNER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not belong to simulation");
        }

        @Test
        void throwsWhenUserNotAuthorized() {
            stubBacktestSimulation();

            Strategy strategy = mock(Strategy.class);
            when(strategy.getOwnerId()).thenReturn(OWNER);
            when(strategyRepository.findById(STRATEGY_ID)).thenReturn(Optional.of(strategy));

            assertThatThrownBy(() -> service.preview(SIM_ID.value(), DECISION_ID, OTHER))
                .isInstanceOf(AccessDeniedException.class);
        }

        @Test
        void deniesNullUserId() {
            stubBacktestSimulation();

            // A null user used to skip the ownership check outright, so an unresolved caller
            // could restart another user's simulation — which deletes decisions and snapshots.
            assertThatThrownBy(() -> service.preview(SIM_ID.value(), DECISION_ID, null))
                .isInstanceOf(AccessDeniedException.class);
        }
    }

    @Nested
    @DisplayName("Preview")
    class PreviewTests {

        @Test
        void returnsCorrectPreviewData() {
            stubBacktestSimulation();
            stubDecision();
            stubCounts(10, 40, 20);
            stubCostToArchive(new BigDecimal("3.25"), 500, 300, 800, 10);

            PortfolioSnapshot snapshot = mock(PortfolioSnapshot.class);
            when(snapshot.getCashAvailable()).thenReturn(new Money(new BigDecimal("5000.00"), Money.ZERO.currency()));
            when(snapshot.getTotalValue()).thenReturn(new Money(new BigDecimal("12500.00"), Money.ZERO.currency()));
            when(portfolioRepository.findBySimulationId(eq(SIM_ID), isNull(), any(Instant.class), eq(1)))
                .thenReturn(List.of(snapshot));

            RestartFromPreview preview = service.preview(SIM_ID.value(), DECISION_ID, OWNER);

            assertThat(preview.consensusDecisionsToDelete()).isEqualTo(10);
            assertThat(preview.llmDecisionsToDelete()).isEqualTo(40);
            assertThat(preview.portfolioSnapshotsToDelete()).isEqualTo(20);
            assertThat(preview.archivedCostUsd()).isEqualByComparingTo(new BigDecimal("3.25"));
            assertThat(preview.archivedTokens()).isEqualTo(800);
            assertThat(preview.portfolioCashAtRestart()).isEqualByComparingTo(new BigDecimal("5000.00"));
            assertThat(preview.portfolioValueAtRestart()).isEqualByComparingTo(new BigDecimal("12500.00"));
            assertThat(preview.restartTimestamp()).isEqualTo(CUTOFF);
        }

        @Test
        void handlesNoPortfolioSnapshotsBeforeCutoff() {
            stubBacktestSimulation();
            stubDecision();
            stubCounts(3, 12, 5);
            stubCostToArchive(BigDecimal.ZERO, 0, 0, 0, 0);
            stubPortfolioBeforeCutoff(null, null); // No snapshots

            RestartFromPreview preview = service.preview(SIM_ID.value(), DECISION_ID, OWNER);

            assertThat(preview.portfolioCashAtRestart()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(preview.portfolioValueAtRestart()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        void previewDoesNotModifyData() {
            stubBacktestSimulation();
            stubDecision();
            stubCounts(5, 20, 10);
            stubCostToArchive(new BigDecimal("1.00"), 100, 50, 150, 5);
            stubPortfolioBeforeCutoff(BigDecimal.ZERO, BigDecimal.ZERO);

            service.preview(SIM_ID.value(), DECISION_ID, OWNER);

            // Verify no modifications were made
            verify(consensusDecisionRepository, never()).deleteBySimulationIdAfter(any(), any());
            verify(llmDecisionRepository, never()).deleteBySimulationIdAfter(any(), any());
            verify(portfolioRepository, never()).deleteBySimulationIdAfter(any(), any());
            verify(simulationRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Execute")
    class ExecuteTests {

        @Test
        void archivesCostsAndDeletesData() {
            Simulation simulation = stubBacktestSimulation();
            stubDecision();
            stubCounts(8, 32, 15);
            stubCostToArchive(new BigDecimal("2.50"), 400, 200, 600, 8);
            stubPortfolioBeforeCutoff(new BigDecimal("7000"), new BigDecimal("15000"));
            stubDeletions(32, 8, 15);

            RestartFromPreview result = service.execute(SIM_ID.value(), DECISION_ID, OWNER);

            // Verify costs were archived on the simulation
            verify(simulation).archiveLLMCost(
                new BigDecimal("2.50"), 400, 200, 600, 8);

            // Verify deletions happened in correct order (LLM first, consensus, then snapshots)
            var inOrder = inOrder(llmDecisionRepository, consensusDecisionRepository, portfolioRepository);
            inOrder.verify(llmDecisionRepository).deleteBySimulationIdAfter(SIM_ID.value(), CUTOFF);
            inOrder.verify(consensusDecisionRepository).deleteBySimulationIdAfter(SIM_ID.value(), CUTOFF);
            inOrder.verify(portfolioRepository).deleteBySimulationIdAfter(SIM_ID, CUTOFF);

            // Verify simulation state was updated
            verify(simulation).restartFrom(anyInt());
            verify(simulation).unlock();
            verify(simulationRepository).save(simulation);
            verify(simulationRepository).updatePauseState(eq(SIM_ID), eq(true), anyBoolean(), any());

            // Verify return data
            assertThat(result.consensusDecisionsToDelete()).isEqualTo(8);
            assertThat(result.llmDecisionsToDelete()).isEqualTo(32);
            assertThat(result.portfolioSnapshotsToDelete()).isEqualTo(15);
            assertThat(result.archivedCostUsd()).isEqualByComparingTo(new BigDecimal("2.50"));
        }

        /**
         * A restart leaves the simulation paused so the user can inspect it and resume deliberately.
         *
         * <p>save() deliberately does not write the pause columns, so restartFrom()'s in-memory
         * paused=true used to die there: SimulationPickupScheduler found SIMULATING && !paused on
         * its next tick and re-ran the simulation unasked, re-spending LLM budget. The pause must
         * go through updatePauseState(), and it must land before the status flips to SIMULATING —
         * otherwise the scheduler has a window in which the row looks runnable.</p>
         */
        @Test
        void pausesThroughUpdatePauseStateBeforeMakingTheSimulationRunnable() {
            Simulation simulation = stubBacktestSimulation();
            stubDecision();
            stubCounts(1, 4, 2);
            stubCostToArchive(BigDecimal.ZERO, 0, 0, 0, 0);
            stubPortfolioBeforeCutoff(BigDecimal.ZERO, BigDecimal.ZERO);
            stubDeletions(4, 1, 2);

            service.execute(SIM_ID.value(), DECISION_ID, OWNER);

            var inOrder = inOrder(simulationRepository);
            inOrder.verify(simulationRepository)
                .updatePauseState(eq(SIM_ID), eq(true), anyBoolean(), any());
            inOrder.verify(simulationRepository).save(simulation);
        }

        @Test
        void calculatesProgressCorrectly() {
            // Cutoff is exactly halfway through a 1-year sim
            Simulation simulation = stubBacktestSimulation();
            stubDecision();
            stubCounts(1, 4, 2);
            stubCostToArchive(BigDecimal.ZERO, 0, 0, 0, 0);
            stubPortfolioBeforeCutoff(BigDecimal.ZERO, BigDecimal.ZERO);
            stubDeletions(4, 1, 2);

            service.execute(SIM_ID.value(), DECISION_ID, OWNER);

            // CUTOFF (2024-06-01) is ~41% through the year (152/366 days in leap year 2024)
            // The exact value depends on millis between start/cutoff vs start/end
            verify(simulation).restartFrom(intThat(progress ->
                progress >= 40 && progress <= 42));
        }

        @Test
        void returnsPortfolioStateAtRestart() {
            stubBacktestSimulation();
            stubDecision();
            stubCounts(5, 20, 10);
            stubCostToArchive(new BigDecimal("1.00"), 100, 50, 150, 5);
            stubDeletions(20, 5, 10);

            PortfolioSnapshot snapshot = mock(PortfolioSnapshot.class);
            when(snapshot.getCashAvailable()).thenReturn(new Money(new BigDecimal("8500.00"), Money.ZERO.currency()));
            when(snapshot.getTotalValue()).thenReturn(new Money(new BigDecimal("20000.00"), Money.ZERO.currency()));
            when(portfolioRepository.findBySimulationId(eq(SIM_ID), isNull(), any(Instant.class), eq(1)))
                .thenReturn(List.of(snapshot));

            RestartFromPreview result = service.execute(SIM_ID.value(), DECISION_ID, OWNER);

            assertThat(result.portfolioCashAtRestart()).isEqualByComparingTo(new BigDecimal("8500.00"));
            assertThat(result.portfolioValueAtRestart()).isEqualByComparingTo(new BigDecimal("20000.00"));
        }
    }

    // ===== Helpers =====

    private Simulation stubBacktestSimulation() {
        // The strategy is loaded on every call now that the ownership check is unconditional,
        // so every test that stubs a simulation must stub its strategy too.
        Strategy strategy = mock(Strategy.class);
        lenient().when(strategy.getOwnerId()).thenReturn(OWNER);
        lenient().when(strategyRepository.findById(STRATEGY_ID)).thenReturn(Optional.of(strategy));
        return stubSimulation(SIM_ID, STRATEGY_ID, SIM_START, SIM_END);
    }

    private Simulation stubSimulation(SimulationId simId, StrategyId strategyId, Instant start, Instant end) {
        Simulation simulation = mock(Simulation.class);
        lenient().when(simulation.getSimulationId()).thenReturn(simId);
        lenient().when(simulation.getStrategyId()).thenReturn(strategyId);
        lenient().when(simulation.getStartDate()).thenReturn(start);
        lenient().when(simulation.getEndDate()).thenReturn(end);

        // isBacktest() is a real method that checks endDate <= now
        // We use the actual logic via a stub: end in the past = backtest
        boolean isBacktest = end != null && !end.isAfter(Instant.now());
        lenient().when(simulation.isBacktest()).thenReturn(isBacktest);

        when(simulationRepository.findById(simId)).thenReturn(Optional.of(simulation));
        return simulation;
    }

    private void stubDecision() {
        ConsensusDecision decision = mock(ConsensusDecision.class);
        when(decision.getSimulationId()).thenReturn(SIM_ID);
        when(decision.getDecisionTimestamp()).thenReturn(CUTOFF);
        when(consensusDecisionRepository.findById(DECISION_ID)).thenReturn(Optional.of(decision));
    }

    private void stubCounts(long consensus, long llm, long snapshots) {
        when(consensusDecisionRepository.countBySimulationIdAfter(SIM_ID.value(), CUTOFF)).thenReturn(consensus);
        when(llmDecisionRepository.countBySimulationIdAfter(SIM_ID.value(), CUTOFF)).thenReturn(llm);
        when(portfolioRepository.countBySimulationIdAfter(SIM_ID, CUTOFF)).thenReturn(snapshots);
    }

    private void stubCostToArchive(BigDecimal rawCost, int prompt, int completion, int total, int count) {
        when(consensusDecisionRepository.sumCostBySimulationIdAfter(SIM_ID.value(), CUTOFF))
            .thenReturn(new AggregatedLLMCost(prompt, completion, total, rawCost, count));
    }

    private void stubPortfolioBeforeCutoff(BigDecimal cash, BigDecimal totalValue) {
        if (cash == null) {
            when(portfolioRepository.findBySimulationId(eq(SIM_ID), isNull(), any(Instant.class), eq(1)))
                .thenReturn(List.of());
        } else {
            PortfolioSnapshot snapshot = mock(PortfolioSnapshot.class);
            when(snapshot.getCashAvailable()).thenReturn(new Money(cash, Money.ZERO.currency()));
            when(snapshot.getTotalValue()).thenReturn(new Money(totalValue, Money.ZERO.currency()));
            when(portfolioRepository.findBySimulationId(eq(SIM_ID), isNull(), any(Instant.class), eq(1)))
                .thenReturn(List.of(snapshot));
        }
    }

    private void stubDeletions(long llm, long consensus, long snapshots) {
        when(llmDecisionRepository.deleteBySimulationIdAfter(SIM_ID.value(), CUTOFF)).thenReturn(llm);
        when(consensusDecisionRepository.deleteBySimulationIdAfter(SIM_ID.value(), CUTOFF)).thenReturn(consensus);
        when(portfolioRepository.deleteBySimulationIdAfter(SIM_ID, CUTOFF)).thenReturn(snapshots);
    }
}
