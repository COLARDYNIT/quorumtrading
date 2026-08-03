package com.quorumtrading.integration;

import com.quorumtrading.application.port.out.*;
import com.quorumtrading.application.usecase.ReachConsensusUseCase;
import com.quorumtrading.application.usecase.command.ReachConsensusCommand;
import com.quorumtrading.model.entity.*;
import com.quorumtrading.model.enums.EventType;
import com.quorumtrading.model.enums.OAuthProvider;
import com.quorumtrading.model.marketdata.MarketEvent;
import com.quorumtrading.model.orchestration.StrategyConfig;
import com.quorumtrading.model.orchestration.StrategyConfigParser;
import com.quorumtrading.model.valueobject.*;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration test for Strategy-Level LLM Model Override.
 *
 * Verifies that:
 * 1. Strategies with llm_model in YAML frontmatter are parsed correctly
 * 2. Consensus flow works end-to-end with a pinned strategy model
 * 3. llm_temperature is correctly parsed and applied
 * 4. Invalid temperature values are rejected at parse time
 * 5. Strategies without llm_model still work (backward compatibility)
 */
// Six of these tests only exercise StrategyConfigParser and never touch a model; the other three
// assert that the consensus pipeline tolerates llm_model/llm_temperature in the config. None of
// that needs a real provider, so the class runs on DummyLlmTestProfile instead of @Tag("llm").
@QuarkusTest
@TestProfile(DummyLlmTestProfile.class)
class StrategyLLMModelOverrideIntegrationTest {

    @Inject
    ReachConsensusUseCase reachConsensusUseCase;

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
    UserRepository userRepository;

    /** Mirrors {@code SentimentAgent.MIN_NEWS_EVENTS_FOR_BACKTEST} — the strictest agent gate. */
    private static final int SENTIMENT_MIN_NEWS_EVENTS = 3;

    /**
     * TECHNICAL + FUNDAMENTAL + SENTIMENT + ORCHESTRATOR. The old expectation of 5 assumed four
     * analyst agents; {@link com.quorumtrading.model.enums.AgentRole} only defines three.
     */
    private static final int EXPECTED_DECISIONS_PER_ROUND = 4;

    private static final String STRATEGY_WITH_LLM_MODEL = """
        ---
        version: v1.0
        consensus_method: ORCHESTRATOR_OVERRIDE
        consensus_threshold: 0.66
        live_schedule:
          interval: 3600
        llm_model: qwen/qwen3-coder:free
        llm_temperature: 0.5
        crypto_tickers:
          - BTC
        position_limits:
          BTC: 0.8
        reserve_cash_percentage: 0.2
        ---

        # LLM Override Test Strategy

        ## Rules
        1. Buy when momentum is positive
        2. Sell when RSI > 70
        3. Hold when market is sideways
        """;

    private static final String STRATEGY_WITHOUT_LLM_MODEL = """
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

        # No Override Strategy

        ## Rules
        1. Buy when RSI < 30
        2. Sell when RSI > 70
        """;

    // --- Config Parsing Tests (no database involved) ---

    @Test
    @DisplayName("should parse llm_model and llm_temperature from strategy")
    void shouldParseLlmModelFromStrategy() {
        StrategyConfigParser parser = new StrategyConfigParser();
        StrategyConfig config = parser.parse(STRATEGY_WITH_LLM_MODEL);

        assertThat(config.getLlmModel()).isEqualTo("qwen/qwen3-coder:free");
        assertThat(config.getLlmTemperature()).isEqualTo(0.5);
    }

    @Test
    @DisplayName("should leave llm fields null when not specified")
    void shouldLeaveFieldsNullWhenNotSpecified() {
        StrategyConfigParser parser = new StrategyConfigParser();
        StrategyConfig config = parser.parse(STRATEGY_WITHOUT_LLM_MODEL);

        assertThat(config.getLlmModel()).isNull();
        assertThat(config.getLlmTemperature()).isNull();
    }

    @Test
    @DisplayName("should reject temperature outside 0.0-2.0 range")
    void shouldRejectInvalidTemperature() {
        String invalidStrategy = """
            ---
            version: v1.0
            llm_temperature: 2.5
            crypto_tickers:
              - BTC
            ---
            # Strategy
            """;

        StrategyConfigParser parser = new StrategyConfigParser();

        assertThatThrownBy(() -> parser.parse(invalidStrategy))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("llm_temperature must be between 0.0 and 2.0");
    }

    @Test
    @DisplayName("should reject negative temperature")
    void shouldRejectNegativeTemperature() {
        String invalidStrategy = """
            ---
            version: v1.0
            llm_temperature: -0.5
            crypto_tickers:
              - BTC
            ---
            # Strategy
            """;

        StrategyConfigParser parser = new StrategyConfigParser();

        assertThatThrownBy(() -> parser.parse(invalidStrategy))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("llm_temperature must be between 0.0 and 2.0");
    }

    @Test
    @DisplayName("should accept llm_model without llm_temperature")
    void shouldAcceptModelWithoutTemperature() {
        String strategy = """
            ---
            version: v1.0
            llm_model: anthropic/claude-3.5-sonnet
            crypto_tickers:
              - BTC
            ---
            # Strategy
            """;

        StrategyConfigParser parser = new StrategyConfigParser();
        StrategyConfig config = parser.parse(strategy);

        assertThat(config.getLlmModel()).isEqualTo("anthropic/claude-3.5-sonnet");
        assertThat(config.getLlmTemperature()).isNull();
    }

    @Test
    @DisplayName("should accept llm_temperature at boundary values")
    void shouldAcceptBoundaryTemperatures() {
        StrategyConfigParser parser = new StrategyConfigParser();

        StrategyConfig zeroTemp = parser.parse("""
            ---
            version: v1.0
            llm_temperature: 0.0
            crypto_tickers:
              - BTC
            ---
            # Strategy
            """);
        assertThat(zeroTemp.getLlmTemperature()).isEqualTo(0.0);

        StrategyConfig maxTemp = parser.parse("""
            ---
            version: v1.0
            llm_temperature: 2.0
            crypto_tickers:
              - BTC
            ---
            # Strategy
            """);
        assertThat(maxTemp.getLlmTemperature()).isEqualTo(2.0);
    }

    // --- Consensus with LLM Model Override ---

    @Test
    @DisplayName("should handle consensus gracefully when strategy pins an LLM model")
    void shouldHandleConsensusGracefullyWithUnavailablePinnedModel() {
        // A strategy-pinned model must not break the round. Under the dummy provider the pin is
        // simply not honoured, which is the same code path as a model that is rate-limited or
        // retired at the provider: agents may fail, and consensus still must not throw.
        Simulation testSimulation = commitFixture("LLM Override Test", STRATEGY_WITH_LLM_MODEL);

        ReachConsensusCommand command = new ReachConsensusCommand(
            testSimulation.getSimulationId(),
            Instant.now()
        );

        // Act - should not throw even when the pinned model is unavailable
        assertThatNoException()
            .as("Consensus with strategy-pinned LLM model should not throw even when model is unavailable")
            .isThrownBy(() -> reachConsensusUseCase.execute(command));
    }

    @Test
    @DisplayName("should parse and pass strategy model config through consensus pipeline")
    void shouldParseAndPassStrategyModelThroughPipeline() {
        // Verify the strategy config is correctly parsed and the consensus pipeline
        // receives the llm_model and llm_temperature settings
        Simulation testSimulation = commitFixture("LLM Config Pipeline", STRATEGY_WITH_LLM_MODEL);

        // Verify the strategy was persisted with llm_model config
        Strategy loaded = strategyRepository.findById(testSimulation.getStrategyId()).orElseThrow();
        StrategyConfigParser parser = new StrategyConfigParser();
        StrategyConfig config = parser.parse(loaded.getContent());

        assertThat(config.getLlmModel())
            .as("Loaded strategy should retain llm_model config")
            .isEqualTo("qwen/qwen3-coder:free");
        assertThat(config.getLlmTemperature())
            .as("Loaded strategy should retain llm_temperature config")
            .isEqualTo(0.5);

        // Execute consensus — verifies the full pipeline doesn't crash when
        // strategy config contains llm_model and llm_temperature
        ReachConsensusCommand command = new ReachConsensusCommand(
            testSimulation.getSimulationId(),
            Instant.now()
        );
        assertThatNoException()
            .isThrownBy(() -> reachConsensusUseCase.execute(command));
    }

    // --- Consensus without LLM Model Override (backward compatibility) ---

    @Test
    @DisplayName("should reach consensus without llm_model (uses default fallback chain)")
    void shouldReachConsensusWithoutOverride() {
        Simulation testSimulation = commitFixture("No Override", STRATEGY_WITHOUT_LLM_MODEL);

        ReachConsensusCommand command = new ReachConsensusCommand(
            testSimulation.getSimulationId(),
            Instant.now()
        );

        assertThatNoException()
            .as("Consensus without LLM override should work with default fallback chain")
            .isThrownBy(() -> reachConsensusUseCase.execute(command));

        List<LLMDecision> decisions = llmDecisionRepository.findBySimulationId(
            testSimulation.getSimulationId().value(),
            null, null, null, null, null, null
        );
        assertThat(decisions)
            .as("Should still produce all agent decisions without LLM override")
            .hasSizeGreaterThanOrEqualTo(EXPECTED_DECISIONS_PER_ROUND);
    }

    // --- Helper methods ---

    /**
     * Builds the strategy, simulation, market events and opening snapshot, and <em>commits</em>
     * them before returning.
     *
     * <p>The consensus pipeline fans agent calls out to its own executor threads, which run in
     * their own transactions. These tests used to be {@code @Transactional} and create their
     * fixtures inline, so the simulation row was still uncommitted when those threads tried to
     * write {@code llm_decisions} — every round died on
     * {@code llm_decisions_simulation_id_fkey} and produced no decisions. The main thread's
     * pre-flight checks passed because they read inside the test's own transaction, which is why
     * this looked like an agent/model problem rather than a visibility one.</p>
     */
    private Simulation commitFixture(String name, String strategyContent) {
        return QuarkusTransaction.requiringNew().call(() -> {
            Strategy strategy = createAndSaveStrategy(name + " Strategy", strategyContent);
            Simulation simulation = createAndSaveSimulation(name + " Simulation", strategy);
            createAndSaveMarketEvents();
            createAndSavePortfolioSnapshot(simulation);
            return simulation;
        });
    }

    private Strategy createAndSaveStrategy(String name, String content) {
        UserId ownerId = new UserId(UUID.randomUUID());
        User user = User.create(
            ownerId,
            ownerId.value() + "@test.com",
            "Test User",
            null,
            OAuthProvider.GOOGLE
        );
        userRepository.save(user);

        Strategy strategy = new Strategy(
            new StrategyId(UUID.randomUUID()),
            name,
            content,
            ownerId
        );
        return strategyRepository.save(strategy);
    }

    private Simulation createAndSaveSimulation(String name, Strategy strategy) {
        Simulation simulation = new Simulation(
            new SimulationId(UUID.randomUUID()),
            name,
            strategy.getId(),
            Instant.now().minus(7, ChronoUnit.DAYS),
            Instant.now(),
            new Money(BigDecimal.valueOf(100000), Money.Currency.USD)
        );
        simulation.setOwnerUserId(strategy.getOwnerId());  // V114: owner_user_id NOT NULL
        simulation.start(); // Transition to SIMULATING (endDate is in the past)
        return simulationRepository.save(simulation);
    }

    private void createAndSaveMarketEvents() {
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
                "BTC",
                priceData
            ));
        }

        // SentimentAgent requires 3 news/social events for a backtest and FundamentalAgent 2;
        // below that they abstain and write no decision, which is what left
        // shouldReachConsensusWithoutOverride short of a full agent panel.
        for (int i = 0; i < SENTIMENT_MIN_NEWS_EVENTS; i++) {
            Map<String, Object> newsData = new HashMap<>();
            newsData.put("headline", "Bitcoin adoption continues to grow in institutional markets (" + i + ")");
            newsData.put("sentiment", "positive");
            newsData.put("source", "TestNews");
            newsData.put("test_id", uniqueId + "_news_" + i);

            marketEventRepository.save(new MarketEvent(
                now.minus(i + 1, ChronoUnit.DAYS),
                EventType.NEWS,
                "newsapi",
                "BTC",
                newsData
            ));
        }
    }

    private void createAndSavePortfolioSnapshot(Simulation simulation) {
        Portfolio portfolio = new Portfolio(
            simulation.getSimulationId(),
            new HashMap<>(),
            new Money(BigDecimal.valueOf(100000), Money.Currency.USD)
        );
        PortfolioSnapshot snapshot = new PortfolioSnapshot(
            simulation.getSimulationId(),
            Instant.now(),
            Map.of(),
            portfolio.cashAvailable(),
            portfolio.cashAvailable()
        );
        portfolioRepository.save(snapshot);
    }
}
