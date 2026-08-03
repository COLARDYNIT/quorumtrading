package com.quorumtrading.adapter.persistence.repository;

import com.quorumtrading.application.port.out.StrategyRepository;
import com.quorumtrading.model.entity.Strategy;
import com.quorumtrading.model.valueobject.StrategyId;
import com.quorumtrading.model.valueobject.UserId;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration test for StrategyRepository.
 *
 * Tests CRUD operations and query methods with real database.
 * Following the test diamond strategy: Integration test (60-70% of suite).
 */
@QuarkusTest
class StrategyRepositoryIntegrationTest {

    @Inject
    StrategyRepository strategyRepository;

    // Note: Using H2 in-memory database with drop-and-create strategy
    // Each test class gets a clean database, tests use unique IDs to avoid conflicts

    @Test
    @Transactional
    @DisplayName("shouldSaveAndRetrieveStrategy_WhenValidStrategyProvided")
    void shouldSaveAndRetrieveStrategy_WhenValidStrategyProvided() {
        // Arrange
        Strategy strategy = createTestStrategy("Momentum Trading", "1.0.0");

        // Act
        Strategy saved = strategyRepository.save(strategy);
        Optional<Strategy> retrieved = strategyRepository.findById(saved.getId());

        // Assert
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().getId()).isEqualTo(saved.getId());
        // Now that database uses UUID primary keys, name is properly mapped
        assertThat(retrieved.get().getName()).isEqualTo(strategy.getName());
        assertThat(retrieved.get().getContent()).isEqualTo(strategy.getContent());
    }

    @Test
    @DisplayName("shouldReturnEmpty_WhenStrategyNotFound")
    void shouldReturnEmpty_WhenStrategyNotFound() {
        // Arrange
        StrategyId nonExistentId = new StrategyId(UUID.randomUUID());

        // Act
        Optional<Strategy> result = strategyRepository.findById(nonExistentId);

        // Assert
        assertThat(result).isEmpty();
    }

    @Test
    @Transactional
    @DisplayName("shouldDeleteStrategy_WhenStrategyExists")
    void shouldDeleteStrategy_WhenStrategyExists() {
        // Arrange
        Strategy strategy = createTestStrategy("Delete Test", "1.0.0");
        Strategy saved = strategyRepository.save(strategy);

        // Act
        boolean deleted = strategyRepository.deleteById(saved.getId());
        Optional<Strategy> retrieved = strategyRepository.findById(saved.getId());

        // Assert
        assertThat(deleted).isTrue();
        assertThat(retrieved).isEmpty();
    }

    @Test
    @DisplayName("shouldReturnFalse_WhenDeletingNonExistentStrategy")
    void shouldReturnFalse_WhenDeletingNonExistentStrategy() {
        // Arrange
        StrategyId nonExistentId = new StrategyId(UUID.randomUUID());

        // Act
        boolean deleted = strategyRepository.deleteById(nonExistentId);

        // Assert
        assertThat(deleted).isFalse();
    }

    @Test
    @Transactional
    @DisplayName("shouldFindAllStrategies_WhenMultipleStrategiesExist")
    void shouldFindAllStrategies_WhenMultipleStrategiesExist() {
        // Arrange
        Strategy strategy1 = createTestStrategy("Strategy 1", "1.0.0");
        Strategy strategy2 = createTestStrategy("Strategy 2", "2.0.0");
        Strategy strategy3 = createTestStrategy("Strategy 3", "3.0.0");

        strategyRepository.save(strategy1);
        strategyRepository.save(strategy2);
        strategyRepository.save(strategy3);

        // Act
        List<Strategy> allStrategies = strategyRepository.findAll();

        // Assert
        assertThat(allStrategies).hasSizeGreaterThanOrEqualTo(3);
        assertThat(allStrategies).extracting(Strategy::getId)
            .contains(strategy1.getId(), strategy2.getId(), strategy3.getId());
    }

    @Test
    @Transactional
    @DisplayName("shouldUpdateStrategy_WhenStrategyExists")
    void shouldUpdateStrategy_WhenStrategyExists() {
        // Arrange
        Strategy original = createTestStrategy("Original Name", "1.0.0");
        Strategy saved = strategyRepository.save(original);

        // Create updated version with same ID but different content
        UserId systemUserId = new UserId(UUID.fromString("00000000-0000-0000-0000-000000000000"));

        Strategy updated = new Strategy(
            saved.getId(),
            "Updated Name",
            "## Updated Strategy\n\nNew trading rules...",
            systemUserId
        );

        // Act
        Strategy result = strategyRepository.save(updated);
        Optional<Strategy> retrieved = strategyRepository.findById(saved.getId());

        // Assert
        assertThat(retrieved).isPresent();
        // Now that database uses UUID primary keys, name is properly mapped
        assertThat(retrieved.get().getName()).isEqualTo(updated.getName());
        assertThat(retrieved.get().getContent()).contains("Updated Strategy");
        assertThat(retrieved.get().getId()).isEqualTo(saved.getId());
    }

    @Test
    @Transactional
    @DisplayName("shouldReturnTrue_WhenStrategyExists")
    void shouldReturnTrue_WhenStrategyExists() {
        // Arrange
        Strategy strategy = createTestStrategy("Exists Test", "1.0.0");
        Strategy saved = strategyRepository.save(strategy);

        // Act
        boolean exists = strategyRepository.existsById(saved.getId());

        // Assert
        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("shouldReturnFalse_WhenStrategyDoesNotExist")
    void shouldReturnFalse_WhenStrategyDoesNotExist() {
        // Arrange
        StrategyId nonExistentId = new StrategyId(UUID.randomUUID());

        // Act
        boolean exists = strategyRepository.existsById(nonExistentId);

        // Assert
        assertThat(exists).isFalse();
    }

    @Test
    @Transactional
    @DisplayName("shouldPreserveMarkdownContent_WhenSavingStrategy")
    void shouldPreserveMarkdownContent_WhenSavingStrategy() {
        // Arrange
        String markdownContent = """
            # Trading Strategy

            ## Consensus Method
            MAJORITY

            ## Evaluation Interval Hours
            6

            ## Rules
            - Buy when RSI < 30
            - Sell when RSI > 70
            - Maximum position size: 10%
            """;

        UserId systemUserId = new UserId(UUID.fromString("00000000-0000-0000-0000-000000000000"));

        Strategy strategy = new Strategy(
            new StrategyId(UUID.randomUUID()),
            "RSI Strategy",
            markdownContent,
            systemUserId
        );

        // Act
        Strategy saved = strategyRepository.save(strategy);
        Optional<Strategy> retrieved = strategyRepository.findById(saved.getId());

        // Assert
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().getContent()).isEqualTo(markdownContent);
        assertThat(retrieved.get().getContent()).contains("# Trading Strategy");
        assertThat(retrieved.get().getContent()).contains("MAJORITY");
        assertThat(retrieved.get().getContent()).contains("RSI < 30");
    }

    @Test
    @DisplayName("shouldThrowException_WhenSavingStrategyWithBlankName")
    void shouldThrowException_WhenSavingStrategyWithBlankName() {
        // Act & Assert
        UserId systemUserId = new UserId(UUID.fromString("00000000-0000-0000-0000-000000000000"));

        assertThatThrownBy(() -> new Strategy(
                new StrategyId(UUID.randomUUID()),
                "",  // Blank name
                "## Strategy content",
                systemUserId
            ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Name cannot be blank");
    }

    @Test
    @DisplayName("shouldThrowException_WhenSavingStrategyWithNullContent")
    void shouldThrowException_WhenSavingStrategyWithNullContent() {
        // Act & Assert
        UserId systemUserId = new UserId(UUID.fromString("00000000-0000-0000-0000-000000000000"));

        assertThatThrownBy(() -> new Strategy(
                new StrategyId(UUID.randomUUID()),
                "Valid Name",
                null,  // Null content
                systemUserId
            ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Content cannot be null");
    }

    @Test
    @Transactional
    @DisplayName("shouldBatchFindStrategiesByIds_WhenIdsExist")
    void shouldBatchFindStrategiesByIds_WhenIdsExist() {
        // Arrange
        Strategy strategy1 = strategyRepository.save(createTestStrategy("Batch 1", "1.0.0"));
        Strategy strategy2 = strategyRepository.save(createTestStrategy("Batch 2", "1.0.0"));
        Strategy strategy3 = strategyRepository.save(createTestStrategy("Batch 3", "1.0.0"));
        StrategyId unknownId = new StrategyId(UUID.randomUUID());

        // Act: request two of the three saved strategies plus an unknown id
        List<Strategy> found = strategyRepository.findByIds(
            List.of(strategy1.getId(), strategy3.getId(), unknownId));

        // Assert: exactly the requested-and-existing rows come back
        assertThat(found).extracting(Strategy::getId)
            .containsExactlyInAnyOrder(strategy1.getId(), strategy3.getId());
        assertThat(found).extracting(Strategy::getId)
            .doesNotContain(strategy2.getId(), unknownId);
    }

    @Test
    @DisplayName("shouldReturnEmptyList_WhenFindByIdsGivenEmptyOrNull")
    void shouldReturnEmptyList_WhenFindByIdsGivenEmptyOrNull() {
        assertThat(strategyRepository.findByIds(List.of())).isEmpty();
        assertThat(strategyRepository.findByIds(null)).isEmpty();
    }

    // Helper method to create test strategy
    private Strategy createTestStrategy(String name, String version) {
        // Note: version parameter is ignored, kept for backward compatibility
        // Version is now managed by Hibernate @Version annotation
        UserId systemUserId = new UserId(UUID.fromString("00000000-0000-0000-0000-000000000000"));

        return new Strategy(
            new StrategyId(UUID.randomUUID()),
            name,
            String.format("""
                # %s

                ## Consensus Method
                WEIGHTED

                ## Evaluation Interval Hours
                12

                ## Rules
                - Technical analysis driven
                - Risk management focused
                - Diversified portfolio approach
                """, name),
            systemUserId
        );
    }
}
