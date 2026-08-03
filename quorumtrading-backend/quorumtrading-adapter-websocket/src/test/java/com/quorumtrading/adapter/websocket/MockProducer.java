package com.quorumtrading.adapter.websocket;

import com.quorumtrading.application.orchestration.StrategyParser;
import com.quorumtrading.application.service.IndicatorEnrichmentService;
import com.quorumtrading.application.usecase.CreateSimulationUseCase;
import com.quorumtrading.application.usecase.GetSimulationChartDataUseCase;
import com.quorumtrading.application.port.out.*;
import com.quorumtrading.application.usecase.CreateSimulationUseCase;
import com.quorumtrading.application.usecase.GetSimulationChartDataUseCase;
import com.quorumtrading.application.event.SimulationCreatedEvent;
import com.quorumtrading.application.usecase.CreateSimulationUseCase;
import com.quorumtrading.application.usecase.GetSimulationChartDataUseCase;
import com.quorumtrading.application.usecase.StartSimulationUseCase;
import com.quorumtrading.application.usecase.GetSimulationChartDataUseCase;
import com.quorumtrading.application.usecase.CreateSimulationUseCase;
import com.quorumtrading.application.usecase.GetSimulationChartDataUseCase;
import com.quorumtrading.application.usecase.command.StartSimulationCommand;
import com.quorumtrading.application.usecase.GetSimulationChartDataUseCase;
import com.quorumtrading.application.usecase.CreateSimulationUseCase;
import com.quorumtrading.application.usecase.GetSimulationChartDataUseCase;
import com.quorumtrading.application.usecase.*;
import com.quorumtrading.application.usecase.GetSimulationChartDataUseCase;
import com.quorumtrading.application.usecase.CreateSimulationUseCase;
import com.quorumtrading.application.usecase.GetSimulationChartDataUseCase;
import com.quorumtrading.application.port.out.UserRepository;
import com.quorumtrading.application.port.out.UserBudgetRepository;
import io.quarkus.test.Mock;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

import static org.mockito.Mockito.mock;

/**
 * CDI producer for mock beans needed by WebSocket tests.
 */
@Mock
@ApplicationScoped
public class MockProducer {

    @Produces
    @ApplicationScoped
    public ReachConsensusUseCase reachConsensusUseCase() {
        return mock(ReachConsensusUseCase.class);
    }

    @Produces
    @ApplicationScoped
    public SimulationRepository simulationRepository() {
        return mock(SimulationRepository.class);
    }

    @Produces
    @ApplicationScoped
    public StrategyRepository strategyRepository() {
        return mock(StrategyRepository.class);
    }

    @Produces
    @ApplicationScoped
    public PortfolioRepository portfolioRepository() {
        return mock(PortfolioRepository.class);
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
    public StrategyParser strategyParser() {
        return mock(StrategyParser.class);
    }

    @Produces
    @ApplicationScoped
    public ConsensusDecisionRepository consensusDecisionRepository() {
        return mock(ConsensusDecisionRepository.class);
    }

    @Produces
    @ApplicationScoped
    public LLMDecisionRepository llmDecisionRepository() {
        return mock(LLMDecisionRepository.class);
    }

    @Produces
    @ApplicationScoped
    public BrokerPort brokerPort() {
        return mock(BrokerPort.class);
    }

    @Produces
    @ApplicationScoped
    public BenchmarkRepository benchmarkRepository() {
        return mock(BenchmarkRepository.class);
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
        return mock(EventPublisher.class);
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
    public BackfillJobRepository backfillJobRepository() {
        return mock(BackfillJobRepository.class);
    }

    @Produces
    @ApplicationScoped
    public UserRepository userRepository() {
        return mock(UserRepository.class);
    }

    @Produces
    @ApplicationScoped
    public ResearchExecutionRepository researchExecutionRepository() {
        return mock(ResearchExecutionRepository.class);
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
