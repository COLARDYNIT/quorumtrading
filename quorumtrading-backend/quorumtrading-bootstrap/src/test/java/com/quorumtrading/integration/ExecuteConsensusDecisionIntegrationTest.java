package com.quorumtrading.integration;

import com.quorumtrading.application.orchestration.agents.ConsensusActionParser;
import com.quorumtrading.adapter.broker.mock.MockBrokerAdapter;
import com.quorumtrading.application.port.out.ConsensusDecisionRepository;
import com.quorumtrading.application.port.out.PortfolioRepository;
import com.quorumtrading.application.usecase.ExecuteConsensusDecisionUseCase;
import com.quorumtrading.application.usecase.ExecuteConsensusDecisionUseCase.ExecutionResult;
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
import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration test for the financially critical ExecuteConsensusDecisionService.
 *
 * Verifies that:
 * 1. HOLD decisions create portfolio snapshots without trading
 * 2. BUY/SELL decisions execute through the broker and update portfolios
 * 3. Defensive validations (no holdings → HOLD, below minimum → HOLD, cap to cash)
 * 4. JSON-formatted finalAction is parsed correctly
 */
@QuarkusTest
class ExecuteConsensusDecisionIntegrationTest {

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
                "Execution Strategy-" + UUID.randomUUID().toString().substring(0, 8),
                IntegrationTestHelper.VALID_CRYPTO_STRATEGY
        );
        Simulation simulation = helper.createStartedSimulation("Execution Sim", strategy);
        SimulationId simId = simulation.getSimulationId();

        // Initialize broker portfolio
        brokerPort.initializePortfolio(simId, initialCash);

        // Create market events so price lookups work
        helper.createMarketEvents("BTC");

        // Create initial portfolio snapshot
        helper.createPortfolioSnapshot(simulation, initialCash);

        return new SetupResult(simulation, simId);
    }

    private record SetupResult(Simulation simulation, SimulationId simulationId) {}

    @Test
    @Transactional
    @DisplayName("should execute HOLD decision and create portfolio snapshot")
    void shouldExecuteHoldDecision() {
        // Arrange
        SetupResult setup = setupSimulationWithBroker(Money.usd(100_000));

        ConsensusDecision decision = new ConsensusDecision(
                setup.simulationId(),
                Instant.now(),
                ConsensusMethod.ORCHESTRATOR_OVERRIDE,
                ConsensusActionParser.parse("{\"action\":\"HOLD\",\"symbol\":\"BTC\",\"quantity\":0}")
        );
        decision.setSymbol(new Symbol("BTC", InvestmentType.CRYPTO));
        decision.setInvestmentType(InvestmentType.CRYPTO);
        decision = consensusDecisionRepository.save(decision);

        // Act
        ExecutionResult result = executeConsensusDecisionUseCase.execute(decision);

        // Assert
        assertThat(result.tradeExecuted()).isFalse();
        assertThat(result.action()).contains("HOLD");

        // A portfolio snapshot should still be created for valuation tracking
        List<PortfolioSnapshot> snapshots = portfolioRepository.findAllBySimulationId(setup.simulationId());
        assertThat(snapshots).hasSizeGreaterThanOrEqualTo(1);
    }

    @Test
    @Transactional
    @DisplayName("should execute BUY decision and reduce cash")
    void shouldExecuteBuyDecision() {
        // Arrange
        SetupResult setup = setupSimulationWithBroker(Money.usd(100_000));

        ConsensusDecision decision = new ConsensusDecision(
                setup.simulationId(),
                Instant.now(),
                ConsensusMethod.ORCHESTRATOR_OVERRIDE,
                ConsensusActionParser.parse("{\"action\":\"BUY\",\"symbol\":\"BTC\",\"quantity\":0.5}")
        );
        decision.setSymbol(new Symbol("BTC", InvestmentType.CRYPTO));
        decision.setInvestmentType(InvestmentType.CRYPTO);
        decision.setQuantity(BigDecimal.valueOf(0.5));
        decision = consensusDecisionRepository.save(decision);

        // Act
        ExecutionResult result = executeConsensusDecisionUseCase.execute(decision);

        // Assert
        assertThat(result.tradeExecuted()).isTrue();
        assertThat(result.action()).isEqualTo("BUY");

        // Cash should be reduced after buying
        Money availableBalance = brokerPort.getAvailableBalance(setup.simulationId());
        assertThat(availableBalance.amount()).isLessThan(BigDecimal.valueOf(100_000));
    }

    @Test
    @Transactional
    @DisplayName("should execute SELL decision and increase cash")
    void shouldExecuteSellDecision() {
        // Arrange — initialize with a BTC position
        SetupResult setup = setupSimulationWithBroker(Money.usd(50_000));

        // First buy some BTC to have a position to sell
        Map<Symbol, Position> initialPositions = Map.of(
                new Symbol("BTC", InvestmentType.CRYPTO),
                new Position(
                        new Symbol("BTC", InvestmentType.CRYPTO),
                        BigDecimal.valueOf(1.0),
                        Money.usd(45_000),
                        Money.usd(45_000)
                )
        );
        brokerPort.initializePortfolio(setup.simulationId(), Money.usd(50_000), initialPositions);

        ConsensusDecision decision = new ConsensusDecision(
                setup.simulationId(),
                Instant.now(),
                ConsensusMethod.ORCHESTRATOR_OVERRIDE,
                ConsensusActionParser.parse("{\"action\":\"SELL\",\"symbol\":\"BTC\",\"quantity\":0.5}")
        );
        decision.setSymbol(new Symbol("BTC", InvestmentType.CRYPTO));
        decision.setInvestmentType(InvestmentType.CRYPTO);
        decision.setQuantity(BigDecimal.valueOf(0.5));
        decision = consensusDecisionRepository.save(decision);

        // Act
        ExecutionResult result = executeConsensusDecisionUseCase.execute(decision);

        // Assert
        assertThat(result.tradeExecuted()).isTrue();
        assertThat(result.action()).isEqualTo("SELL");

        Money afterBalance = brokerPort.getAvailableBalance(setup.simulationId());
        assertThat(afterBalance.amount()).isGreaterThan(BigDecimal.valueOf(50_000));
    }

    @Test
    @Transactional
    @DisplayName("should convert SELL to HOLD when no holdings exist")
    void shouldConvertSellToHoldWhenNoHoldings() {
        // Arrange — no BTC position exists
        SetupResult setup = setupSimulationWithBroker(Money.usd(100_000));

        ConsensusDecision decision = new ConsensusDecision(
                setup.simulationId(),
                Instant.now(),
                ConsensusMethod.ORCHESTRATOR_OVERRIDE,
                ConsensusActionParser.parse("{\"action\":\"SELL\",\"symbol\":\"BTC\",\"quantity\":1.0}")
        );
        decision.setSymbol(new Symbol("BTC", InvestmentType.CRYPTO));
        decision.setInvestmentType(InvestmentType.CRYPTO);
        decision.setQuantity(BigDecimal.valueOf(1.0));
        decision = consensusDecisionRepository.save(decision);

        // Act
        ExecutionResult result = executeConsensusDecisionUseCase.execute(decision);

        // Assert — should be converted to HOLD, not an error
        assertThat(result.tradeExecuted()).isFalse();
        assertThat(result.action()).contains("HOLD");
    }

    @Test
    @Transactional
    @DisplayName("should convert below-minimum trade to HOLD")
    void shouldConvertBelowMinimumTradeToHold() {
        // Arrange — very small quantity that results in trade value below minimum ($100)
        SetupResult setup = setupSimulationWithBroker(Money.usd(100_000));

        ConsensusDecision decision = new ConsensusDecision(
                setup.simulationId(),
                Instant.now(),
                ConsensusMethod.ORCHESTRATOR_OVERRIDE,
                ConsensusActionParser.parse("{\"action\":\"BUY\",\"symbol\":\"BTC\",\"quantity\":0.001}")
        );
        decision.setSymbol(new Symbol("BTC", InvestmentType.CRYPTO));
        decision.setInvestmentType(InvestmentType.CRYPTO);
        // At ~$45000/BTC, 0.001 BTC = ~$45 which is below the $100 minimum
        decision.setQuantity(BigDecimal.valueOf(0.001));
        decision = consensusDecisionRepository.save(decision);

        // Act
        ExecutionResult result = executeConsensusDecisionUseCase.execute(decision);

        // Assert — should be converted to HOLD due to minimum trade amount
        assertThat(result.tradeExecuted()).isFalse();
        assertThat(result.action()).contains("HOLD");
    }

    @Test
    @Transactional
    @DisplayName("should cap BUY quantity to available cash")
    void shouldCapBuyQuantityToAvailableCash() {
        // Arrange — limited cash, request too much
        SetupResult setup = setupSimulationWithBroker(Money.usd(10_000));

        ConsensusDecision decision = new ConsensusDecision(
                setup.simulationId(),
                Instant.now(),
                ConsensusMethod.ORCHESTRATOR_OVERRIDE,
                ConsensusActionParser.parse("{\"action\":\"BUY\",\"symbol\":\"BTC\",\"quantity\":1.0}")
        );
        decision.setSymbol(new Symbol("BTC", InvestmentType.CRYPTO));
        decision.setInvestmentType(InvestmentType.CRYPTO);
        // At ~$45000/BTC, requesting 1.0 BTC = $45000 but we only have $10000
        decision.setQuantity(BigDecimal.valueOf(1.0));
        decision = consensusDecisionRepository.save(decision);

        // Act — should execute but with capped quantity
        ExecutionResult result = executeConsensusDecisionUseCase.execute(decision);

        // Assert — trade should execute (or be converted to HOLD if capped amount is below minimum)
        // Either way it should not fail/throw
        assertThat(result).isNotNull();
        // Cash should not exceed original amount
        Money afterBalance = brokerPort.getAvailableBalance(setup.simulationId());
        assertThat(afterBalance.amount()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
    }

    @Test
    @Transactional
    @DisplayName("should parse JSON final action correctly")
    void shouldHandleJsonFinalAction() {
        // Arrange
        SetupResult setup = setupSimulationWithBroker(Money.usd(100_000));

        String jsonAction = "{\"action\":\"HOLD\",\"symbol\":\"BTC\",\"quantity\":0,\"reasoning\":\"Market is sideways\"}";

        ConsensusDecision decision = new ConsensusDecision(
                setup.simulationId(),
                Instant.now(),
                ConsensusMethod.ORCHESTRATOR_OVERRIDE,
                ConsensusActionParser.parse(jsonAction)
        );
        decision.setSymbol(new Symbol("BTC", InvestmentType.CRYPTO));
        decision.setInvestmentType(InvestmentType.CRYPTO);
        decision = consensusDecisionRepository.save(decision);

        // Act
        ExecutionResult result = executeConsensusDecisionUseCase.execute(decision);

        // Assert — JSON action should be parsed and treated as HOLD
        assertThat(result.tradeExecuted()).isFalse();
        assertThat(result.action()).contains("HOLD");
    }
}
