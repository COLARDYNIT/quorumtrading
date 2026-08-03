package com.quorumtrading.integration;

import com.quorumtrading.application.orchestration.agents.ConsensusActionParser;
import com.quorumtrading.adapter.broker.mock.MockBrokerAdapter;
import com.quorumtrading.application.port.out.ConsensusDecisionRepository;
import com.quorumtrading.application.port.out.PortfolioRepository;
import com.quorumtrading.application.usecase.ExecuteConsensusDecisionUseCase;
import com.quorumtrading.application.usecase.UpdatePortfolioWithMarketValueUseCase;
import com.quorumtrading.model.entity.*;
import com.quorumtrading.model.enums.*;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.SimulationId;
import com.quorumtrading.model.valueobject.Symbol;
import com.quorumtrading.model.enums.InvestmentType;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for portfolio valuation via UpdatePortfolioWithMarketValueUseCase.
 *
 * Verifies that portfolio snapshots accurately track broker-reported values,
 * including cash-only portfolios, portfolios with positions, and multi-snapshot tracking.
 */
@QuarkusTest
class PortfolioValuationIntegrationTest {

    @Inject
    UpdatePortfolioWithMarketValueUseCase updatePortfolioWithMarketValueUseCase;

    @Inject
    ExecuteConsensusDecisionUseCase executeConsensusDecisionUseCase;

    @Inject
    PortfolioRepository portfolioRepository;

    @Inject
    ConsensusDecisionRepository consensusDecisionRepository;

    @Inject
    @Named("mockBroker")
    MockBrokerAdapter brokerPort;

    @Inject
    IntegrationTestHelper helper;

    private SetupResult setupSimulationWithBroker(Money initialCash) {
        Strategy strategy = helper.createStrategy(
                "Valuation Strategy-" + UUID.randomUUID().toString().substring(0, 8),
                IntegrationTestHelper.VALID_CRYPTO_STRATEGY
        );
        Simulation simulation = helper.createStartedSimulation("Valuation Sim", strategy);
        SimulationId simId = simulation.getSimulationId();

        brokerPort.initializePortfolio(simId, initialCash);
        helper.createMarketEvents("BTC");
        helper.createPortfolioSnapshot(simulation, initialCash);

        return new SetupResult(simulation, simId);
    }

    private record SetupResult(Simulation simulation, SimulationId simulationId) {}

    @Test
    @Transactional
    @DisplayName("should create snapshot for cash-only portfolio")
    void shouldCreateSnapshotForCashOnlyPortfolio() {
        // Arrange
        SetupResult setup = setupSimulationWithBroker(Money.usd(100_000));

        // Act
        PortfolioSnapshot snapshot = updatePortfolioWithMarketValueUseCase
                .updatePortfolioWithMarketValue(setup.simulationId(), Instant.now());

        // Assert
        assertThat(snapshot).isNotNull();
        assertThat(snapshot.getCashAvailable().amount())
                .isEqualByComparingTo(BigDecimal.valueOf(100_000));
        assertThat(snapshot.isAllCash()).isTrue();
    }

    @Test
    @Transactional
    @DisplayName("should create snapshot with positions reflecting total value")
    void shouldCreateSnapshotWithPositions() {
        // Arrange — initialize portfolio with a BTC position
        SetupResult setup = setupSimulationWithBroker(Money.usd(50_000));

        Map<Symbol, Position> positions = Map.of(
                new Symbol("BTC", InvestmentType.CRYPTO),
                new Position(
                        new Symbol("BTC", InvestmentType.CRYPTO),
                        BigDecimal.valueOf(1.0),
                        Money.usd(45_000),
                        Money.usd(45_000)
                )
        );
        brokerPort.initializePortfolio(setup.simulationId(), Money.usd(50_000), positions);

        // Act
        PortfolioSnapshot snapshot = updatePortfolioWithMarketValueUseCase
                .updatePortfolioWithMarketValue(setup.simulationId(), Instant.now());

        // Assert
        assertThat(snapshot).isNotNull();
        assertThat(snapshot.getTotalValue().amount()).isGreaterThan(BigDecimal.ZERO);
        assertThat(snapshot.hasPositions()).isTrue();
    }

    @Test
    @Transactional
    @DisplayName("should track multiple snapshots over time")
    void shouldTrackMultipleSnapshotsOverTime() {
        // Arrange
        SetupResult setup = setupSimulationWithBroker(Money.usd(100_000));

        // Act — create 3 snapshots at different timestamps
        Instant baseTime = Instant.now();
        for (int i = 0; i < 3; i++) {
            updatePortfolioWithMarketValueUseCase.updatePortfolioWithMarketValue(
                    setup.simulationId(), baseTime.plus(i, ChronoUnit.HOURS));
        }

        // Assert — all snapshots should be queryable
        List<PortfolioSnapshot> snapshots = portfolioRepository.findAllBySimulationId(setup.simulationId());
        // Initial snapshot + 3 valuation snapshots
        assertThat(snapshots).hasSizeGreaterThanOrEqualTo(3);
    }

    @Test
    @Transactional
    @DisplayName("should reflect BUY in next snapshot")
    void shouldReflectBuyInNextSnapshot() {
        // Arrange
        SetupResult setup = setupSimulationWithBroker(Money.usd(100_000));

        // Execute a BUY
        ConsensusDecision buyDecision = new ConsensusDecision(
                setup.simulationId(),
                Instant.now(),
                ConsensusMethod.ORCHESTRATOR_OVERRIDE,
                ConsensusActionParser.parse("{\"action\":\"BUY\",\"symbol\":\"BTC\",\"quantity\":0.5}")
        );
        buyDecision.setSymbol(new Symbol("BTC", InvestmentType.CRYPTO));
        buyDecision.setInvestmentType(InvestmentType.CRYPTO);
        buyDecision.setQuantity(BigDecimal.valueOf(0.5));
        buyDecision = consensusDecisionRepository.save(buyDecision);

        executeConsensusDecisionUseCase.execute(buyDecision);

        // Act — update valuation after buy
        PortfolioSnapshot snapshot = updatePortfolioWithMarketValueUseCase
                .updatePortfolioWithMarketValue(setup.simulationId(), Instant.now());

        // Assert — snapshot should show position and reduced cash
        assertThat(snapshot).isNotNull();
        assertThat(snapshot.hasPositions()).isTrue();
        assertThat(snapshot.getCashAvailable().amount())
                .isLessThan(BigDecimal.valueOf(100_000));
    }
}
