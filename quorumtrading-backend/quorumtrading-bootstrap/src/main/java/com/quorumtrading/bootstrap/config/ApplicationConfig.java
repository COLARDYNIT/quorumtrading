package com.quorumtrading.bootstrap.config;

import com.quorumtrading.adapter.llm.router.LLMRouterAdapter;
import com.quorumtrading.application.config.FeatureFlagDefaults;
import com.quorumtrading.application.event.BackfillJobCreatedEvent;
import com.quorumtrading.application.event.IndicatorUpdateEvent;
import com.quorumtrading.application.orchestration.*;
import com.quorumtrading.application.orchestration.agents.*;
import com.quorumtrading.application.port.out.*;
import com.quorumtrading.application.chat.*;
import com.quorumtrading.application.port.out.ConversationalLLMPort;
import com.quorumtrading.application.port.out.HistoricalEventRepository;
import com.quorumtrading.application.service.*;
import com.quorumtrading.application.usecase.*;
import com.quorumtrading.application.usecase.budget.RecordLLMUsageUseCase;
import com.quorumtrading.application.port.out.PaymentPort;
import com.quorumtrading.application.port.out.PaymentTransactionRepository;
import io.quarkus.runtime.StartupEvent;
import io.smallrye.common.annotation.Identifier;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Disposes;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.context.ManagedExecutor;
import org.eclipse.microprofile.context.ThreadContext;

import com.quorumtrading.model.enums.BrokerType;
import com.quorumtrading.model.enums.Feature;
import com.quorumtrading.model.valueobject.BrokerCredentials;
import com.quorumtrading.model.valueobject.NewsDataAvailability;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;

/**
 * Application configuration for dependency injection.
 * <p>
 * As our application layer is pure and free of framework annotations,
 * we need to manually produce the beans that we need in our application.
 * </p>
 */
@ApplicationScoped
public class ApplicationConfig {

    // Strategy Use Cases

    @Produces
    @ApplicationScoped
    public CreateStrategyUseCase createStrategyUseCase(StrategyRepository strategyRepository, com.quorumtrading.application.service.BrokerRouter brokerRouter, LLMRouterAdapter llmRouter,
            com.quorumtrading.application.service.ModelPinValidator modelPinValidator) {
        return new CreateStrategyService(strategyRepository, brokerRouter, llmRouter, modelPinValidator);
    }

    @Produces
    @ApplicationScoped
    public UpdateStrategyUseCase updateStrategyUseCase(StrategyRepository strategyRepository, SimulationRepository simulationRepository, LLMRouterAdapter llmRouter, BrokerRouter brokerRouter, AuthorizationService authorizationService,
            com.quorumtrading.application.service.ModelPinValidator modelPinValidator) {
        return new UpdateStrategyService(strategyRepository, simulationRepository, llmRouter, brokerRouter, authorizationService, modelPinValidator);
    }

    @Produces
    @ApplicationScoped
    public GetStrategyUseCase getStrategyUseCase(StrategyRepository strategyRepository, AuthorizationService authorizationService) {
        return new GetStrategyService(strategyRepository, authorizationService);
    }

    @Produces
    @ApplicationScoped
    public ListStrategiesUseCase listStrategiesUseCase(StrategyRepository strategyRepository) {
        return new ListStrategiesService(strategyRepository);
    }

    @Produces
    @ApplicationScoped
    public DeleteStrategyUseCase deleteStrategyUseCase(StrategyRepository strategyRepository, SimulationRepository simulationRepository, BrokerAccountRepository brokerAccountRepository, AuthorizationService authorizationService) {
        return new DeleteStrategyService(strategyRepository, simulationRepository, brokerAccountRepository, authorizationService);
    }

    @Produces
    @ApplicationScoped
    public DeleteAllStrategiesUseCase deleteAllStrategiesUseCase(StrategyRepository strategyRepository) {
        return new DeleteAllStrategiesService(strategyRepository);
    }

    @Produces
    @ApplicationScoped
    public CloneStrategyUseCase cloneStrategyUseCase(StrategyRepository strategyRepository) {
        return new CloneStrategyService(strategyRepository);
    }

    @Produces
    @ApplicationScoped
    public ToggleStrategyVisibilityUseCase toggleStrategyVisibilityUseCase(StrategyRepository strategyRepository, UserRepository userRepository) {
        return new ToggleStrategyVisibilityService(strategyRepository, userRepository);
    }

    // Billing

    // The platform markup is deployment configuration, not code: 1.0 (billed == raw) unless the
    // environment sets BILLING_MARKUP_RATE. PlatformMarkup is a process-wide policy the domain
    // reads directly, so it is seeded here once before anything can bill.
    void configurePlatformMarkup(@Observes StartupEvent event,
            @ConfigProperty(name = "quorumtrading.billing.markup-rate", defaultValue = "1.0")
            java.math.BigDecimal markupRate) {
        com.quorumtrading.model.service.PlatformMarkup.configure(markupRate);
    }

    // Feature Flags
    //
    // The defaults below are the *seed* for each flag and the fallback when evaluation is
    // unavailable — never an override. Once a row exists the database wins, which is what lets an
    // administrator's change survive a redeploy and lets a self-hoster switch a feature on for good.

    // @Singleton, not @ApplicationScoped: a normal-scoped bean is client-proxied and a record is
    // final, so ARC rejects it outright. A pseudo-scope needs no proxy, and the value is immutable
    // anyway.
    @Produces
    @jakarta.inject.Singleton
    public FeatureFlagDefaults featureFlagDefaults(
            @ConfigProperty(name = "quorumtrading.features.challenges.default", defaultValue = "false")
            boolean challengesDefault,
            @ConfigProperty(name = "quorumtrading.features.market-research.default", defaultValue = "true")
            boolean marketResearchDefault) {
        return new FeatureFlagDefaults(java.util.Map.of(
            Feature.CHALLENGES, challengesDefault,
            Feature.MARKET_RESEARCH, marketResearchDefault));
    }

    @Produces
    @ApplicationScoped
    public FeatureFlagAdminGuard featureFlagAdminGuard(UserRepository userRepository) {
        return new FeatureFlagAdminGuard(userRepository);
    }

    @Produces
    @ApplicationScoped
    public EvaluateFeatureUseCase evaluateFeatureUseCase(FeatureFlagEvaluationPort evaluationPort,
                                                        FeatureFlagDefaults defaults,
                                                        UserRepository userRepository) {
        return new EvaluateFeatureService(evaluationPort, defaults, userRepository);
    }

    @Produces
    @ApplicationScoped
    public ListFeatureFlagsUseCase listFeatureFlagsUseCase(FeatureFlagRepository featureFlagRepository,
                                                          FeatureFlagAdminGuard adminGuard,
                                                          FeatureFlagDefaults defaults) {
        return new ListFeatureFlagsService(featureFlagRepository, adminGuard, defaults);
    }

    @Produces
    @ApplicationScoped
    public UpdateFeatureFlagUseCase updateFeatureFlagUseCase(FeatureFlagRepository featureFlagRepository,
                                                             FeatureFlagAdminGuard adminGuard,
                                                             FeatureFlagDefaults defaults) {
        return new UpdateFeatureFlagService(featureFlagRepository, adminGuard, defaults);
    }

    @Produces
    @ApplicationScoped
    public SeedFeatureFlagsUseCase seedFeatureFlagsUseCase(FeatureFlagRepository featureFlagRepository,
                                                           FeatureFlagDefaults defaults) {
        return new SeedFeatureFlagsService(featureFlagRepository, defaults);
    }

    @Produces
    @ApplicationScoped
    public ListPublicStrategiesUseCase listPublicStrategiesUseCase(StrategyRepository strategyRepository) {
        return new ListPublicStrategiesService(strategyRepository);
    }

    @Produces
    @ApplicationScoped
    public PruneUnusedUseCase pruneUnusedUseCase(
            StrategyRepository strategyRepository,
            SimulationRepository simulationRepository,
            MarketResearchRepository marketResearchRepository,
            ResearchExecutionRepository researchExecutionRepository) {
        return new PruneUnusedService(strategyRepository, simulationRepository, marketResearchRepository, researchExecutionRepository);
    }

    @Produces
    @ApplicationScoped
    public CalculateStrategyPerformanceUseCase calculateStrategyPerformanceUseCase(
            SimulationRepository simulationRepository,
            PortfolioRepository portfolioRepository,
            StrategyRepository strategyRepository,
            AuthorizationService authorizationService,
            com.quorumtrading.application.port.out.SimulationContributionRepository contributionRepository,
            com.quorumtrading.application.port.out.ConsensusDecisionRepository consensusDecisionRepository) {
        return new CalculateStrategyPerformanceService(simulationRepository, portfolioRepository, strategyRepository, authorizationService, contributionRepository, consensusDecisionRepository);
    }

    @Produces
    @ApplicationScoped
    public FindTickerUseCase findTickerUseCase(
            LLMRouterAdapter llmRouter,
            BrokerRouter brokerRouter,
            @ConfigProperty(name = "quorumtrading.llm.defaults.research") String researchModel,
            RecordLLMUsageUseCase recordLLMUsageUseCase,
            com.quorumtrading.application.port.out.DividendInfoPort dividendInfoPort) {
        // ':online' is the web-search decoration, applied here because ticker lookup always wants it.
        return new FindTickerService(llmRouter, brokerRouter, researchModel + ":online", recordLLMUsageUseCase, dividendInfoPort);
    }

    /**
     * Reports which indicators a backtest cannot compute from the history stored
     * before its start date, so the shortfall is stated at creation rather than
     * discovered inside agent reasoning after the run has been paid for.
     */
    @Produces
    @ApplicationScoped
    public com.quorumtrading.application.service.PriceHistoryWarmupInspector priceHistoryWarmupInspector(
            MarketEventRepository marketEventRepository) {
        return new com.quorumtrading.application.service.PriceHistoryWarmupInspector(marketEventRepository);
    }

    // Simulation Use Cases

    @Produces
    @ApplicationScoped
    public CreateSimulationUseCase createSimulationUseCase(
            SimulationRepository simulationRepository,
            StrategyRepository strategyRepository,
            PortfolioRepository portfolioRepository,
            BrokerAccountRepository brokerAccountRepository,
            EventPublisher<com.quorumtrading.application.event.SimulationCreatedEvent> simulationCreatedEventPublisher,
            AuthorizationService authorizationService) {
        return new CreateSimulationService(simulationRepository, strategyRepository, portfolioRepository, brokerAccountRepository, simulationCreatedEventPublisher, authorizationService);
    }

    @Produces
    @ApplicationScoped
    public StartSimulationUseCase startSimulationUseCase(
            SimulationRepository simulationRepository,
            StrategyRepository strategyRepository,
            PortfolioRepository portfolioRepository,
            SimulationExecutor simulationExecutor,
            AuthorizationService authorizationService,
            com.quorumtrading.application.port.out.AsyncExecutionPort asyncExecutionPort) {
        return new StartSimulationService(simulationRepository, strategyRepository, portfolioRepository, simulationExecutor, authorizationService, asyncExecutionPort);
    }

    @Produces
    @ApplicationScoped
    public GetSimulationUseCase getSimulationUseCase(SimulationRepository simulationRepository, StrategyRepository strategyRepository, AuthorizationService authorizationService) {
        return new GetSimulationService(simulationRepository, strategyRepository, authorizationService);
    }

    @Produces
    @ApplicationScoped
    public com.quorumtrading.application.usecase.health.GetSimulationHealthUseCase getSimulationHealthUseCase(
            SimulationRepository simulationRepository,
            StrategyRepository strategyRepository,
            com.quorumtrading.application.port.out.ConsensusDecisionRepository consensusDecisionRepository,
            AuthorizationService authorizationService) {
        return new com.quorumtrading.application.service.SimulationHealthService(
                simulationRepository, strategyRepository, consensusDecisionRepository, authorizationService);
    }

    @Produces
    @ApplicationScoped
    public ListSimulationsUseCase listSimulationsUseCase(SimulationRepository simulationRepository) {
        return new ListSimulationsService(simulationRepository);
    }

    @Produces
    @ApplicationScoped
    public DeleteSimulationUseCase deleteSimulationUseCase(SimulationRepository simulationRepository, StrategyRepository strategyRepository, AuthorizationService authorizationService, BrokerAccountRepository brokerAccountRepository) {
        return new DeleteSimulationService(simulationRepository, strategyRepository, authorizationService, brokerAccountRepository);
    }

    @Produces
    @ApplicationScoped
    public DeletePausedSimulationsUseCase deletePausedSimulationsUseCase(SimulationRepository simulationRepository, StrategyRepository strategyRepository, AuthorizationService authorizationService) {
        return new DeletePausedSimulationsService(simulationRepository, strategyRepository, authorizationService);
    }

    @Produces
    @ApplicationScoped
    public com.quorumtrading.application.usecase.ReorderQueueUseCase reorderQueueUseCase(SimulationRepository simulationRepository, StrategyRepository strategyRepository, AuthorizationService authorizationService) {
        return new com.quorumtrading.application.usecase.ReorderQueueUseCase(simulationRepository, strategyRepository, authorizationService);
    }

    @Produces
    @ApplicationScoped
    public PauseSimulationUseCase pauseSimulationUseCase(
            SimulationRepository simulationRepository,
            StrategyRepository strategyRepository,
            AuthorizationService authorizationService,
            com.quorumtrading.application.service.BrokerRouter brokerRouter) {
        return new PauseSimulationService(simulationRepository, strategyRepository, authorizationService, brokerRouter);
    }

    @Produces
    @ApplicationScoped
    public com.quorumtrading.application.usecase.UpdateSimulationNotificationPreferenceUseCase
            updateSimulationNotificationPreferenceUseCase(SimulationRepository simulationRepository) {
        return new com.quorumtrading.application.service.UpdateSimulationNotificationPreferenceService(simulationRepository);
    }

    @Produces
    @ApplicationScoped
    public ResumeSimulationUseCase resumeSimulationUseCase(
            SimulationRepository simulationRepository,
            StrategyRepository strategyRepository,
            SimulationExecutor simulationExecutor,
            AsyncExecutionPort asyncExecutionPort,
            com.quorumtrading.application.service.AuthorizationService authorizationService) {
        return new ResumeSimulationService(simulationRepository, strategyRepository,
            simulationExecutor, asyncExecutionPort, authorizationService);
    }

    @Produces
    @ApplicationScoped
    public TriggerScheduledConsensusUseCase triggerScheduledConsensusUseCase(
            ReachConsensusUseCase reachConsensusUseCase,
            SimulationRepository simulationRepository,
            StrategyRepository strategyRepository,
            PortfolioRepository portfolioRepository,
            MarketEventRepository marketEventRepository,
            ConsensusDecisionRepository consensusDecisionRepository,
            StrategyParser strategyParser,
            ManagedExecutor managedExecutor) {
        return new TriggerScheduledConsensusService(
                reachConsensusUseCase,
                simulationRepository,
                strategyRepository,
                portfolioRepository,
                marketEventRepository,
                consensusDecisionRepository,
                strategyParser,
                managedExecutor
        );
    }

    @Produces
    @ApplicationScoped
    public EvaluatePortfolioValueUseCase evaluatePortfolioValueUseCase(
            SimulationRepository simulationRepository,
            PortfolioRepository portfolioRepository,
            MarketEventRepository marketEventRepository,
            com.quorumtrading.application.service.BrokerRouter brokerRouter) {
        return new EvaluatePortfolioValueService(
                simulationRepository,
                portfolioRepository,
                marketEventRepository,
                brokerRouter
        );
    }

    @Produces
    @ApplicationScoped
    public EndSimulationUseCase endSimulationUseCase(
            SimulationRepository simulationRepository,
            PortfolioRepository portfolioRepository,
            EventPublisher<com.quorumtrading.application.event.SimulationCompletedEvent> simulationCompletedEventPublisher,
            BrokerAccountRepository brokerAccountRepository,
            com.quorumtrading.application.port.out.SimulationContributionRepository contributionRepository,
            // Read here rather than in the service: the application layer must stay free of
            // MicroProfile Config (ArchUnit enforces it), so the producer converts and injects.
            @ConfigProperty(name = "quorumtrading.scheduler.end-simulation.lease-stale-after",
                            defaultValue = "20m") String leaseStaleAfter,
            @ConfigProperty(name = "quorumtrading.scheduler.end-simulation.stall-grace",
                            defaultValue = "30m") String stallGrace,
            @ConfigProperty(name = "quorumtrading.scheduler.end-simulation.observation-gap-reset",
                            defaultValue = "20m") String observationGapReset,
            @ConfigProperty(name = "quorumtrading.scheduler.end-simulation.stall-action",
                            defaultValue = "pause") String stallAction) {
        return new EndSimulationService(
                simulationRepository,
                portfolioRepository,
                simulationCompletedEventPublisher,
                brokerAccountRepository,
                contributionRepository,
                parseDuration(leaseStaleAfter),
                parseDuration(stallGrace),
                parseDuration(observationGapReset),
                stallAction,
                java.time.Clock.systemUTC());
    }

    /**
     * Parse a Quarkus-style duration ("20m", "2h", "40s", or an ISO-8601 "PT30M").
     *
     * <p>Quarkus accepts the shorthand in {@code @Scheduled(every = …)}, so the neighbouring
     * interval key is written that way and these must match it — a config file where two adjacent
     * durations use different syntaxes is a trap.</p>
     */
    private static java.time.Duration parseDuration(String value) {
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Duration must not be empty");
        }
        if (trimmed.startsWith("P") || trimmed.startsWith("p")
                || trimmed.startsWith("-P") || trimmed.startsWith("-p")) {
            return java.time.Duration.parse(trimmed);
        }
        return java.time.Duration.parse("PT" + trimmed);
    }

    // Position Management Use Cases

    @Produces
    @ApplicationScoped
    public SellPositionUseCase sellPositionUseCase(
            SimulationRepository simulationRepository,
            StrategyRepository strategyRepository,
            BrokerRouter brokerRouter,
            UpdatePortfolioWithMarketValueUseCase updatePortfolioWithMarketValueUseCase,
            AuthorizationService authorizationService) {
        return new SellPositionService(simulationRepository, strategyRepository, brokerRouter, updatePortfolioWithMarketValueUseCase, authorizationService);
    }

    @Produces
    @ApplicationScoped
    public LiquidateAllPositionsUseCase liquidateAllPositionsUseCase(
            SimulationRepository simulationRepository,
            StrategyRepository strategyRepository,
            BrokerRouter brokerRouter,
            UpdatePortfolioWithMarketValueUseCase updatePortfolioWithMarketValueUseCase,
            AuthorizationService authorizationService) {
        return new LiquidateAllPositionsService(simulationRepository, strategyRepository, brokerRouter, updatePortfolioWithMarketValueUseCase, authorizationService);
    }

    @Produces
    @ApplicationScoped
    public ForceEndSimulationUseCase forceEndSimulationUseCase(
            SimulationRepository simulationRepository,
            StrategyRepository strategyRepository,
            LiquidateAllPositionsUseCase liquidateAllPositionsUseCase,
            PortfolioRepository portfolioRepository,
            EventPublisher<com.quorumtrading.application.event.SimulationCompletedEvent> simulationCompletedEventPublisher,
            BrokerAccountRepository brokerAccountRepository,
            AuthorizationService authorizationService,
            com.quorumtrading.application.port.out.SimulationContributionRepository contributionRepository) {
        return new ForceEndSimulationService(simulationRepository, strategyRepository, liquidateAllPositionsUseCase, portfolioRepository, simulationCompletedEventPublisher, brokerAccountRepository, authorizationService, contributionRepository);
    }

    // Benchmark Use Cases

    @Produces
    @ApplicationScoped
    public DeleteBenchmarkUseCase deleteBenchmarkUseCase(BenchmarkRepository benchmarkRepository, AuthorizationService authorizationService) {
        return new DeleteBenchmarkService(benchmarkRepository, authorizationService);
    }

    @Produces
    @ApplicationScoped
    public AddSimulationToBenchmarkUseCase addSimulationToBenchmarkUseCase(
            BenchmarkRepository benchmarkRepository,
            SimulationRepository simulationRepository,
            StrategyRepository strategyRepository,
            AuthorizationService authorizationService) {
        return new AddSimulationToBenchmarkService(benchmarkRepository, simulationRepository, strategyRepository, authorizationService);
    }

    @Produces
    @ApplicationScoped
    public CreateBenchmarkUseCase createBenchmarkUseCase(
            StrategyRepository strategyRepository,
            BenchmarkRepository benchmarkRepository,
            SimulationRepository simulationRepository,
            EventPublisher<com.quorumtrading.application.event.SimulationCreatedEvent> simulationCreatedEventPublisher,
            com.quorumtrading.application.usecase.brokeraccount.CreateBrokerAccountUseCase createBrokerAccountUseCase,
            BrokerAccountRepository brokerAccountRepository,
            AuthorizationService authorizationService) {
        return new CreateBenchmarkService(
                strategyRepository,
                benchmarkRepository,
                simulationRepository,
                simulationCreatedEventPublisher,
                createBrokerAccountUseCase,
                brokerAccountRepository,
                authorizationService
        );
    }

    @Produces
    @ApplicationScoped
    public com.quorumtrading.application.service.BuyAndHoldOverlayCalculator buyAndHoldOverlayCalculator(
            com.quorumtrading.application.port.out.HistoricalPricePort historicalPricePort,
            BackFillTickerPriceDataUseCase backFillTickerPriceDataUseCase) {
        return new com.quorumtrading.application.service.BuyAndHoldOverlayCalculator(
                historicalPricePort, backFillTickerPriceDataUseCase);
    }

    @Produces
    @ApplicationScoped
    public CalculateBenchmarkOverlaysUseCase calculateBenchmarkOverlaysUseCase(
            com.quorumtrading.application.port.out.BenchmarkRepository benchmarkRepository,
            SimulationRepository simulationRepository,
            AuthorizationService authorizationService,
            com.quorumtrading.application.service.BuyAndHoldOverlayCalculator calculator) {
        return new com.quorumtrading.application.service.CalculateBenchmarkOverlaysService(
                benchmarkRepository, simulationRepository, authorizationService, calculator);
    }

    @Produces
    @ApplicationScoped
    public com.quorumtrading.application.usecase.BuildBenchmarkLeaderboardUseCase buildBenchmarkLeaderboardUseCase(
            com.quorumtrading.application.port.out.BenchmarkRepository benchmarkRepository,
            SimulationRepository simulationRepository,
            StrategyRepository strategyRepository,
            com.quorumtrading.application.port.out.PortfolioRepository portfolioRepository,
            com.quorumtrading.application.port.out.SimulationContributionRepository contributionRepository,
            com.quorumtrading.application.port.out.ConsensusDecisionRepository consensusDecisionRepository,
            com.quorumtrading.application.port.out.BrokerAccountRepository brokerAccountRepository) {
        return new com.quorumtrading.application.service.BuildBenchmarkLeaderboardService(
                benchmarkRepository, simulationRepository, strategyRepository, portfolioRepository,
                contributionRepository, consensusDecisionRepository, brokerAccountRepository);
    }

    // Challenge Use Cases

    @Produces
    @ApplicationScoped
    public com.quorumtrading.application.usecase.BuildChallengeLeaderboardUseCase buildChallengeLeaderboardUseCase(
            com.quorumtrading.application.port.out.ChallengeRepository challengeRepository,
            SimulationRepository simulationRepository,
            StrategyRepository strategyRepository,
            com.quorumtrading.application.port.out.UserRepository userRepository,
            com.quorumtrading.application.port.out.PortfolioRepository portfolioRepository,
            com.quorumtrading.application.port.out.SimulationContributionRepository contributionRepository) {
        return new com.quorumtrading.application.service.BuildChallengeLeaderboardService(
                challengeRepository, simulationRepository, strategyRepository, userRepository,
                portfolioRepository, contributionRepository);
    }

    @Produces
    @ApplicationScoped
    public com.quorumtrading.application.usecase.CreateChallengeUseCase createChallengeUseCase(
            com.quorumtrading.application.port.out.ChallengeRepository challengeRepository,
            com.quorumtrading.application.port.out.UserRepository userRepository) {
        return new com.quorumtrading.application.service.CreateChallengeService(challengeRepository, userRepository);
    }

    @Produces
    @ApplicationScoped
    public com.quorumtrading.application.usecase.JoinChallengeUseCase joinChallengeUseCase(
            com.quorumtrading.application.port.out.ChallengeRepository challengeRepository,
            SimulationRepository simulationRepository,
            StrategyRepository strategyRepository,
            CreateSimulationUseCase createSimulationUseCase,
            AuthorizationService authorizationService) {
        return new com.quorumtrading.application.service.JoinChallengeService(
                challengeRepository, simulationRepository, strategyRepository, createSimulationUseCase, authorizationService);
    }

    @Produces
    @ApplicationScoped
    public com.quorumtrading.application.usecase.DeleteChallengeUseCase deleteChallengeUseCase(
            com.quorumtrading.application.port.out.ChallengeRepository challengeRepository,
            SimulationRepository simulationRepository,
            com.quorumtrading.application.port.out.UserRepository userRepository) {
        return new com.quorumtrading.application.service.DeleteChallengeService(
                challengeRepository, simulationRepository, userRepository);
    }

    @Produces
    @ApplicationScoped
    public com.quorumtrading.application.usecase.CalculateSimulationOverlaysUseCase calculateSimulationOverlaysUseCase(
            SimulationRepository simulationRepository,
            StrategyRepository strategyRepository,
            AuthorizationService authorizationService,
            com.quorumtrading.application.service.BuyAndHoldOverlayCalculator calculator) {
        return new com.quorumtrading.application.service.CalculateSimulationOverlaysService(
                simulationRepository, strategyRepository, authorizationService, calculator);
    }

    @Produces
    @ApplicationScoped
    public GetTickerPerformanceUseCase getTickerPerformanceUseCase(
            com.quorumtrading.application.port.out.TickerPerformancePort tickerPerformancePort,
            com.quorumtrading.application.port.out.DividendInfoPort dividendInfoPort) {
        return new com.quorumtrading.application.service.GetTickerPerformanceService(tickerPerformancePort, dividendInfoPort);
    }

    @Produces
    @ApplicationScoped
    public com.quorumtrading.application.usecase.GetDividendInfoUseCase getDividendInfoUseCase(
            com.quorumtrading.application.port.out.DividendInfoPort dividendInfoPort) {
        return new com.quorumtrading.application.service.GetDividendInfoService(dividendInfoPort);
    }

    @Produces
    @ApplicationScoped
    public CompareSimulationsUseCase compareSimulationsUseCase(
            SimulationRepository simulationRepository,
            StrategyRepository strategyRepository,
            BenchmarkRepository benchmarkRepository,
            AuthorizationService authorizationService) {
        return new CompareSimulationsService(simulationRepository, strategyRepository, benchmarkRepository, authorizationService);
    }

    @Produces
    @ApplicationScoped
    public ComposeBenchmarkUseCase composeBenchmarkUseCase(
            BenchmarkRepository benchmarkRepository,
            SimulationRepository simulationRepository,
            StrategyRepository strategyRepository,
            AuthorizationService authorizationService) {
        return new ComposeBenchmarkService(benchmarkRepository, simulationRepository, strategyRepository, authorizationService);
    }

    @Produces
    @ApplicationScoped
    public RemoveSimulationFromBenchmarkUseCase removeSimulationFromBenchmarkUseCase(
            SimulationRepository simulationRepository,
            StrategyRepository strategyRepository,
            AuthorizationService authorizationService) {
        return new RemoveSimulationFromBenchmarkService(simulationRepository, strategyRepository, authorizationService);
    }

    // Trade Execution Use Cases

    @Produces
    @ApplicationScoped
    @jakarta.enterprise.inject.Typed(com.quorumtrading.application.service.ExecuteConsensusDecisionService.class)
    public com.quorumtrading.application.service.ExecuteConsensusDecisionService executeConsensusDecisionService(
            com.quorumtrading.application.service.BrokerRouter brokerRouter,
            PortfolioRepository portfolioRepository,
            ConsensusDecisionRepository consensusDecisionRepository,
            MarketEventRepository marketEventRepository,
            EventPublisher<com.quorumtrading.application.event.TradeExecutionEvent> tradeExecutionEventPublisher,
            EventPublisher<com.quorumtrading.application.event.PortfolioChangeEvent> portfolioChangeEventPublisher,
            com.quorumtrading.application.config.TradingLimitsConfig tradingLimitsConfig,
            SimulationRepository simulationRepository,
            StrategyRepository strategyRepository) {
        return new ExecuteConsensusDecisionService(
                brokerRouter,
                portfolioRepository,
                consensusDecisionRepository,
                marketEventRepository,
                tradeExecutionEventPublisher,
                portfolioChangeEventPublisher,
                tradingLimitsConfig,
                simulationRepository,
                strategyRepository,
                new com.quorumtrading.model.orchestration.StrategyConfigParser(),
                brokerRouter
        );
    }

    @Produces
    @ApplicationScoped
    public ExecuteConsensusDecisionUseCase executeConsensusDecisionUseCase(
            com.quorumtrading.application.service.ExecuteConsensusDecisionService service) {
        return service;
    }

    // Query Use Cases

    @Produces
    @ApplicationScoped
    public QueryPortfolioSnapshotsUseCase queryPortfolioSnapshotsUseCase(PortfolioRepository portfolioRepository, SimulationRepository simulationRepository, StrategyRepository strategyRepository, AuthorizationService authorizationService) {
        return new QueryPortfolioSnapshotsService(portfolioRepository, simulationRepository, strategyRepository, authorizationService);
    }

    @Produces
    @ApplicationScoped
    public QueryLLMDecisionsUseCase queryLLMDecisionsUseCase(LLMDecisionRepository llmDecisionRepository,
            SimulationRepository simulationRepository, StrategyRepository strategyRepository,
            AuthorizationService authorizationService) {
        return new QueryLLMDecisionsService(llmDecisionRepository, simulationRepository,
                strategyRepository, authorizationService);
    }

    @Produces
    @ApplicationScoped
    public QueryConsensusDecisionsUseCase queryConsensusDecisionsUseCase(ConsensusDecisionRepository consensusDecisionRepository, SimulationRepository simulationRepository, StrategyRepository strategyRepository, AuthorizationService authorizationService) {
        return new QueryConsensusDecisionsService(consensusDecisionRepository, simulationRepository, strategyRepository, authorizationService);
    }

    @Produces
    @ApplicationScoped
    public QueryMarketEventsUseCase queryMarketEventsUseCase(MarketEventRepository marketEventRepository) {
        return new QueryMarketEventsService(marketEventRepository);
    }

    @Produces
    @ApplicationScoped
    public GetSimulationChartDataUseCase getSimulationChartDataUseCase(PortfolioRepository portfolioRepository, SimulationRepository simulationRepository, StrategyRepository strategyRepository, AuthorizationService authorizationService) {
        return new GetSimulationChartDataService(portfolioRepository, simulationRepository, strategyRepository, authorizationService);
    }

    @Produces
    @ApplicationScoped
    public BackFillTickerPriceDataUseCase backFillTickerPriceDataUseCase(
            MarketDataPortDiscovery marketDataPortDiscovery,
            MarketEventRepository marketEventRepository) {
        // Return pure application service - each repository call has its own transaction
        return new BackFillTickerPriceDataService(
                marketDataPortDiscovery,
                marketEventRepository
        );
    }

    /**
     * Market-data counterpart to {@code GetLlmProviderStatusUseCase}: which price providers exist,
     * which are switched on, which hold credentials, and whether they answer a live call.
     */
    @Produces
    @ApplicationScoped
    public com.quorumtrading.application.usecase.marketdata.GetMarketDataStatusUseCase getMarketDataStatusUseCase(
            MarketDataPortDiscovery marketDataPortDiscovery) {
        return new com.quorumtrading.application.service.GetMarketDataStatusService(marketDataPortDiscovery);
    }

    @Produces
    @ApplicationScoped
    public GetConsensusWithAgentsUseCase getConsensusWithAgentsUseCase(
            ConsensusDecisionRepository consensusDecisionRepository,
            LLMDecisionRepository llmDecisionRepository,
            SimulationRepository simulationRepository,
            StrategyRepository strategyRepository,
            AuthorizationService authorizationService,
            LlmParseFailureRepository llmParseFailureRepository) {
        return new GetConsensusWithAgentsUseCase(consensusDecisionRepository, llmDecisionRepository, simulationRepository, strategyRepository, authorizationService, llmParseFailureRepository);
    }

    @Produces
    @ApplicationScoped
    public TickerRegistryService tickerRegistryService(StrategyRepository strategyRepository) {
        return new TickerRegistryService(strategyRepository);
    }

    @Produces
    @ApplicationScoped
    public TriggerDataCollectionUseCase triggerDataCollectionUseCase(
            Instance<MarketDataPort> marketDataPorts,
            MarketEventRepository marketEventRepository,
            RateLimitTracker rateLimitTracker,
            TickerRegistryService tickerRegistryService) {
        // Convert CDI Instance to List
        return new TriggerDataCollectionService(
            marketDataPorts.stream().toList(),
            marketEventRepository,
            rateLimitTracker,
            tickerRegistryService);
    }

    @Produces
    @ApplicationScoped
    public EventPublisher<com.quorumtrading.application.event.LLMDecisionCreatedEvent> llmDecisionEventPublisher(
            jakarta.enterprise.event.Event<com.quorumtrading.application.event.LLMDecisionCreatedEvent> cdiEvent) {
        return new com.quorumtrading.bootstrap.event.CdiEventPublisher<>(cdiEvent);
    }

    @Produces
    @ApplicationScoped
    public EventPublisher<com.quorumtrading.application.event.ConsensusStartedEvent> consensusStartedEventPublisher(
            jakarta.enterprise.event.Event<com.quorumtrading.application.event.ConsensusStartedEvent> cdiEvent) {
        return new com.quorumtrading.bootstrap.event.CdiEventPublisher<>(cdiEvent);
    }

    @Produces
    @ApplicationScoped
    public EventPublisher<com.quorumtrading.application.event.ConsensusSkippedEvent> consensusSkippedEventPublisher(
            jakarta.enterprise.event.Event<com.quorumtrading.application.event.ConsensusSkippedEvent> cdiEvent) {
        return new com.quorumtrading.bootstrap.event.CdiEventPublisher<>(cdiEvent);
    }

    @Produces
    @ApplicationScoped
    public EventPublisher<com.quorumtrading.application.event.TradeExecutionEvent> tradeExecutionEventPublisher(
            jakarta.enterprise.event.Event<com.quorumtrading.application.event.TradeExecutionEvent> cdiEvent) {
        return new com.quorumtrading.bootstrap.event.CdiEventPublisher<>(cdiEvent);
    }

    @Produces
    @ApplicationScoped
    public EventPublisher<com.quorumtrading.application.event.PortfolioChangeEvent> portfolioChangeEventPublisher(
            jakarta.enterprise.event.Event<com.quorumtrading.application.event.PortfolioChangeEvent> cdiEvent) {
        return new com.quorumtrading.bootstrap.event.CdiEventPublisher<>(cdiEvent);
    }

    @Produces
    @ApplicationScoped
    public EventPublisher<com.quorumtrading.application.event.SimulationCreatedEvent> simulationCreatedEventPublisher(
            jakarta.enterprise.event.Event<com.quorumtrading.application.event.SimulationCreatedEvent> cdiEvent) {
        // Use synchronous fire() so backfill check completes before create_simulation returns.
        // This prevents the MCP race condition where start_simulation is called before
        // BackfillOrchestratorHandler has a chance to set WAITING_FOR_DATA status.
        return new com.quorumtrading.bootstrap.event.CdiEventPublisher<>(cdiEvent);
    }

    @Produces
    @ApplicationScoped
    public EventPublisher<com.quorumtrading.application.event.ResearchExecutionCompleted> researchExecutionCompletedEventPublisher(
            jakarta.enterprise.event.Event<com.quorumtrading.application.event.ResearchExecutionCompleted> cdiEvent) {
        return new com.quorumtrading.bootstrap.adapter.CdiEventPublisher<>(cdiEvent);
    }

    @Produces
    @ApplicationScoped
    public EventPublisher<com.quorumtrading.application.event.BudgetWarningEvent> budgetWarningEventPublisher(
            jakarta.enterprise.event.Event<com.quorumtrading.application.event.BudgetWarningEvent> cdiEvent) {
        return new com.quorumtrading.bootstrap.event.CdiEventPublisher<>(cdiEvent);
    }

    @Produces
    @ApplicationScoped
    public EventPublisher<com.quorumtrading.application.event.BudgetDepletedEvent> budgetDepletedEventPublisher(
            jakarta.enterprise.event.Event<com.quorumtrading.application.event.BudgetDepletedEvent> cdiEvent) {
        return new com.quorumtrading.bootstrap.event.CdiEventPublisher<>(cdiEvent);
    }

    @Produces
    @ApplicationScoped
    public EventPublisher<com.quorumtrading.application.event.DataGapDetectedEvent> dataGapDetectedEventPublisher(
            jakarta.enterprise.event.Event<com.quorumtrading.application.event.DataGapDetectedEvent> cdiEvent) {
        return new com.quorumtrading.bootstrap.adapter.CdiEventPublisher<>(cdiEvent);
    }

    // Consensus components (application.service.consensus) — plain POJOs, produced here
    // per the CDI convention. Extracted from the former 28-parameter ReachConsensusService.

    @Produces
    @ApplicationScoped
    public com.quorumtrading.application.service.consensus.AgentInvoker agentInvoker(
            Instance<Agent> agentInstances,
            @Identifier("agent-orchestrator") org.eclipse.microprofile.context.ManagedExecutor managedExecutor,
            LLMDecisionRepository llmDecisionRepository,
            LLMRouterAdapter llmRouter,
            com.quorumtrading.application.config.TradingLimitsConfig tradingLimitsConfig,
            ModelResolutionService modelResolutionService,
            LlmParseFailureRepository llmParseFailureRepository,
            @ConfigProperty(name = "quorumtrading.orchestrator.a2ui-enabled", defaultValue = "false") boolean a2uiEnabled,
            @ConfigProperty(name = "quorumtrading.consensus.agent-timeout-seconds", defaultValue = "30") int agentTimeoutSeconds) {
        // Convert CDI Instance to List
        List<Agent> agents = agentInstances.stream().toList();
        return new com.quorumtrading.application.service.consensus.AgentInvoker(
            agents,
            managedExecutor,
            llmDecisionRepository,
            llmRouter,
            tradingLimitsConfig,
            modelResolutionService,
            llmParseFailureRepository,
            a2uiEnabled,
            agentTimeoutSeconds
        );
    }

    @Produces
    @ApplicationScoped
    public com.quorumtrading.application.service.consensus.ConsensusContextBuilder consensusContextBuilder(
            MarketEventRepository marketEventRepository,
            IndicatorEnrichmentService indicatorEnrichmentService,
            Instance<FinancialDataPort> financialDataPorts,
            PortfolioRepository portfolioRepository,
            ConsensusDecisionRepository consensusDecisionRepository,
            UserBudgetRepository userBudgetRepository,
            SystemModelDefaultsProvider systemModelDefaultsProvider,
            com.quorumtrading.application.port.out.ActiveLlmEnginePort activeLlmEnginePort) {
        FinancialDataPort financialDataPort = financialDataPorts.stream().findFirst().orElse(null);
        return new com.quorumtrading.application.service.consensus.ConsensusContextBuilder(
            marketEventRepository,
            indicatorEnrichmentService,
            financialDataPort,
            portfolioRepository,
            consensusDecisionRepository,
            userBudgetRepository,
            systemModelDefaultsProvider,
            activeLlmEnginePort
        );
    }

    @Produces
    @ApplicationScoped
    public com.quorumtrading.application.service.consensus.ConsensusOutcomeRecorder consensusOutcomeRecorder(
            LLMDecisionRepository llmDecisionRepository,
            ConsensusDecisionRepository consensusDecisionRepository,
            EventPublisher<com.quorumtrading.application.event.LLMDecisionCreatedEvent> llmDecisionEventPublisher,
            com.quorumtrading.application.usecase.budget.RecordLLMUsageUseCase recordLLMUsageUseCase) {
        return new com.quorumtrading.application.service.consensus.ConsensusOutcomeRecorder(
            llmDecisionRepository,
            consensusDecisionRepository,
            llmDecisionEventPublisher,
            recordLLMUsageUseCase
        );
    }

    @Produces
    @ApplicationScoped
    public com.quorumtrading.application.service.consensus.ConsensusTradeCoordinator consensusTradeCoordinator(
            com.quorumtrading.application.service.BrokerRouter brokerRouter,
            ExecuteConsensusDecisionUseCase executeConsensusDecisionUseCase,
            com.quorumtrading.application.usecase.notification.DispatchNotificationUseCase dispatchNotificationUseCase) {
        return new com.quorumtrading.application.service.consensus.ConsensusTradeCoordinator(
            brokerRouter,
            executeConsensusDecisionUseCase,
            dispatchNotificationUseCase
        );
    }

    @Produces
    @ApplicationScoped
    public com.quorumtrading.application.service.consensus.DataGapCircuitBreaker dataGapCircuitBreaker(
            SimulationRepository simulationRepository,
            EventPublisher<com.quorumtrading.application.event.DataGapDetectedEvent> dataGapDetectedEventPublisher,
            EventPublisher<com.quorumtrading.application.event.ConsensusSkippedEvent> consensusSkippedEventPublisher,
            @ConfigProperty(name = "quorumtrading.circuit-breaker.consecutive-skip-threshold", defaultValue = "5") int consecutiveSkipThreshold,
            @ConfigProperty(name = "quorumtrading.circuit-breaker.percentage-skip-threshold", defaultValue = "50") int percentageSkipThreshold) {
        return new com.quorumtrading.application.service.consensus.DataGapCircuitBreaker(
            simulationRepository,
            dataGapDetectedEventPublisher,
            consensusSkippedEventPublisher,
            consecutiveSkipThreshold,
            percentageSkipThreshold
        );
    }

    @Produces
    @ApplicationScoped
    @jakarta.enterprise.inject.Typed(com.quorumtrading.application.service.ReachConsensusService.class)
    public com.quorumtrading.application.service.ReachConsensusService reachConsensusService(
            SimulationRepository simulationRepository,
            StrategyRepository strategyRepository,
            StrategyParser strategyParser,
            com.quorumtrading.application.usecase.budget.RecordLLMUsageUseCase recordLLMUsageUseCase,
            EventPublisher<com.quorumtrading.application.event.ConsensusStartedEvent> consensusStartedEventPublisher,
            com.quorumtrading.application.service.consensus.AgentInvoker agentInvoker,
            com.quorumtrading.application.service.consensus.ConsensusContextBuilder consensusContextBuilder,
            com.quorumtrading.application.service.consensus.ConsensusOutcomeRecorder consensusOutcomeRecorder,
            com.quorumtrading.application.service.consensus.ConsensusTradeCoordinator consensusTradeCoordinator,
            com.quorumtrading.application.service.consensus.DataGapCircuitBreaker dataGapCircuitBreaker,
            @Identifier("agent-orchestrator") org.eclipse.microprofile.context.ManagedExecutor managedExecutor,
            @ConfigProperty(name = "quorumtrading.consensus.pipeline-timeout-seconds", defaultValue = "45") int pipelineTimeoutSeconds) {
        return new com.quorumtrading.application.service.ReachConsensusService(
            simulationRepository,
            strategyRepository,
            strategyParser,
            recordLLMUsageUseCase,
            consensusStartedEventPublisher,
            agentInvoker,
            consensusContextBuilder,
            consensusOutcomeRecorder,
            consensusTradeCoordinator,
            dataGapCircuitBreaker,
            managedExecutor,
            pipelineTimeoutSeconds
        );
    }

    @Produces
    @ApplicationScoped
    public com.quorumtrading.application.usecase.ReachConsensusUseCase reachConsensusUseCase(
            com.quorumtrading.application.service.ReachConsensusService service) {
        return service;
    }

    @Produces
    @ApplicationScoped
    public com.quorumtrading.application.usecase.UpdatePortfolioWithMarketValueUseCase updatePortfolioWithMarketValueUseCase(
            com.quorumtrading.application.service.ExecuteConsensusDecisionService service) {
        return service;
    }

    @Produces
    @ApplicationScoped
    public com.quorumtrading.application.usecase.GetMarketDataContextUseCase getMarketDataContextUseCase(
            SimulationRepository simulationRepository,
            StrategyRepository strategyRepository,
            StrategyParser strategyParser,
            AuthorizationService authorizationService,
            com.quorumtrading.application.service.consensus.ConsensusContextBuilder consensusContextBuilder) {
        return new com.quorumtrading.application.service.consensus.MarketDataContextService(
            simulationRepository,
            strategyRepository,
            strategyParser,
            authorizationService,
            consensusContextBuilder
        );
    }

    // Orchestration Components

    @Produces
    @ApplicationScoped
    public StrategyParser strategyParser() {
        return new StrategyParser();
    }

    @Produces
    @ApplicationScoped
    public IndicatorCalculationService indicatorCalculationService() {
        return new IndicatorCalculationService();
    }

    @Produces
    @ApplicationScoped
    public IndicatorEnrichmentService indicatorEnrichmentService(
            HistoricalPricePort historicalPricePort,
            IndicatorCalculationService calculationService) {
        return new IndicatorEnrichmentService(historicalPricePort, calculationService);
    }

    @Produces
    @ApplicationScoped
    public StrategyTemplateRegistry strategyTemplateRegistry() {
        return new StrategyTemplateRegistry();
    }

    /**
     * Validates model pins against the engine that will actually run them, and supplies the
     * servable alternatives that turn a refusal into something a caller can act on.
     */
    @Produces
    @ApplicationScoped
    public com.quorumtrading.application.service.ModelPinValidator modelPinValidator(
            LLMRouterAdapter llmRouter,
            com.quorumtrading.application.port.out.ActiveLlmEnginePort activeLlmEnginePort) {
        return new com.quorumtrading.application.service.ModelPinValidator(llmRouter, activeLlmEnginePort);
    }

    /** Post-resolution SERVE / SUBSTITUTE / REJECT negotiation against the active engine. */
    @Produces
    @ApplicationScoped
    public com.quorumtrading.application.service.EngineCapabilityService engineCapabilityService(
            LLMRouterAdapter llmRouter,
            com.quorumtrading.application.port.out.ActiveLlmEnginePort activeLlmEnginePort) {
        return new com.quorumtrading.application.service.EngineCapabilityService(llmRouter, activeLlmEnginePort);
    }

    /** Sweeps stored preferences and strategy pins after an engine switch. */
    @Produces
    @ApplicationScoped
    public com.quorumtrading.application.service.RevalidateStoredModelPinsService revalidateStoredModelPinsService(
            UserBudgetRepository userBudgetRepository,
            StrategyRepository strategyRepository,
            com.quorumtrading.application.service.ModelPinValidator modelPinValidator) {
        return new com.quorumtrading.application.service.RevalidateStoredModelPinsService(
                userBudgetRepository, strategyRepository, modelPinValidator);
    }

    /**
     * The one read model every surface renders for "which model actually runs?".
     */
    @Produces
    @ApplicationScoped
    public com.quorumtrading.application.usecase.GetEffectiveModelsUseCase getEffectiveModelsUseCase(
            UserBudgetRepository userBudgetRepository,
            SystemModelDefaultsProvider systemModelDefaultsProvider,
            com.quorumtrading.application.port.out.ActiveLlmEnginePort activeLlmEnginePort,
            com.quorumtrading.application.service.EngineCapabilityService engineCapabilityService,
            LLMRouterAdapter llmRouter) {
        return new com.quorumtrading.application.usecase.GetEffectiveModelsUseCase(
                userBudgetRepository, systemModelDefaultsProvider, activeLlmEnginePort,
                engineCapabilityService, llmRouter);
    }

    /** Engine-aware description of what "fallback" means on the selected engine. */
    @Produces
    @ApplicationScoped
    public com.quorumtrading.application.usecase.DescribeFallbackBehaviourUseCase describeFallbackBehaviourUseCase(
            com.quorumtrading.application.port.out.ActiveLlmEnginePort activeLlmEnginePort,
            @ConfigProperty(name = "quorumtrading.llm.openrouter.model.fallback") java.util.List<String> openRouterChain,
            jakarta.enterprise.inject.Instance<com.quorumtrading.adapter.llm.local.LocalLLMAdapter> localAdapter) {
        // Only INSTALLED tools are shown: a chain entry is not evidence the tool exists, and
        // listing an absent one is the same class of lie as showing a foreign engine's models.
        // The adapter is unresolvable when the CLI engine is disabled, in which case there is no
        // tool chain to describe at all.
        com.quorumtrading.application.usecase.DescribeFallbackBehaviourUseCase.InstalledToolsSupplier installed =
                () -> localAdapter.isResolvable() ? localAdapter.get().installedTools() : java.util.List.of();
        return new com.quorumtrading.application.usecase.DescribeFallbackBehaviourUseCase(
                activeLlmEnginePort, openRouterChain, installed);
    }

    /** Research model resolution on the shared chain — tiers 3-6, per research agent. */
    @Produces
    @ApplicationScoped
    public com.quorumtrading.application.service.ResearchModelResolver researchModelResolver(
            UserBudgetRepository userBudgetRepository,
            SystemModelDefaultsProvider systemModelDefaultsProvider,
            com.quorumtrading.application.port.out.ActiveLlmEnginePort activeLlmEnginePort,
            com.quorumtrading.application.service.EngineCapabilityService engineCapabilityService) {
        return new com.quorumtrading.application.service.ResearchModelResolver(
                userBudgetRepository, systemModelDefaultsProvider, activeLlmEnginePort, engineCapabilityService);
    }

    @Produces
    @ApplicationScoped
    public ModelResolutionService modelResolutionService(
            com.quorumtrading.application.service.EngineCapabilityService engineCapabilityService) {
        return new ModelResolutionService(engineCapabilityService);
    }

    @Produces
    @ApplicationScoped
    public BoundedConsensusEvaluator boundedConsensusEvaluator(
            ReachConsensusUseCase reachConsensusUseCase,
            com.quorumtrading.application.service.consensus.DataGapCircuitBreaker dataGapCircuitBreaker,
            // Context-propagating executor, the same pool the scheduled-consensus service uses for
            // the identical workload — deliberately NOT the agent-orchestrator pool the pipeline
            // fans out onto, so waiting here can never starve the work being waited for.
            ManagedExecutor managedExecutor,
            @ConfigProperty(name = "quorumtrading.consensus.pipeline-timeout-seconds",
                            defaultValue = "45") long pipelineTimeoutSeconds) {
        // Twice the pipeline budget: a backstop for "the engine never answered at all", not a
        // second copy of the pipeline's own timeout policy.
        return new BoundedConsensusEvaluator(reachConsensusUseCase, dataGapCircuitBreaker,
                managedExecutor, java.time.Duration.ofSeconds(2 * pipelineTimeoutSeconds));
    }

    @Produces
    @ApplicationScoped
    public SimulationExecutor simulationExecutor(
            BoundedConsensusEvaluator boundedConsensusEvaluator,
            UpdatePortfolioWithMarketValueUseCase updatePortfolioWithMarketValueUseCase,
            StrategyParser strategyParser,
            StrategyRepository strategyRepository,
            MarketEventRepository marketEventRepository,
            PortfolioRepository portfolioRepository,
            EventPublisher<com.quorumtrading.application.event.ConsensusStartedEvent> consensusStartedEventPublisher,
            BrokerAccountRepository brokerAccountRepository,
            SimulationPortfolioRepository simulationPortfolioRepository,
            BrokerRouter brokerRouter,
            SimulationRepository simulationRepository,
            SimulationLockPort simulationLockPort,
            com.quorumtrading.application.usecase.notification.DispatchNotificationUseCase dispatchNotificationUseCase,
            com.quorumtrading.application.port.out.ConsensusDecisionRepository consensusDecisionRepository) {
        return new SimulationExecutor(
                boundedConsensusEvaluator,
                updatePortfolioWithMarketValueUseCase,
                strategyParser,
                strategyRepository,
                marketEventRepository,
                portfolioRepository,
                consensusStartedEventPublisher,
                brokerAccountRepository,
                simulationPortfolioRepository,
                brokerRouter,
                simulationRepository,
                simulationLockPort,
                dispatchNotificationUseCase,
                consensusDecisionRepository
        );
    }

    @Produces
    @ApplicationScoped
    public MarketResearchOrchestrator marketResearchOrchestrator(
            com.quorumtrading.application.service.ResearchModelResolver researchModelResolver,
            LLMRouterAdapter router,
            StrategyRepository strategyRepository,
            ResearchExecutionRepository executionRepository,
            EventPublisher<com.quorumtrading.application.event.ResearchExecutionCompleted> executionCompletedPublisher,
            EventPublisher<com.quorumtrading.application.event.ResearchAgentStartedEvent> agentStartedPublisher,
            EventPublisher<com.quorumtrading.application.event.ResearchAgentCompletedEvent> agentCompletedPublisher,
            StrategyTemplateRegistry templateRegistry,
            CreateStrategyUseCase createStrategyUseCase,
            ConversationalLLMPort conversationalLLMPort,
            com.quorumtrading.application.chat.ChatToolExecutor chatToolExecutor,
            ManagedExecutor managedExecutor,
            MarketEventRepository marketEventRepository,
            com.quorumtrading.application.usecase.notification.DispatchNotificationUseCase dispatchNotificationUseCase,
            // One key, read through the one chain. The ':online' variant is a decoration applied
            // at call time, not a separate model, so it is derived here rather than configured.
            @ConfigProperty(name = "quorumtrading.llm.defaults.research") String researchModel,
            @ConfigProperty(name = "quorumtrading.llm.research.temperature") double temperature,
            @ConfigProperty(name = "quorumtrading.llm.research.max-tokens") int maxTokens,
            @ConfigProperty(name = "quorumtrading.indicators.enabled", defaultValue = "true") boolean indicatorsEnabled,
            @ConfigProperty(name = "quorumtrading.indicators.template-confidence-threshold", defaultValue = "60") int templateConfidenceThreshold) {
        MarketResearchOrchestrator orchestrator = new MarketResearchOrchestrator(
            router,
            strategyRepository,
            executionRepository,
            executionCompletedPublisher,
            agentStartedPublisher,
            agentCompletedPublisher,
            researchModel,
            researchModel + ":online",
            temperature,
            maxTokens,
            templateRegistry,
            indicatorsEnabled,
            templateConfidenceThreshold,
            createStrategyUseCase,
            conversationalLLMPort,
            chatToolExecutor,
            managedExecutor,
            marketEventRepository
        );
        orchestrator.setDispatchNotificationUseCase(dispatchNotificationUseCase);
        orchestrator.setResearchModelResolver(researchModelResolver);
        return orchestrator;
    }

    @Produces
    @ApplicationScoped
    public ExecuteMarketResearchService executeMarketResearchService(
            MarketResearchRepository repository,
            MarketResearchOrchestrator orchestrator,
            EventPublisher<com.quorumtrading.application.event.ResearchExecutionStartedEvent> executionStartedPublisher,
            EventPublisher<com.quorumtrading.application.event.ResearchExecutionCompleted> executionCompletedPublisher,
            @Identifier("research-executor") ManagedExecutor researchExecutor,
            com.quorumtrading.application.usecase.budget.RecordLLMUsageUseCase recordLLMUsageUseCase,
            AuthorizationService authorizationService) {
        return new ExecuteMarketResearchService(repository, orchestrator, executionStartedPublisher, executionCompletedPublisher, researchExecutor, recordLLMUsageUseCase, authorizationService);
    }

    @Produces
    @ApplicationScoped
    public CreateMarketResearchService createMarketResearchService(
            MarketResearchRepository marketResearchRepository,
            ExecuteMarketResearchUseCase executeMarketResearchUseCase) {
        return new CreateMarketResearchService(marketResearchRepository, executeMarketResearchUseCase);
    }

    @Produces
    @ApplicationScoped
    public UpdateMarketResearchService updateMarketResearchService(
            MarketResearchRepository marketResearchRepository,
            AuthorizationService authorizationService) {
        return new UpdateMarketResearchService(marketResearchRepository, authorizationService);
    }

    @Produces
    @ApplicationScoped
    public GetMarketResearchDetailsService getMarketResearchDetailsService(
            MarketResearchRepository marketResearchRepository,
            ResearchExecutionRepository executionRepository,
            AuthorizationService authorizationService) {
        return new GetMarketResearchDetailsService(marketResearchRepository, executionRepository, authorizationService);
    }

    @Produces
    @ApplicationScoped
    public com.quorumtrading.application.event.listener.ResearchExecutionEventListener autoBacktestService(
            CreateSimulationUseCase createSimulationUseCase,
            StartSimulationUseCase startSimulationUseCase,
            StrategyRepository strategyRepository,
            com.quorumtrading.application.config.AutoBacktestConfig autoBacktestConfig,
            AnalyzeDataAvailabilityUseCase analyzeDataAvailabilityUseCase,
            RequestBackfillUseCase requestBackfillUseCase,
            BackfillJobRepository backfillJobRepository) {
        return new AutoBacktestService(
            createSimulationUseCase,
            startSimulationUseCase,
            strategyRepository,
            autoBacktestConfig,
            analyzeDataAvailabilityUseCase,
            requestBackfillUseCase,
            backfillJobRepository
        );
    }

    @Produces
    @ApplicationScoped
    public PauseMarketResearchService pauseMarketResearchService(
            MarketResearchRepository marketResearchRepository,
            AuthorizationService authorizationService) {
        return new PauseMarketResearchService(marketResearchRepository, authorizationService);
    }

    @Produces
    @ApplicationScoped
    public ResumeMarketResearchService resumeMarketResearchService(
            MarketResearchRepository marketResearchRepository,
            AuthorizationService authorizationService) {
        return new ResumeMarketResearchService(marketResearchRepository, authorizationService);
    }

    // Market Calendar

    @Produces
    @ApplicationScoped
    public com.quorumtrading.model.service.USMarketHolidayCalendar usMarketHolidayCalendar() {
        return new com.quorumtrading.model.service.USMarketHolidayCalendar();
    }

    // Backfill Services
    @Produces
    @ApplicationScoped
    public AnalyzeDataAvailabilityUseCase analyzeDataAvailabilityService(
            MarketEventRepository marketEventRepository,
            com.quorumtrading.model.service.USMarketHolidayCalendar holidayCalendar) {
        return new AnalyzeDataAvailabilityService(marketEventRepository, holidayCalendar);
    }

    @Produces
    @ApplicationScoped
    public QueryBackfillProgressUseCase queryBackfillProgressService(
            BackfillJobRepository backfillJobRepository) {
        return new QueryBackfillProgressService(backfillJobRepository);
    }

    @Produces
    @ApplicationScoped
    public RequestBackfillUseCase requestBackfillService(
            BackfillJobRepository backfillJobRepository,
            AnalyzeDataAvailabilityUseCase analyzeDataAvailabilityUseCase,
            Event<BackfillJobCreatedEvent> jobCreatedEvent) {
        return new RequestBackfillService(backfillJobRepository, analyzeDataAvailabilityUseCase, jobCreatedEvent);
    }

    // Data Horizon Services (POJO application services — CDI wired here)

    @Produces
    @ApplicationScoped
    public DataHorizonProbeService dataHorizonProbeService(
            Instance<MarketDataPort> ports,
            RateLimitTracker rateLimitTracker) {
        return new DataHorizonProbeService(ports.stream().toList(), rateLimitTracker);
    }

    @Produces
    @ApplicationScoped
    public DataHorizonService dataHorizonService(
            MarketDataHorizonRepository marketDataHorizonRepository,
            DataHorizonProbeService probeService,
            @ConfigProperty(name = "quorumtrading.marketdata.horizon.ttl-days", defaultValue = "14") int ttlDays) {
        return new DataHorizonService(marketDataHorizonRepository, probeService, ttlDays);
    }

    @Produces
    @ApplicationScoped
    public ClassifyGapsService classifyGapsService(
            Instance<MarketDataPort> ports,
            DataHorizonService dataHorizonService) {
        return new ClassifyGapsService(ports.stream().toList(), dataHorizonService);
    }

    @Produces
    @ApplicationScoped
    public EventPublisher<com.quorumtrading.application.event.SimulationDataReadyEvent> simulationDataReadyEventPublisher(
            jakarta.enterprise.event.Event<com.quorumtrading.application.event.SimulationDataReadyEvent> cdiEvent) {
        // Use adapter implementation with fireAsync() to support @ObservesAsync handlers
        return new com.quorumtrading.bootstrap.adapter.CdiEventPublisher<>(cdiEvent);
    }

    @Produces
    @ApplicationScoped
    public BackfillOrchestratorService backfillOrchestratorService(
            SimulationRepository simulationRepository,
            StrategyRepository strategyRepository,
            AnalyzeDataAvailabilityUseCase analyzeDataAvailabilityUseCase,
            RequestBackfillUseCase requestBackfillUseCase,
            EventPublisher<com.quorumtrading.application.event.SimulationDataReadyEvent> eventPublisher,
            MarketEventRepository marketEventRepository) {
        return new BackfillOrchestratorService(
            simulationRepository,
            strategyRepository,
            analyzeDataAvailabilityUseCase,
            requestBackfillUseCase,
            eventPublisher,
            marketEventRepository
        );
    }

    @Produces
    @ApplicationScoped
    public TriggerSimulationUseCase triggerSimulationUseCase(
            ScheduledSimulationRepository scheduledSimulationRepository,
            StrategyRepository strategyRepository,
            CreateSimulationUseCase createSimulationUseCase,
            AuthorizationService authorizationService) {
        return new SimulationSchedulerService(
            scheduledSimulationRepository,
            strategyRepository,
            createSimulationUseCase,
            authorizationService
        );
    }

    // Agents
    // Note: LLMRouterAdapter provides intelligent routing across all LLM providers
    // Individual adapters (LocalLLMAdapter, OpenRouterAdapter) are discovered by router

    @Produces
    @ApplicationScoped
    public TechnicalAgent technicalAgent(
            LLMRouterAdapter router,
            @ConfigProperty(name = "quorumtrading.agents.cache.technical.ttl-minutes") long cacheTTLMinutes,
            ModelResolutionService modelResolutionService,
            LlmParseFailureRepository llmParseFailureRepository) {
        return new TechnicalAgent(router, cacheTTLMinutes, modelResolutionService, llmParseFailureRepository);
    }

    @Produces
    @ApplicationScoped
    public FundamentalAgent fundamentalAgent(
            LLMRouterAdapter router,
            @ConfigProperty(name = "quorumtrading.agents.cache.fundamental.ttl-minutes") long cacheTTLMinutes,
            ModelResolutionService modelResolutionService,
            LlmParseFailureRepository llmParseFailureRepository) {
        return new FundamentalAgent(router, cacheTTLMinutes, modelResolutionService, llmParseFailureRepository);
    }

    @Produces
    @ApplicationScoped
    public SentimentAgent sentimentAgent(
            LLMRouterAdapter router,
            @ConfigProperty(name = "quorumtrading.agents.cache.sentiment.ttl-minutes") long cacheTTLMinutes,
            ModelResolutionService modelResolutionService,
            LlmParseFailureRepository llmParseFailureRepository) {
        return new SentimentAgent(router, cacheTTLMinutes, modelResolutionService, llmParseFailureRepository);
    }

    // Phase 4: Strategy Health Checker and Results Analyzer

    @Produces
    @ApplicationScoped
    public StrategyHealthCheckerService strategyHealthCheckerService(
            StrategyRepository strategyRepository,
            @ConfigProperty(name = "quorumtrading.strategy-health.min-return-percent", defaultValue = "-10") java.math.BigDecimal minReturnPercent,
            @ConfigProperty(name = "quorumtrading.strategy-health.consecutive-failures-to-disable", defaultValue = "3") int consecutiveFailures) {
        return new StrategyHealthCheckerService(strategyRepository, minReturnPercent, consecutiveFailures);
    }

    @Produces
    @ApplicationScoped
    public ResultsAnalyzerService resultsAnalyzerService(
            StrategyRepository strategyRepository,
            StrategyHealthCheckerService healthChecker) {
        return new ResultsAnalyzerService(strategyRepository, healthChecker);
    }

    @Produces
    @ApplicationScoped
    public EventPublisher<com.quorumtrading.application.event.SimulationCompletedEvent> simulationCompletedEventPublisher(
            jakarta.enterprise.event.Event<com.quorumtrading.application.event.SimulationCompletedEvent> cdiEvent) {
        return new com.quorumtrading.bootstrap.adapter.CdiEventPublisher<>(cdiEvent);
    }

    @Produces
    @ApplicationScoped
    public EventPublisher<com.quorumtrading.application.event.ResearchExecutionStartedEvent> researchExecutionStartedEventPublisher(
            jakarta.enterprise.event.Event<com.quorumtrading.application.event.ResearchExecutionStartedEvent> cdiEvent) {
        return new com.quorumtrading.bootstrap.adapter.CdiEventPublisher<>(cdiEvent);
    }

    @Produces
    @ApplicationScoped
    public EventPublisher<com.quorumtrading.application.event.ResearchAgentStartedEvent> researchAgentStartedEventPublisher(
            jakarta.enterprise.event.Event<com.quorumtrading.application.event.ResearchAgentStartedEvent> cdiEvent) {
        return new com.quorumtrading.bootstrap.adapter.CdiEventPublisher<>(cdiEvent);
    }

    @Produces
    @ApplicationScoped
    public EventPublisher<com.quorumtrading.application.event.ResearchAgentCompletedEvent> researchAgentCompletedEventPublisher(
            jakarta.enterprise.event.Event<com.quorumtrading.application.event.ResearchAgentCompletedEvent> cdiEvent) {
        return new com.quorumtrading.bootstrap.adapter.CdiEventPublisher<>(cdiEvent);
    }

    @Produces
    @ApplicationScoped
    public EventPublisher<IndicatorUpdateEvent> indicatorUpdateEventPublisher(
            jakarta.enterprise.event.Event<IndicatorUpdateEvent> cdiEvent) {
        return new com.quorumtrading.bootstrap.adapter.CdiEventPublisher<>(cdiEvent);
    }

    @Produces
    @ApplicationScoped
    public BroadcastIndicatorsUseCase broadcastIndicatorsUseCase(
            SimulationRepository simulationRepository,
            StrategyRepository strategyRepository,
            IndicatorEnrichmentService indicatorEnrichmentService,
            HistoricalPricePort historicalPricePort,
            EventPublisher<IndicatorUpdateEvent> indicatorEventPublisher) {
        return new BroadcastIndicatorsUseCase(
            simulationRepository,
            strategyRepository,
            indicatorEnrichmentService,
            historicalPricePort,
            indicatorEventPublisher
        );
    }

    // Named ManagedExecutor for LLM call isolation
    // Allows LLMRouterAdapter to run LLM calls without blocking caller threads

    @Produces
    @ApplicationScoped
    @Identifier("llm-executor")
    public ManagedExecutor llmExecutor() {
        return ManagedExecutor.builder()
                .propagated(ThreadContext.CDI)
                .maxAsync(100)
                .maxQueued(500)
                .build();
    }

    public void disposeLlmExecutor(
            @Disposes @Identifier("llm-executor") ManagedExecutor executor) {
        executor.shutdownNow();
    }

    // Named ManagedExecutor for agent orchestration
    // Provides better visibility in ThreadPool health check metrics

    @Produces
    @ApplicationScoped
    @Identifier("agent-orchestrator")
    public ManagedExecutor agentOrchestratorExecutor(
            @ConfigProperty(name = "quorumtrading.agent.executor.max-async", defaultValue = "50") int maxAsync,
            @ConfigProperty(name = "quorumtrading.agent.executor.max-queued", defaultValue = "200") int maxQueued) {
        return ManagedExecutor.builder()
                .propagated(ThreadContext.CDI)
                .maxAsync(maxAsync)
                .maxQueued(maxQueued)
                .build();
    }

    public void disposeAgentOrchestratorExecutor(
            @Disposes @Identifier("agent-orchestrator") ManagedExecutor executor) {
        executor.shutdownNow();
    }

    // Named ManagedExecutor for research execution
    // Allows research to run asynchronously without blocking the caller

    @Produces
    @ApplicationScoped
    @Identifier("research-executor")
    public ManagedExecutor researchExecutor() {
        return ManagedExecutor.builder()
                .propagated(ThreadContext.CDI)
                .maxAsync(5)
                .maxQueued(50)
                .build();
    }

    public void disposeResearchExecutor(
            @Disposes @Identifier("research-executor") ManagedExecutor executor) {
        executor.shutdownNow();
    }

    // Named ManagedExecutor for simulation execution
    // Runs simulation backtests asynchronously without blocking CDI event threads

    @Produces
    @ApplicationScoped
    @Identifier("simulation-executor")
    public ManagedExecutor simulationExecutorPool(
            @ConfigProperty(name = "quorumtrading.simulation.executor.max-async", defaultValue = "5") int maxAsync,
            @ConfigProperty(name = "quorumtrading.simulation.executor.max-queued", defaultValue = "50") int maxQueued) {
        return ManagedExecutor.builder()
                .propagated(ThreadContext.CDI)
                .maxAsync(maxAsync)
                .maxQueued(maxQueued)
                .build();
    }

    public void disposeSimulationExecutor(
            @Disposes @Identifier("simulation-executor") ManagedExecutor executor) {
        // Graceful shutdown: simulation tasks are long-running and worth waiting for.
        // Give running simulations 45s to reach a checkpoint before forcing shutdown.
        // Combined with ECS stopTimeout=65, this provides adequate time for graceful exit.
        executor.shutdown();
        try {
            if (!executor.awaitTermination(45, java.util.concurrent.TimeUnit.SECONDS)) {
                org.jboss.logging.Logger.getLogger(ApplicationConfig.class)
                    .warn("simulation-executor did not terminate in 45s, forcing shutdown");
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    @Produces
    @ApplicationScoped
    public com.quorumtrading.application.port.out.AsyncExecutionPort asyncExecutionPort(
            @Identifier("simulation-executor") ManagedExecutor simulationExecutor) {
        return new com.quorumtrading.bootstrap.adapter.ManagedExecutorAsyncAdapter(simulationExecutor);
    }

    // API Token Use Cases

    @Produces
    @ApplicationScoped
    @jakarta.enterprise.inject.Typed(ApiTokenService.class)
    public ApiTokenService apiTokenService(ApiTokenRepository apiTokenRepository) {
        return new ApiTokenService(apiTokenRepository);
    }

    @Produces
    @ApplicationScoped
    public com.quorumtrading.application.usecase.apitoken.CreateApiTokenUseCase createApiTokenUseCase(ApiTokenService service) {
        return service;
    }

    @Produces
    @ApplicationScoped
    public com.quorumtrading.application.usecase.apitoken.ListApiTokensUseCase listApiTokensUseCase(ApiTokenService service) {
        return service;
    }

    @Produces
    @ApplicationScoped
    public com.quorumtrading.application.usecase.apitoken.RevokeApiTokenUseCase revokeApiTokenUseCase(ApiTokenService service) {
        return service;
    }

    @Produces
    @ApplicationScoped
    public com.quorumtrading.application.usecase.apitoken.ResolveUserByApiTokenUseCase resolveUserByApiTokenUseCase(ApiTokenService service) {
        return service;
    }

    // Notification Service

    @Produces
    @ApplicationScoped
    @jakarta.enterprise.inject.Typed(com.quorumtrading.application.service.NotificationService.class)
    public com.quorumtrading.application.service.NotificationService notificationService(
            com.quorumtrading.application.port.out.NotificationRepository notificationRepository,
            com.quorumtrading.application.port.out.NotificationPreferenceRepository notificationPreferenceRepository,
            Instance<com.quorumtrading.application.port.out.NotificationDispatcherPort> dispatchers) {
        java.util.List<com.quorumtrading.application.port.out.NotificationDispatcherPort> list =
                dispatchers.stream().toList();
        return new com.quorumtrading.application.service.NotificationService(
                notificationRepository, notificationPreferenceRepository, list);
    }

    // Email-side notification plumbing (used by EmailNotificationDispatcher)

    @Produces
    @ApplicationScoped
    public com.quorumtrading.application.service.EmailTemplateRegistry emailTemplateRegistry() {
        return new com.quorumtrading.application.service.EmailTemplateRegistry();
    }

    @Produces
    @ApplicationScoped
    public com.quorumtrading.application.service.UnsubscribeTokenService unsubscribeTokenService(
            @ConfigProperty(name = "quorumtrading.notifications.email.unsubscribe-secret",
                    defaultValue = "dev-only-unsubscribe-secret-rotate-in-prod") String secret) {
        return new com.quorumtrading.application.service.UnsubscribeTokenService(secret);
    }

    @Produces
    @ApplicationScoped
    public com.quorumtrading.application.usecase.notification.ListNotificationsUseCase listNotificationsUseCase(
            com.quorumtrading.application.service.NotificationService service) {
        return service;
    }

    @Produces
    @ApplicationScoped
    public com.quorumtrading.application.usecase.notification.GetNotificationUseCase getNotificationUseCase(
            com.quorumtrading.application.service.NotificationService service) {
        return service;
    }

    @Produces
    @ApplicationScoped
    public com.quorumtrading.application.usecase.notification.MarkNotificationReadUseCase markNotificationReadUseCase(
            com.quorumtrading.application.service.NotificationService service) {
        return service;
    }

    @Produces
    @ApplicationScoped
    public com.quorumtrading.application.usecase.notification.MarkAllNotificationsReadUseCase markAllNotificationsReadUseCase(
            com.quorumtrading.application.service.NotificationService service) {
        return service;
    }

    @Produces
    @ApplicationScoped
    public com.quorumtrading.application.usecase.notification.CountUnreadNotificationsUseCase countUnreadNotificationsUseCase(
            com.quorumtrading.application.service.NotificationService service) {
        return service;
    }

    @Produces
    @ApplicationScoped
    public com.quorumtrading.application.usecase.notification.GetNotificationPreferencesUseCase getNotificationPreferencesUseCase(
            com.quorumtrading.application.service.NotificationService service) {
        return service;
    }

    @Produces
    @ApplicationScoped
    public com.quorumtrading.application.usecase.notification.UpdateNotificationPreferenceUseCase updateNotificationPreferenceUseCase(
            com.quorumtrading.application.service.NotificationService service) {
        return service;
    }

    @Produces
    @ApplicationScoped
    public com.quorumtrading.application.usecase.notification.DispatchNotificationUseCase dispatchNotificationUseCase(
            com.quorumtrading.application.service.NotificationService service) {
        return service;
    }

    // Saxo OAuth token provider (lease-protected proactive refresh)

    @Produces
    @ApplicationScoped
    public com.quorumtrading.application.port.out.BrokerTokenProviderPort brokerTokenProviderPort(
            BrokerAccountRepository brokerAccountRepository,
            com.quorumtrading.application.port.out.SaxoOAuthClientPort oauthClient,
            com.quorumtrading.application.port.out.BrokerTokenLeasePort leasePort,
            com.quorumtrading.application.usecase.notification.DispatchNotificationUseCase notificationDispatcher,
            @ConfigProperty(name = "quorumtrading.broker.saxo.refresh.holder-id", defaultValue = "qt-instance")
            String holderId) {
        return new com.quorumtrading.application.service.BrokerTokenRefreshService(
                brokerAccountRepository, oauthClient, leasePort, notificationDispatcher, holderId);
    }

    // Simulation Contributions (recurring-investment top-ups)

    @Produces
    @ApplicationScoped
    @jakarta.enterprise.inject.Typed(com.quorumtrading.application.service.SimulationContributionService.class)
    public com.quorumtrading.application.service.SimulationContributionService simulationContributionService(
            SimulationRepository simulationRepository,
            SimulationPortfolioRepository simulationPortfolioRepository,
            BrokerAccountRepository brokerAccountRepository,
            com.quorumtrading.application.port.out.SimulationContributionRepository contributionRepository,
            StrategyRepository strategyRepository,
            AuthorizationService authorizationService,
            PortfolioRepository portfolioRepository) {
        return new com.quorumtrading.application.service.SimulationContributionService(
                simulationRepository, simulationPortfolioRepository,
                brokerAccountRepository, contributionRepository,
                strategyRepository, authorizationService, portfolioRepository);
    }

    @Produces
    @ApplicationScoped
    public com.quorumtrading.application.usecase.contribution.RecordSimulationContributionUseCase recordSimulationContributionUseCase(
            com.quorumtrading.application.service.SimulationContributionService service) {
        return service;
    }

    @Produces
    @ApplicationScoped
    public com.quorumtrading.application.usecase.contribution.GetSimulationContributionsUseCase getSimulationContributionsUseCase(
            com.quorumtrading.application.service.SimulationContributionService service) {
        return service;
    }

    // Strategy Picker. Switches between heuristic (fast, deterministic) and LLM-backed
    // (richer reasoning, costs money) via quorumtrading.picker.mode. The LLM picker
    // falls back to the heuristic on any failure so plans never break.

    @Produces
    @ApplicationScoped
    public com.quorumtrading.application.port.out.StrategyPickerPort strategyPickerPort(
            StrategyRepository strategyRepository,
            LLMRouterAdapter llmRouter,
            RecordLLMUsageUseCase recordLLMUsageUseCase,
            @ConfigProperty(name = "quorumtrading.picker.mode", defaultValue = "heuristic") String mode,
            @ConfigProperty(name = "quorumtrading.picker.llm.model",
                            defaultValue = "openai/gpt-4o-mini") String llmModel,
            @ConfigProperty(name = "quorumtrading.picker.llm.temperature",
                            defaultValue = "0.2") double llmTemperature,
            @ConfigProperty(name = "quorumtrading.picker.llm.max-tokens",
                            defaultValue = "500") int llmMaxTokens) {
        var heuristic = new com.quorumtrading.application.service.HeuristicStrategyPicker(strategyRepository);
        if ("llm".equalsIgnoreCase(mode)) {
            return new com.quorumtrading.application.service.LlmStrategyPicker(
                    llmRouter, strategyRepository, heuristic, recordLLMUsageUseCase,
                    llmModel, llmTemperature, llmMaxTokens);
        }
        return heuristic;
    }

    // Investment Plans

    @Produces
    @ApplicationScoped
    @jakarta.enterprise.inject.Typed(com.quorumtrading.application.service.InvestmentPlanService.class)
    public com.quorumtrading.application.service.InvestmentPlanService investmentPlanService(
            com.quorumtrading.application.port.out.InvestmentPlanRepository planRepository,
            com.quorumtrading.application.port.out.InvestmentAllocationRepository allocationRepository,
            BrokerAccountRepository brokerAccountRepository) {
        return new com.quorumtrading.application.service.InvestmentPlanService(
                planRepository, allocationRepository, brokerAccountRepository);
    }

    @Produces @ApplicationScoped
    public com.quorumtrading.application.usecase.investmentplan.CreateInvestmentPlanUseCase createInvestmentPlanUseCase(
            com.quorumtrading.application.service.InvestmentPlanService service) { return service; }

    @Produces @ApplicationScoped
    public com.quorumtrading.application.usecase.investmentplan.UpdateInvestmentPlanUseCase updateInvestmentPlanUseCase(
            com.quorumtrading.application.service.InvestmentPlanService service) { return service; }

    @Produces @ApplicationScoped
    public com.quorumtrading.application.usecase.investmentplan.PauseInvestmentPlanUseCase pauseInvestmentPlanUseCase(
            com.quorumtrading.application.service.InvestmentPlanService service) { return service; }

    @Produces @ApplicationScoped
    public com.quorumtrading.application.usecase.investmentplan.ResumeInvestmentPlanUseCase resumeInvestmentPlanUseCase(
            com.quorumtrading.application.service.InvestmentPlanService service) { return service; }

    @Produces @ApplicationScoped
    public com.quorumtrading.application.usecase.investmentplan.TerminateInvestmentPlanUseCase terminateInvestmentPlanUseCase(
            com.quorumtrading.application.service.InvestmentPlanService service) { return service; }

    @Produces @ApplicationScoped
    public com.quorumtrading.application.usecase.investmentplan.GetInvestmentPlanUseCase getInvestmentPlanUseCase(
            com.quorumtrading.application.service.InvestmentPlanService service) { return service; }

    @Produces @ApplicationScoped
    public com.quorumtrading.application.usecase.investmentplan.ListInvestmentPlansUseCase listInvestmentPlansUseCase(
            com.quorumtrading.application.service.InvestmentPlanService service) { return service; }

    @Produces @ApplicationScoped
    public com.quorumtrading.application.usecase.investmentplan.ListInvestmentAllocationsUseCase listInvestmentAllocationsUseCase(
            com.quorumtrading.application.service.InvestmentPlanService service) { return service; }

    @Produces @ApplicationScoped
    public com.quorumtrading.application.usecase.investmentplan.GetInvestmentAllocationUseCase getInvestmentAllocationUseCase(
            com.quorumtrading.application.service.InvestmentPlanService service) { return service; }

    @Produces
    @ApplicationScoped
    public com.quorumtrading.application.usecase.investmentplan.ExecuteInvestmentPlanUseCase executeInvestmentPlanUseCase(
            com.quorumtrading.application.port.out.InvestmentPlanRepository planRepository,
            com.quorumtrading.application.port.out.InvestmentAllocationRepository allocationRepository,
            BrokerAccountRepository brokerAccountRepository,
            SimulationRepository simulationRepository,
            StrategyRepository strategyRepository,
            com.quorumtrading.application.port.out.StrategyPickerPort strategyPicker,
            CreateSimulationUseCase createSimulationUseCase,
            com.quorumtrading.application.usecase.contribution.RecordSimulationContributionUseCase recordContribution,
            com.quorumtrading.application.usecase.notification.DispatchNotificationUseCase dispatchNotification) {
        return new com.quorumtrading.application.service.ExecuteInvestmentPlanService(
                planRepository, allocationRepository, brokerAccountRepository,
                simulationRepository, strategyRepository, strategyPicker, createSimulationUseCase,
                recordContribution, dispatchNotification);
    }

    // User & Multi-tenancy Services

    @Produces
    @ApplicationScoped
    public DeleteUserAccountUseCase deleteUserAccountUseCase(
            UserRepository userRepository,
            StrategyRepository strategyRepository,
            MarketResearchRepository marketResearchRepository,
            PaymentTransactionRepository paymentTransactionRepository,
            DeletedAccountHashRepository deletedAccountHashRepository) {
        return new DeleteUserAccountService(userRepository, strategyRepository, marketResearchRepository, paymentTransactionRepository, deletedAccountHashRepository);
    }

    @Produces
    @ApplicationScoped
    public AuthenticationService authenticationService(
            UserRepository userRepository,
            UserBudgetRepository userBudgetRepository,
            DeletedAccountHashRepository deletedAccountHashRepository,
            @ConfigProperty(name = "quorumtrading.user.welcome-budget-cents", defaultValue = "200") int welcomeBudgetCents) {
        return new AuthenticationService(userRepository, userBudgetRepository, deletedAccountHashRepository, welcomeBudgetCents);
    }

    @Produces
    @ApplicationScoped
    public AuthorizationService authorizationService() {
        return new AuthorizationService();
    }

    // Broker Account Use Cases

    @Produces
    @ApplicationScoped
    public com.quorumtrading.application.usecase.brokeraccount.CreateBrokerAccountUseCase createBrokerAccountUseCase(
            BrokerAccountRepository brokerAccountRepository,
            Instance<BrokerCredentialValidationPort> credentialValidationPort) {
        return new CreateBrokerAccountService(
            brokerAccountRepository,
            brokerTypeDispatchingValidationPort(credentialValidationPort)
        );
    }

    // Saxo OAuth onboarding (PKCE begin + callback complete). One shared POJO backs both the
    // begin (StartSaxoOnboardingUseCase) and callback (CompleteSaxoOnboardingUseCase) input-ports.

    @Produces
    @ApplicationScoped
    @jakarta.enterprise.inject.Typed(com.quorumtrading.application.service.SaxoOAuthOnboardingService.class)
    public com.quorumtrading.application.service.SaxoOAuthOnboardingService saxoOAuthOnboardingService(
            com.quorumtrading.application.port.out.SaxoOAuthClientPort oauthClient,
            com.quorumtrading.application.port.out.OAuthStateStorePort stateStore,
            com.quorumtrading.application.usecase.brokeraccount.CreateBrokerAccountUseCase createBrokerAccount,
            @ConfigProperty(name = "quorumtrading.broker.saxo.app-key", defaultValue = "not-configured")
            String appKey,
            @ConfigProperty(name = "quorumtrading.broker.saxo.app-secret", defaultValue = "not-configured")
            String appSecret,
            @ConfigProperty(name = "quorumtrading.broker.saxo.authorize-url",
                            defaultValue = "https://sim.logonvalidation.net/authorize")
            String authorizeBaseUrl,
            @ConfigProperty(name = "quorumtrading.broker.saxo.onboarding.state-ttl-seconds",
                            defaultValue = "600") long stateTtlSeconds) {
        return new com.quorumtrading.application.service.SaxoOAuthOnboardingService(
                oauthClient, stateStore, createBrokerAccount,
                appKey, appSecret, authorizeBaseUrl, java.time.Duration.ofSeconds(stateTtlSeconds));
    }

    @Produces
    @ApplicationScoped
    public com.quorumtrading.application.usecase.saxo.StartSaxoOnboardingUseCase startSaxoOnboardingUseCase(
            com.quorumtrading.application.service.SaxoOAuthOnboardingService service) {
        return service;
    }

    @Produces
    @ApplicationScoped
    public com.quorumtrading.application.usecase.saxo.CompleteSaxoOnboardingUseCase completeSaxoOnboardingUseCase(
            com.quorumtrading.application.service.SaxoOAuthOnboardingService service) {
        return service;
    }

    // Runtime LLM provider selection (self-host edition). One shared POJO backs both the status
    // (GetLlmProviderStatusUseCase) and selection (SelectLlmProviderUseCase) input-ports.

    @Produces
    @ApplicationScoped
    @jakarta.enterprise.inject.Typed(com.quorumtrading.application.service.LlmProviderSelectionService.class)
    public com.quorumtrading.application.service.LlmProviderSelectionService llmProviderSelectionService(
            com.quorumtrading.application.port.out.LlmProviderPreferencePort preferencePort,
            com.quorumtrading.application.port.out.LlmProviderAvailabilityPort availabilityPort,
            jakarta.enterprise.inject.Instance<com.quorumtrading.application.port.out.LlmTelemetryPort> telemetryPort,
            com.quorumtrading.application.service.RevalidateStoredModelPinsService pinRevalidator) {
        return new com.quorumtrading.application.service.LlmProviderSelectionService(
                preferencePort, availabilityPort,
                telemetryPort.isResolvable() ? telemetryPort.get() : null,
                pinRevalidator);
    }

    @Produces
    @ApplicationScoped
    public com.quorumtrading.application.usecase.llm.GetLlmProviderStatusUseCase getLlmProviderStatusUseCase(
            com.quorumtrading.application.service.LlmProviderSelectionService service) {
        return service;
    }

    @Produces
    @ApplicationScoped
    public com.quorumtrading.application.usecase.llm.SelectLlmProviderUseCase selectLlmProviderUseCase(
            com.quorumtrading.application.service.LlmProviderSelectionService service) {
        return service;
    }

    @Produces
    @ApplicationScoped
    public com.quorumtrading.application.usecase.brokeraccount.ListBrokerAccountsUseCase listBrokerAccountsUseCase(
            BrokerAccountRepository brokerAccountRepository) {
        return new ListBrokerAccountsService(brokerAccountRepository);
    }

    @Produces
    @ApplicationScoped
    public com.quorumtrading.application.usecase.brokeraccount.GetBrokerAccountUseCase getBrokerAccountUseCase(
            BrokerAccountRepository brokerAccountRepository,
            AuthorizationService authorizationService) {
        return new GetBrokerAccountService(brokerAccountRepository, authorizationService);
    }

    @Produces
    @ApplicationScoped
    public com.quorumtrading.application.usecase.brokeraccount.DeleteBrokerAccountUseCase deleteBrokerAccountUseCase(
            BrokerAccountRepository brokerAccountRepository,
            AuthorizationService authorizationService) {
        return new DeleteBrokerAccountService(brokerAccountRepository, authorizationService);
    }

    @Produces
    @ApplicationScoped
    public com.quorumtrading.application.usecase.brokeraccount.ReleaseTradingCapitalUseCase releaseTradingCapitalUseCase(
            BrokerAccountRepository brokerAccountRepository) {
        return new ReleaseTradingCapitalService(brokerAccountRepository);
    }

    @Produces
    @ApplicationScoped
    public com.quorumtrading.application.usecase.brokeraccount.UpdateBrokerAccountCredentialsUseCase updateBrokerAccountCredentialsUseCase(
            BrokerAccountRepository brokerAccountRepository,
            Instance<BrokerCredentialValidationPort> credentialValidationPort,
            SimulationRepository simulationRepository,
            AuthorizationService authorizationService) {
        return new com.quorumtrading.application.service.UpdateBrokerAccountCredentialsService(
            brokerAccountRepository,
            brokerTypeDispatchingValidationPort(credentialValidationPort),
            simulationRepository,
            authorizationService
        );
    }

    @Produces
    @ApplicationScoped
    public com.quorumtrading.application.usecase.brokeraccount.ValidateBrokerAccountCredentialsUseCase validateBrokerAccountCredentialsUseCase(
            BrokerAccountRepository brokerAccountRepository,
            Instance<BrokerCredentialValidationPort> credentialValidationPort,
            SimulationRepository simulationRepository,
            AuthorizationService authorizationService) {
        return new ValidateBrokerAccountCredentialsService(
            brokerAccountRepository,
            brokerTypeDispatchingValidationPort(credentialValidationPort),
            simulationRepository,
            authorizationService
        );
    }

    // User Resolution Use Case (shared across REST resources)

    @Produces
    @ApplicationScoped
    public com.quorumtrading.application.usecase.ResolveCurrentUserUseCase resolveCurrentUserUseCase(
            UserRepository userRepository) {
        return new ResolveCurrentUserService(userRepository);
    }

    // Market Research Use Cases (fixing hexagonal violations)

    @Produces
    @ApplicationScoped
    public com.quorumtrading.application.usecase.ListMarketResearchUseCase listMarketResearchUseCase(
            MarketResearchRepository marketResearchRepository,
            ResearchExecutionRepository researchExecutionRepository) {
        return new ListMarketResearchService(marketResearchRepository, researchExecutionRepository);
    }

    @Produces
    @ApplicationScoped
    public com.quorumtrading.application.usecase.DeleteMarketResearchUseCase deleteMarketResearchUseCase(
            MarketResearchRepository marketResearchRepository,
            AuthorizationService authorizationService) {
        return new DeleteMarketResearchService(marketResearchRepository, authorizationService);
    }

    @Produces
    @ApplicationScoped
    public com.quorumtrading.application.usecase.DeleteResearchExecutionUseCase deleteResearchExecutionUseCase(
            ResearchExecutionRepository researchExecutionRepository,
            MarketResearchRepository marketResearchRepository,
            AuthorizationService authorizationService) {
        return new DeleteResearchExecutionService(researchExecutionRepository, marketResearchRepository, authorizationService);
    }

    // Scheduled Simulation Use Case (fixing hexagonal violations)

    @Produces
    @ApplicationScoped
    public com.quorumtrading.application.usecase.GetScheduledSimulationByStrategyUseCase getScheduledSimulationByStrategyUseCase(
            ScheduledSimulationRepository scheduledSimulationRepository,
            StrategyRepository strategyRepository,
            AuthorizationService authorizationService) {
        return new GetScheduledSimulationByStrategyService(scheduledSimulationRepository, strategyRepository, authorizationService);
    }

    // Usage Statistics Use Cases

    @Produces
    @ApplicationScoped
    public GetLLMUsageStatisticsUseCase getLLMUsageStatisticsUseCase(
            LLMUsageRepository llmUsageRepository) {
        return new GetLLMUsageStatisticsUseCase(llmUsageRepository);
    }

    // Model Reliability Use Case

    @Produces
    @ApplicationScoped
    public GetModelReliabilityUseCase getModelReliabilityUseCase(
            LlmParseFailureRepository llmParseFailureRepository) {
        return new GetModelReliabilityUseCase(llmParseFailureRepository);
    }

    // Budget Use Cases

    @Produces
    @ApplicationScoped
    public GetUserBudgetUseCase getUserBudgetUseCase(UserBudgetRepository userBudgetRepository, LLMUsageRepository llmUsageRepository) {
        return new GetUserBudgetUseCase(userBudgetRepository, llmUsageRepository);
    }

    @Produces
    @ApplicationScoped
    public TopUpBudgetUseCase topUpBudgetUseCase(UserBudgetRepository userBudgetRepository) {
        return new TopUpBudgetUseCase(userBudgetRepository);
    }

    @Produces
    @ApplicationScoped
    public com.quorumtrading.application.usecase.UpdateDailySpendLimitUseCase updateDailySpendLimitUseCase(UserBudgetRepository userBudgetRepository) {
        return new com.quorumtrading.application.usecase.UpdateDailySpendLimitUseCase(userBudgetRepository);
    }

    @Produces
    @ApplicationScoped
    public com.quorumtrading.application.usecase.budget.RecordLLMUsageUseCase recordLLMUsageUseCase(
            UserBudgetRepository userBudgetRepository,
            EventPublisher<com.quorumtrading.application.event.BudgetWarningEvent> budgetWarningEventPublisher,
            EventPublisher<com.quorumtrading.application.event.BudgetDepletedEvent> budgetDepletedEventPublisher,
            LLMUsageRepository llmUsageRepository,
            com.quorumtrading.application.port.out.TransactionRunner transactionRunner) {
        return new com.quorumtrading.application.usecase.budget.RecordLLMUsageUseCase(
            userBudgetRepository,
            budgetWarningEventPublisher,
            budgetDepletedEventPublisher,
            llmUsageRepository,
            transactionRunner
        );
    }

    // Payment Use Cases

    @Produces
    @ApplicationScoped
    public com.quorumtrading.application.usecase.payment.CreateCheckoutSessionUseCase createCheckoutSessionUseCase(
            PaymentPort paymentPort,
            PaymentTransactionRepository paymentTransactionRepository) {
        return new com.quorumtrading.application.usecase.payment.CreateCheckoutSessionUseCase(
            paymentPort,
            paymentTransactionRepository
        );
    }

    @Produces
    @ApplicationScoped
    public com.quorumtrading.application.usecase.payment.ProcessPaymentWebhookUseCase processPaymentWebhookUseCase(
            PaymentPort paymentPort,
            PaymentTransactionRepository paymentTransactionRepository,
            TopUpBudgetUseCase topUpBudgetUseCase) {
        return new com.quorumtrading.application.usecase.payment.ProcessPaymentWebhookUseCase(
            paymentPort,
            paymentTransactionRepository,
            topUpBudgetUseCase
        );
    }

    // Model Selection

    @Produces
    @ApplicationScoped
    public com.quorumtrading.model.service.ModelSelectionService modelSelectionService(
            @ConfigProperty(name = "quorumtrading.llm.defaults.backtesting") String backtestingModel,
            @ConfigProperty(name = "quorumtrading.llm.defaults.paper-trading") String paperTradingModel,
            @ConfigProperty(name = "quorumtrading.llm.defaults.live-trading") String liveTradingModel,
            @ConfigProperty(name = "quorumtrading.llm.defaults.research") String researchModel,
            @ConfigProperty(name = "quorumtrading.llm.defaults.chat", defaultValue = "mistralai/mistral-small-3.2-24b-instruct") String chatModel) {
        return com.quorumtrading.model.service.ModelSelectionService.builder()
            .withDefault(com.quorumtrading.model.valueobject.TradingContext.BACKTESTING, backtestingModel)
            .withDefault(com.quorumtrading.model.valueobject.TradingContext.PAPER_TRADING, paperTradingModel)
            .withDefault(com.quorumtrading.model.valueobject.TradingContext.LIVE_TRADING, liveTradingModel)
            .withDefault(com.quorumtrading.model.valueobject.TradingContext.RESEARCH, researchModel)
            .withDefault(com.quorumtrading.model.valueobject.TradingContext.CHAT, chatModel)
            .build();
    }

    // Restart Simulation From Decision

    @Produces
    @ApplicationScoped
    public RestartSimulationFromService restartSimulationFromService(
            SimulationRepository simulationRepository,
            StrategyRepository strategyRepository,
            ConsensusDecisionRepository consensusDecisionRepository,
            LLMDecisionRepository llmDecisionRepository,
            PortfolioRepository portfolioRepository,
            AuthorizationService authorizationService) {
        return new RestartSimulationFromService(simulationRepository, strategyRepository,
                consensusDecisionRepository, llmDecisionRepository, portfolioRepository, authorizationService);
    }

    // Simulation Cost Calculation

    @Produces
    @ApplicationScoped
    public CalculateSimulationCostUseCase calculateSimulationCostUseCase(
            ConsensusDecisionRepository consensusDecisionRepository,
            SimulationRepository simulationRepository,
            StrategyRepository strategyRepository,
            AuthorizationService authorizationService) {
        return new CalculateSimulationCostService(consensusDecisionRepository, simulationRepository, strategyRepository, authorizationService);
    }

    // Cost Estimation

    @Produces
    @ApplicationScoped
    public com.quorumtrading.model.service.CostEstimationService costEstimationService() {
        return new com.quorumtrading.model.service.CostEstimationService();
    }

    @Produces
    @ApplicationScoped
    public com.quorumtrading.application.usecase.EstimateBacktestCostUseCase estimateBacktestCostUseCase(
            StrategyRepository strategyRepository,
            LLMRouterAdapter llmRouter,
            UserBudgetRepository userBudgetRepository,
            com.quorumtrading.model.service.ModelSelectionService modelSelectionService,
            com.quorumtrading.application.orchestration.StrategyParser strategyParser,
            com.quorumtrading.model.service.CostEstimationService costEstimationService,
            @ConfigProperty(name = "quorumtrading.cost-estimate.avg-prompt-tokens-per-call", defaultValue = "2540") int avgPromptTokens,
            @ConfigProperty(name = "quorumtrading.cost-estimate.avg-completion-tokens-per-call", defaultValue = "160") int avgCompletionTokens,
            com.quorumtrading.application.port.out.LlmProviderPreferencePort llmProviderPreferencePort,
            com.quorumtrading.application.port.out.HistoricalPricePort historicalPricePort) {
        // The router doubles as the telemetry source, so the estimate can project wall-clock
        // time from the engine's observed latency — for a free local engine, time IS the cost.
        return new com.quorumtrading.application.usecase.EstimateBacktestCostUseCase(
            strategyRepository, llmRouter, userBudgetRepository, modelSelectionService,
            strategyParser, costEstimationService, avgPromptTokens, avgCompletionTokens,
            llmProviderPreferencePort, llmRouter, historicalPricePort
        );
    }

    // AI Configuration Use Cases

    @Produces
    @ApplicationScoped
    public com.quorumtrading.model.service.ModelFilter modelFilter() {
        return new com.quorumtrading.model.service.ModelFilter();
    }

    @Produces
    @ApplicationScoped
    public GetAvailableModelsUseCase getAvailableModelsUseCase(
            LLMRouterAdapter llmRouter,
            com.quorumtrading.model.service.ModelFilter modelFilter) {
        return new GetAvailableModelsUseCase(llmRouter, modelFilter);
    }

    @Produces
    @ApplicationScoped
    public UpdateModelPreferencesUseCase updateModelPreferencesUseCase(
            UserBudgetRepository userBudgetRepository,
            LLMRouterAdapter llmRouter,
            com.quorumtrading.model.service.ModelFilter modelFilter,
            com.quorumtrading.application.service.ModelPinValidator modelPinValidator) {
        return new UpdateModelPreferencesUseCase(userBudgetRepository, llmRouter, modelFilter, modelPinValidator);
    }

    // Historical Events Use Cases

    @Produces
    @ApplicationScoped
    public ListHistoricalEventsUseCase listHistoricalEventsUseCase(HistoricalEventRepository historicalEventRepository) {
        return new ListHistoricalEventsService(historicalEventRepository);
    }

    @Produces
    @ApplicationScoped
    public GetHistoricalEventUseCase getHistoricalEventUseCase(HistoricalEventRepository historicalEventRepository) {
        return new GetHistoricalEventService(historicalEventRepository);
    }

    // Chat Use Cases

    @Produces
    @ApplicationScoped
    public SystemPromptBuilder systemPromptBuilder(
            SimulationRepository simulationRepository,
            StrategyRepository strategyRepository,
            MarketResearchRepository marketResearchRepository,
            UserBudgetRepository userBudgetRepository,
            @ConfigProperty(name = "quorumtrading.chat.a2ui-enabled", defaultValue = "false") boolean chatA2uiEnabled) {
        return new SystemPromptBuilder(simulationRepository, strategyRepository, marketResearchRepository, userBudgetRepository, chatA2uiEnabled);
    }

    @Produces
    @ApplicationScoped
    public ChatToolExecutor chatToolExecutor(
            ListStrategiesUseCase listStrategiesUseCase,
            ListPublicStrategiesUseCase listPublicStrategiesUseCase,
            GetStrategyUseCase getStrategyUseCase,
            CalculateStrategyPerformanceUseCase calculateStrategyPerformanceUseCase,
            ListSimulationsUseCase listSimulationsUseCase,
            GetSimulationUseCase getSimulationUseCase,
            QueryPortfolioSnapshotsUseCase queryPortfolioSnapshotsUseCase,
            GetConsensusWithAgentsUseCase getConsensusWithAgentsUseCase,
            QueryConsensusDecisionsUseCase queryConsensusDecisionsUseCase,
            com.quorumtrading.application.service.IndicatorEnrichmentService indicatorEnrichmentService,
            QueryMarketEventsUseCase queryMarketEventsUseCase,
            BackFillTickerPriceDataUseCase backFillTickerPriceDataUseCase,
            ListMarketResearchUseCase listMarketResearchUseCase,
            GetMarketResearchDetailsUseCase getMarketResearchDetailsUseCase,
            com.quorumtrading.application.usecase.brokeraccount.ListBrokerAccountsUseCase listBrokerAccountsUseCase,
            CreateStrategyUseCase createStrategyUseCase,
            CreateSimulationUseCase createSimulationUseCase,
            StartSimulationUseCase startSimulationUseCase,
            CreateMarketResearchUseCase createMarketResearchUseCase,
            ExecuteMarketResearchUseCase executeMarketResearchUseCase,
            DeleteStrategyUseCase deleteStrategyUseCase,
            DeleteSimulationUseCase deleteSimulationUseCase,
            DeleteMarketResearchUseCase deleteMarketResearchUseCase,
            DeleteBenchmarkUseCase deleteBenchmarkUseCase,
            CompareSimulationsUseCase compareSimulationsUseCase,
            CreateBenchmarkUseCase createBenchmarkUseCase,
            PauseSimulationUseCase pauseSimulationUseCase,
            SellPositionUseCase sellPositionUseCase,
            LiquidateAllPositionsUseCase liquidateAllPositionsUseCase,
            ForceEndSimulationUseCase forceEndSimulationUseCase,
            FindTickerUseCase findTickerUseCase,
            PauseMarketResearchUseCase pauseMarketResearchUseCase,
            ResumeMarketResearchUseCase resumeMarketResearchUseCase,
            PruneUnusedUseCase pruneUnusedUseCase,
            CalculateBenchmarkOverlaysUseCase calculateBenchmarkOverlaysUseCase,
            GetTickerPerformanceUseCase getTickerPerformanceUseCase,
            ListHistoricalEventsUseCase listHistoricalEventsUseCase,
            GetHistoricalEventUseCase getHistoricalEventUseCase,
            com.quorumtrading.application.usecase.JoinChallengeUseCase joinChallengeUseCase,
            com.quorumtrading.application.port.out.ChallengeRepository challengeRepository,
            com.quorumtrading.application.port.out.BenchmarkRepository benchmarkRepository,
            SimulationRepository simulationRepository,
            com.fasterxml.jackson.databind.ObjectMapper objectMapper,
            ManagedExecutor managedExecutor) {
        return new ChatToolExecutor(
            listStrategiesUseCase, listPublicStrategiesUseCase, getStrategyUseCase, calculateStrategyPerformanceUseCase,
            listSimulationsUseCase, getSimulationUseCase,
            queryPortfolioSnapshotsUseCase, getConsensusWithAgentsUseCase, queryConsensusDecisionsUseCase,
            indicatorEnrichmentService, queryMarketEventsUseCase, backFillTickerPriceDataUseCase,
            listMarketResearchUseCase, getMarketResearchDetailsUseCase,
            listBrokerAccountsUseCase, createStrategyUseCase, createSimulationUseCase,
            startSimulationUseCase, createMarketResearchUseCase, executeMarketResearchUseCase,
            deleteStrategyUseCase, deleteSimulationUseCase, deleteMarketResearchUseCase,
            deleteBenchmarkUseCase, compareSimulationsUseCase, createBenchmarkUseCase,
            pauseSimulationUseCase, sellPositionUseCase, liquidateAllPositionsUseCase,
            forceEndSimulationUseCase, findTickerUseCase, pauseMarketResearchUseCase,
            resumeMarketResearchUseCase, pruneUnusedUseCase, calculateBenchmarkOverlaysUseCase,
            getTickerPerformanceUseCase,
            listHistoricalEventsUseCase, getHistoricalEventUseCase,
            joinChallengeUseCase, challengeRepository,
            benchmarkRepository, simulationRepository, objectMapper, managedExecutor);
    }

    @Produces
    @ApplicationScoped
    public CreateChatSessionUseCase createChatSessionUseCase(
            ChatSessionPort chatSessionPort,
            ConversationalLLMPort conversationalLLMPort,
            SystemPromptBuilder systemPromptBuilder,
            FollowUpSuggestionService followUpSuggestionService,
            SystemModelDefaultsProvider systemModelDefaultsProvider,
            UpdateModelPreferencesUseCase updateModelPreferencesUseCase,
            RecordLLMUsageUseCase recordLLMUsageUseCase) {
        String defaultModel = systemModelDefaultsProvider
            .getDefaultModel(com.quorumtrading.model.valueobject.TradingContext.CHAT)
            .orElse("mistralai/mistral-small-3.2-24b-instruct");
        return new CreateChatSessionUseCase(
            chatSessionPort, conversationalLLMPort, systemPromptBuilder,
            followUpSuggestionService, defaultModel, updateModelPreferencesUseCase,
            recordLLMUsageUseCase);
    }

    @Produces
    @ApplicationScoped
    public GetChatSessionUseCase getChatSessionUseCase(ChatSessionPort chatSessionPort) {
        return new GetChatSessionUseCase(chatSessionPort);
    }

    @Produces
    @ApplicationScoped
    public com.quorumtrading.application.chat.FollowUpSuggestionService followUpSuggestionService(
            ConversationalLLMPort conversationalLLMPort,
            com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        return new com.quorumtrading.application.chat.FollowUpSuggestionService(conversationalLLMPort, objectMapper);
    }

    @Produces
    @ApplicationScoped
    public com.quorumtrading.application.chat.ChatTitleGenerationService chatTitleGenerationService(
            ConversationalLLMPort conversationalLLMPort) {
        return new com.quorumtrading.application.chat.ChatTitleGenerationService(conversationalLLMPort);
    }

    @Produces
    @ApplicationScoped
    public com.quorumtrading.application.chat.ListChatSessionsUseCase listChatSessionsUseCase(
            ChatSessionPort chatSessionPort) {
        return new com.quorumtrading.application.chat.ListChatSessionsUseCase(chatSessionPort);
    }

    @Produces
    @ApplicationScoped
    public com.quorumtrading.model.service.FreeModelPolicy freeModelPolicy(
            @ConfigProperty(name = "quorumtrading.free-model.ids", defaultValue = "") String freeModelIds,
            @ConfigProperty(name = "quorumtrading.free-model.daily-limit", defaultValue = "50") int dailyLimit) {
        java.util.List<String> ids = freeModelIds.isBlank()
            ? java.util.List.of()
            : java.util.Arrays.stream(freeModelIds.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        return new com.quorumtrading.model.service.FreeModelPolicy(ids, dailyLimit);
    }

    @Produces
    @ApplicationScoped
    public SendChatMessageUseCase sendChatMessageUseCase(
            ChatSessionPort chatSessionPort,
            ConversationalLLMPort conversationalLLMPort,
            ChatToolExecutor chatToolExecutor,
            RecordLLMUsageUseCase recordLLMUsageUseCase,
            com.quorumtrading.application.chat.FollowUpSuggestionService followUpSuggestionService,
            com.quorumtrading.application.chat.ChatTitleGenerationService chatTitleGenerationService,
            com.quorumtrading.model.service.FreeModelPolicy freeModelPolicy,
            com.quorumtrading.application.port.out.FreeModelRateLimitPort freeModelRateLimitPort) {
        return new SendChatMessageUseCase(
            chatSessionPort, conversationalLLMPort, chatToolExecutor,
            recordLLMUsageUseCase, followUpSuggestionService, chatTitleGenerationService,
            freeModelPolicy, freeModelRateLimitPort);
    }

    @Produces
    @jakarta.inject.Singleton
    public NewsDataAvailability newsDataAvailability(
            @ConfigProperty(name = "quorumtrading.news.earliest-date.alpaca", defaultValue = "2015-01-01") String alpacaDate,
            @ConfigProperty(name = "quorumtrading.news.earliest-date.finnhub", defaultValue = "2020-01-01") String finnhubDate) {
        LocalDate earliest = Stream.of(alpacaDate, finnhubDate)
            .map(LocalDate::parse)
            .min(LocalDate::compareTo)
            .orElse(LocalDate.of(2015, 1, 1));
        return new NewsDataAvailability(earliest);
    }

    // ========== Broker credential validation selector ==========

    /**
     * Adapts an (possibly ambiguous) {@code Instance<BrokerCredentialValidationPort>} into a single
     * {@link BrokerCredentialValidationPort} that the broker-account services can hold, dispatching
     * each {@code validate}/{@code supports} call to the implementation whose
     * {@link BrokerCredentialValidationPort#supports(BrokerType)} matches the requested broker type.
     *
     * <p>This replaces the previous {@code Instance.get()} call, which throws
     * {@code AmbiguousResolutionException} the moment a second validation port (e.g. Saxo) is on the
     * classpath. When the {@code Instance} is unsatisfied this returns {@code null}, preserving the
     * existing "no validator → real-broker creation fails loudly in the service" behavior. When a
     * single implementation is present, dispatch resolves to that one implementation, identical to
     * today's behavior.</p>
     *
     * @param ports the CDI instance of all available validation ports
     * @return a dispatching validation port, or {@code null} when no implementation is available
     */
    private BrokerCredentialValidationPort brokerTypeDispatchingValidationPort(
            Instance<BrokerCredentialValidationPort> ports) {
        if (ports.isUnsatisfied()) {
            return null;
        }
        return new DispatchingBrokerCredentialValidationPort(ports);
    }

    /**
     * Selects the {@link BrokerCredentialValidationPort} implementation that supports the given
     * broker type from an ambiguous {@code Instance<…>}.
     *
     * <p>Iterates the available implementations and returns the first whose
     * {@link BrokerCredentialValidationPort#supports(BrokerType)} returns {@code true}. Never calls
     * {@code Instance.get()}, so it is safe with more than one implementation on the classpath.</p>
     *
     * @param brokerType the broker type a validation is requested for
     * @param ports      the CDI instance of all available validation ports
     * @return the matching implementation
     * @throws IllegalStateException if no implementation supports the broker type
     */
    private static BrokerCredentialValidationPort selectValidationPort(
            BrokerType brokerType, Instance<BrokerCredentialValidationPort> ports) {
        for (BrokerCredentialValidationPort port : ports) {
            if (port.supports(brokerType)) {
                return port;
            }
        }
        throw new IllegalStateException(
            "No BrokerCredentialValidationPort implementation supports broker type: " + brokerType);
    }

    /**
     * A {@link BrokerCredentialValidationPort} facade over the CDI {@code Instance<…>} that resolves
     * the concrete implementation per call via {@link #selectValidationPort(BrokerType, Instance)}.
     * Lets the broker-account services keep their single-port constructor signature while supporting
     * multiple broker validators on the classpath.
     */
    private static final class DispatchingBrokerCredentialValidationPort
            implements BrokerCredentialValidationPort {

        private final Instance<BrokerCredentialValidationPort> ports;

        private DispatchingBrokerCredentialValidationPort(Instance<BrokerCredentialValidationPort> ports) {
            this.ports = ports;
        }

        @Override
        public CredentialValidationResult validate(BrokerType brokerType, BrokerCredentials credentials) {
            return selectValidationPort(brokerType, ports).validate(brokerType, credentials);
        }

        @Override
        public boolean supports(BrokerType brokerType) {
            for (BrokerCredentialValidationPort port : ports) {
                if (port.supports(brokerType)) {
                    return true;
                }
            }
            return false;
        }
    }

}
