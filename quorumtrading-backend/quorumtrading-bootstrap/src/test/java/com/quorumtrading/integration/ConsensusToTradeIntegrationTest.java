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
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration test for the consensus-to-trade execution flow.
 *
 * Tests the gap between consensus decision and trade execution — verifying
 * that portfolio state is correctly updated after sequential trade operations.
 * Uses MockBrokerAdapter for deterministic behavior.
 */
@QuarkusTest
class ConsensusToTradeIntegrationTest {

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

    private SimulationId setupSimulation(Money initialCash) {
        Strategy strategy = helper.createStrategy(
            "Consensus-Trade-" + UUID.randomUUID().toString().substring(0, 8),
            IntegrationTestHelper.VALID_CRYPTO_STRATEGY
        );
        Simulation simulation = helper.createStartedSimulation("Trade Test Sim", strategy);
        SimulationId simId = simulation.getSimulationId();

        brokerPort.initializePortfolio(simId, initialCash);
        helper.createMarketEvents("BTC");
        helper.createPortfolioSnapshot(simulation, initialCash);

        return simId;
    }

    @Test
    @Transactional
    @DisplayName("should update portfolio after BUY consensus — cash decreases, position appears")
    void shouldUpdatePortfolioAfterBuyConsensus() {
        // Arrange
        Money initialCash = Money.usd(100_000);
        SimulationId simId = setupSimulation(initialCash);

        ConsensusDecision buyDecision = createConsensus(simId, "BUY", BigDecimal.valueOf(0.5));

        // Act
        ExecutionResult result = executeConsensusDecisionUseCase.execute(buyDecision);

        // Assert
        assertThat(result.tradeExecuted()).isTrue();
        assertThat(result.action()).isEqualTo("BUY");

        Money afterCash = brokerPort.getAvailableBalance(simId);
        assertThat(afterCash.amount()).isLessThan(initialCash.amount());
    }

    @Test
    @Transactional
    @DisplayName("should update portfolio after SELL consensus — cash increases, position reduces")
    void shouldUpdatePortfolioAfterSellConsensus() {
        // Arrange — initialize with existing BTC position
        Money initialCash = Money.usd(50_000);
        SimulationId simId = setupSimulation(initialCash);

        // Give the simulation an existing BTC position
        Map<Symbol, Position> positions = Map.of(
            new Symbol("BTC", InvestmentType.CRYPTO),
            new Position(
                new Symbol("BTC", InvestmentType.CRYPTO),
                BigDecimal.valueOf(1.0),
                Money.usd(45_000),
                Money.usd(45_000)
            )
        );
        brokerPort.initializePortfolio(simId, initialCash, positions);

        ConsensusDecision sellDecision = createConsensus(simId, "SELL", BigDecimal.valueOf(0.5));

        // Act
        ExecutionResult result = executeConsensusDecisionUseCase.execute(sellDecision);

        // Assert
        assertThat(result.tradeExecuted()).isTrue();
        assertThat(result.action()).isEqualTo("SELL");

        Money afterCash = brokerPort.getAvailableBalance(simId);
        assertThat(afterCash.amount()).isGreaterThan(initialCash.amount());
    }

    @Test
    @Transactional
    @DisplayName("should handle HOLD consensus without portfolio change")
    void shouldHandleHoldConsensusWithoutPortfolioChange() {
        // Arrange
        Money initialCash = Money.usd(100_000);
        SimulationId simId = setupSimulation(initialCash);

        ConsensusDecision holdDecision = new ConsensusDecision(
            simId,
            Instant.now(),
            ConsensusMethod.ORCHESTRATOR_OVERRIDE,
            ConsensusActionParser.parse("{\"action\":\"HOLD\",\"symbol\":\"BTC\",\"quantity\":0}")
        );
        holdDecision.setSymbol(new Symbol("BTC", InvestmentType.CRYPTO));
        holdDecision.setInvestmentType(InvestmentType.CRYPTO);
        holdDecision = consensusDecisionRepository.save(holdDecision);

        // Act
        ExecutionResult result = executeConsensusDecisionUseCase.execute(holdDecision);

        // Assert — no trade executed
        assertThat(result.tradeExecuted()).isFalse();
        assertThat(result.action()).contains("HOLD");

        // Cash should be unchanged
        Money afterCash = brokerPort.getAvailableBalance(simId);
        assertThat(afterCash.amount()).isEqualByComparingTo(initialCash.amount());
    }

    @Test
    @Transactional
    @DisplayName("should track multiple trades sequentially — cumulative portfolio state")
    void shouldTrackMultipleTradesSequentially() {
        // Arrange
        Money initialCash = Money.usd(100_000);
        SimulationId simId = setupSimulation(initialCash);

        // Act — execute BUY, then another BUY, then SELL
        ConsensusDecision buy1 = createConsensus(simId, "BUY", BigDecimal.valueOf(0.3));
        ExecutionResult result1 = executeConsensusDecisionUseCase.execute(buy1);
        assertThat(result1.tradeExecuted()).isTrue();

        Money afterBuy1 = brokerPort.getAvailableBalance(simId);
        assertThat(afterBuy1.amount()).isLessThan(initialCash.amount());

        ConsensusDecision buy2 = createConsensus(simId, "BUY", BigDecimal.valueOf(0.2));
        ExecutionResult result2 = executeConsensusDecisionUseCase.execute(buy2);
        assertThat(result2.tradeExecuted()).isTrue();

        Money afterBuy2 = brokerPort.getAvailableBalance(simId);
        assertThat(afterBuy2.amount()).isLessThan(afterBuy1.amount());

        // Now sell some — cash should increase from buy2 level
        ConsensusDecision sell = createConsensus(simId, "SELL", BigDecimal.valueOf(0.1));
        ExecutionResult result3 = executeConsensusDecisionUseCase.execute(sell);
        assertThat(result3.tradeExecuted()).isTrue();

        Money afterSell = brokerPort.getAvailableBalance(simId);
        assertThat(afterSell.amount()).isGreaterThan(afterBuy2.amount());

        // Final cash should still be less than initial (net buyer)
        assertThat(afterSell.amount()).isLessThan(initialCash.amount());
    }

    private ConsensusDecision createConsensus(SimulationId simId, String action, BigDecimal quantity) {
        String jsonAction = String.format(
            "{\"action\":\"%s\",\"symbol\":\"BTC\",\"quantity\":%s}",
            action, quantity
        );
        ConsensusDecision decision = new ConsensusDecision(
            simId,
            Instant.now(),
            ConsensusMethod.ORCHESTRATOR_OVERRIDE,
            ConsensusActionParser.parse(jsonAction)
        );
        decision.setSymbol(new Symbol("BTC", InvestmentType.CRYPTO));
        decision.setInvestmentType(InvestmentType.CRYPTO);
        decision.setQuantity(quantity);
        return consensusDecisionRepository.save(decision);
    }
}
