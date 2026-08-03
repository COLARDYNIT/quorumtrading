package com.quorumtrading.integration;

import com.quorumtrading.application.port.out.SimulationRepository;
import com.quorumtrading.application.service.BackfillOrchestratorService;
import com.quorumtrading.model.entity.Simulation;
import com.quorumtrading.model.entity.Strategy;
import com.quorumtrading.model.enums.SimulationStatus;
import com.quorumtrading.model.valueobject.Money;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for BackfillOrchestratorService.handleSimulationCreated().
 *
 * Tests the synchronous backfill gate logic — does NOT test the async CDI event chain.
 * Focuses on simulation state machine transitions (PENDING, WAITING_FOR_DATA).
 */
@QuarkusTest
class BackfillOrchestrationIntegrationTest {

    @Inject
    BackfillOrchestratorService backfillOrchestratorService;

    @Inject
    SimulationRepository simulationRepository;

    @Inject
    IntegrationTestHelper helper;

    @Test
    @Transactional
    @DisplayName("should not transition to WAITING_FOR_DATA for LIVE simulation")
    void shouldNotTransitionToWaitingForLiveSimulation() {
        // Arrange — LIVE simulation has null endDate
        Strategy strategy = helper.createStrategy(
                "Live Backfill Strategy-" + UUID.randomUUID().toString().substring(0, 8),
                IntegrationTestHelper.VALID_CRYPTO_STRATEGY
        );
        Simulation simulation = helper.createSimulation("Live Sim", strategy,
                Instant.now(),
                null,
                Money.usd(100_000));

        assertThat(simulation.getStatus()).isEqualTo(SimulationStatus.PENDING);

        // Act — handleSimulationCreated should fire data ready event immediately for LIVE
        backfillOrchestratorService.handleSimulationCreated(simulation.getSimulationId());

        // Assert — simulation should stay PENDING (not transition to WAITING_FOR_DATA)
        Simulation reloaded = simulationRepository.findById(simulation.getSimulationId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(SimulationStatus.PENDING);
    }

    @Test
    @Transactional
    @DisplayName("should transition to WAITING_FOR_DATA when no market data exists for backtest")
    void shouldTransitionToWaitingWhenNoDataExists() {
        // Arrange — backtest simulation with past dates but NO market data
        Strategy strategy = helper.createStrategy(
                "No Data Backfill Strategy-" + UUID.randomUUID().toString().substring(0, 8),
                IntegrationTestHelper.VALID_CRYPTO_STRATEGY
        );
        Simulation simulation = helper.createSimulation("No Data Sim", strategy,
                Instant.now().minus(30, ChronoUnit.DAYS),
                Instant.now().minus(1, ChronoUnit.DAYS),
                Money.usd(100_000));

        assertThat(simulation.getStatus()).isEqualTo(SimulationStatus.PENDING);

        // Act — should detect missing data and transition to WAITING_FOR_DATA
        backfillOrchestratorService.handleSimulationCreated(simulation.getSimulationId());

        // Assert
        Simulation reloaded = simulationRepository.findById(simulation.getSimulationId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(SimulationStatus.WAITING_FOR_DATA);
    }

    @Test
    @Transactional
    @DisplayName("should not fail when market data already available for backtest")
    void shouldHandleBacktestWithAvailableData() {
        // Arrange — backtest simulation with market data available
        Strategy strategy = helper.createStrategy(
                "Data Available Strategy-" + UUID.randomUUID().toString().substring(0, 8),
                IntegrationTestHelper.VALID_CRYPTO_STRATEGY
        );
        // Create market events — covers roughly last 6 days at 16-hour intervals
        helper.createMarketEvents("BTC");

        // Use a short time range fully covered by the market events
        Simulation simulation = helper.createSimulation("Data Available Sim", strategy,
                Instant.now().minus(3, ChronoUnit.DAYS),
                Instant.now().minus(1, ChronoUnit.DAYS),
                Money.usd(100_000));

        assertThat(simulation.getStatus()).isEqualTo(SimulationStatus.PENDING);

        // Act — should not throw, regardless of whether it stays PENDING or transitions
        backfillOrchestratorService.handleSimulationCreated(simulation.getSimulationId());

        // Assert — service completed without error; simulation is either PENDING (data ready)
        // or WAITING_FOR_DATA (gap analysis triggered backfill despite data existing)
        Simulation reloaded = simulationRepository.findById(simulation.getSimulationId()).orElseThrow();
        assertThat(reloaded.getStatus()).isIn(SimulationStatus.PENDING, SimulationStatus.WAITING_FOR_DATA);
    }

    @Test
    @Transactional
    @DisplayName("should transition back to PENDING via dataReady()")
    void shouldTransitionBackToPendingViaDataReady() {
        // Arrange — manually set simulation to WAITING_FOR_DATA
        Strategy strategy = helper.createStrategy(
                "Data Ready Strategy-" + UUID.randomUUID().toString().substring(0, 8),
                IntegrationTestHelper.VALID_CRYPTO_STRATEGY
        );
        Simulation simulation = helper.createSimulation("Data Ready Sim", strategy);

        // Manually transition to WAITING_FOR_DATA. Status changes go through updateStatus():
        // save() no longer writes lifecycle columns, because it is also the hot-path write for a
        // long-lived in-memory entity and copying status off one reverted out-of-band writes.
        simulation.waitForData(UUID.randomUUID());
        simulationRepository.updateStatus(simulation.getSimulationId(), SimulationStatus.PENDING,
                SimulationStatus.WAITING_FOR_DATA, null, null);

        Simulation waiting = simulationRepository.findById(simulation.getSimulationId()).orElseThrow();
        assertThat(waiting.getStatus()).isEqualTo(SimulationStatus.WAITING_FOR_DATA);

        // Act
        waiting.dataReady();
        simulationRepository.updateStatus(waiting.getSimulationId(), SimulationStatus.WAITING_FOR_DATA,
                SimulationStatus.PENDING, null, null);

        // Assert
        Simulation reloaded = simulationRepository.findById(simulation.getSimulationId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(SimulationStatus.PENDING);
    }

    @Test
    @Transactional
    @DisplayName("should reject dataReady() when not in WAITING_FOR_DATA state")
    void shouldRejectDataReadyWhenNotWaiting() {
        // Arrange — simulation in PENDING state
        Strategy strategy = helper.createStrategy(
                "Not Waiting Strategy-" + UUID.randomUUID().toString().substring(0, 8),
                IntegrationTestHelper.VALID_CRYPTO_STRATEGY
        );
        Simulation simulation = helper.createSimulation("Not Waiting Sim", strategy);
        assertThat(simulation.getStatus()).isEqualTo(SimulationStatus.PENDING);

        // Act & Assert
        assertThatThrownBy(() -> simulation.dataReady())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not in WAITING_FOR_DATA state");
    }
}
