package com.quorumtrading.adapter.rest;

import com.quorumtrading.application.chat.CreateChatSessionUseCase;
import com.quorumtrading.application.chat.GetChatSessionUseCase;
import com.quorumtrading.application.chat.SendChatMessageUseCase;
import com.quorumtrading.application.usecase.*;
import com.quorumtrading.application.usecase.apitoken.CreateApiTokenUseCase;
import com.quorumtrading.application.usecase.apitoken.ListApiTokensUseCase;
import com.quorumtrading.application.usecase.apitoken.RevokeApiTokenUseCase;
import io.quarkus.test.Mock;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

import static org.mockito.Mockito.mock;

/**
 * CDI producer for mock use case beans.
 * Provides mocks for all use cases needed by REST resources during testing.
 */
@Mock
@ApplicationScoped
public class MockUseCaseProducer {

    @Produces
    @ApplicationScoped
    public CreateStrategyUseCase createStrategyUseCase() {
        return mock(CreateStrategyUseCase.class);
    }

    @Produces
    @ApplicationScoped
    public ListStrategiesUseCase listStrategiesUseCase() {
        return mock(ListStrategiesUseCase.class);
    }

    @Produces
    @ApplicationScoped
    public GetStrategyUseCase getStrategyUseCase() {
        return mock(GetStrategyUseCase.class);
    }

    @Produces
    @ApplicationScoped
    public UpdateStrategyUseCase updateStrategyUseCase() {
        return mock(UpdateStrategyUseCase.class);
    }

    @Produces
    @ApplicationScoped
    public DeleteStrategyUseCase deleteStrategyUseCase() {
        return mock(DeleteStrategyUseCase.class);
    }

    @Produces
    @ApplicationScoped
    public DeleteAllStrategiesUseCase deleteAllStrategiesUseCase() {
        return mock(DeleteAllStrategiesUseCase.class);
    }

    @Produces
    @ApplicationScoped
    public CalculateStrategyPerformanceUseCase calculateStrategyPerformanceUseCase() {
        return mock(CalculateStrategyPerformanceUseCase.class);
    }

    @Produces
    @ApplicationScoped
    public CreateSimulationUseCase createSimulationUseCase() {
        return mock(CreateSimulationUseCase.class);
    }

    @Produces
    @ApplicationScoped
    public ListSimulationsUseCase listSimulationsUseCase() {
        return mock(ListSimulationsUseCase.class);
    }

    @Produces
    @ApplicationScoped
    public GetSimulationUseCase getSimulationUseCase() {
        return mock(GetSimulationUseCase.class);
    }

    @Produces
    @ApplicationScoped
    public com.quorumtrading.application.usecase.health.GetSimulationHealthUseCase getSimulationHealthUseCase() {
        return mock(com.quorumtrading.application.usecase.health.GetSimulationHealthUseCase.class);
    }

    @Produces
    @ApplicationScoped
    public DeleteSimulationUseCase deleteSimulationUseCase() {
        return mock(DeleteSimulationUseCase.class);
    }

    @Produces
    @ApplicationScoped
    public StartSimulationUseCase startSimulationUseCase() {
        return mock(StartSimulationUseCase.class);
    }

    @Produces
    @ApplicationScoped
    public PauseSimulationUseCase pauseSimulationUseCase() {
        return mock(PauseSimulationUseCase.class);
    }

    @Produces
    @ApplicationScoped
    public ResumeSimulationUseCase resumeSimulationUseCase() {
        return mock(ResumeSimulationUseCase.class);
    }

    @Produces
    @ApplicationScoped
    public TriggerDataCollectionUseCase triggerDataCollectionUseCase() {
        return mock(TriggerDataCollectionUseCase.class);
    }

    @Produces
    @ApplicationScoped
    public QueryPortfolioSnapshotsUseCase queryPortfolioSnapshotsUseCase() {
        return mock(QueryPortfolioSnapshotsUseCase.class);
    }

    // NOTE: These are mocked even though they're disabled in bootstrap ApplicationConfig
    // because the REST resources still inject them and need mocks for testing
    @Produces
    @ApplicationScoped
    public QueryMarketEventsUseCase queryMarketEventsUseCase() {
        return mock(QueryMarketEventsUseCase.class);
    }

    @Produces
    @ApplicationScoped
    public QueryConsensusDecisionsUseCase queryConsensusDecisionsUseCase() {
        return mock(QueryConsensusDecisionsUseCase.class);
    }

    @Produces
    @ApplicationScoped
    public QueryLLMDecisionsUseCase queryLLMDecisionsUseCase() {
        return mock(QueryLLMDecisionsUseCase.class);
    }

    @Produces
    @ApplicationScoped
    public ReachConsensusUseCase reachConsensusUseCase() {
        return mock(ReachConsensusUseCase.class);
    }

    @Produces
    @ApplicationScoped
    public GetConsensusWithAgentsUseCase getConsensusWithAgentsUseCase() {
        return mock(GetConsensusWithAgentsUseCase.class);
    }

    @Produces
    @ApplicationScoped
    public GetMarketDataContextUseCase getMarketDataContextUseCase() {
        return mock(GetMarketDataContextUseCase.class);
    }

    @Produces
    @ApplicationScoped
    public CreateBenchmarkUseCase createBenchmarkUseCase() {
        return mock(CreateBenchmarkUseCase.class);
    }

    @Produces
    @ApplicationScoped
    public CreateMarketResearchUseCase createMarketResearchUseCase() {
        return mock(CreateMarketResearchUseCase.class);
    }

    @Produces
    @ApplicationScoped
    public UpdateMarketResearchUseCase updateMarketResearchUseCase() {
        return mock(UpdateMarketResearchUseCase.class);
    }

    @Produces
    @ApplicationScoped
    public GetMarketResearchDetailsUseCase getMarketResearchDetailsUseCase() {
        return mock(GetMarketResearchDetailsUseCase.class);
    }

    @Produces
    @ApplicationScoped
    public PauseMarketResearchUseCase pauseMarketResearchUseCase() {
        return mock(PauseMarketResearchUseCase.class);
    }

    @Produces
    @ApplicationScoped
    public ResumeMarketResearchUseCase resumeMarketResearchUseCase() {
        return mock(ResumeMarketResearchUseCase.class);
    }

    @Produces
    @ApplicationScoped
    public ExecuteMarketResearchUseCase executeMarketResearchUseCase() {
        return mock(ExecuteMarketResearchUseCase.class);
    }

    @Produces
    @ApplicationScoped
    public GetSimulationChartDataUseCase getSimulationChartDataUseCase() {
        return mock(GetSimulationChartDataUseCase.class);
    }

    @Produces
    @ApplicationScoped
    public TriggerSimulationUseCase triggerSimulationUseCase() {
        return mock(TriggerSimulationUseCase.class);
    }

    @Produces
    @ApplicationScoped
    public ReorderQueueUseCase reorderQueueUseCase() {
        return mock(ReorderQueueUseCase.class);
    }

    @Produces
    @ApplicationScoped
    public DeletePausedSimulationsUseCase deletePausedSimulationsUseCase() {
        return mock(DeletePausedSimulationsUseCase.class);
    }

    // User Accounts & Multi-tenancy use cases
    @Produces
    @ApplicationScoped
    public AuthenticateUserUseCase authenticateUserUseCase() {
        return mock(AuthenticateUserUseCase.class);
    }

    // Budget Use Cases
    @Produces
    @ApplicationScoped
    public GetUserBudgetUseCase getUserBudgetUseCase() {
        return mock(GetUserBudgetUseCase.class);
    }

    @Produces
    @ApplicationScoped
    public TopUpBudgetUseCase topUpBudgetUseCase() {
        return mock(TopUpBudgetUseCase.class);
    }

    @Produces
    @ApplicationScoped
    public com.quorumtrading.application.usecase.UpdateDailySpendLimitUseCase updateDailySpendLimitUseCase() {
        return mock(com.quorumtrading.application.usecase.UpdateDailySpendLimitUseCase.class);
    }

    // Config Use Cases
    @Produces
    @ApplicationScoped
    public GetAvailableModelsUseCase getAvailableModelsUseCase() {
        return mock(GetAvailableModelsUseCase.class);
    }

    @Produces
    @ApplicationScoped
    public UpdateModelPreferencesUseCase updateModelPreferencesUseCase() {
        return mock(UpdateModelPreferencesUseCase.class);
    }

    @Produces
    @ApplicationScoped
    public EstimateBacktestCostUseCase estimateBacktestCostUseCase() {
        return mock(EstimateBacktestCostUseCase.class);
    }

    // API Token Use Cases

    @Produces
    @ApplicationScoped
    public CreateApiTokenUseCase createApiTokenUseCase() {
        return mock(CreateApiTokenUseCase.class);
    }

    @Produces
    @ApplicationScoped
    public ListApiTokensUseCase listApiTokensUseCase() {
        return mock(ListApiTokensUseCase.class);
    }

    @Produces
    @ApplicationScoped
    public RevokeApiTokenUseCase revokeApiTokenUseCase() {
        return mock(RevokeApiTokenUseCase.class);
    }

    // Historical Events Use Cases

    @Produces
    @ApplicationScoped
    public ListHistoricalEventsUseCase listHistoricalEventsUseCase() {
        return mock(ListHistoricalEventsUseCase.class);
    }

    @Produces
    @ApplicationScoped
    public GetHistoricalEventUseCase getHistoricalEventUseCase() {
        return mock(GetHistoricalEventUseCase.class);
    }

    // Chat Use Cases

    @Produces
    @ApplicationScoped
    public CreateChatSessionUseCase createChatSessionUseCase() {
        return mock(CreateChatSessionUseCase.class);
    }

    @Produces
    @ApplicationScoped
    public GetChatSessionUseCase getChatSessionUseCase() {
        return mock(GetChatSessionUseCase.class);
    }

    @Produces
    @ApplicationScoped
    public SendChatMessageUseCase sendChatMessageUseCase() {
        return mock(SendChatMessageUseCase.class);
    }

    // Ticker Performance Use Case

    @Produces
    @ApplicationScoped
    public GetTickerPerformanceUseCase getTickerPerformanceUseCase() {
        return mock(GetTickerPerformanceUseCase.class);
    }

    // Feature Flags

    /**
     * Every flagged feature is on by default in REST tests.
     *
     * <p>{@code FeatureFlagGateFilter} runs on every request into a gated path, so a bare Mockito
     * mock (which answers {@code false}) would 403 every challenge and market-research test in the
     * module for reasons that have nothing to do with what those tests assert. Tests that care
     * about the gate override this with {@code @InjectMock} and re-stub it.</p>
     */
    @Produces
    @ApplicationScoped
    public EvaluateFeatureUseCase evaluateFeatureUseCase() {
        EvaluateFeatureUseCase useCase = mock(EvaluateFeatureUseCase.class);
        org.mockito.Mockito.when(useCase.isEnabled(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
            .thenReturn(true);
        return useCase;
    }

    @Produces
    @ApplicationScoped
    public ListFeatureFlagsUseCase listFeatureFlagsUseCase() {
        return mock(ListFeatureFlagsUseCase.class);
    }

    @Produces
    @ApplicationScoped
    public UpdateFeatureFlagUseCase updateFeatureFlagUseCase() {
        return mock(UpdateFeatureFlagUseCase.class);
    }
}
