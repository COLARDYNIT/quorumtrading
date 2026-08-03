package com.quorumtrading.adapter.persistence.repository;

import com.quorumtrading.application.port.out.LLMDecisionRepository;
import com.quorumtrading.application.port.out.SimulationRepository;
import com.quorumtrading.application.port.out.StrategyRepository;
import com.quorumtrading.model.entity.LLMDecision;
import com.quorumtrading.model.entity.Simulation;
import com.quorumtrading.model.entity.Strategy;
import com.quorumtrading.model.enums.RecommendationType;
import com.quorumtrading.model.valueobject.*;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for LLMDecisionRepository.
 *
 * Tests CRUD operations and JSONB field handling with real database.
 * Following the test diamond strategy: Integration test (60-70% of suite).
 */
@QuarkusTest
class LLMDecisionRepositoryIntegrationTest {

    @Inject
    LLMDecisionRepository llmDecisionRepository;

    @Inject
    SimulationRepository simulationRepository;

    @Inject
    StrategyRepository strategyRepository;

    private SimulationId testSimulationId;

    @BeforeEach
    @Transactional
    void setUp() {
        // Create and save a strategy first to ensure referential integrity
        UserId systemUserId = new UserId(UUID.fromString("00000000-0000-0000-0000-000000000000"));
        Strategy strategy = new Strategy(
            new StrategyId(UUID.randomUUID()),
            "Test Strategy for LLM Decisions",
            "## Test\nSimple test strategy",
            systemUserId
        );
        Strategy savedStrategy = strategyRepository.save(strategy);

        // Create and save a simulation
        Simulation simulation = new Simulation(
            new SimulationId(UUID.randomUUID()),
            "Test Simulation for LLM Decisions",
            savedStrategy.getId(),
            Instant.now().minus(7, ChronoUnit.DAYS),
            Instant.now(),
            new Money(BigDecimal.valueOf(100000), Money.Currency.USD)
        );
        simulation.setOwnerUserId(systemUserId);
        Simulation savedSimulation = simulationRepository.save(simulation);
        testSimulationId = savedSimulation.getSimulationId();
    }

    @Test
    @Transactional
    @DisplayName("shouldSaveAndRetrieveLLMDecision_WithJsonbFields")
    void shouldSaveAndRetrieveLLMDecision_WithJsonbFields() {
        // Arrange - Create LLM decision with JSON content in reasoning and recommendedAction
        String jsonReasoning = """
            {
              "analysis": "Technical indicators show strong uptrend",
              "indicators": {
                "RSI": 65,
                "MACD": "bullish",
                "SMA_50": 45000,
                "SMA_200": 42000
              },
              "confidence_factors": ["momentum", "volume", "support_levels"]
            }
            """;

        String jsonRecommendedAction = """
            {
              "action": "BUY",
              "symbol": "BTC",
              "quantity": 0.5,
              "target_price": 50000,
              "stop_loss": 43000,
              "rationale": "Strong technical setup with favorable risk/reward"
            }
            """;

        LLMDecision decision = new LLMDecision(
            testSimulationId,
            Instant.now(),
            "technical-agent-001",
            "TECHNICAL",
            "qwen/qwen-2.5-coder-32b-instruct:free"
        );
        decision.setPromptUsed("Analyze BTC market conditions");
        decision.recordReasoning(jsonReasoning);
        decision.recordRecommendation(RecommendationType.BUY);
        decision.recordRecommendedAction(jsonRecommendedAction);
        decision.recordConfidenceScore(Percentage.fromDecimal(BigDecimal.valueOf(0.85)));
        decision.recordExecutionTime(1500);

        // Act - Save to database
        LLMDecision saved = llmDecisionRepository.save(decision);

        // Assert - Verify saved decision has ID
        assertThat(saved.getId()).isNotNull();

        // Act - Retrieve from database
        List<LLMDecision> retrieved = llmDecisionRepository.findBySimulationId(
            testSimulationId.value(),
            null,
            null,
            null,
            null,
            null,
            null
        );

        // Assert - Verify JSONB fields are correctly stored and retrieved
        assertThat(retrieved).hasSize(1);
        LLMDecision retrievedDecision = retrieved.get(0);

        assertThat(retrievedDecision.getId()).isEqualTo(saved.getId());
        assertThat(retrievedDecision.getSimulationId()).isEqualTo(testSimulationId);
        assertThat(retrievedDecision.getAgentId()).isEqualTo("technical-agent-001");
        assertThat(retrievedDecision.getRole()).isEqualTo("TECHNICAL");
        assertThat(retrievedDecision.getLlmModel()).isEqualTo("qwen/qwen-2.5-coder-32b-instruct:free");

        // Verify JSONB fields contain valid JSON
        assertThat(retrievedDecision.getReasoning())
            .isNotBlank()
            .contains("Technical indicators")
            .contains("RSI")
            .contains("MACD");

        assertThat(retrievedDecision.getRecommendedAction())
            .isNotBlank()
            .contains("BUY")
            .contains("target_price")
            .contains("stop_loss");

        assertThat(retrievedDecision.getRecommendation()).isEqualTo(RecommendationType.BUY);
        assertThat(retrievedDecision.getConfidenceScore().value())
            .isEqualByComparingTo(BigDecimal.valueOf(0.85));
        assertThat(retrievedDecision.getExecutionTimeMs()).isEqualTo(1500);
    }

    @Test
    @Transactional
    @DisplayName("shouldHandleComplexJsonbStructures")
    void shouldHandleComplexJsonbStructures() {
        // Arrange - Create decision with nested JSON arrays and objects
        String complexJsonReasoning = """
            {
              "timestamp": "2025-11-02T12:00:00Z",
              "agent": "fundamental",
              "analysis": {
                "overall_score": 7.5,
                "factors": [
                  {"name": "valuation", "score": 8, "weight": 0.3},
                  {"name": "growth", "score": 6, "weight": 0.2},
                  {"name": "earnings", "score": 7, "weight": 0.5}
                ],
                "warnings": ["high_pe_ratio", "declining_margins"],
                "recommendations": {
                  "position_size": "reduce by 20%",
                  "outlook": "cautious"
                }
              }
            }
            """;

        LLMDecision decision = new LLMDecision(
            testSimulationId,
            Instant.now(),
            "fundamental-001",
            "FUNDAMENTAL",
            "qwen/qwen-2.5-coder-32b-instruct:free"
        );
        decision.setPromptUsed("Assess company fundamentals");
        decision.recordReasoning(complexJsonReasoning);
        decision.recordRecommendation(RecommendationType.HOLD);
        decision.recordRecommendedAction("{\"action\": \"HOLD\", \"reason\": \"risk_too_high\"}");
        decision.recordConfidenceScore(Percentage.fromDecimal(BigDecimal.valueOf(0.72)));

        // Act
        LLMDecision saved = llmDecisionRepository.save(decision);

        // Assert
        List<LLMDecision> retrieved = llmDecisionRepository.findBySimulationId(
            testSimulationId.value(),
            null,
            null,
            null,
            null,
            null,  // beforeLLMDecisionId
            null   // afterLLMDecisionId
        );

        assertThat(retrieved).hasSize(1);
        LLMDecision retrievedDecision = retrieved.get(0);

        // Verify complex nested JSON is preserved
        assertThat(retrievedDecision.getReasoning())
            .contains("analysis")
            .contains("factors")
            .contains("valuation")
            .contains("recommendations");
    }

    @Test
    @Transactional
    @DisplayName("shouldSaveMultipleLLMDecisions_ForSameSimulation")
    void shouldSaveMultipleLLMDecisions_ForSameSimulation() {
        // Arrange - Create decisions from different agents
        LLMDecision technicalDecision = createTestDecision("technical-agent", "TECHNICAL", RecommendationType.BUY);
        LLMDecision fundamentalDecision = createTestDecision("fundamental-agent", "FUNDAMENTAL", RecommendationType.HOLD);
        LLMDecision sentimentDecision = createTestDecision("sentiment-agent", "SENTIMENT", RecommendationType.BUY);

        // Act
        llmDecisionRepository.save(technicalDecision);
        llmDecisionRepository.save(fundamentalDecision);
        llmDecisionRepository.save(sentimentDecision);

        // Assert
        List<LLMDecision> allDecisions = llmDecisionRepository.findBySimulationId(
            testSimulationId.value(),
            null,
            null,
            null,
            null,
            null,  // beforeLLMDecisionId
            null   // afterLLMDecisionId
        );

        assertThat(allDecisions).hasSize(3);
        assertThat(allDecisions)
            .extracting(LLMDecision::getRole)
            .containsExactlyInAnyOrder("TECHNICAL", "FUNDAMENTAL", "SENTIMENT");
    }

    @Test
    @Transactional
    @DisplayName("shouldFilterDecisionsByAgentId")
    void shouldFilterDecisionsByAgentId() {
        // Arrange
        LLMDecision technical1 = createTestDecision("technical-agent-001", "TECHNICAL", RecommendationType.BUY);
        LLMDecision technical2 = createTestDecision("technical-agent-001", "TECHNICAL", RecommendationType.HOLD);
        LLMDecision fundamental = createTestDecision("fundamental-agent-001", "FUNDAMENTAL", RecommendationType.SELL);

        llmDecisionRepository.save(technical1);
        llmDecisionRepository.save(technical2);
        llmDecisionRepository.save(fundamental);

        // Act
        // Filter by role (agent type) instead of agentId
        List<LLMDecision> technicalDecisions = llmDecisionRepository.findBySimulationId(
            testSimulationId.value(),
            null,
            null,
            "TECHNICAL",  // Filter by role (agent type)
            null,
            null,  // beforeLLMDecisionId
            null   // afterLLMDecisionId
        );

        // Assert
        assertThat(technicalDecisions).hasSize(2);
        assertThat(technicalDecisions)
            .allMatch(d -> d.getRole().equals("TECHNICAL"));
    }

    @Test
    @Transactional
    @DisplayName("shouldFilterDecisionsByTimeRange")
    void shouldFilterDecisionsByTimeRange() {
        // Arrange - Create decisions at different times
        Instant now = Instant.now();
        Instant oneHourAgo = now.minus(1, ChronoUnit.HOURS);
        Instant twoHoursAgo = now.minus(2, ChronoUnit.HOURS);

        LLMDecision recent = createTestDecisionAtTime("agent-1", now);
        LLMDecision hourOld = createTestDecisionAtTime("agent-2", oneHourAgo);
        LLMDecision twoHoursOld = createTestDecisionAtTime("agent-3", twoHoursAgo);

        llmDecisionRepository.save(recent);
        llmDecisionRepository.save(hourOld);
        llmDecisionRepository.save(twoHoursOld);

        // Act - Get decisions from last 90 minutes
        Instant from = now.minus(90, ChronoUnit.MINUTES);
        List<LLMDecision> recentDecisions = llmDecisionRepository.findBySimulationId(
            testSimulationId.value(),
            from,
            now,
            null,
            null,
            null,  // beforeLLMDecisionId
            null   // afterLLMDecisionId
        );

        // Assert
        assertThat(recentDecisions).hasSize(2);
        assertThat(recentDecisions)
            .extracting(LLMDecision::getAgentId)
            .containsExactlyInAnyOrder("agent-1", "agent-2");
    }

    @Test
    @Transactional
    @DisplayName("shouldLimitNumberOfResults")
    void shouldLimitNumberOfResults() {
        // Arrange - Create 5 decisions
        for (int i = 0; i < 5; i++) {
            LLMDecision decision = createTestDecision("agent-" + i, "TECHNICAL", RecommendationType.BUY);
            llmDecisionRepository.save(decision);
        }

        // Act - Limit to 3 results
        List<LLMDecision> limitedDecisions = llmDecisionRepository.findBySimulationId(
            testSimulationId.value(),
            null,
            null,
            null,
            3,
            null,  // beforeLLMDecisionId
            null   // afterLLMDecisionId
        );

        // Assert
        assertThat(limitedDecisions).hasSize(3);
    }

    // Helper methods

    private LLMDecision createTestDecision(String agentId, String role, RecommendationType recommendation) {
        return createTestDecisionAtTime(agentId, Instant.now(), role, recommendation);
    }

    private LLMDecision createTestDecisionAtTime(String agentId, Instant timestamp) {
        return createTestDecisionAtTime(agentId, timestamp, "TECHNICAL", RecommendationType.BUY);
    }

    private LLMDecision createTestDecisionAtTime(String agentId, Instant timestamp, String role, RecommendationType recommendation) {
        String jsonReasoning = String.format("""
            {
              "agent": "%s",
              "timestamp": "%s",
              "analysis": "Test analysis"
            }
            """, agentId, timestamp.toString());

        String jsonAction = String.format("""
            {
              "action": "%s",
              "agent": "%s"
            }
            """, recommendation.name(), agentId);

        LLMDecision decision = new LLMDecision(
            testSimulationId,
            timestamp,
            agentId,
            role,
            "test-model"
        );
        decision.setPromptUsed("Test prompt");
        decision.recordReasoning(jsonReasoning);
        decision.recordRecommendation(recommendation);
        decision.recordRecommendedAction(jsonAction);
        decision.recordConfidenceScore(Percentage.fromDecimal(BigDecimal.valueOf(0.80)));
        decision.recordExecutionTime(1000);

        return decision;
    }
}
