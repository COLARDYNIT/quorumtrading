package com.quorumtrading.adapter.broker.alpaca;

import com.quorumtrading.application.event.ResearchExecutionCompleted;
import com.quorumtrading.application.orchestration.agents.LLMQueryResult;
import com.quorumtrading.application.port.out.BackfillJobRepository;
import com.quorumtrading.application.port.out.EventPublisher;
import com.quorumtrading.application.port.out.LLMPort;
import com.quorumtrading.application.port.out.LLMUsageRepository;
import com.quorumtrading.model.valueobject.LLMUsage;
import com.quorumtrading.application.port.out.StrategyRepository;
import com.quorumtrading.application.port.out.UserRepository;
import com.quorumtrading.application.port.out.UserBudgetRepository;
import com.quorumtrading.application.port.out.MarketEventRepository;
import com.quorumtrading.application.port.out.HistoricalPricePort;
import com.quorumtrading.application.service.IndicatorEnrichmentService;
import com.quorumtrading.application.usecase.CreateSimulationUseCase;
import com.quorumtrading.application.usecase.GetSimulationChartDataUseCase;
import com.quorumtrading.application.usecase.StartSimulationUseCase;
import io.quarkus.test.Mock;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

import static org.mockito.Mockito.mock;

/**
 * Produces mock beans for Alpaca broker adapter tests.
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
