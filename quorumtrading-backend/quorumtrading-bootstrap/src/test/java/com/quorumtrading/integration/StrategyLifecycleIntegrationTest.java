package com.quorumtrading.integration;

import com.quorumtrading.application.port.out.SimulationRepository;
import com.quorumtrading.application.port.out.StrategyRepository;
import com.quorumtrading.application.usecase.CreateStrategyUseCase;
import com.quorumtrading.application.usecase.DeleteStrategyUseCase;
import com.quorumtrading.application.usecase.UpdateStrategyUseCase;
import com.quorumtrading.application.usecase.command.CreateStrategyCommand;
import com.quorumtrading.application.usecase.command.DeleteStrategyCommand;
import com.quorumtrading.application.usecase.command.UpdateStrategyCommand;
import com.quorumtrading.model.entity.Simulation;
import com.quorumtrading.model.entity.Strategy;
import com.quorumtrading.model.entity.User;
import com.quorumtrading.model.orchestration.StrategyConfig;
import com.quorumtrading.model.orchestration.StrategyConfigParser;
import com.quorumtrading.model.valueobject.StrategyId;
import com.quorumtrading.model.valueobject.UserId;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration test for Strategy create/update/delete lifecycle.
 *
 * Verifies that:
 * 1. Strategies are correctly created and parsed via the use case
 * 2. Invalid YAML content is rejected
 * 3. Stock strategies require live_schedule configuration
 * 4. Updates respect ticker immutability when simulations exist
 * 5. Delete cascades to linked simulations
 */
@QuarkusTest
class StrategyLifecycleIntegrationTest {

    private UserId testUserId;

    @Inject
    CreateStrategyUseCase createStrategyUseCase;

    @Inject
    UpdateStrategyUseCase updateStrategyUseCase;

    @Inject
    DeleteStrategyUseCase deleteStrategyUseCase;

    @Inject
    StrategyRepository strategyRepository;

    @Inject
    SimulationRepository simulationRepository;

    @Inject
    IntegrationTestHelper helper;

    @BeforeEach
    @Transactional
    void setUp() {
        User user = helper.createUser();
        testUserId = user.getId();
    }

    @Test
    @Transactional
    @DisplayName("should create crypto strategy with parsed tickers")
    void shouldCreateCryptoStrategy() {
        // Act
        StrategyId strategyId = createStrategyUseCase.execute(
                new CreateStrategyCommand("BTC Crypto Strategy", IntegrationTestHelper.VALID_CRYPTO_STRATEGY, testUserId)
        );

        // Assert
        assertThat(strategyId).isNotNull();

        Strategy saved = strategyRepository.findById(strategyId).orElseThrow();
        assertThat(saved.getName()).isEqualTo("BTC Crypto Strategy");

        StrategyConfigParser parser = new StrategyConfigParser();
        StrategyConfig config = parser.parse(saved.getContent());
        assertThat(config.getCryptoTickers()).isNotNull().isNotEmpty();
        assertThat(config.getCryptoTickers())
                .extracting(s -> s.ticker())
                .contains("BTC");
    }

    @Test
    @Transactional
    @DisplayName("should create stock strategy with market hours")
    void shouldCreateStockStrategyWithMarketHours() {
        // Act
        StrategyId strategyId = createStrategyUseCase.execute(
                new CreateStrategyCommand("AAPL Stock Strategy", IntegrationTestHelper.VALID_STOCK_STRATEGY, testUserId)
        );

        // Assert
        assertThat(strategyId).isNotNull();

        Strategy saved = strategyRepository.findById(strategyId).orElseThrow();
        StrategyConfigParser parser = new StrategyConfigParser();
        StrategyConfig config = parser.parse(saved.getContent());
        assertThat(config.getStockTickers()).isNotNull().isNotEmpty();
        assertThat(config.getStockTickers())
                .extracting(s -> s.ticker())
                .contains("AAPL");
        assertThat(config.getMarketHours()).isNotNull();
    }

    @Test
    @Transactional
    @DisplayName("should reject strategy with invalid YAML frontmatter")
    void shouldRejectStrategyWithInvalidYaml() {
        assertThatThrownBy(() ->
                createStrategyUseCase.execute(
                        new CreateStrategyCommand("Bad Strategy", IntegrationTestHelper.STRATEGY_INVALID_YAML, testUserId)
                )
        ).isInstanceOf(Exception.class);
    }

    @Test
    @Transactional
    @DisplayName("should reject stock strategy without live_schedule")
    void shouldRejectStockStrategyWithoutLiveSchedule() {
        // Stock tickers require live_schedule configuration — the parser should enforce this
        assertThatThrownBy(() ->
                createStrategyUseCase.execute(
                        new CreateStrategyCommand("No Hours Strategy", IntegrationTestHelper.STRATEGY_MISSING_LIVE_SCHEDULE, testUserId)
                )
        ).isInstanceOf(Exception.class);
    }

    @Test
    @Transactional
    @DisplayName("should update strategy when no simulations exist")
    void shouldUpdateStrategyWhenNoSimulations() {
        // Arrange — create strategy directly so we own the ID
        Strategy strategy = helper.createStrategy("Updatable Strategy", IntegrationTestHelper.VALID_CRYPTO_STRATEGY);

        String updatedContent = IntegrationTestHelper.VALID_CRYPTO_STRATEGY
                .replace("Buy when momentum is positive", "Buy when RSI < 30");

        // Act — update with changed rules but same tickers
        updateStrategyUseCase.execute(
                new UpdateStrategyCommand(strategy.getId(), updatedContent, strategy.getOwnerId())
        );

        // Assert
        Strategy updated = strategyRepository.findById(strategy.getId()).orElseThrow();
        assertThat(updated.getContent()).contains("Buy when RSI < 30");
    }

    @Test
    @Transactional
    @DisplayName("should reject ticker change when simulations exist")
    void shouldRejectTickerChangeWhenSimulationsExist() {
        // Arrange — create strategy + simulation
        Strategy strategy = helper.createStrategy("Immutable Tickers Strategy", IntegrationTestHelper.VALID_CRYPTO_STRATEGY);
        helper.createSimulation("Linked Simulation", strategy);

        // Act & Assert — try changing from BTC to ETH
        assertThatThrownBy(() ->
                updateStrategyUseCase.execute(
                        new UpdateStrategyCommand(strategy.getId(), IntegrationTestHelper.VALID_CRYPTO_STRATEGY_ETH, strategy.getOwnerId())
                )
        ).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot modify tickers");
    }

    @Test
    @Transactional
    @DisplayName("should delete strategy and cascade to linked simulations")
    void shouldDeleteStrategyCascade() {
        // Arrange — create strategy owned by testUserId + simulation
        Strategy strategy = helper.createStrategy("Deletable Strategy", IntegrationTestHelper.VALID_CRYPTO_STRATEGY, testUserId);
        Simulation simulation = helper.createSimulation("Linked Sim", strategy);

        // Verify they exist
        assertThat(strategyRepository.findById(strategy.getId())).isPresent();
        assertThat(simulationRepository.findById(simulation.getSimulationId())).isPresent();

        // Act
        deleteStrategyUseCase.execute(new DeleteStrategyCommand(strategy.getId(), testUserId));

        // Assert — both strategy and simulation are gone (cascade)
        assertThat(strategyRepository.findById(strategy.getId())).isEmpty();
        // Simulation should be cascade-deleted via DB constraint
        List<Simulation> remainingSimulations = simulationRepository.findByStrategyId(strategy.getId());
        assertThat(remainingSimulations).isEmpty();
    }
}
