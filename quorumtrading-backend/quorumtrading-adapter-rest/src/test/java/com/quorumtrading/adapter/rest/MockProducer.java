package com.quorumtrading.adapter.rest;

import com.quorumtrading.application.usecase.AnalyzeDataAvailabilityUseCase;
import com.quorumtrading.application.usecase.QueryBackfillProgressUseCase;
import com.quorumtrading.application.usecase.RequestBackfillUseCase;
import com.quorumtrading.application.usecase.brokeraccount.CreateBrokerAccountUseCase;
import com.quorumtrading.application.usecase.brokeraccount.DeleteBrokerAccountUseCase;
import com.quorumtrading.application.usecase.brokeraccount.GetBrokerAccountUseCase;
import com.quorumtrading.application.usecase.brokeraccount.ListBrokerAccountsUseCase;
import com.quorumtrading.application.usecase.brokeraccount.UpdateBrokerAccountCredentialsUseCase;
import com.quorumtrading.application.usecase.brokeraccount.ValidateBrokerAccountCredentialsUseCase;
import com.quorumtrading.application.usecase.CalculateBenchmarkOverlaysUseCase;
import com.quorumtrading.application.usecase.DeleteBenchmarkUseCase;
import com.quorumtrading.application.usecase.DeleteUserAccountUseCase;
import com.quorumtrading.application.usecase.GetDividendInfoUseCase;
import com.quorumtrading.application.usecase.llm.GetLlmProviderStatusUseCase;
import com.quorumtrading.application.usecase.llm.SelectLlmProviderUseCase;
import com.quorumtrading.application.port.out.HistoricalPricePort;
import com.quorumtrading.application.port.out.UserBudgetRepository;
import com.quorumtrading.application.service.IndicatorEnrichmentService;
import io.quarkus.test.Mock;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

import static org.mockito.Mockito.mock;

/**
 * CDI producer for mock beans needed by REST adapter tests.
 *
 * <p>This MockProducer provides mock implementations of use cases
 * that are injected into REST resources. Since the REST adapter module
 * is isolated and doesn't include the ApplicationConfig from the bootstrap module,
 * these mocks are needed for @QuarkusTest integration tests to run.</p>
 */
@Mock
@ApplicationScoped
public class MockProducer {

    @Produces
    @ApplicationScoped
    public AnalyzeDataAvailabilityUseCase analyzeDataAvailabilityUseCase() {
        return mock(AnalyzeDataAvailabilityUseCase.class);
    }

    @Produces
    @ApplicationScoped
    public GetLlmProviderStatusUseCase getLlmProviderStatusUseCase() {
        return mock(GetLlmProviderStatusUseCase.class);
    }

    @Produces
    @ApplicationScoped
    public SelectLlmProviderUseCase selectLlmProviderUseCase() {
        return mock(SelectLlmProviderUseCase.class);
    }

    @Produces
    @ApplicationScoped
    public RequestBackfillUseCase requestBackfillUseCase() {
        return mock(RequestBackfillUseCase.class);
    }

    @Produces
    @ApplicationScoped
    public QueryBackfillProgressUseCase queryBackfillProgressUseCase() {
        return mock(QueryBackfillProgressUseCase.class);
    }

    @Produces
    @ApplicationScoped
    public com.quorumtrading.application.usecase.marketdata.GetMarketDataStatusUseCase getMarketDataStatusUseCase() {
        return mock(com.quorumtrading.application.usecase.marketdata.GetMarketDataStatusUseCase.class);
    }

    @Produces
    @ApplicationScoped
    public com.quorumtrading.application.service.DataHorizonService dataHorizonService() {
        return mock(com.quorumtrading.application.service.DataHorizonService.class);
    }

    @Produces
    @ApplicationScoped
    public com.quorumtrading.application.port.out.MarketDataHorizonRepository marketDataHorizonRepository() {
        return mock(com.quorumtrading.application.port.out.MarketDataHorizonRepository.class);
    }

    @Produces
    @ApplicationScoped
    public com.quorumtrading.application.service.ClassifyGapsService classifyGapsService() {
        return mock(com.quorumtrading.application.service.ClassifyGapsService.class);
    }

    // Broker Account Use Cases

    @Produces
    @ApplicationScoped
    public CreateBrokerAccountUseCase createBrokerAccountUseCase() {
        return mock(CreateBrokerAccountUseCase.class);
    }

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
    public DeleteBrokerAccountUseCase deleteBrokerAccountUseCase() {
        return mock(DeleteBrokerAccountUseCase.class);
    }

    @Produces
    @ApplicationScoped
    public UpdateBrokerAccountCredentialsUseCase updateBrokerAccountCredentialsUseCase() {
        return mock(UpdateBrokerAccountCredentialsUseCase.class);
    }

    @Produces
    @ApplicationScoped
    public ValidateBrokerAccountCredentialsUseCase validateBrokerAccountCredentialsUseCase() {
        return mock(ValidateBrokerAccountCredentialsUseCase.class);
    }

    // Saxo OAuth onboarding use cases (SaxoOnboardingResource)
    @Produces
    @ApplicationScoped
    public com.quorumtrading.application.usecase.saxo.StartSaxoOnboardingUseCase startSaxoOnboardingUseCase() {
        return mock(com.quorumtrading.application.usecase.saxo.StartSaxoOnboardingUseCase.class);
    }

    @Produces
    @ApplicationScoped
    public com.quorumtrading.application.usecase.saxo.CompleteSaxoOnboardingUseCase completeSaxoOnboardingUseCase() {
        return mock(com.quorumtrading.application.usecase.saxo.CompleteSaxoOnboardingUseCase.class);
    }

    @Produces
    @ApplicationScoped
    public DeleteBenchmarkUseCase deleteBenchmarkUseCase() {
        return mock(DeleteBenchmarkUseCase.class);
    }

    @Produces
    @ApplicationScoped
    public com.quorumtrading.application.usecase.UpdateSimulationNotificationPreferenceUseCase
            updateSimulationNotificationPreferenceUseCase() {
        return mock(com.quorumtrading.application.usecase.UpdateSimulationNotificationPreferenceUseCase.class);
    }

    @Produces
    @ApplicationScoped
    public CalculateBenchmarkOverlaysUseCase calculateBenchmarkOverlaysUseCase() {
        return mock(CalculateBenchmarkOverlaysUseCase.class);
    }

    @Produces
    @ApplicationScoped
    public com.quorumtrading.application.usecase.BuildBenchmarkLeaderboardUseCase buildBenchmarkLeaderboardUseCase() {
        return mock(com.quorumtrading.application.usecase.BuildBenchmarkLeaderboardUseCase.class);
    }

    @Produces
    @ApplicationScoped
    public com.quorumtrading.application.usecase.BuildChallengeLeaderboardUseCase buildChallengeLeaderboardUseCase() {
        return mock(com.quorumtrading.application.usecase.BuildChallengeLeaderboardUseCase.class);
    }

    @Produces
    @ApplicationScoped
    public com.quorumtrading.application.usecase.CalculateSimulationOverlaysUseCase calculateSimulationOverlaysUseCase() {
        return mock(com.quorumtrading.application.usecase.CalculateSimulationOverlaysUseCase.class);
    }

    @Produces
    @ApplicationScoped
    public DeleteUserAccountUseCase deleteUserAccountUseCase() {
        return mock(DeleteUserAccountUseCase.class);
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

    // Position Management Use Cases

    @Produces
    @ApplicationScoped
    public com.quorumtrading.application.usecase.SellPositionUseCase sellPositionUseCase() {
        return mock(com.quorumtrading.application.usecase.SellPositionUseCase.class);
    }

    @Produces
    @ApplicationScoped
    public com.quorumtrading.application.usecase.LiquidateAllPositionsUseCase liquidateAllPositionsUseCase() {
        return mock(com.quorumtrading.application.usecase.LiquidateAllPositionsUseCase.class);
    }

    @Produces
    @ApplicationScoped
    public com.quorumtrading.application.usecase.ForceEndSimulationUseCase forceEndSimulationUseCase() {
        return mock(com.quorumtrading.application.usecase.ForceEndSimulationUseCase.class);
    }

    @Produces
    @ApplicationScoped
    public com.quorumtrading.application.usecase.PruneUnusedUseCase pruneUnusedUseCase() {
        return mock(com.quorumtrading.application.usecase.PruneUnusedUseCase.class);
    }

    // Dividend Use Cases

    @Produces
    @ApplicationScoped
    public GetDividendInfoUseCase getDividendInfoUseCase() {
        return mock(GetDividendInfoUseCase.class);
    }

    // Restart from decision

    @Produces
    @ApplicationScoped
    public com.quorumtrading.application.service.RestartSimulationFromService restartSimulationFromService() {
        return mock(com.quorumtrading.application.service.RestartSimulationFromService.class);
    }

    // Model Reliability

    @Produces
    @ApplicationScoped
    public com.quorumtrading.application.usecase.GetModelReliabilityUseCase getModelReliabilityUseCase() {
        return mock(com.quorumtrading.application.usecase.GetModelReliabilityUseCase.class);
    }

    /** The effective-model read model every config surface renders. */
    @Produces
    @ApplicationScoped
    public com.quorumtrading.application.usecase.GetEffectiveModelsUseCase getEffectiveModelsUseCase() {
        return mock(com.quorumtrading.application.usecase.GetEffectiveModelsUseCase.class);
    }

    /** Engine-aware description of what "fallback" means on the selected engine. */
    @Produces
    @ApplicationScoped
    public com.quorumtrading.application.usecase.DescribeFallbackBehaviourUseCase describeFallbackBehaviourUseCase() {
        return mock(com.quorumtrading.application.usecase.DescribeFallbackBehaviourUseCase.class);
    }

    // Public Strategy

    @Produces
    @ApplicationScoped
    public com.quorumtrading.application.usecase.ListPublicStrategiesUseCase listPublicStrategiesUseCase() {
        return mock(com.quorumtrading.application.usecase.ListPublicStrategiesUseCase.class);
    }

    @Produces
    @ApplicationScoped
    public com.quorumtrading.application.usecase.CloneStrategyUseCase cloneStrategyUseCase() {
        return mock(com.quorumtrading.application.usecase.CloneStrategyUseCase.class);
    }

    @Produces
    @ApplicationScoped
    public com.quorumtrading.application.usecase.ToggleStrategyVisibilityUseCase toggleStrategyVisibilityUseCase() {
        return mock(com.quorumtrading.application.usecase.ToggleStrategyVisibilityUseCase.class);
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
    public com.quorumtrading.application.usecase.CreateChallengeUseCase createChallengeUseCase() {
        return mock(com.quorumtrading.application.usecase.CreateChallengeUseCase.class);
    }

    @Produces
    @ApplicationScoped
    public com.quorumtrading.application.usecase.JoinChallengeUseCase joinChallengeUseCase() {
        return mock(com.quorumtrading.application.usecase.JoinChallengeUseCase.class);
    }

    @Produces
    @ApplicationScoped
    public com.quorumtrading.application.usecase.DeleteChallengeUseCase deleteChallengeUseCase() {
        return mock(com.quorumtrading.application.usecase.DeleteChallengeUseCase.class);
    }

    @Produces
    @ApplicationScoped
    public com.quorumtrading.application.port.out.ChallengeRepository challengeRepository() {
        return mock(com.quorumtrading.application.port.out.ChallengeRepository.class);
    }

    @Produces
    @ApplicationScoped
    public com.quorumtrading.application.service.BuyAndHoldOverlayCalculator buyAndHoldOverlayCalculator() {
        return mock(com.quorumtrading.application.service.BuyAndHoldOverlayCalculator.class);
    }

    // -------------------------------------------------------------------------
    // DCA / recurring investment plans + notifications + contributions
    // Added with the e105d7a3 (DCA) feature commit — these use cases are
    // wired through bootstrap's ApplicationConfig at runtime, but REST-adapter
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

    @Produces
    @ApplicationScoped
    public com.quorumtrading.application.usecase.contribution.RecordSimulationContributionUseCase recordSimulationContributionUseCase() {
        return mock(com.quorumtrading.application.usecase.contribution.RecordSimulationContributionUseCase.class);
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

    // ---- Email services + port ----

    @Produces
    @ApplicationScoped
    public com.quorumtrading.application.port.out.EmailSender emailSender() {
        return mock(com.quorumtrading.application.port.out.EmailSender.class);
    }

    @Produces
    @ApplicationScoped
    public com.quorumtrading.application.service.EmailTemplateRegistry emailTemplateRegistry() {
        return mock(com.quorumtrading.application.service.EmailTemplateRegistry.class);
    }

    @Produces
    @ApplicationScoped
    public com.quorumtrading.application.service.UnsubscribeTokenService unsubscribeTokenService() {
        // Real instance — token mint/verify is pure and stateless, no need to mock.
        return new com.quorumtrading.application.service.UnsubscribeTokenService(
                "test-only-unsubscribe-secret-1234567890");
    }
}
