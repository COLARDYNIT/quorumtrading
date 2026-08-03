package com.quorumtrading.application.service;

import com.quorumtrading.application.exception.AccessDeniedException;
import com.quorumtrading.application.port.out.ConsensusDecisionRepository;
import com.quorumtrading.application.port.out.ConsensusDecisionRepository.AggregatedLLMCost;
import com.quorumtrading.application.port.out.SimulationRepository;
import com.quorumtrading.application.port.out.StrategyRepository;
import com.quorumtrading.application.usecase.query.CalculateSimulationCostQuery;
import com.quorumtrading.model.entity.Simulation;
import com.quorumtrading.model.entity.Strategy;
import com.quorumtrading.model.enums.SimulationStatus;
import com.quorumtrading.model.valueobject.SimulationCost;
import com.quorumtrading.model.valueobject.SimulationId;
import com.quorumtrading.model.valueobject.StrategyId;
import com.quorumtrading.model.valueobject.UserId;
import com.quorumtrading.model.service.PlatformMarkup;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CalculateSimulationCostServiceTest {

    @Mock private ConsensusDecisionRepository consensusRepo;
    @Mock private SimulationRepository simulationRepository;
    @Mock private StrategyRepository strategyRepository;

    private CalculateSimulationCostService service;

    private static final UserId OWNER = new UserId(UUID.randomUUID());
    private static final UserId OTHER = new UserId(UUID.randomUUID());
    private static final SimulationId SIM_ID = SimulationId.generate();
    private static final StrategyId STRATEGY_ID = StrategyId.generate();

    @AfterEach
    void resetMarkup() {
        PlatformMarkup.reset();
    }

    @BeforeEach
    void setUp() {
        // Tests in this class assert billed figures at the historical 1.50 hosted rate
        PlatformMarkup.configure(new java.math.BigDecimal("1.50"));
        AuthorizationService authorizationService = new AuthorizationService();
        service = new CalculateSimulationCostService(
            consensusRepo, simulationRepository, strategyRepository, authorizationService);
    }

    @Nested
    @DisplayName("Authorization")
    class AuthorizationTests {

        @Test
        void rejectsQueryByNonOwner() {
            Simulation simulation = mock(Simulation.class);
            when(simulation.getSimulationId()).thenReturn(SIM_ID);
            when(simulation.getStrategyId()).thenReturn(STRATEGY_ID);
            when(simulationRepository.findById(SIM_ID)).thenReturn(Optional.of(simulation));

            Strategy strategy = mock(Strategy.class);
            when(strategy.getOwnerId()).thenReturn(OWNER);
            when(strategyRepository.findById(STRATEGY_ID)).thenReturn(Optional.of(strategy));

            CalculateSimulationCostQuery query = new CalculateSimulationCostQuery(SIM_ID.value(), OTHER);

            assertThatThrownBy(() -> service.execute(query))
                .isInstanceOf(AccessDeniedException.class);

            verify(consensusRepo, never()).sumCostBySimulationId(any());
        }

        @Test
        void allowsQueryByOwner() {
            stubSimulation(SimulationStatus.COMPLETED, 100);
            Strategy strategy = mock(Strategy.class);
            when(strategy.getOwnerId()).thenReturn(OWNER);
            when(strategyRepository.findById(STRATEGY_ID)).thenReturn(Optional.of(strategy));

            when(consensusRepo.sumCostBySimulationId(SIM_ID.value()))
                .thenReturn(new AggregatedLLMCost(100, 50, 150, BigDecimal.ONE, 5));

            SimulationCost result = service.execute(new CalculateSimulationCostQuery(SIM_ID.value(), OWNER));

            assertThat(result).isNotNull();
            verify(consensusRepo).sumCostBySimulationId(SIM_ID.value());
        }

        @Test
        void nullUserIdIsDenied() {
            // Stub only what authorization touches: the check now runs before any cost
            // aggregation, where a null user previously skipped it and returned the costs.
            Simulation simulation = mock(Simulation.class);
            when(simulation.getSimulationId()).thenReturn(SIM_ID);
            when(simulation.getStrategyId()).thenReturn(STRATEGY_ID);
            when(simulationRepository.findById(SIM_ID)).thenReturn(Optional.of(simulation));
            when(strategyRepository.findById(STRATEGY_ID)).thenReturn(Optional.of(mock(Strategy.class)));

            assertThatThrownBy(() -> service.execute(new CalculateSimulationCostQuery(SIM_ID.value(), null)))
                .isInstanceOf(AccessDeniedException.class);

            verify(consensusRepo, never()).sumCostBySimulationId(any());
        }

        @Test
        void throwsWhenSimulationNotFound() {
            UUID unknownId = UUID.randomUUID();
            when(simulationRepository.findById(new SimulationId(unknownId))).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.execute(new CalculateSimulationCostQuery(unknownId, OWNER)))
                .isInstanceOf(NoSuchElementException.class);
        }
    }

    @Nested
    @DisplayName("Cost Calculation")
    class CostCalculationTests {

        @Test
        void appliesMarkupToRawCost() {
            stubSimulation(SimulationStatus.COMPLETED, 100);

            // raw cost $2.00 * 1.50 markup = $3.00 billed
            when(consensusRepo.sumCostBySimulationId(SIM_ID.value()))
                .thenReturn(new AggregatedLLMCost(1000, 500, 1500, new BigDecimal("2.00"), 10));

            SimulationCost result = service.execute(new CalculateSimulationCostQuery(SIM_ID.value(), OWNER));

            assertThat(result.totalCost()).isEqualByComparingTo(new BigDecimal("3.00"));
            assertThat(result.formattedCost()).isEqualTo("$3.00");
            assertThat(result.totalPromptTokens()).isEqualTo(1000);
            assertThat(result.totalCompletionTokens()).isEqualTo(500);
            assertThat(result.totalTokens()).isEqualTo(1500);
            assertThat(result.decisionCount()).isEqualTo(10);
            assertThat(result.partial()).isFalse();
            assertThat(result.progressPercent()).isEqualTo(100);
        }

        @Test
        void returnsPartialCostForRunningSimulation() {
            stubSimulation(SimulationStatus.SIMULATING, 50);

            // raw $1.00 * 1.50 = $1.50 billed at 50% → estimated $3.00
            when(consensusRepo.sumCostBySimulationId(SIM_ID.value()))
                .thenReturn(new AggregatedLLMCost(500, 250, 750, new BigDecimal("1.00"), 5));

            SimulationCost result = service.execute(new CalculateSimulationCostQuery(SIM_ID.value(), OWNER));

            assertThat(result.totalCost()).isEqualByComparingTo(new BigDecimal("1.50"));
            assertThat(result.partial()).isTrue();
            assertThat(result.progressPercent()).isEqualTo(50);
            assertThat(result.estimatedTotalCost()).isEqualByComparingTo(new BigDecimal("3.000000"));
        }

        @Test
        void handlesFailedSimulationAsTerminal() {
            stubSimulation(SimulationStatus.FAILED, 30);

            // raw $0.50 * 1.50 = $0.75 billed, terminal so no extrapolation
            when(consensusRepo.sumCostBySimulationId(SIM_ID.value()))
                .thenReturn(new AggregatedLLMCost(100, 50, 150, new BigDecimal("0.50"), 2));

            SimulationCost result = service.execute(new CalculateSimulationCostQuery(SIM_ID.value(), OWNER));

            assertThat(result.partial()).isFalse();
            assertThat(result.totalCost()).isEqualByComparingTo(new BigDecimal("0.75"));
            assertThat(result.estimatedTotalCost()).isEqualByComparingTo(new BigDecimal("0.75"));
        }

        @Test
        void handlesZeroCost() {
            stubSimulation(SimulationStatus.COMPLETED, 100);

            when(consensusRepo.sumCostBySimulationId(SIM_ID.value()))
                .thenReturn(AggregatedLLMCost.ZERO);

            SimulationCost result = service.execute(new CalculateSimulationCostQuery(SIM_ID.value(), OWNER));

            assertThat(result.totalCost()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(result.decisionCount()).isZero();
            assertThat(result.formattedCost()).isEqualTo("$0.00");
        }

        @Test
        void includesArchivedCostFromDeletedDecisions() {
            // Simulate a restarted simulation with archived costs from previously deleted decisions
            Simulation simulation = stubSimulation(SimulationStatus.COMPLETED, 100);

            // Archived cost: $1.00 raw from 5 deleted decisions (200 prompt, 100 completion, 300 total)
            when(simulation.getArchivedLLMCostUsd()).thenReturn(new BigDecimal("1.00"));
            when(simulation.getArchivedLLMPromptTokens()).thenReturn(200);
            when(simulation.getArchivedLLMCompletionTokens()).thenReturn(100);
            when(simulation.getArchivedLLMTotalTokens()).thenReturn(300);
            when(simulation.getArchivedLLMDecisionCount()).thenReturn(5);

            // Live cost: $2.00 raw from 10 decisions (1000 prompt, 500 completion, 1500 total)
            when(consensusRepo.sumCostBySimulationId(SIM_ID.value()))
                .thenReturn(new AggregatedLLMCost(1000, 500, 1500, new BigDecimal("2.00"), 10));

            SimulationCost result = service.execute(new CalculateSimulationCostQuery(SIM_ID.value(), OWNER));

            // Total raw = $1.00 + $2.00 = $3.00, billed = $3.00 * 1.50 = $4.50
            assertThat(result.totalCost()).isEqualByComparingTo(new BigDecimal("4.50"));
            assertThat(result.totalPromptTokens()).isEqualTo(1200);    // 200 + 1000
            assertThat(result.totalCompletionTokens()).isEqualTo(600); // 100 + 500
            assertThat(result.totalTokens()).isEqualTo(1800);          // 300 + 1500
            assertThat(result.decisionCount()).isEqualTo(15);          // 5 + 10
        }
    }

    private Simulation stubSimulation(SimulationStatus status, int progress) {
        // The strategy is loaded on every call now that the ownership check is unconditional.
        Strategy ownedStrategy = mock(Strategy.class);
        lenient().when(ownedStrategy.getOwnerId()).thenReturn(OWNER);
        lenient().when(strategyRepository.findById(STRATEGY_ID)).thenReturn(Optional.of(ownedStrategy));

        Simulation simulation = mock(Simulation.class);
        lenient().when(simulation.getStrategyId()).thenReturn(STRATEGY_ID);
        when(simulation.getProgress()).thenReturn(progress);
        when(simulation.isTerminal()).thenReturn(
            status == SimulationStatus.COMPLETED || status == SimulationStatus.FAILED);

        // Default archived cost fields to zero (restart-from-decision feature)
        lenient().when(simulation.getArchivedLLMCostUsd()).thenReturn(BigDecimal.ZERO);
        lenient().when(simulation.getArchivedLLMPromptTokens()).thenReturn(0);
        lenient().when(simulation.getArchivedLLMCompletionTokens()).thenReturn(0);
        lenient().when(simulation.getArchivedLLMTotalTokens()).thenReturn(0);
        lenient().when(simulation.getArchivedLLMDecisionCount()).thenReturn(0);

        when(simulationRepository.findById(SIM_ID)).thenReturn(Optional.of(simulation));
        return simulation;
    }
}
