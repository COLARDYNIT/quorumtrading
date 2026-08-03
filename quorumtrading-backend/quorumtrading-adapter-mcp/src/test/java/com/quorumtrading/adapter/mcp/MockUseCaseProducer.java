package com.quorumtrading.adapter.mcp;

import com.quorumtrading.application.event.SimulationCreatedEvent;
import com.quorumtrading.application.port.out.*;
import com.quorumtrading.application.service.IndicatorCalculationService;
import com.quorumtrading.application.service.IndicatorEnrichmentService;
import com.quorumtrading.application.usecase.*;
import com.quorumtrading.application.usecase.apitoken.ResolveUserByApiTokenUseCase;
import com.quorumtrading.application.usecase.brokeraccount.GetBrokerAccountUseCase;
import com.quorumtrading.application.usecase.llm.GetLlmProviderStatusUseCase;
import com.quorumtrading.application.usecase.llm.SelectLlmProviderUseCase;
import com.quorumtrading.application.usecase.brokeraccount.ListBrokerAccountsUseCase;
import com.quorumtrading.application.usecase.brokeraccount.ValidateBrokerAccountCredentialsUseCase;
import io.quarkus.test.Mock;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

import static org.mockito.Mockito.mock;

/**
 * CDI producer for mock use case and repository beans.
 * Provides mocks for all dependencies needed by MCP tools during testing.
 * Follows the same pattern as the REST adapter's MockUseCaseProducer.
 */
@Mock
@ApplicationScoped
public class MockUseCaseProducer {

    // Authorization policy is deliberately NOT mocked: a mocked requireView() does nothing,
    // which would let an ownership regression pass the tool tests unnoticed. It is a
    // dependency-free POJO, so the real one is cheap and actually enforces.
    @Produces
    @ApplicationScoped
    public com.quorumtrading.application.service.AuthorizationService authorizationService() {
        return new com.quorumtrading.application.service.AuthorizationService();
    }

    // Use case mocks

    @Produces
    @ApplicationScoped
    public GetLlmProviderStatusUseCase getLlmProviderStatusUseCase() {
        return mock(GetLlmProviderStatusUseCase.class);
    }

    @Produces
    @ApplicationScoped
    public com.quorumtrading.application.usecase.marketdata.GetMarketDataStatusUseCase getMarketDataStatusUseCase() {
        return mock(com.quorumtrading.application.usecase.marketdata.GetMarketDataStatusUseCase.class);
    }

    @Produces
    @ApplicationScoped
    public SelectLlmProviderUseCase selectLlmProviderUseCase() {
        return mock(SelectLlmProviderUseCase.class);
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
    public CreateSimulationUseCase createSimulationUseCase() {
        return mock(CreateSimulationUseCase.class);
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
    public com.quorumtrading.application.usecase.ResumeSimulationUseCase resumeSimulationUseCase() {
        return mock(com.quorumtrading.application.usecase.ResumeSimulationUseCase.class);
    }

    @Produces
    @ApplicationScoped
    public com.quorumtrading.application.usecase.UpdateSimulationNotificationPreferenceUseCase
            updateSimulationNotificationPreferenceUseCase() {
        return mock(com.quorumtrading.application.usecase.UpdateSimulationNotificationPreferenceUseCase.class);
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
    public CalculateStrategyPerformanceUseCase calculateStrategyPerformanceUseCase() {
        return mock(CalculateStrategyPerformanceUseCase.class);
    }

    @Produces
    @ApplicationScoped
    public CreateStrategyUseCase createStrategyUseCase() {
        return mock(CreateStrategyUseCase.class);
    }

    @Produces
    @ApplicationScoped
    public GetConsensusWithAgentsUseCase getConsensusWithAgentsUseCase() {
        return mock(GetConsensusWithAgentsUseCase.class);
    }

    @Produces
    @ApplicationScoped
    public QueryConsensusDecisionsUseCase queryConsensusDecisionsUseCase() {
        return mock(QueryConsensusDecisionsUseCase.class);
    }

    @Produces
    @ApplicationScoped
    public QueryPortfolioSnapshotsUseCase queryPortfolioSnapshotsUseCase() {
        return mock(QueryPortfolioSnapshotsUseCase.class);
    }

    @Produces
    @ApplicationScoped
    public ResolveCurrentUserUseCase resolveCurrentUserUseCase() {
        return mock(ResolveCurrentUserUseCase.class);
    }

    // Benchmark use case mocks

    @Produces
    @ApplicationScoped
    public CreateBenchmarkUseCase createBenchmarkUseCase() {
        return mock(CreateBenchmarkUseCase.class);
    }

    @Produces
    @ApplicationScoped
    public DeleteBenchmarkUseCase deleteBenchmarkUseCase() {
        return mock(DeleteBenchmarkUseCase.class);
    }

    @Produces
    @ApplicationScoped
    public CalculateBenchmarkOverlaysUseCase calculateBenchmarkOverlaysUseCase() {
        return mock(CalculateBenchmarkOverlaysUseCase.class);
    }

    @Produces
    @ApplicationScoped
    public ComposeBenchmarkUseCase composeBenchmarkUseCase() {
        return mock(ComposeBenchmarkUseCase.class);
    }

    @Produces
    @ApplicationScoped
    public com.quorumtrading.application.usecase.BuildBenchmarkLeaderboardUseCase buildBenchmarkLeaderboardUseCase() {
        return mock(com.quorumtrading.application.usecase.BuildBenchmarkLeaderboardUseCase.class);
    }

    // Repository mocks needed for use case instantiation

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
    public UserRepository userRepository() {
        return mock(UserRepository.class);
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
    public com.quorumtrading.application.port.out.SimulationContributionRepository simulationContributionRepository() {
        return mock(com.quorumtrading.application.port.out.SimulationContributionRepository.class);
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
    public BenchmarkRepository benchmarkRepository() {
        return mock(BenchmarkRepository.class);
    }

    @Produces
    @ApplicationScoped
    public BackfillJobRepository backfillJobRepository() {
        return mock(BackfillJobRepository.class);
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
    public ScheduledSimulationRepository scheduledSimulationRepository() {
        return mock(ScheduledSimulationRepository.class);
    }

    @Produces
    @ApplicationScoped
    public LLMUsageRepository llmUsageRepository() {
        return mock(LLMUsageRepository.class);
    }

    @Produces
    @ApplicationScoped
    public BrokerAccountRepository brokerAccountRepository() {
        return mock(BrokerAccountRepository.class);
    }

    // Port mocks

    @Produces
    @ApplicationScoped
    public LLMPort llmPort() {
        return mock(LLMPort.class);
    }

    @Produces
    @ApplicationScoped
    public EventPublisher<SimulationCreatedEvent> simulationCreatedEventPublisher() {
        return mock(EventPublisher.class);
    }

    @Produces
    @ApplicationScoped
    public EventPublisher<com.quorumtrading.application.event.ResearchExecutionCompleted> researchEventPublisher() {
        return event -> {};
    }

    @Produces
    @ApplicationScoped
    public DeleteStrategyUseCase deleteStrategyUseCase() {
        return mock(DeleteStrategyUseCase.class);
    }

    @Produces
    @ApplicationScoped
    public UpdateStrategyUseCase updateStrategyUseCase() {
        return mock(UpdateStrategyUseCase.class);
    }

    @Produces
    @ApplicationScoped
    public DeleteSimulationUseCase deleteSimulationUseCase() {
        return mock(DeleteSimulationUseCase.class);
    }

    @Produces
    @ApplicationScoped
    public DeleteMarketResearchUseCase deleteMarketResearchUseCase() {
        return mock(DeleteMarketResearchUseCase.class);
    }

    // Market Research use case mocks

    @Produces
    @ApplicationScoped
    public ListMarketResearchUseCase listMarketResearchUseCase() {
        return mock(ListMarketResearchUseCase.class);
    }

    @Produces
    @ApplicationScoped
    public GetMarketResearchDetailsUseCase getMarketResearchDetailsUseCase() {
        return mock(GetMarketResearchDetailsUseCase.class);
    }

    @Produces
    @ApplicationScoped
    public CreateMarketResearchUseCase createMarketResearchUseCase() {
        return mock(CreateMarketResearchUseCase.class);
    }

    @Produces
    @ApplicationScoped
    public ExecuteMarketResearchUseCase executeMarketResearchUseCase() {
        return mock(ExecuteMarketResearchUseCase.class);
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

    // Market Data use case mocks

    @Produces
    @ApplicationScoped
    public QueryMarketEventsUseCase queryMarketEventsUseCase() {
        return mock(QueryMarketEventsUseCase.class);
    }

    @Produces
    @ApplicationScoped
    public BackFillTickerPriceDataUseCase backFillTickerPriceDataUseCase() {
        return mock(BackFillTickerPriceDataUseCase.class);
    }

    // Indicator service mocks

    @Produces
    @ApplicationScoped
    public IndicatorEnrichmentService indicatorEnrichmentService() {
        return mock(IndicatorEnrichmentService.class);
    }

    @Produces
    @ApplicationScoped
    public IndicatorCalculationService indicatorCalculationService() {
        return mock(IndicatorCalculationService.class);
    }

    @Produces
    @ApplicationScoped
    public HistoricalPricePort historicalPricePort() {
        return mock(HistoricalPricePort.class);
    }

    // Broker Account use case mocks

    @Produces
    @ApplicationScoped
    public ListBrokerAccountsUseCase listBrokerAccountsUseCase() {
        return mock(ListBrokerAccountsUseCase.class);
    }

    @Produces
    @ApplicationScoped
    public GetBrokerAccountUseCase getBrokerAccountUseCase() {
        return mock(GetBrokerAccountUseCase.class);
    }

    @Produces
    @ApplicationScoped
    public ValidateBrokerAccountCredentialsUseCase validateBrokerAccountCredentialsUseCase() {
        return mock(ValidateBrokerAccountCredentialsUseCase.class);
    }

    // Additional port mocks

    @Produces
    @ApplicationScoped
    public BrokerCredentialValidationPort brokerCredentialValidationPort() {
        return mock(BrokerCredentialValidationPort.class);
    }

    // API Token use case mock

    @Produces
    @ApplicationScoped
    public ResolveUserByApiTokenUseCase resolveUserByApiTokenUseCase() {
        return mock(ResolveUserByApiTokenUseCase.class);
    }

    // Config use case mocks

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

    @Produces
    @ApplicationScoped
    public com.quorumtrading.application.service.PriceHistoryWarmupInspector priceHistoryWarmupInspector() {
        return mock(com.quorumtrading.application.service.PriceHistoryWarmupInspector.class);
    }

    /**
     * The effective-model read model. get_model_preferences renders it rather than re-deriving an
     * answer from config plus the DB, so it can no longer disagree with the other surfaces.
     */
    @Produces
    @ApplicationScoped
    public com.quorumtrading.application.usecase.GetEffectiveModelsUseCase getEffectiveModelsUseCase() {
        return mock(com.quorumtrading.application.usecase.GetEffectiveModelsUseCase.class);
    }

    @Produces
    @ApplicationScoped
    public SystemModelDefaultsPort systemModelDefaultsPort() {
        return mock(SystemModelDefaultsPort.class);
    }

    @Produces
    @ApplicationScoped
    public UserBudgetRepository userBudgetRepository() {
        return mock(UserBudgetRepository.class);
    }

    // Position Management use case mocks

    @Produces
    @ApplicationScoped
    public SellPositionUseCase sellPositionUseCase() {
        return mock(SellPositionUseCase.class);
    }

    @Produces
    @ApplicationScoped
    public LiquidateAllPositionsUseCase liquidateAllPositionsUseCase() {
        return mock(LiquidateAllPositionsUseCase.class);
    }

    @Produces
    @ApplicationScoped
    public ForceEndSimulationUseCase forceEndSimulationUseCase() {
        return mock(ForceEndSimulationUseCase.class);
    }

    // Ticker search use case mock

    @Produces
    @ApplicationScoped
    public FindTickerUseCase findTickerUseCase() {
        return mock(FindTickerUseCase.class);
    }

    // Cleanup use case mock

    @Produces
    @ApplicationScoped
    public PruneUnusedUseCase pruneUnusedUseCase() {
        return mock(PruneUnusedUseCase.class);
    }

    // Historical Events use case mocks

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

    // Simulation Cost use case mock

    @Produces
    @ApplicationScoped
    public CalculateSimulationCostUseCase calculateSimulationCostUseCase() {
        return mock(CalculateSimulationCostUseCase.class);
    }

    // Ticker Performance use case mock

    @Produces
    @ApplicationScoped
    public GetTickerPerformanceUseCase getTickerPerformanceUseCase() {
        return mock(GetTickerPerformanceUseCase.class);
    }

    @Produces
    @ApplicationScoped
    public com.quorumtrading.application.port.out.TickerPerformancePort tickerPerformancePort() {
        return mock(com.quorumtrading.application.port.out.TickerPerformancePort.class);
    }

    // Dividend Info use case mock

    @Produces
    @ApplicationScoped
    public GetDividendInfoUseCase getDividendInfoUseCase() {
        return mock(GetDividendInfoUseCase.class);
    }

    @Produces
    @ApplicationScoped
    public com.quorumtrading.application.port.out.DividendInfoPort dividendInfoPort() {
        return mock(com.quorumtrading.application.port.out.DividendInfoPort.class);
    }

    // Restart from decision

    @Produces
    @ApplicationScoped
    public com.quorumtrading.application.service.RestartSimulationFromService restartSimulationFromService() {
        return mock(com.quorumtrading.application.service.RestartSimulationFromService.class);
    }

    @Produces
    @ApplicationScoped
    public LlmParseFailureRepository llmParseFailureRepository() {
        return mock(LlmParseFailureRepository.class);
    }

    // News Data Availability

    @Produces
    @jakarta.inject.Singleton
    public com.quorumtrading.model.valueobject.NewsDataAvailability newsDataAvailability() {
        return new com.quorumtrading.model.valueobject.NewsDataAvailability(java.time.LocalDate.of(2015, 1, 1));
    }

    // Challenge beans

    @Produces
    @ApplicationScoped
    public com.quorumtrading.application.port.out.ChallengeRepository challengeRepository() {
        return mock(com.quorumtrading.application.port.out.ChallengeRepository.class);
    }

    @Produces
    @ApplicationScoped
    public com.quorumtrading.application.usecase.JoinChallengeUseCase joinChallengeUseCase() {
        return mock(com.quorumtrading.application.usecase.JoinChallengeUseCase.class);
    }

    @Produces
    @ApplicationScoped
    public com.quorumtrading.application.usecase.BuildChallengeLeaderboardUseCase buildChallengeLeaderboardUseCase() {
        return mock(com.quorumtrading.application.usecase.BuildChallengeLeaderboardUseCase.class);
    }

    // -------------------------------------------------------------------------
    // DCA / recurring investment plans + notifications + contributions
    // Added with the e105d7a3 (DCA) feature commit — these use cases are
    // wired through bootstrap's ApplicationConfig at runtime, but MCP-adapter
    // tests don't include bootstrap, so each one needs its own mock here.
    // -------------------------------------------------------------------------

    // ---- Notification use cases ----

    @Produces
    @ApplicationScoped
    public com.quorumtrading.application.usecase.notification.CountUnreadNotificationsUseCase countUnreadNotificationsUseCase() {
        return mock(com.quorumtrading.application.usecase.notification.CountUnreadNotificationsUseCase.class);
    }

    @Produces
    @ApplicationScoped
    public com.quorumtrading.application.usecase.notification.GetNotificationUseCase getNotificationUseCase() {
        return mock(com.quorumtrading.application.usecase.notification.GetNotificationUseCase.class);
    }

    @Produces
    @ApplicationScoped
    public com.quorumtrading.application.usecase.notification.ListNotificationsUseCase listNotificationsUseCase() {
        return mock(com.quorumtrading.application.usecase.notification.ListNotificationsUseCase.class);
    }

    @Produces
    @ApplicationScoped
    public com.quorumtrading.application.usecase.notification.MarkAllNotificationsReadUseCase markAllNotificationsReadUseCase() {
        return mock(com.quorumtrading.application.usecase.notification.MarkAllNotificationsReadUseCase.class);
    }

    @Produces
    @ApplicationScoped
    public com.quorumtrading.application.usecase.notification.MarkNotificationReadUseCase markNotificationReadUseCase() {
        return mock(com.quorumtrading.application.usecase.notification.MarkNotificationReadUseCase.class);
    }

    @Produces
    @ApplicationScoped
    public com.quorumtrading.application.usecase.notification.GetNotificationPreferencesUseCase getNotificationPreferencesUseCase() {
        return mock(com.quorumtrading.application.usecase.notification.GetNotificationPreferencesUseCase.class);
    }

    @Produces
    @ApplicationScoped
    public com.quorumtrading.application.usecase.notification.UpdateNotificationPreferenceUseCase updateNotificationPreferenceUseCase() {
        return mock(com.quorumtrading.application.usecase.notification.UpdateNotificationPreferenceUseCase.class);
    }

    // ---- Contribution use cases ----

    @Produces
    @ApplicationScoped
    public com.quorumtrading.application.usecase.contribution.GetSimulationContributionsUseCase getSimulationContributionsUseCase() {
        return mock(com.quorumtrading.application.usecase.contribution.GetSimulationContributionsUseCase.class);
    }

    // ---- Investment plan use cases ----

    @Produces
    @ApplicationScoped
    public com.quorumtrading.application.usecase.investmentplan.CreateInvestmentPlanUseCase createInvestmentPlanUseCase() {
        return mock(com.quorumtrading.application.usecase.investmentplan.CreateInvestmentPlanUseCase.class);
    }

    @Produces
    @ApplicationScoped
    public com.quorumtrading.application.usecase.investmentplan.ExecuteInvestmentPlanUseCase executeInvestmentPlanUseCase() {
        return mock(com.quorumtrading.application.usecase.investmentplan.ExecuteInvestmentPlanUseCase.class);
    }

    @Produces
    @ApplicationScoped
    public com.quorumtrading.application.usecase.investmentplan.GetInvestmentPlanUseCase getInvestmentPlanUseCase() {
        return mock(com.quorumtrading.application.usecase.investmentplan.GetInvestmentPlanUseCase.class);
    }

    @Produces
    @ApplicationScoped
    public com.quorumtrading.application.usecase.investmentplan.ListInvestmentPlansUseCase listInvestmentPlansUseCase() {
        return mock(com.quorumtrading.application.usecase.investmentplan.ListInvestmentPlansUseCase.class);
    }

    @Produces
    @ApplicationScoped
    public com.quorumtrading.application.usecase.investmentplan.PauseInvestmentPlanUseCase pauseInvestmentPlanUseCase() {
        return mock(com.quorumtrading.application.usecase.investmentplan.PauseInvestmentPlanUseCase.class);
    }

    @Produces
    @ApplicationScoped
    public com.quorumtrading.application.usecase.investmentplan.ResumeInvestmentPlanUseCase resumeInvestmentPlanUseCase() {
        return mock(com.quorumtrading.application.usecase.investmentplan.ResumeInvestmentPlanUseCase.class);
    }

    @Produces
    @ApplicationScoped
    public com.quorumtrading.application.usecase.investmentplan.TerminateInvestmentPlanUseCase terminateInvestmentPlanUseCase() {
        return mock(com.quorumtrading.application.usecase.investmentplan.TerminateInvestmentPlanUseCase.class);
    }

    @Produces
    @ApplicationScoped
    public com.quorumtrading.application.usecase.investmentplan.UpdateInvestmentPlanUseCase updateInvestmentPlanUseCase() {
        return mock(com.quorumtrading.application.usecase.investmentplan.UpdateInvestmentPlanUseCase.class);
    }

    @Produces
    @ApplicationScoped
    public com.quorumtrading.application.usecase.investmentplan.GetInvestmentAllocationUseCase getInvestmentAllocationUseCase() {
        return mock(com.quorumtrading.application.usecase.investmentplan.GetInvestmentAllocationUseCase.class);
    }

    @Produces
    @ApplicationScoped
    public com.quorumtrading.application.usecase.investmentplan.ListInvestmentAllocationsUseCase listInvestmentAllocationsUseCase() {
        return mock(com.quorumtrading.application.usecase.investmentplan.ListInvestmentAllocationsUseCase.class);
    }

    /**
     * Every flagged feature is on by default in MCP tests.
     *
     * <p>{@code McpToolBase} guards gated tools with this, so a bare Mockito mock (which answers
     * {@code false}) would turn every challenge and market-research tool test into an assertion
     * about the flag rather than about the tool. Tests that care about the gate re-stub it.</p>
     */
    @Produces
    @ApplicationScoped
    public com.quorumtrading.application.usecase.EvaluateFeatureUseCase evaluateFeatureUseCase() {
        var useCase = mock(com.quorumtrading.application.usecase.EvaluateFeatureUseCase.class);
        org.mockito.Mockito.when(useCase.isEnabledForUser(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
            .thenReturn(true);
        org.mockito.Mockito.when(useCase.isEnabled(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
            .thenReturn(true);
        return useCase;
    }
}
