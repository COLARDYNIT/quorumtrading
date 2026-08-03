package com.quorumtrading.application.service.consensus;

import com.quorumtrading.application.port.out.BrokerPort;
import com.quorumtrading.application.usecase.ExecuteConsensusDecisionUseCase;
import com.quorumtrading.application.usecase.command.DispatchNotificationCommand;
import com.quorumtrading.application.usecase.notification.DispatchNotificationUseCase;
import com.quorumtrading.model.entity.ConsensusDecision;
import com.quorumtrading.model.entity.Portfolio;
import com.quorumtrading.model.entity.Simulation;
import com.quorumtrading.model.enums.InvestmentType;
import com.quorumtrading.model.enums.NotificationType;
import com.quorumtrading.model.enums.SimulationStatus;
import com.quorumtrading.model.valueobject.SimulationId;
import com.quorumtrading.model.valueobject.Symbol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Owns the broker interaction of a consensus round: portfolio initialization/restore,
 * trade execution for a saved consensus decision, post-trade portfolio refresh, and the
 * opt-in SIMULATION_DECISION notification.
 *
 * <p>Plain POJO wired via {@code ApplicationConfig} (CDI convention: application
 * services are produced, not annotated). Extracted move-only from
 * {@code ReachConsensusService} — behavior preserved.</p>
 */
public class ConsensusTradeCoordinator {

    private static final Logger logger = LoggerFactory.getLogger(ConsensusTradeCoordinator.class);

    private final BrokerPort brokerPort;
    private final ExecuteConsensusDecisionUseCase executeConsensusDecisionUseCase;
    private final DispatchNotificationUseCase dispatchNotificationUseCase;

    public ConsensusTradeCoordinator(
            BrokerPort brokerPort,
            ExecuteConsensusDecisionUseCase executeConsensusDecisionUseCase,
            DispatchNotificationUseCase dispatchNotificationUseCase) {
        this.brokerPort = brokerPort;
        this.executeConsensusDecisionUseCase = executeConsensusDecisionUseCase;
        this.dispatchNotificationUseCase = dispatchNotificationUseCase;
    }

    /**
     * Initialize broker portfolio state if not already initialized.
     * This ensures the broker can execute trades (required for first trade).
     * IMPORTANT: Restore both cash AND positions to prevent position loss on server restart.
     */
    public void initializeBrokerPortfolio(SimulationId simulationId, Portfolio currentPortfolio) {
        if (currentPortfolio.positions().isEmpty()) {
            // No positions yet - use simple initialization
            brokerPort.initializePortfolio(simulationId, currentPortfolio.cashAvailable());
            logger.debug("Initialized broker portfolio for simulation {} with cash only",
                simulationId.value().toString().substring(0, 8));
        } else {
            // Restore both cash and positions from latest snapshot
            brokerPort.initializePortfolio(
                simulationId,
                currentPortfolio.cashAvailable(),
                currentPortfolio.positions()
            );
            logger.info("Restored broker portfolio for simulation {} with cash {} and {} positions",
                simulationId.value().toString().substring(0, 8),
                currentPortfolio.cashAvailable(),
                currentPortfolio.positions().size());
        }
    }

    /** Current broker view of the portfolio (used to refresh state after each ticker's trade). */
    public Portfolio currentPortfolio(SimulationId simulationId) {
        return brokerPort.getCurrentPortfolio(simulationId);
    }

    /**
     * Execute the trade for a saved consensus decision and, when it actually traded on a
     * LIVE opted-in simulation, dispatch the SIMULATION_DECISION notification.
     */
    public void executeTradeAndNotify(String ticker, ConsensusDecision savedConsensus, Simulation simulation) {
        Symbol tickerSymbol = new Symbol(ticker, InvestmentType.fromTicker(ticker));
        String simIdShort = simulation.getSimulationId().value().toString().substring(0, 8);

        // Execute trade
        ExecuteConsensusDecisionUseCase.ExecutionResult tradeResult = null;
        try {
            tradeResult = executeConsensusDecisionUseCase.execute(savedConsensus);
            if (tradeResult.tradeExecuted()) {
                logger.info("[sim={}] Trade executed for {}: {} - {}", simIdShort, ticker, tradeResult.action(), tradeResult.message());
            } else {
                logger.info("[sim={}] No trade executed for {}: {} - {}", simIdShort, ticker, tradeResult.action(), tradeResult.message());
            }
        } catch (Exception tradeEx) {
            logger.error("Failed to execute trade for consensus decision {} (ticker: {}): {}",
                savedConsensus.getId(), ticker, tradeEx.getMessage(), tradeEx);
        }

        // SIMULATION_DECISION notification — fires only when:
        //   1) the simulation is LIVE (not a backtest replay),
        //   2) the owner has explicitly opted-in via notifyDecisions, and
        //   3) the trade actually executed and the action was BUY or SELL (HOLDs never fire).
        // Best-effort: a notification failure must never roll back the trade.
        if (tradeResult != null
                && tradeResult.tradeExecuted()
                && simulation.getStatus() == SimulationStatus.LIVE
                && simulation.isNotifyDecisions()
                && simulation.getOwnerUserId() != null
                && isBuyOrSell(tradeResult.action())) {
            try {
                Map<String, Object> payload = new HashMap<>();
                payload.put("simulationId", simulation.getSimulationId().value().toString());
                payload.put("decisionId", savedConsensus.getId());
                payload.put("action", tradeResult.action());
                payload.put("symbol", tickerSymbol.ticker());
                if (savedConsensus.getQuantity() != null) {
                    payload.put("quantity", savedConsensus.getQuantity().toPlainString());
                }
                if (savedConsensus.getExecutionPrice() != null) {
                    payload.put("price", savedConsensus.getExecutionPrice().amount().toPlainString());
                    payload.put("currency", savedConsensus.getExecutionPrice().currency().name());
                }
                String title = tradeResult.action() + " " + tickerSymbol.ticker()
                        + " executed in " + (simulation.getName() == null ? "your simulation" : simulation.getName());
                dispatchNotificationUseCase.execute(new DispatchNotificationCommand(
                        simulation.getOwnerUserId(),
                        NotificationType.SIMULATION_DECISION,
                        title,
                        tradeResult.message(),
                        payload,
                        simulation.getSimulationId().value()
                ));
            } catch (Exception notifyEx) {
                logger.warn("Failed to dispatch SIMULATION_DECISION notification for simulation {} decision {}: {}",
                        simulation.getSimulationId().value(), savedConsensus.getId(), notifyEx.getMessage());
            }
        }
    }

    /**
     * Note: this classifies {@code ExecutionResult.action()} strings ("BUY", "HOLD
     * (converted)", ...) — a different string domain than ConsensusAction; deliberately
     * NOT routed through ConsensusActionParser.
     */
    private static boolean isBuyOrSell(String action) {
        if (action == null) return false;
        String upper = action.toUpperCase();
        return upper.equals("BUY") || upper.equals("SELL");
    }
}
