package com.quorumtrading.integration;

import com.quorumtrading.application.orchestration.agents.ConsensusActionParser;
import com.quorumtrading.application.port.out.*;
import com.quorumtrading.model.entity.*;
import com.quorumtrading.model.enums.InvestmentType;
import com.quorumtrading.model.enums.*;
import com.quorumtrading.model.marketdata.MarketEvent;
import com.quorumtrading.model.valueobject.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static com.quorumtrading.model.enums.CostPreset.TYPICAL_RETAIL;

/**
 * Shared CDI helper bean for integration tests.
 * Provides reusable data creation methods with repository injection.
 */
@ApplicationScoped
public class IntegrationTestHelper {

    @Inject
    UserRepository userRepository;

    @Inject
    StrategyRepository strategyRepository;

    @Inject
    SimulationRepository simulationRepository;

    @Inject
    MarketEventRepository marketEventRepository;

    @Inject
    PortfolioRepository portfolioRepository;

    @Inject
    LLMDecisionRepository llmDecisionRepository;

    @Inject
    ConsensusDecisionRepository consensusDecisionRepository;

    @Inject
    BrokerAccountRepository brokerAccountRepository;

    // ===================== Strategy content constants =====================

    public static final String VALID_CRYPTO_STRATEGY = """
            ---
            version: v1.0
            consensus_method: ORCHESTRATOR_OVERRIDE
            consensus_threshold: 0.66
            live_schedule:
              interval: 3600
            crypto_tickers:
              - BTC
            position_limits:
              BTC: 0.8
            reserve_cash_percentage: 0.2
            ---

            # Crypto Strategy

            ## Rules
            1. Buy when momentum is positive
            2. Sell when RSI > 70
            3. Hold when market is sideways
            """;

    public static final String VALID_STOCK_STRATEGY = """
            ---
            version: v1.0
            consensus_method: ORCHESTRATOR_OVERRIDE
            consensus_threshold: 0.66
            stock_tickers:
              - AAPL
            position_limits:
              AAPL: 0.8
            reserve_cash_percentage: 0.2
            live_schedule:
              timezone: America/New_York
              open: "09:30"
              close: "16:00"
              evaluation_times:
                - "10:00"
                - "14:00"
            ---

            # Stock Strategy

            ## Rules
            1. Buy when price breaks above SMA-50
            2. Sell when price falls below SMA-200
            """;

    public static final String STRATEGY_MISSING_LIVE_SCHEDULE = """
            ---
            version: v1.0
            consensus_method: ORCHESTRATOR_OVERRIDE
            stock_tickers:
              - AAPL
            position_limits:
              AAPL: 0.5
            ---

            # Missing Live Schedule Strategy
            """;

    public static final String STRATEGY_INVALID_YAML = """
            ---
            version: v1.0
            consensus_method: [INVALID
            ---

            # Bad Strategy
            """;

    public static final String VALID_CRYPTO_STRATEGY_ETH = """
            ---
            version: v1.0
            consensus_method: ORCHESTRATOR_OVERRIDE
            consensus_threshold: 0.66
            live_schedule:
              interval: 3600
            crypto_tickers:
              - ETH
            position_limits:
              ETH: 0.8
            reserve_cash_percentage: 0.2
            ---

            # ETH Strategy

            ## Rules
            1. Buy when momentum is positive
            """;

    // ===================== Entity creation helpers =====================

    public User createUser() {
        UserId userId = new UserId(UUID.randomUUID());
        User user = User.create(
                userId,
                userId.value() + "@test.com",
                "Test User",
                null,
                OAuthProvider.GOOGLE
        );
        return userRepository.save(user);
    }

    public Strategy createStrategy(String name, String content, UserId ownerId) {
        Strategy strategy = new Strategy(
                new StrategyId(UUID.randomUUID()),
                name,
                content,
                ownerId
        );
        return strategyRepository.save(strategy);
    }

    public Strategy createStrategy(String name, String content) {
        User user = createUser();
        return createStrategy(name, content, user.getId());
    }

    public Simulation createSimulation(String name, Strategy strategy) {
        return createSimulation(name, strategy,
                Instant.now().minus(7, ChronoUnit.DAYS),
                null,  // No end date — still SIMULATING (startDate in past)
                Money.usd(100_000));
    }

    public Simulation createSimulation(String name, Strategy strategy,
                                       Instant startDate, Instant endDate, Money initialCash) {
        Simulation simulation = new Simulation(
                new SimulationId(UUID.randomUUID()),
                name,
                strategy.getId(),
                startDate,
                endDate,
                initialCash
        );
        // V114 enforces owner_user_id NOT NULL — inherit from the strategy.
        simulation.setOwnerUserId(strategy.getOwnerId());
        return simulationRepository.save(simulation);
    }

    public Simulation createStartedSimulation(String name, Strategy strategy) {
        Simulation simulation = createSimulation(name, strategy);
        simulation.start();
        return simulationRepository.save(simulation);
    }

    public void createMarketEvents(String ticker) {
        Instant now = Instant.now();
        String uniqueId = UUID.randomUUID().toString();

        for (int i = 0; i < 10; i++) {
            Map<String, Object> priceData = new HashMap<>();
            priceData.put("price", BigDecimal.valueOf(45000 + (i * 100)));
            priceData.put("volume", BigDecimal.valueOf(1000000));
            priceData.put("change24h", BigDecimal.valueOf(-2.5 + (i * 0.5)));
            priceData.put("test_id", uniqueId + "_price_" + i);

            marketEventRepository.save(new MarketEvent(
                    now.minus(i * 16, ChronoUnit.HOURS),
                    EventType.PRICE,
                    "coinbase",
                    ticker,
                    priceData
            ));
        }

        Map<String, Object> newsData = new HashMap<>();
        newsData.put("headline", ticker + " adoption continues to grow");
        newsData.put("sentiment", "positive");
        newsData.put("source", "TestNews");
        newsData.put("test_id", uniqueId + "_news");

        marketEventRepository.save(new MarketEvent(
                now.minus(1, ChronoUnit.DAYS),
                EventType.NEWS,
                "newsapi",
                ticker,
                newsData
        ));
    }

    public PortfolioSnapshot createPortfolioSnapshot(Simulation simulation) {
        return createPortfolioSnapshot(simulation, Money.usd(100_000));
    }

    public PortfolioSnapshot createPortfolioSnapshot(Simulation simulation, Money cashAvailable) {
        PortfolioSnapshot snapshot = new PortfolioSnapshot(
                simulation.getSimulationId(),
                Instant.now(),
                Map.of(),
                cashAvailable,
                cashAvailable
        );
        return portfolioRepository.save(snapshot);
    }

    public LLMDecision createLlmDecision(SimulationId simulationId, String agentId, String role) {
        LLMDecision decision = new LLMDecision(
                simulationId,
                Instant.now(),
                agentId,
                role,
                "test-model"
        );
        decision.recordTicker("BTC");
        decision.recordReasoning("{\"reasoning\": \"test\"}");
        decision.recordRecommendation(RecommendationType.BUY);
        decision.recordRecommendedAction("{\"action\": \"BUY\"}");
        decision.recordConfidenceScore(new Percentage(BigDecimal.valueOf(0.85)));
        return llmDecisionRepository.save(decision);
    }

    public User createUserWithEmail(String email) {
        UserId userId = new UserId(UUID.randomUUID());
        User user = User.create(
                userId,
                email,
                "Test User",
                null,
                OAuthProvider.GOOGLE
        );
        return userRepository.save(user);
    }

    public User createUserWithId(UserId userId, String email) {
        User user = User.create(
                userId,
                email,
                "Test User",
                null,
                OAuthProvider.GOOGLE
        );
        return userRepository.save(user);
    }

    public BrokerAccount createMockBrokerAccount(UserId userId, Money initialCapital) {
        MockBrokerConfig config = MockBrokerConfig.fromPreset(initialCapital, TYPICAL_RETAIL);
        BrokerAccount account = BrokerAccount.createMock(userId, "Test Mock Broker", config);
        return brokerAccountRepository.save(account);
    }

    public ConsensusDecision createConsensusDecision(SimulationId simulationId, String action) {
        String jsonAction = "{\"action\":\"" + action + "\",\"symbol\":\"BTC\",\"quantity\":0.1}";
        ConsensusDecision decision = new ConsensusDecision(
                simulationId,
                Instant.now(),
                ConsensusMethod.ORCHESTRATOR_OVERRIDE,
                ConsensusActionParser.parse(jsonAction)
        );
        decision.setSymbol(new Symbol("BTC", InvestmentType.CRYPTO));
        decision.setInvestmentType(InvestmentType.CRYPTO);
        decision.setQuantity(BigDecimal.valueOf(0.1));
        return consensusDecisionRepository.save(decision);
    }
}
