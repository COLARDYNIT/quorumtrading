package com.quorumtrading.adapter.persistence.repository;

import com.quorumtrading.application.port.out.MarketResearchRepository;
import com.quorumtrading.application.port.out.ResearchExecutionRepository;
import com.quorumtrading.application.port.out.UserRepository;
import com.quorumtrading.model.entity.MarketResearch;
import com.quorumtrading.model.entity.ResearchExecution;
import com.quorumtrading.model.entity.User;
import com.quorumtrading.model.enums.OAuthProvider;
import com.quorumtrading.model.valueobject.*;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static com.quorumtrading.model.enums.InvestmentType.CRYPTO;
import static com.quorumtrading.adapter.persistence.repository.TestConstants.TEST_USER_ID;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for ResearchExecutionRepository.
 *
 * Tests CRUD operations and query methods with real database.
 * Following the test diamond strategy: Integration test (60-70% of suite).
 *
 * Uses PostgreSQL test database (quorumtrading_test), not H2.
 */
@QuarkusTest
class ResearchExecutionRepositoryIntegrationTest {

    @Inject
    ResearchExecutionRepository repository;

    @Inject
    MarketResearchRepository marketResearchRepository;

    @Inject
    UserRepository userRepository;

    @BeforeEach
    @Transactional
    void setUp() {
        // Ensure test user exists for foreign key constraint
        if (userRepository.findById(TEST_USER_ID).isEmpty()) {
            User testUser = User.create(
                TEST_USER_ID,
                "test@example.com",
                "Test User",
                null,
                OAuthProvider.GOOGLE
            );
            userRepository.save(testUser);
        }
    }

    @Test
    @Transactional
    @DisplayName("shouldSaveAndRetrieveExecution_WhenValidExecutionProvided")
    void shouldSaveAndRetrieveExecution_WhenValidExecutionProvided() {
        // Arrange
        MarketResearch research = createAndSaveTestResearch("Test Research");
        ResearchExecution execution = createTestExecution(
            research.getId(),
            ResearchOutcome.STRATEGY_CREATED,
            new ViabilityScore(new BigDecimal("85.5"))
        );

        // Act
        ResearchExecution saved = repository.save(execution);
        Optional<ResearchExecution> retrieved = repository.findById(saved.getId());

        // Assert
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().getId()).isEqualTo(saved.getId());
        assertThat(retrieved.get().getMarketResearchId()).isEqualTo(research.getId());
        assertThat(retrieved.get().getOutcome()).isEqualTo(ResearchOutcome.STRATEGY_CREATED);
        assertThat(retrieved.get().getViabilityScore().value()).isEqualByComparingTo(new BigDecimal("85.50"));
    }

    @Test
    @DisplayName("shouldReturnEmpty_WhenExecutionNotFound")
    void shouldReturnEmpty_WhenExecutionNotFound() {
        // Arrange
        UUID nonExistentId = UUID.randomUUID();

        // Act
        Optional<ResearchExecution> result = repository.findById(nonExistentId);

        // Assert
        assertThat(result).isEmpty();
    }

    @Test
    @Transactional
    @DisplayName("shouldFindByMarketResearchId_WhenMultipleExecutionsExist")
    void shouldFindByMarketResearchId_WhenMultipleExecutionsExist() {
        // Arrange
        MarketResearch research = createAndSaveTestResearch("Test Research");
        ResearchExecution execution1 = createTestExecution(
            research.getId(),
            ResearchOutcome.STRATEGY_CREATED,
            new ViabilityScore(new BigDecimal("80.0"))
        );
        ResearchExecution execution2 = createTestExecution(
            research.getId(),
            ResearchOutcome.NO_OPPORTUNITY,
            null
        );

        repository.save(execution1);
        repository.save(execution2);

        // Act
        List<ResearchExecution> executions = repository.findByMarketResearchId(research.getId());

        // Assert
        assertThat(executions).hasSize(2);
        assertThat(executions).extracting(ResearchExecution::getId)
            .contains(execution1.getId(), execution2.getId());
    }

    @Test
    @Transactional
    @DisplayName("shouldFindByOutcome_WhenFilteringByStrategyCreated")
    void shouldFindByOutcome_WhenFilteringByStrategyCreated() {
        // Arrange
        MarketResearch research = createAndSaveTestResearch("Test Research");
        ResearchExecution createdExecution = createTestExecution(
            research.getId(),
            ResearchOutcome.STRATEGY_CREATED,
            new ViabilityScore(new BigDecimal("90.0"))
        );
        ResearchExecution failedExecution = createTestExecution(
            research.getId(),
            ResearchOutcome.FAILED,
            null
        );

        repository.save(createdExecution);
        repository.save(failedExecution);

        // Act
        List<ResearchExecution> createdResults = repository.findByOutcome(ResearchOutcome.STRATEGY_CREATED);
        List<ResearchExecution> failedResults = repository.findByOutcome(ResearchOutcome.FAILED);

        // Assert
        assertThat(createdResults).extracting(ResearchExecution::getId)
            .contains(createdExecution.getId());
        assertThat(failedResults).extracting(ResearchExecution::getId)
            .contains(failedExecution.getId());
    }

    @Test
    @Transactional
    @DisplayName("shouldFindLatestExecution_WhenMultipleExecutionsExist")
    void shouldFindLatestExecution_WhenMultipleExecutionsExist() throws InterruptedException {
        // Arrange
        MarketResearch research = createAndSaveTestResearch("Test Research");
        ResearchExecution execution1 = createTestExecution(
            research.getId(),
            ResearchOutcome.NO_OPPORTUNITY,
            null
        );
        repository.save(execution1);

        // Wait to ensure different timestamps
        Thread.sleep(10);

        ResearchExecution execution2 = createTestExecution(
            research.getId(),
            ResearchOutcome.STRATEGY_CREATED,
            new ViabilityScore(new BigDecimal("85.0"))
        );
        repository.save(execution2);

        // Act
        Optional<ResearchExecution> latest = repository.findLatestByMarketResearchId(research.getId());

        // Assert
        assertThat(latest).isPresent();
        assertThat(latest.get().getId()).isEqualTo(execution2.getId());
    }

    @Test
    @Transactional
    @DisplayName("shouldDeleteExecution_WhenExecutionExists")
    void shouldDeleteExecution_WhenExecutionExists() {
        // Arrange
        MarketResearch research = createAndSaveTestResearch("Test Research");
        ResearchExecution execution = createTestExecution(
            research.getId(),
            ResearchOutcome.STRATEGY_CREATED,
            new ViabilityScore(new BigDecimal("75.0"))
        );
        ResearchExecution saved = repository.save(execution);

        // Act
        repository.delete(saved.getId());
        Optional<ResearchExecution> retrieved = repository.findById(saved.getId());

        // Assert
        assertThat(retrieved).isEmpty();
    }

    @Test
    @Transactional
    @DisplayName("shouldAllowNullStrategyAndSimulationIds_WhenNotYetSet")
    void shouldAllowNullStrategyAndSimulationIds_WhenNotYetSet() {
        // Arrange
        MarketResearch research = createAndSaveTestResearch("Test Research");

        // Note: In real usage, strategyId and simulationId would be set after creating actual Strategy/Simulation entities
        // For this test, we verify that NULL values are allowed (they can be set later)
        ResearchExecution execution = new ResearchExecution(
            research.getId(),
            ResearchOutcome.STRATEGY_CREATED,
            "Strategy created but IDs not yet populated",
            new ViabilityScore(new BigDecimal("92.5")),
            null,  // Would be set after creating strategy
            null   // Would be set after creating simulation
        );

        // Act
        ResearchExecution saved = repository.save(execution);
        Optional<ResearchExecution> retrieved = repository.findById(saved.getId());

        // Assert
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().getStrategyId()).isNull();
        assertThat(retrieved.get().getSimulationId()).isNull();
        assertThat(retrieved.get().getViabilityScore()).isNotNull();
    }

    @Test
    @Transactional
    @DisplayName("shouldPreserveNullViabilityScore_WhenOutcomeIsNotStrategyCreated")
    void shouldPreserveNullViabilityScore_WhenOutcomeIsNotStrategyCreated() {
        // Arrange
        MarketResearch research = createAndSaveTestResearch("Test Research");
        ResearchExecution execution = createTestExecution(
            research.getId(),
            ResearchOutcome.NO_OPPORTUNITY,
            null
        );

        // Act
        ResearchExecution saved = repository.save(execution);
        Optional<ResearchExecution> retrieved = repository.findById(saved.getId());

        // Assert
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().getViabilityScore()).isNull();
        assertThat(retrieved.get().getStrategyId()).isNull();
        assertThat(retrieved.get().getSimulationId()).isNull();
    }

    // Helper methods

    private MarketResearch createAndSaveTestResearch(String name) {
        MarketResearch research = new MarketResearch(
            name,
            "# " + name + "\n\nAnalyze market for opportunities",
            Set.of(new Symbol("BTC", CRYPTO)),
            new ResearchInterval(1, ResearchInterval.Unit.DAYS),
            false,
            null,
            TEST_USER_ID
        );
        return marketResearchRepository.save(research);
    }

    private ResearchExecution createTestExecution(
        UUID marketResearchId,
        ResearchOutcome outcome,
        ViabilityScore viabilityScore
    ) {
        // Don't set strategyId and simulationId in tests - they require actual foreign keys
        // Only set them in tests that specifically test these fields
        return new ResearchExecution(
            marketResearchId,
            outcome,
            "Test conclusion for " + outcome,
            viabilityScore,
            null,
            null
        );
    }
}
