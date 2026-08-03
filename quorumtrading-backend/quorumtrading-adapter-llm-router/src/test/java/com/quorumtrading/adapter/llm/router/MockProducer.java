package com.quorumtrading.adapter.llm.router;

import com.quorumtrading.application.event.ResearchExecutionCompleted;
import com.quorumtrading.application.usecase.CreateSimulationUseCase;
import com.quorumtrading.application.usecase.GetSimulationChartDataUseCase;
import com.quorumtrading.application.usecase.query.GetSimulationChartDataQuery;
import com.quorumtrading.application.usecase.command.CreateSimulationCommand;
import com.quorumtrading.application.usecase.GetSimulationChartDataUseCase;
import com.quorumtrading.application.usecase.query.GetSimulationChartDataQuery;
import com.quorumtrading.model.valueobject.SimulationId;
import com.quorumtrading.application.event.SimulationCreatedEvent;
import com.quorumtrading.application.usecase.CreateSimulationUseCase;
import com.quorumtrading.application.usecase.GetSimulationChartDataUseCase;
import com.quorumtrading.application.usecase.query.GetSimulationChartDataQuery;
import com.quorumtrading.application.usecase.command.CreateSimulationCommand;
import com.quorumtrading.application.usecase.GetSimulationChartDataUseCase;
import com.quorumtrading.application.usecase.query.GetSimulationChartDataQuery;
import com.quorumtrading.model.valueobject.SimulationId;
import com.quorumtrading.application.orchestration.agents.LLMConfig;
import com.quorumtrading.application.orchestration.agents.LLMQueryResult;
import com.quorumtrading.application.usecase.CreateSimulationUseCase;
import com.quorumtrading.model.valueobject.LLMUsage;
import com.quorumtrading.application.usecase.GetSimulationChartDataUseCase;
import com.quorumtrading.application.usecase.query.GetSimulationChartDataQuery;
import com.quorumtrading.application.usecase.command.CreateSimulationCommand;
import com.quorumtrading.application.usecase.GetSimulationChartDataUseCase;
import com.quorumtrading.application.usecase.query.GetSimulationChartDataQuery;
import com.quorumtrading.model.valueobject.SimulationId;
import com.quorumtrading.application.orchestration.agents.LLMQueryException;
import com.quorumtrading.application.usecase.CreateSimulationUseCase;
import com.quorumtrading.application.usecase.GetSimulationChartDataUseCase;
import com.quorumtrading.application.usecase.query.GetSimulationChartDataQuery;
import com.quorumtrading.application.usecase.command.CreateSimulationCommand;
import com.quorumtrading.application.usecase.GetSimulationChartDataUseCase;
import com.quorumtrading.application.usecase.query.GetSimulationChartDataQuery;
import com.quorumtrading.model.valueobject.SimulationId;
import com.quorumtrading.application.port.out.*;
import com.quorumtrading.application.service.IndicatorEnrichmentService;
import com.quorumtrading.application.usecase.CreateSimulationUseCase;
import com.quorumtrading.application.usecase.GetSimulationChartDataUseCase;
import com.quorumtrading.application.usecase.query.GetSimulationChartDataQuery;
import com.quorumtrading.application.usecase.command.CreateSimulationCommand;
import com.quorumtrading.application.usecase.GetSimulationChartDataUseCase;
import com.quorumtrading.application.usecase.query.GetSimulationChartDataQuery;
import com.quorumtrading.model.valueobject.SimulationId;
import com.quorumtrading.application.usecase.StartSimulationUseCase;
import com.quorumtrading.application.usecase.GetSimulationChartDataUseCase;
import com.quorumtrading.application.usecase.query.GetSimulationChartDataQuery;
import com.quorumtrading.application.usecase.CreateSimulationUseCase;
import com.quorumtrading.application.usecase.GetSimulationChartDataUseCase;
import com.quorumtrading.application.usecase.query.GetSimulationChartDataQuery;
import com.quorumtrading.application.usecase.command.CreateSimulationCommand;
import com.quorumtrading.application.usecase.GetSimulationChartDataUseCase;
import com.quorumtrading.application.usecase.query.GetSimulationChartDataQuery;
import com.quorumtrading.model.valueobject.SimulationId;
import com.quorumtrading.application.usecase.command.StartSimulationCommand;
import com.quorumtrading.application.usecase.GetSimulationChartDataUseCase;
import com.quorumtrading.application.usecase.query.GetSimulationChartDataQuery;
import com.quorumtrading.application.usecase.CreateSimulationUseCase;
import com.quorumtrading.application.usecase.GetSimulationChartDataUseCase;
import com.quorumtrading.application.usecase.query.GetSimulationChartDataQuery;
import com.quorumtrading.application.usecase.command.CreateSimulationCommand;
import com.quorumtrading.application.usecase.GetSimulationChartDataUseCase;
import com.quorumtrading.application.usecase.query.GetSimulationChartDataQuery;
import com.quorumtrading.model.valueobject.SimulationId;
import com.quorumtrading.model.entity.Simulation;
import com.quorumtrading.model.entity.Benchmark;
import com.quorumtrading.model.entity.Strategy;
import com.quorumtrading.model.enums.SimulationStatus;
import com.quorumtrading.model.valueobject.BenchmarkId;
import com.quorumtrading.model.valueobject.BrokerAccountId;
import com.quorumtrading.model.valueobject.StrategyId;
import io.quarkus.test.Mock;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

import static org.mockito.Mockito.mock;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Provides mock LLM providers for testing the router.
 * Creates two mock providers: mock-a (always succeeds), mock-b (always fails).
 */
@Mock
public class MockProducer {

    @Produces
    @ApplicationScoped
    @TestProvider("mock-a")
    public LLMPort mockProviderA() {
        return new MockLLMPort("mock-a", true);
    }

    @Produces
    @ApplicationScoped
    @TestProvider("mock-b")
    public LLMPort mockProviderB() {
        return new MockLLMPort("mock-b", false);
    }

    /**
     * Mock LLM provider implementation for testing.
     */
    private static class MockLLMPort implements LLMPort {

        private final String name;
        private final boolean shouldSucceed;

        MockLLMPort(String name, boolean shouldSucceed) {
            this.name = name;
            this.shouldSucceed = shouldSucceed;
        }

        @Override
        public LLMQueryResult queryWithUsage(String prompt, LLMConfig config) {
            if (shouldSucceed) {
                return LLMQueryResult.of("Mock response from " + name, LLMUsage.ZERO, name);
            } else {
                throw new LLMQueryException("Mock failure from " + name);
            }
        }

        @Override
        public String getProviderName() {
            return name;
        }

        @Override
        public boolean isAvailable() {
            return true;
        }
    }

    @Produces
    @ApplicationScoped
    public BenchmarkRepository benchmarkRepository() {
        return new BenchmarkRepository() {
            @Override
            public Benchmark save(Benchmark benchmark) {
                return benchmark;
            }

            @Override
            public Optional<Benchmark> findById(BenchmarkId id) {
                return Optional.empty();
            }

            @Override
            public List<Benchmark> findAll() {
                return Collections.emptyList();
            }

            @Override
            public boolean deleteById(BenchmarkId id) {
                return false;
            }

            @Override
            public List<Benchmark> findAllSorted(int limit) {
                return Collections.emptyList();
            }

            @Override
            public List<Benchmark> findByOwnerId(com.quorumtrading.model.valueobject.UserId ownerId, int limit) {
                return List.of();
            }
        };
    }

    @Produces
    @ApplicationScoped
    public StartSimulationUseCase startSimulationUseCase() {
        return new StartSimulationUseCase() {
            @Override
            public void execute(StartSimulationCommand command) {
                // No-op for adapter tests
            }
        };
    }

    @Produces
    @ApplicationScoped
    public EventPublisher<SimulationCreatedEvent> simulationCreatedEventPublisher() {
        return new EventPublisher<SimulationCreatedEvent>() {
            @Override
            public void publish(SimulationCreatedEvent event) {
                // No-op for adapter tests
            }
        };
    }

    @Produces
    @ApplicationScoped
    public StrategyRepository strategyRepository() {
        return new StrategyRepository() {
            @Override
            public Strategy save(Strategy strategy) { return strategy; }

            @Override
            public Optional<Strategy> findById(StrategyId id) { return Optional.empty(); }

            @Override
            public List<Strategy> findByIds(java.util.Collection<StrategyId> ids) { return Collections.emptyList(); }

            @Override
            public List<Strategy> findAll() { return Collections.emptyList(); }

            @Override
            public boolean deleteById(StrategyId id) { return false; }

            @Override
            public int deleteAll() { return 0; }

            @Override
            public boolean existsById(StrategyId id) { return false; }

            @Override
            public List<Strategy> findByOwnerId(com.quorumtrading.model.valueobject.UserId ownerId) { return Collections.emptyList(); }

            @Override
            public long count() { return 0L; }

            @Override
            public List<Strategy> findAllPublic() { return Collections.emptyList(); }
        };
    }

    @Produces
    @ApplicationScoped
    public MarketResearchRepository marketResearchRepository() {
        return new MarketResearchRepository() {
            @Override
            public com.quorumtrading.model.entity.MarketResearch save(com.quorumtrading.model.entity.MarketResearch research) { return research; }

            @Override
            public Optional<com.quorumtrading.model.entity.MarketResearch> findById(UUID id) { return Optional.empty(); }

            @Override
            public List<com.quorumtrading.model.entity.MarketResearch> findAll() { return Collections.emptyList(); }

            @Override
            public List<com.quorumtrading.model.entity.MarketResearch> findByStatus(com.quorumtrading.model.valueobject.ResearchStatus status) { return Collections.emptyList(); }

            @Override
            public List<com.quorumtrading.model.entity.MarketResearch> findDueForExecution(java.time.Instant now) { return Collections.emptyList(); }

            @Override
            public void delete(UUID id) {}

            @Override
            public boolean existsById(UUID id) { return false; }

            @Override
            public List<com.quorumtrading.model.entity.MarketResearch> findPausedForExternalReasonByOwnerId(com.quorumtrading.model.valueobject.UserId ownerId) { return Collections.emptyList(); }

            @Override
            public List<com.quorumtrading.model.entity.MarketResearch> findActiveByOwnerId(com.quorumtrading.model.valueobject.UserId ownerId) { return Collections.emptyList(); }

            @Override
            public List<com.quorumtrading.model.entity.MarketResearch> findByOwnerId(com.quorumtrading.model.valueobject.UserId ownerId) { return Collections.emptyList(); }

            @Override
            public long count() { return 0L; }
        };
    }

    @Produces
    @ApplicationScoped
    public ResearchExecutionRepository researchExecutionRepository() {
        return new ResearchExecutionRepository() {
            @Override
            public com.quorumtrading.model.entity.ResearchExecution save(com.quorumtrading.model.entity.ResearchExecution execution) { return execution; }

            @Override
            public Optional<com.quorumtrading.model.entity.ResearchExecution> findById(UUID id) { return Optional.empty(); }

            @Override
            public List<com.quorumtrading.model.entity.ResearchExecution> findByMarketResearchId(UUID marketResearchId) { return Collections.emptyList(); }

            @Override
            public List<com.quorumtrading.model.entity.ResearchExecution> findByOutcome(com.quorumtrading.model.valueobject.ResearchOutcome outcome) { return Collections.emptyList(); }

            @Override
            public Optional<com.quorumtrading.model.entity.ResearchExecution> findLatestByMarketResearchId(UUID marketResearchId) { return Optional.empty(); }

            @Override
            public void delete(UUID id) {}

            @Override
            public int countStrategiesCreatedByResearch(UUID marketResearchId) { return 0; }
        };
    }

    @Produces
    @ApplicationScoped
    public EventPublisher<ResearchExecutionCompleted> researchEventPublisher() {
        return event -> {}; // No-op
    }

    @Produces
    @ApplicationScoped
    public CreateSimulationUseCase mockCreateSimulationUseCase() {
        return new CreateSimulationUseCase() {
            @Override
            public SimulationId execute(CreateSimulationCommand command) {
                // No-op for adapter tests
                return new SimulationId(java.util.UUID.randomUUID());
            }
        };
    }

    @Produces
    @ApplicationScoped
    public GetSimulationChartDataUseCase mockGetSimulationChartDataUseCase() {
        return new GetSimulationChartDataUseCase() {
            @Override
            public java.util.List<com.quorumtrading.model.valueobject.ChartDataPoint> execute(GetSimulationChartDataQuery query) {
                // No-op for adapter tests
                return java.util.Collections.emptyList();
            }
        };
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
            public List<Simulation> findByChallengeId(com.quorumtrading.model.valueobject.ChallengeId challengeId) {
                return Collections.emptyList();
            }

            @Override
            public Optional<Simulation> findByOwnerIdAndChallengeId(com.quorumtrading.model.valueobject.UserId ownerId, com.quorumtrading.model.valueobject.ChallengeId challengeId) {
                return Optional.empty();
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
