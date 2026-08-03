package com.quorumtrading.integration;

import com.quorumtrading.application.port.out.*;
import com.quorumtrading.application.usecase.ReachConsensusUseCase;
import com.quorumtrading.application.usecase.command.ReachConsensusCommand;
import com.quorumtrading.model.entity.*;
import com.quorumtrading.model.enums.EventType;
import com.quorumtrading.model.marketdata.MarketEvent;
import com.quorumtrading.model.valueobject.*;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * Integration test for ReachConsensusUseCase.
 *
 * Tests the complete consensus flow from strategy creation to final consensus decision,
 * verifying that all database operations work correctly and the CDI context is properly managed.
 *
 * Following the test diamond strategy: Integration test (60-70% of suite).
 */
// No @Tag("llm"): this asserts wiring, not model behaviour, and DummyLlmTestProfile answers it
// offline and deterministically. The tag was a misdiagnosis of three unrelated fixture faults
// (unseeded owner, PENDING simulation, ticker mismatch) and kept the whole class out of CI.
@QuarkusTest
@TestProfile(DummyLlmTestProfile.class)
class ReachConsensusIntegrationTest {

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

    /**
     * TECHNICAL + FUNDAMENTAL + SENTIMENT + ORCHESTRATOR. The assertions here used to demand 5
     * ("4 agents + 1 orchestrator"), but {@link com.quorumtrading.model.enums.AgentRole} defines
     * three analyst roles, so 5 was never reachable — a full panel produces exactly 4 decisions.
     */
    private static final int EXPECTED_DECISIONS_PER_ROUND = 4;

    private Strategy testStrategy;
    private Simulation testSimulation;
    private List<MarketEvent> testMarketEvents;
    private Portfolio testPortfolio;
    private PortfolioSnapshot testPortfolioSnapshot;

    @Inject
    IntegrationTestHelper helper;

    @BeforeEach
    @Transactional
    void setUp() {
        // The owner has to exist: orchestrator_strategies.owner_id is a foreign key into users.
        // This built a Strategy around a random UserId that was never inserted, so setUp blew up
        // on a constraint violation the moment that FK was added — and because the class is
        // @Tag("llm") and excluded from CI, nothing caught it. Note the failure happens before
        // any LLM call, so this test never needed a model to stay honest.
        UserId ownerId = helper.createUser().getId();

        // Create and save a test strategy
        testStrategy = new Strategy(
            new StrategyId(UUID.randomUUID()),
            "Test Consensus Strategy",
            
            IntegrationTestHelper.VALID_CRYPTO_STRATEGY,
            ownerId
        );
        testStrategy = strategyRepository.save(testStrategy);

        // Create and save a test simulation
        testSimulation = new Simulation(
            new SimulationId(UUID.randomUUID()),
            "Test Consensus Simulation",
            testStrategy.getId(),
            Instant.now().minus(7, ChronoUnit.DAYS),
            Instant.now(),
            new Money(BigDecimal.valueOf(100000), Money.Currency.USD)
        );
        testSimulation.setOwnerUserId(testStrategy.getOwnerId());  // V114: owner_user_id NOT NULL
        // ReachConsensusService skips anything that is not SIMULATING or LIVE, and a freshly
        // constructed Simulation is PENDING. Without this the use case returned in ~34ms having
        // done nothing, and every assertion here read as "the agents produced no decisions" —
        // which looked like an LLM problem and got the class tagged @Tag("llm") and excluded
        // from CI. It never needed a model; it needed a started simulation.
        testSimulation.start();
        testSimulation = simulationRepository.save(testSimulation);

        // Create and save test market events
        testMarketEvents = createTestMarketEvents();
        testMarketEvents = testMarketEvents.stream()
            .map(marketEventRepository::save)
            .toList();

        // Create test portfolio
        testPortfolio = new Portfolio(
            testSimulation.getSimulationId(),
            new HashMap<>(),
            new Money(BigDecimal.valueOf(100000), Money.Currency.USD)
        );

        // Create and save initial portfolio snapshot
        testPortfolioSnapshot = new PortfolioSnapshot(
            testSimulation.getSimulationId(),
            Instant.now(),
            java.util.Map.of(),  // Empty positions
            testPortfolio.cashAvailable(),
            testPortfolio.cashAvailable()
        );
        testPortfolioSnapshot = portfolioRepository.save(testPortfolioSnapshot);
    }

    @Test
    @Transactional
    @DisplayName("shouldReachConsensus_WhenValidCommandProvided")
    void shouldReachConsensus_WhenValidCommandProvided() {
        // Arrange
        ReachConsensusCommand command = new ReachConsensusCommand(
            testSimulation.getSimulationId(),
            java.time.Instant.now()
        );

        // Act
        assertThatNoException()
            .as("Consensus should execute without throwing exceptions")
            .isThrownBy(() -> reachConsensusUseCase.execute(command));

        // Assert
        // Verify LLM decisions were created (4 agents + 1 orchestrator = 5 total)
        List<LLMDecision> decisions = llmDecisionRepository.findBySimulationId(
            testSimulation.getSimulationId().value(),
            null,
            null,
            null,
            null,
            null,  // beforeLLMDecisionId
            null   // afterLLMDecisionId
        );
        assertThat(decisions)
            .as("Should create decisions from all agents and orchestrator")
            .hasSizeGreaterThanOrEqualTo(EXPECTED_DECISIONS_PER_ROUND);

        // Verify consensus decision was created. `executed` must stay null: passing false filters
        // to decisions that were NOT traded, and a BUY that the mock broker fills is stored with
        // executed=true — so the old `false` argument asked for the empty set.
        List<ConsensusDecision> consensusDecisions = consensusDecisionRepository
            .findBySimulationId(testSimulation.getSimulationId().value(), null, null, null, null, null, 100);
        assertThat(consensusDecisions)
            .as("Should create at least one consensus decision")
            .isNotEmpty();

        ConsensusDecision consensusDecision = consensusDecisions.get(0);
        assertThat(consensusDecision.getSimulationId()).isEqualTo(testSimulation.getSimulationId());
        assertThat(consensusDecision.getFinalAction()).isNotNull();
        assertThat(consensusDecision.getDecisionTimestamp()).isNotNull();
    }

    @Test
    @Transactional
    @DisplayName("shouldHandleDatabaseAccess_WhenExecutingFromWebSocketContext")
    void shouldHandleDatabaseAccess_WhenExecutingFromWebSocketContext() {
        // Arrange
        ReachConsensusCommand command = new ReachConsensusCommand(
            testSimulation.getSimulationId(),
            java.time.Instant.now()
        );

        // Act - Simulate WebSocket context by executing on a different thread
        // This tests the CDI context management fix we implemented
        assertThatNoException()
            .as("Should handle database access correctly even when called from async context")
            .isThrownBy(() -> {
                java.util.concurrent.CompletableFuture.runAsync(() -> {
                    reachConsensusUseCase.execute(command);
                }).join();
            });

        // Assert
        List<LLMDecision> decisions = llmDecisionRepository.findBySimulationId(
            testSimulation.getSimulationId().value(),
            null,
            null,
            null,
            null,
            null,  // beforeLLMDecisionId
            null   // afterLLMDecisionId
        );
        assertThat(decisions)
            .as("Should successfully save decisions from async context")
            .hasSizeGreaterThanOrEqualTo(EXPECTED_DECISIONS_PER_ROUND);
    }

    @Test
    @Transactional
    @DisplayName("shouldCreateAgentDecisions_WhenConsensusExecutes")
    void shouldCreateAgentDecisions_WhenConsensusExecutes() {
        // Arrange
        ReachConsensusCommand command = new ReachConsensusCommand(
            testSimulation.getSimulationId(),
            java.time.Instant.now()
        );

        // Act
        reachConsensusUseCase.execute(command);

        // Assert
        List<LLMDecision> decisions = llmDecisionRepository.findBySimulationId(
            testSimulation.getSimulationId().value(),
            null,
            null,
            null,
            null,
            null,  // beforeLLMDecisionId
            null   // afterLLMDecisionId
        );

        // Verify we have decisions from all agent types
        Set<String> agentRoles = decisions.stream()
            .map(LLMDecision::getRole)
            .collect(java.util.stream.Collectors.toSet());

        assertThat(agentRoles)
            .as("Should have decisions from Technical, Fundamental, Sentiment, and Orchestrator agents")
            .hasSizeGreaterThanOrEqualTo(4);
    }

    @Test
    @Transactional
    @DisplayName("shouldLinkDecisionsToConsensus_WhenConsensusCreated")
    void shouldLinkDecisionsToConsensus_WhenConsensusCreated() {
        // Arrange
        ReachConsensusCommand command = new ReachConsensusCommand(
            testSimulation.getSimulationId(),
            java.time.Instant.now()
        );

        // Act
        reachConsensusUseCase.execute(command);

        // Assert
        List<ConsensusDecision> consensusDecisions = consensusDecisionRepository
            .findBySimulationId(testSimulation.getSimulationId().value(), null, null, null, null, null, null);
        assertThat(consensusDecisions).hasSize(1);

        ConsensusDecision consensus = consensusDecisions.get(0);
        assertThat(consensus.getLlmDecisionIds())
            .as("Consensus should reference all agent decisions")
            .hasSizeGreaterThanOrEqualTo(EXPECTED_DECISIONS_PER_ROUND);
    }

    // Helper methods

    /**
     * The strategy fixture declares BTC. These events used to be stamped "TST", so the
     * market-data pre-flight in {@code ReachConsensusService} found zero PRICE events for BTC,
     * recorded a "skipped" consensus decision and returned — producing no agent decisions at all.
     * That read as an LLM failure and is the last of the three causes behind the {@code @Tag("llm")}.
     * The ticker here must stay in sync with {@link IntegrationTestHelper#VALID_CRYPTO_STRATEGY}.
     */
    private static final String FIXTURE_TICKER = "BTC";

    /** Mirrors {@code SentimentAgent.MIN_NEWS_EVENTS_FOR_BACKTEST} — the strictest agent gate. */
    private static final int SENTIMENT_MIN_NEWS_EVENTS = 3;

    private List<MarketEvent> createTestMarketEvents() {
        List<MarketEvent> events = new ArrayList<>();
        Instant now = Instant.now();

        // Add unique identifier to ensure each test run creates unique market events
        // This prevents duplicate content_hash violations in the database
        String uniqueId = UUID.randomUUID().toString();

        // Create 10 price update events over the last 7 days
        for (int i = 0; i < 10; i++) {
            Map<String, Object> priceData = new HashMap<>();
            priceData.put("price", BigDecimal.valueOf(45000 + (i * 100)));
            priceData.put("volume", BigDecimal.valueOf(1000000));
            priceData.put("change24h", BigDecimal.valueOf(-2.5 + (i * 0.5)));
            priceData.put("test_id", uniqueId + "_price_" + i);  // Unique per test run

            events.add(new MarketEvent(
                now.minus(i * 16, ChronoUnit.HOURS),
                EventType.PRICE,
                "coinbase",
                FIXTURE_TICKER,
                priceData
            ));
        }

        // News events. SentimentAgent needs MIN_NEWS_EVENTS_FOR_BACKTEST=3 and FundamentalAgent
        // needs 2, or they abstain and write no decision at all. A single event here meant both
        // agents dropped out — and because market events are keyed by ticker rather than by
        // simulation, they accumulated across test methods, so how many agents participated
        // depended on execution order: the first test saw 1 event and got 2 decisions, the third
        // saw 3 and got 4. Seeding the sentiment threshold up front makes every method see a full
        // panel regardless of order.
        for (int i = 0; i < SENTIMENT_MIN_NEWS_EVENTS; i++) {
            Map<String, Object> newsData = new HashMap<>();
            newsData.put("headline", "Test token adoption continues to grow (" + i + ")");
            newsData.put("sentiment", "positive");
            newsData.put("source", "TestNews");
            newsData.put("test_id", uniqueId + "_news_" + i);  // Unique per test run

            events.add(new MarketEvent(
                now.minus(i + 1, ChronoUnit.DAYS),
                EventType.NEWS,
                "newsapi",
                FIXTURE_TICKER,
                newsData
            ));
        }

        return events;
    }
}
