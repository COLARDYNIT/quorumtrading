package com.quorumtrading.adapter.marketdata.alpaca;

import com.quorumtrading.application.event.ResearchExecutionCompleted;
import com.quorumtrading.application.orchestration.agents.LLMQueryResult;
import com.quorumtrading.application.port.out.EventPublisher;
import com.quorumtrading.application.port.out.LLMPort;
import com.quorumtrading.application.port.out.LLMUsageRepository;
import com.quorumtrading.model.valueobject.LLMUsage;
import com.quorumtrading.application.port.out.SimulationRepository;
import com.quorumtrading.application.port.out.StrategyRepository;
import com.quorumtrading.application.port.out.BackfillJobRepository;
import com.quorumtrading.application.port.out.MarketEventRepository;
import com.quorumtrading.application.usecase.CreateSimulationUseCase;
import com.quorumtrading.application.usecase.GetSimulationChartDataUseCase;
import com.quorumtrading.application.usecase.StartSimulationUseCase;
import com.quorumtrading.model.entity.Simulation;
import com.quorumtrading.model.enums.SimulationStatus;
import com.quorumtrading.model.valueobject.SimulationId;
import com.quorumtrading.model.valueobject.BenchmarkId;
import com.quorumtrading.model.valueobject.BrokerAccountId;
import com.quorumtrading.model.valueobject.StrategyId;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import com.quorumtrading.application.port.out.UserRepository;
import com.quorumtrading.application.port.out.UserBudgetRepository;
import com.quorumtrading.application.port.out.HistoricalPricePort;
import com.quorumtrading.application.service.IndicatorEnrichmentService;
import io.quarkus.test.Mock;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

import static org.mockito.Mockito.mock;

/**
 * Produces mock beans for Alpaca market data adapter tests.
 * These mocks satisfy CDI dependencies without requiring full application bootstrap.
 */
@Mock
@ApplicationScoped
public class MockProducer {

    @Produces
    @ApplicationScoped
    public LLMPort mockLLMPort() {
        return new LLMPort() {
            @Override
            public LLMQueryResult queryWithUsage(String prompt, com.quorumtrading.application.orchestration.agents.LLMConfig config) {
                return LLMQueryResult.of("Mock LLM response", LLMUsage.ZERO, "mock-model");
            }

            @Override
            public String getProviderName() {
                return "MockProvider";
            }

            @Override
            public boolean isAvailable() {
                return true;
            }
        };
    }

    @Produces
    @ApplicationScoped
    public EventPublisher<ResearchExecutionCompleted> mockResearchEventPublisher() {
        return event -> {
            // No-op event publisher for tests
        };
    }

    @Produces
    @ApplicationScoped
    public CreateSimulationUseCase createSimulationUseCase() {
        return mock(CreateSimulationUseCase.class);
    }

    @Produces
    @ApplicationScoped
    public GetSimulationChartDataUseCase getSimulationChartDataUseCase() {
        return mock(GetSimulationChartDataUseCase.class);
    }

    @Produces
    @ApplicationScoped
    public StartSimulationUseCase startSimulationUseCase() {
        return mock(StartSimulationUseCase.class);
    }

    @Produces
    @ApplicationScoped
    public StrategyRepository strategyRepository() {
        return mock(StrategyRepository.class);
    }

    @Produces
    @ApplicationScoped
    public MarketEventRepository marketEventRepository() {
        return mock(MarketEventRepository.class);
    }

    @jakarta.enterprise.inject.Produces
    @jakarta.enterprise.context.ApplicationScoped
    public com.quorumtrading.application.service.DataHorizonService dataHorizonService() {
        return org.mockito.Mockito.mock(com.quorumtrading.application.service.DataHorizonService.class);
    }

    @Produces
    @ApplicationScoped
    public BackfillJobRepository backfillJobRepository() {
        return mock(BackfillJobRepository.class);
    }

    @Produces
    @ApplicationScoped
    public SimulationRepository simulationRepository() {
        return new SimulationRepository() {
            @Override
            public Optional<Simulation> findById(SimulationId simulationId) {
                return Optional.empty();
            }

            @Override
            public Simulation save(Simulation simulation) {
                return simulation;
            }

            @Override
            public List<Simulation> findByStrategyId(StrategyId strategyId) {
                return Collections.emptyList();
            }

            @Override
            public List<Simulation> findAll() {
                return Collections.emptyList();
            }

            @Override
            public boolean deleteById(SimulationId id) {
                return true;
            }

            @Override
            public List<Simulation> findExpiredSimulations(Instant cutoffTime) {
                return Collections.emptyList();
            }

            @Override
            public List<Simulation> findStalledBacktests(java.time.Duration leaseStaleAfter,
                                                         java.time.Duration stallGrace) {
                return Collections.emptyList();
            }

            @Override
            public List<Simulation> findRunnable(SimulationStatus status) {
                return Collections.emptyList();
            }

            @Override
            public void updateProgress(SimulationId id, int progress) {
                // no-op stub
            }

            @Override
            public boolean updateStatus(SimulationId id, SimulationStatus expected,
                                        SimulationStatus next, Instant completedAt,
                                        String failureReason) {
                return true;
            }

            @Override
            public boolean markStarted(SimulationId id, SimulationStatus next, Instant startedAt) {
                return true;
            }

            @Override
            public List<Simulation> findWithFilters(
                Optional<SimulationStatus> status,
                Optional<StrategyId> strategyId,
                Optional<Instant> since,
                Optional<Integer> limit
            ) {
                return Collections.emptyList();
            }

            @Override
            public List<Simulation> findByBenchmarkId(BenchmarkId benchmarkId) {
                return Collections.emptyList();
            }

            @Override
            public List<Simulation> findPausedSimulations() {
                return Collections.emptyList();
            }

            @Override
            public void updateCreatedAt(SimulationId id, Instant newCreatedAt) {
                // No-op for mock
            }

            @Override
            public List<Simulation> findPausedForExternalReasonByOwnerId(com.quorumtrading.model.valueobject.UserId ownerId) {
                return Collections.emptyList();
            }

            @Override
            public List<Simulation> findRunningByOwnerId(com.quorumtrading.model.valueobject.UserId ownerId) {
                return Collections.emptyList();
            }

            @Override
            public List<Simulation> findByOwnerId(com.quorumtrading.model.valueobject.UserId ownerId) {
                return Collections.emptyList();
            }

            @Override
            public long count() { return 0L; }

            @Override
            public List<Simulation> findActiveByBrokerAccountId(BrokerAccountId brokerAccountId) {
                return List.of();
            }

            @Override
            public java.util.Map<String, Long> countByStatus() {
                return java.util.Collections.emptyMap();
            }

            @Override
            public void updateBenchmarkId(SimulationId id, BenchmarkId benchmarkId) {
                // No-op for mock
            }

            @Override
            public java.util.List<Simulation> findByChallengeId(com.quorumtrading.model.valueobject.ChallengeId challengeId) {
                return java.util.Collections.emptyList();
            }

            @Override
            public java.util.Optional<Simulation> findByOwnerIdAndChallengeId(com.quorumtrading.model.valueobject.UserId ownerId, com.quorumtrading.model.valueobject.ChallengeId challengeId) {
                return java.util.Optional.empty();
            }

            @Override
            public void updateChallengeId(SimulationId id, com.quorumtrading.model.valueobject.ChallengeId challengeId) {
                // No-op for mock
            }

            @Override
            public void updateEvaluationCounters(SimulationId id, int totalEvaluationCount,
                    int skippedEvaluationCount, int totalSkippedEvaluationCount) {
                // no-op
            }

            @Override
            public void updatePauseState(SimulationId id, boolean paused, boolean pausedForExternalReason, String externalPauseReason) {
                // No-op for mock
            }
        };
    }

    @Produces
    @ApplicationScoped
    public UserRepository userRepository() {
        return mock(UserRepository.class);
    }

    @Produces
    @ApplicationScoped
    public LLMUsageRepository llmUsageRepository() {
        return mock(LLMUsageRepository.class);
    }

    @Produces
    @ApplicationScoped
    public UserBudgetRepository userBudgetRepository() {
        return mock(UserBudgetRepository.class);
    }

    @Produces
    @ApplicationScoped
    public HistoricalPricePort historicalPricePort() {
        return mock(HistoricalPricePort.class);
    }

    @Produces
    @ApplicationScoped
    public IndicatorEnrichmentService indicatorEnrichmentService() {
        return mock(IndicatorEnrichmentService.class);
    }
}
