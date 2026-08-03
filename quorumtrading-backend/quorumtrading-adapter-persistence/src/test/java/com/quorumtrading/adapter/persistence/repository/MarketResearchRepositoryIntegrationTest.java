package com.quorumtrading.adapter.persistence.repository;

import com.quorumtrading.application.port.out.MarketResearchRepository;
import com.quorumtrading.application.port.out.UserRepository;
import com.quorumtrading.model.entity.MarketResearch;
import com.quorumtrading.model.entity.User;
import com.quorumtrading.model.enums.OAuthProvider;
import com.quorumtrading.model.valueobject.*;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.quorumtrading.model.enums.InvestmentType.CRYPTO;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static com.quorumtrading.adapter.persistence.repository.TestConstants.TEST_USER_ID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for MarketResearchRepository.
 *
 * Tests CRUD operations and query methods with real database.
 * Following the test diamond strategy: Integration test (60-70% of suite).
 *
 * Uses PostgreSQL test database (quorumtrading_test), not H2.
 */
@QuarkusTest
class MarketResearchRepositoryIntegrationTest {

    @Inject
    MarketResearchRepository repository;

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
    @DisplayName("shouldSaveAndRetrieveMarketResearch_WhenValidResearchProvided")
    void shouldSaveAndRetrieveMarketResearch_WhenValidResearchProvided() {
        // Arrange
        MarketResearch research = createTestResearch(
            "Crypto Opportunity Scanner",
            Set.of(new Symbol("BTC", CRYPTO))
        );

        // Act
        MarketResearch saved = repository.save(research);
        Optional<MarketResearch> retrieved = repository.findById(saved.getId());

        // Assert
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().getId()).isEqualTo(saved.getId());
        assertThat(retrieved.get().getName()).isEqualTo("Crypto Opportunity Scanner");
        assertThat(retrieved.get().getTargetAssets()).hasSize(1);
        assertThat(retrieved.get().getStatus()).isEqualTo(ResearchStatus.ACTIVE);
    }

    @Test
    @DisplayName("shouldReturnEmpty_WhenResearchNotFound")
    void shouldReturnEmpty_WhenResearchNotFound() {
        // Arrange
        UUID nonExistentId = UUID.randomUUID();

        // Act
        Optional<MarketResearch> result = repository.findById(nonExistentId);

        // Assert
        assertThat(result).isEmpty();
    }

    @Test
    @Transactional
    @DisplayName("shouldFindAllMarketResearch_WhenMultipleResearchExist")
    void shouldFindAllMarketResearch_WhenMultipleResearchExist() {
        // Arrange
        MarketResearch research1 = createTestResearch(
            "Research 1",
            Set.of(new Symbol("BTC", CRYPTO))
        );
        MarketResearch research2 = createTestResearch(
            "Research 2",
            Set.of(new Symbol("ETH", CRYPTO))
        );

        repository.save(research1);
        repository.save(research2);

        // Act
        List<MarketResearch> allResearch = repository.findAll();

        // Assert
        assertThat(allResearch).hasSizeGreaterThanOrEqualTo(2);
        assertThat(allResearch).extracting(MarketResearch::getId)
            .contains(research1.getId(), research2.getId());
    }

    @Test
    @Transactional
    @DisplayName("shouldFindByStatus_WhenFilteringByActiveStatus")
    void shouldFindByStatus_WhenFilteringByActiveStatus() {
        // Arrange
        MarketResearch activeResearch = createTestResearch(
            "Active Research",
            Set.of(new Symbol("BTC", CRYPTO))
        );
        MarketResearch pausedResearch = createTestResearch(
            "Paused Research",
            Set.of(new Symbol("ETH", CRYPTO))
        );
        pausedResearch.pause();

        repository.save(activeResearch);
        repository.save(pausedResearch);

        // Act
        List<MarketResearch> activeResults = repository.findByStatus(ResearchStatus.ACTIVE);
        List<MarketResearch> pausedResults = repository.findByStatus(ResearchStatus.PAUSED);

        // Assert
        assertThat(activeResults).extracting(MarketResearch::getId)
            .contains(activeResearch.getId());
        assertThat(pausedResults).extracting(MarketResearch::getId)
            .contains(pausedResearch.getId());
    }

    @Test
    @Transactional
    @DisplayName("shouldFindDueForExecution_WhenNextScheduledRunIsPast")
    void shouldFindDueForExecution_WhenNextScheduledRunIsPast() {
        // Arrange
        MarketResearch dueResearch = createTestResearch(
            "Due Research",
            Set.of(new Symbol("BTC", CRYPTO))
        );
        MarketResearch notDueResearch = createTestResearch(
            "Not Due Research",
            Set.of(new Symbol("ETH", CRYPTO))
        );

        repository.save(dueResearch);
        repository.save(notDueResearch);

        // Simulate time passing beyond dueResearch's next scheduled run
        Instant futureTime = dueResearch.getNextScheduledRun().plus(1, ChronoUnit.HOURS);

        // Act
        List<MarketResearch> dueResults = repository.findDueForExecution(futureTime);

        // Assert
        assertThat(dueResults).extracting(MarketResearch::getId)
            .contains(dueResearch.getId());
    }

    @Test
    @Transactional
    @DisplayName("shouldNotFindDueForExecution_WhenStatusIsPaused")
    void shouldNotFindDueForExecution_WhenStatusIsPaused() {
        // Arrange
        MarketResearch pausedResearch = createTestResearch(
            "Paused Research",
            Set.of(new Symbol("BTC", CRYPTO))
        );
        pausedResearch.pause();
        repository.save(pausedResearch);

        Instant futureTime = pausedResearch.getNextScheduledRun().plus(1, ChronoUnit.HOURS);

        // Act
        List<MarketResearch> dueResults = repository.findDueForExecution(futureTime);

        // Assert
        assertThat(dueResults).extracting(MarketResearch::getId)
            .doesNotContain(pausedResearch.getId());
    }

    @Test
    @Transactional
    @DisplayName("shouldDeleteMarketResearch_WhenResearchExists")
    void shouldDeleteMarketResearch_WhenResearchExists() {
        // Arrange
        MarketResearch research = createTestResearch(
            "Delete Test",
            Set.of(new Symbol("BTC", CRYPTO))
        );
        MarketResearch saved = repository.save(research);

        // Act
        repository.delete(saved.getId());
        Optional<MarketResearch> retrieved = repository.findById(saved.getId());

        // Assert
        assertThat(retrieved).isEmpty();
    }

    @Test
    @Transactional
    @DisplayName("shouldReturnTrue_WhenResearchExists")
    void shouldReturnTrue_WhenResearchExists() {
        // Arrange
        MarketResearch research = createTestResearch(
            "Exists Test",
            Set.of(new Symbol("BTC", CRYPTO))
        );
        MarketResearch saved = repository.save(research);

        // Act
        boolean exists = repository.existsById(saved.getId());

        // Assert
        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("shouldReturnFalse_WhenResearchDoesNotExist")
    void shouldReturnFalse_WhenResearchDoesNotExist() {
        // Arrange
        UUID nonExistentId = UUID.randomUUID();

        // Act
        boolean exists = repository.existsById(nonExistentId);

        // Assert
        assertThat(exists).isFalse();
    }

    @Test
    @Transactional
    @DisplayName("shouldPreserveBacktestConfiguration_WhenAutoBacktestEnabled")
    void shouldPreserveBacktestConfiguration_WhenAutoBacktestEnabled() {
        // Arrange
        BacktestConfiguration backtestConfig = new BacktestConfiguration(
            Duration.ofDays(30),
            Instant.now().minus(60, ChronoUnit.DAYS),
            Instant.now().minus(30, ChronoUnit.DAYS)
        );

        MarketResearch research = new MarketResearch(
            "Backtest Research",
            "# Research with backtest\n\nFind opportunities and backtest them",
            Set.of(new Symbol("BTC", CRYPTO)),
            new ResearchInterval(7, ResearchInterval.Unit.DAYS),
            true,
            backtestConfig,
            TEST_USER_ID
        );

        // Act
        MarketResearch saved = repository.save(research);
        Optional<MarketResearch> retrieved = repository.findById(saved.getId());

        // Assert
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().isEnableAutoBacktest()).isTrue();
        assertThat(retrieved.get().getBacktestConfig()).isNotNull();
        assertThat(retrieved.get().getBacktestConfig().backtestLength()).isEqualTo(Duration.ofDays(30));
    }

    @Test
    @Transactional
    @DisplayName("shouldPreserveMultipleTargetAssets_WhenResearchHasMultipleSymbols")
    void shouldPreserveMultipleTargetAssets_WhenResearchHasMultipleSymbols() {
        // Arrange
        Set<Symbol> targetAssets = Set.of(
            new Symbol("BTC", CRYPTO),
            new Symbol("ETH", CRYPTO),
            new Symbol("SOL", CRYPTO)
        );

        MarketResearch research = createTestResearch("Multi-Asset Research", targetAssets);

        // Act
        MarketResearch saved = repository.save(research);
        Optional<MarketResearch> retrieved = repository.findById(saved.getId());

        // Assert
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().getTargetAssets()).hasSize(3);
        assertThat(retrieved.get().getTargetAssets())
            .extracting(Symbol::ticker)
            .containsExactlyInAnyOrder("BTC", "ETH", "SOL");
    }

    // Helper method to create test research
    private MarketResearch createTestResearch(String name, Set<Symbol> targetAssets) {
        return new MarketResearch(
            name,
            "# " + name + "\n\nAnalyze market for opportunities",
            targetAssets,
            new ResearchInterval(1, ResearchInterval.Unit.DAYS),
            false,
            null,
            TEST_USER_ID
        );
    }
}
