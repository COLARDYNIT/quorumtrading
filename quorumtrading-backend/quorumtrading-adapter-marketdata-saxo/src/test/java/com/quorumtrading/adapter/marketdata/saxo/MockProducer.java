package com.quorumtrading.adapter.marketdata.saxo;

import com.quorumtrading.application.event.SimulationCreatedEvent;
import com.quorumtrading.application.port.out.BackfillJobRepository;
import com.quorumtrading.application.port.out.BenchmarkRepository;
import com.quorumtrading.application.port.out.ConsensusDecisionRepository;
import com.quorumtrading.application.port.out.EventPublisher;
import com.quorumtrading.application.port.out.HistoricalPricePort;
import com.quorumtrading.application.port.out.LLMDecisionRepository;
import com.quorumtrading.application.port.out.LLMUsageRepository;
import com.quorumtrading.application.port.out.MarketEventRepository;
import com.quorumtrading.application.port.out.PortfolioRepository;
import com.quorumtrading.application.port.out.SimulationRepository;
import com.quorumtrading.application.port.out.StrategyRepository;
import com.quorumtrading.application.port.out.UserBudgetRepository;
import com.quorumtrading.application.port.out.UserRepository;
import com.quorumtrading.application.service.DataHorizonService;
import com.quorumtrading.application.service.IndicatorEnrichmentService;
import com.quorumtrading.application.usecase.CreateSimulationUseCase;
import com.quorumtrading.application.usecase.GetSimulationChartDataUseCase;
import com.quorumtrading.application.usecase.StartSimulationUseCase;
import com.quorumtrading.application.usecase.command.StartSimulationCommand;
import io.quarkus.test.Mock;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

import static org.mockito.Mockito.mock;

/**
 * CDI producer for mock beans needed to boot the Saxo market-data adapter under
 * {@code @QuarkusTest} in isolation.
 *
 * <p>The {@code quorumtrading-application} module ships {@code @ApplicationScoped} services
 * that inject out-ports normally supplied by the persistence/market-data adapters. Those
 * adapters are absent from this isolated module, so the WireMock harness ({@code MD1}) can
 * only boot if those ports are mocked here — preserving hexagonal isolation (adapters must
 * not depend on each other). Modelled on the finnhub adapter's MockProducer.</p>
 */
@Mock
@ApplicationScoped
public class MockProducer {

    @Produces
    @ApplicationScoped
    public MarketEventRepository marketEventRepository() {
        return mock(MarketEventRepository.class);
    }

    @Produces
    @ApplicationScoped
    public DataHorizonService dataHorizonService() {
        return mock(DataHorizonService.class);
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
    @SuppressWarnings("unchecked")
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
