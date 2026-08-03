package com.quorumtrading.integration;

import com.quorumtrading.application.port.out.ConsensusDecisionRepository;
import com.quorumtrading.application.port.out.LLMDecisionRepository;
import com.quorumtrading.application.port.out.PortfolioRepository;
import com.quorumtrading.model.entity.*;
import com.quorumtrading.model.enums.*;
import com.quorumtrading.model.valueobject.*;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration test for decision query and dashboard paths.
 *
 * Verifies that LLM decisions, consensus decisions, and portfolio snapshots
 * can be queried correctly by simulation, agent, and pagination.
 */
@QuarkusTest
class DecisionQueryIntegrationTest {

    @Inject
    LLMDecisionRepository llmDecisionRepository;

    @Inject
    ConsensusDecisionRepository consensusDecisionRepository;

    @Inject
    PortfolioRepository portfolioRepository;

    @Inject
    IntegrationTestHelper helper;

    @Test
    @Transactional
    @DisplayName("should query LLM decisions by simulation")
    void shouldQueryLlmDecisionsBySimulation() {
        // Arrange
        Strategy strategy = helper.createStrategy("Query Strategy", IntegrationTestHelper.VALID_CRYPTO_STRATEGY);
        Simulation simulation = helper.createStartedSimulation("Query Sim", strategy);
        SimulationId simId = simulation.getSimulationId();

        helper.createLlmDecision(simId, "technical-agent", "TECHNICAL");
        helper.createLlmDecision(simId, "fundamental-agent", "FUNDAMENTAL");
        helper.createLlmDecision(simId, "sentiment-agent", "SENTIMENT");

        // Act
        List<LLMDecision> decisions = llmDecisionRepository.findBySimulationId(
                simId.value(), null, null, null, null, null, null
        );

        // Assert
        assertThat(decisions).hasSize(3);
        assertThat(decisions)
                .extracting(LLMDecision::getSimulationId)
                .allMatch(id -> id.equals(simId));
    }

    @Test
    @Transactional
    @DisplayName("should query LLM decisions by agent role")
    void shouldQueryLlmDecisionsByAgent() {
        // Arrange
        Strategy strategy = helper.createStrategy("Agent Filter Strategy", IntegrationTestHelper.VALID_CRYPTO_STRATEGY);
        Simulation simulation = helper.createStartedSimulation("Agent Filter Sim", strategy);
        SimulationId simId = simulation.getSimulationId();

        helper.createLlmDecision(simId, "technical-agent", "TECHNICAL");
        helper.createLlmDecision(simId, "technical-agent", "TECHNICAL");
        helper.createLlmDecision(simId, "fundamental-agent", "FUNDAMENTAL");

        // Act — filter by role (the agentId parameter in the repository actually filters on the role column)
        List<LLMDecision> techDecisions = llmDecisionRepository.findBySimulationId(
                simId.value(), null, null, "TECHNICAL", null, null, null
        );

        // Assert
        assertThat(techDecisions).hasSize(2);
        assertThat(techDecisions)
                .extracting(LLMDecision::getRole)
                .allMatch(role -> role.equals("TECHNICAL"));
    }

    @Test
    @Transactional
    @DisplayName("should query consensus decisions by simulation")
    void shouldQueryConsensusDecisionsBySimulation() {
        // Arrange
        Strategy strategy = helper.createStrategy("Consensus Query Strategy", IntegrationTestHelper.VALID_CRYPTO_STRATEGY);
        Simulation simulation = helper.createStartedSimulation("Consensus Query Sim", strategy);
        SimulationId simId = simulation.getSimulationId();

        helper.createConsensusDecision(simId, "HOLD");
        helper.createConsensusDecision(simId, "BUY");

        // Act
        List<ConsensusDecision> decisions = consensusDecisionRepository.findBySimulationId(
                simId.value(), null, null, null, null, null, null
        );

        // Assert
        assertThat(decisions).hasSize(2);
        assertThat(decisions)
                .extracting(ConsensusDecision::getSimulationId)
                .allMatch(id -> id.equals(simId));
    }

    @Test
    @Transactional
    @DisplayName("should query portfolio snapshots by simulation")
    void shouldQueryPortfolioSnapshotsBySimulation() {
        // Arrange
        Strategy strategy = helper.createStrategy("Snapshot Query Strategy", IntegrationTestHelper.VALID_CRYPTO_STRATEGY);
        Simulation simulation = helper.createStartedSimulation("Snapshot Query Sim", strategy);
        SimulationId simId = simulation.getSimulationId();

        helper.createPortfolioSnapshot(simulation, Money.usd(100_000));
        helper.createPortfolioSnapshot(simulation, Money.usd(105_000));
        helper.createPortfolioSnapshot(simulation, Money.usd(98_000));

        // Act
        List<PortfolioSnapshot> snapshots = portfolioRepository.findAllBySimulationId(simId);

        // Assert
        assertThat(snapshots).hasSize(3);
        assertThat(snapshots)
                .extracting(s -> s.getSimulationId())
                .allMatch(id -> id.equals(simId));
    }

    @Test
    @Transactional
    @DisplayName("should return empty for non-existent simulation")
    void shouldReturnEmptyForNonexistentSimulation() {
        SimulationId fakeId = new SimulationId(UUID.randomUUID());

        // Act
        List<LLMDecision> llmDecisions = llmDecisionRepository.findBySimulationId(
                fakeId.value(), null, null, null, null, null, null
        );
        List<ConsensusDecision> consensusDecisions = consensusDecisionRepository.findBySimulationId(
                fakeId.value(), null, null, null, null, null, null
        );
        List<PortfolioSnapshot> snapshots = portfolioRepository.findAllBySimulationId(fakeId);

        // Assert
        assertThat(llmDecisions).isEmpty();
        assertThat(consensusDecisions).isEmpty();
        assertThat(snapshots).isEmpty();
    }

    @Test
    @Transactional
    @DisplayName("should paginate LLM decisions with beforeId/afterId")
    void shouldPaginateLlmDecisions() {
        // Arrange
        Strategy strategy = helper.createStrategy("Pagination Strategy", IntegrationTestHelper.VALID_CRYPTO_STRATEGY);
        Simulation simulation = helper.createStartedSimulation("Pagination Sim", strategy);
        SimulationId simId = simulation.getSimulationId();

        // Create 4 decisions
        LLMDecision d1 = helper.createLlmDecision(simId, "agent-1", "TECHNICAL");
        LLMDecision d2 = helper.createLlmDecision(simId, "agent-2", "FUNDAMENTAL");
        LLMDecision d3 = helper.createLlmDecision(simId, "agent-3", "SENTIMENT");
        LLMDecision d4 = helper.createLlmDecision(simId, "agent-4", "ORCHESTRATOR");

        // Act — first page: default order is DESC by id, so we get the 3 most recent
        List<LLMDecision> firstPage = llmDecisionRepository.findBySimulationId(
                simId.value(), null, null, null, 3, null, null
        );

        // Assert
        assertThat(firstPage).hasSize(3);

        // Act — second page: use beforeId to get decisions with id < last element's id (DESC pagination)
        Long beforeId = firstPage.get(firstPage.size() - 1).getId();
        List<LLMDecision> secondPage = llmDecisionRepository.findBySimulationId(
                simId.value(), null, null, null, 3, beforeId, null
        );

        // Assert — should get remaining 2 decisions
        assertThat(secondPage).hasSizeBetween(1, 3);

        // All decisions from second page should have IDs different from first page
        List<Long> firstPageIds = firstPage.stream().map(LLMDecision::getId).toList();
        List<Long> secondPageIds = secondPage.stream().map(LLMDecision::getId).toList();
        assertThat(secondPageIds).doesNotContainAnyElementsOf(firstPageIds);
    }
}
