package com.quorumtrading.adapter.rest;

import com.quorumtrading.application.event.SimulationCreatedEvent;
import com.quorumtrading.application.port.out.*;
import com.quorumtrading.application.usecase.*;
import io.quarkus.test.Mock;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

import static org.mockito.Mockito.mock;

/**
 * CDI producer for mock repository beans.
 * Provides mocks for all repository port interfaces needed during testing.
 */
@Mock
@ApplicationScoped
public class MockRepositoryProducer {

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
    public SimulationContributionRepository simulationContributionRepository() {
        return mock(SimulationContributionRepository.class);
    }

    @Produces
    @ApplicationScoped
    public MarketEventRepository marketEventRepository() {
        return mock(MarketEventRepository.class);
    }

    @Produces
    @ApplicationScoped
    public BenchmarkRepository benchmarkRepository() {
        return mock(BenchmarkRepository.class);
    }

    @Produces
    @ApplicationScoped
    public EventPublisher<SimulationCreatedEvent> simulationCreatedEventPublisher() {
        return mock(EventPublisher.class);
    }

    @Produces
    @ApplicationScoped
    public ResearchExecutionRepository researchExecutionRepository() {
        return mock(ResearchExecutionRepository.class);
    }

    @Produces
    @ApplicationScoped
    public MarketResearchRepository marketResearchRepository() {
        return mock(MarketResearchRepository.class);
    }

    @Produces
    @ApplicationScoped
    public LLMPort llmPort() {
        return mock(LLMPort.class);
    }

    @Produces
    @ApplicationScoped
    public EventPublisher<com.quorumtrading.application.event.ResearchExecutionCompleted> researchEventPublisher() {
        return event -> {}; // No-op
    }

    @Produces
    @ApplicationScoped
    public UserRepository userRepository() {
        return mock(UserRepository.class);
    }

    @Produces
    @ApplicationScoped
    public ScheduledSimulationRepository scheduledSimulationRepository() {
        return mock(ScheduledSimulationRepository.class);
    }

    // Use case mocks for hexagonal architecture compliance

    @Produces
    @ApplicationScoped
    public ResolveCurrentUserUseCase resolveCurrentUserUseCase() {
        return mock(ResolveCurrentUserUseCase.class);
    }

    @Produces
    @ApplicationScoped
    public ListMarketResearchUseCase listMarketResearchUseCase() {
        return mock(ListMarketResearchUseCase.class);
    }

    @Produces
    @ApplicationScoped
    public DeleteMarketResearchUseCase deleteMarketResearchUseCase() {
        return mock(DeleteMarketResearchUseCase.class);
    }

    @Produces
    @ApplicationScoped
    public DeleteResearchExecutionUseCase deleteResearchExecutionUseCase() {
        return mock(DeleteResearchExecutionUseCase.class);
    }

    @Produces
    @ApplicationScoped
    public GetScheduledSimulationByStrategyUseCase getScheduledSimulationByStrategyUseCase() {
        return mock(GetScheduledSimulationByStrategyUseCase.class);
    }

    @Produces
    @ApplicationScoped
    public LLMUsageRepository llmUsageRepository() {
        return mock(LLMUsageRepository.class);
    }

    @Produces
    @ApplicationScoped
    public GetLLMUsageStatisticsUseCase getLLMUsageStatisticsUseCase() {
        return mock(GetLLMUsageStatisticsUseCase.class);
    }

    @Produces
    @ApplicationScoped
    public ChatSessionPort chatSessionPort() {
        return mock(ChatSessionPort.class);
    }
}
