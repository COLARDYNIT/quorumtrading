package com.quorumtrading.application.service;

import com.quorumtrading.application.config.TradingLimitsConfig;
import com.quorumtrading.application.event.PortfolioChangeEvent;
import com.quorumtrading.application.event.TradeExecutionEvent;
import com.quorumtrading.application.port.out.BrokerPort;
import com.quorumtrading.application.port.out.ConsensusDecisionRepository;
import com.quorumtrading.application.port.out.EventPublisher;
import com.quorumtrading.application.port.out.MarketEventRepository;
import com.quorumtrading.application.port.out.PortfolioRepository;
import com.quorumtrading.application.port.out.SimulationRepository;
import com.quorumtrading.application.port.out.StrategyRepository;
import com.quorumtrading.application.usecase.ExecuteConsensusDecisionUseCase;
import com.quorumtrading.application.usecase.UpdatePortfolioWithMarketValueUseCase;
import com.quorumtrading.model.broker.ExecuteTradeCommand;
import com.quorumtrading.model.broker.TradeResult;
import com.quorumtrading.model.entity.ConsensusDecision;
import com.quorumtrading.model.entity.Portfolio;
import com.quorumtrading.model.entity.PortfolioSnapshot;
import com.quorumtrading.model.entity.Position;
import com.quorumtrading.model.entity.Simulation;
import com.quorumtrading.model.entity.Strategy;
import com.quorumtrading.model.enums.EventType;
import com.quorumtrading.model.enums.InvestmentType;
import com.quorumtrading.model.enums.ExecutionOutcome;
import com.quorumtrading.model.enums.RecommendationType;
import com.quorumtrading.model.enums.TradeAction;
import com.quorumtrading.model.enums.TradeStatus;
import com.quorumtrading.model.marketdata.MarketEvent;
import com.quorumtrading.model.orchestration.StrategyConfig;
import com.quorumtrading.model.orchestration.StrategyConfigParser;
import com.quorumtrading.model.valueobject.BrokerCapabilities;
import com.quorumtrading.model.valueobject.ConsensusAction;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.ShareSizing;
import com.quorumtrading.model.valueobject.SimulationId;
import com.quorumtrading.model.valueobject.Symbol;
import com.quorumtrading.model.valueobject.SymbolParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Service implementation for executing consensus decisions through the broker.
 */
public class ExecuteConsensusDecisionService implements ExecuteConsensusDecisionUseCase, UpdatePortfolioWithMarketValueUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(ExecuteConsensusDecisionService.class);

    /**
     * Headroom assumed for slippage + commission when sizing a BUY. A fill lands at
     * market + slippage, so any sizing that divides by the bare market price overshoots
     * whatever budget it was sizing against — available cash, or a position limit.
     */
    private static final BigDecimal EXECUTION_COST_BUFFER = new BigDecimal("0.005");

    /**
     * Fraction of total portfolio value a single platform-sized BUY may take when the agents
     * return no quantity and the strategy sets no {@code max_order_fraction}. The old default
     * — everything affordable — turned the position_limits cap into a day-one target and left
     * nothing for later tranches.
     */
    static final BigDecimal DEFAULT_ORDER_FRACTION = new BigDecimal("0.25");

    /**
     * Result of one strategy risk control: the (possibly reduced) quantity, and — when the
     * control squeezed the order to zero — a reason naming the control and both of its numbers.
     */
    record RiskCap(BigDecimal quantity, String blockReason) {
        static RiskCap of(BigDecimal quantity) {
            return new RiskCap(quantity, null);
        }

        static RiskCap blocked(String reason) {
            return new RiskCap(BigDecimal.ZERO, reason);
        }
    }

    private final BrokerPort brokerPort;
    private final PortfolioRepository portfolioRepository;
    private final ConsensusDecisionRepository consensusDecisionRepository;
    private final MarketEventRepository marketEventRepository;
    private final EventPublisher<TradeExecutionEvent> tradeExecutionEventPublisher;
    private final EventPublisher<PortfolioChangeEvent> portfolioChangeEventPublisher;
    private final TradingLimitsConfig tradingLimitsConfig;
    private final SimulationRepository simulationRepository;
    private final StrategyRepository strategyRepository;
    private final StrategyConfigParser strategyConfigParser;

    /**
     * The per-account broker router used to resolve the fractional-share capability of the
     * broker that will actually execute a given simulation's trades. Nullable: when absent
     * (bare unit tests that wire only a {@link BrokerPort} mock) the sizing pipeline falls
     * back to {@link BrokerPort#capabilities()}, which is fractional for every real adapter.
     */
    private final BrokerRouter brokerRouter;

    public ExecuteConsensusDecisionService(
            BrokerPort brokerPort,
            PortfolioRepository portfolioRepository,
            ConsensusDecisionRepository consensusDecisionRepository,
            MarketEventRepository marketEventRepository,
            EventPublisher<TradeExecutionEvent> tradeExecutionEventPublisher,
            EventPublisher<PortfolioChangeEvent> portfolioChangeEventPublisher,
            TradingLimitsConfig tradingLimitsConfig,
            SimulationRepository simulationRepository,
            StrategyRepository strategyRepository,
            StrategyConfigParser strategyConfigParser) {
        this(brokerPort, portfolioRepository, consensusDecisionRepository, marketEventRepository,
                tradeExecutionEventPublisher, portfolioChangeEventPublisher, tradingLimitsConfig,
                simulationRepository, strategyRepository, strategyConfigParser, null);
    }

    public ExecuteConsensusDecisionService(
            BrokerPort brokerPort,
            PortfolioRepository portfolioRepository,
            ConsensusDecisionRepository consensusDecisionRepository,
            MarketEventRepository marketEventRepository,
            EventPublisher<TradeExecutionEvent> tradeExecutionEventPublisher,
            EventPublisher<PortfolioChangeEvent> portfolioChangeEventPublisher,
            TradingLimitsConfig tradingLimitsConfig,
            SimulationRepository simulationRepository,
            StrategyRepository strategyRepository,
            StrategyConfigParser strategyConfigParser,
            BrokerRouter brokerRouter) {
        this.brokerPort = brokerPort;
        this.portfolioRepository = portfolioRepository;
        this.consensusDecisionRepository = consensusDecisionRepository;
        this.marketEventRepository = marketEventRepository;
        this.tradeExecutionEventPublisher = tradeExecutionEventPublisher;
        this.portfolioChangeEventPublisher = portfolioChangeEventPublisher;
        this.tradingLimitsConfig = tradingLimitsConfig;
        this.simulationRepository = simulationRepository;
        this.strategyRepository = strategyRepository;
        this.strategyConfigParser = strategyConfigParser;
        this.brokerRouter = brokerRouter;
    }

    @Override
    public ExecutionResult execute(ConsensusDecision decision) {
        LOG.info("Executing consensus decision {} for simulation {}",
                decision.getId(),
                decision.getSimulationId().value().toString().substring(0, 8));

        // The action is parsed ONCE at the boundary (ConsensusActionParser) and stored
        // typed on the decision; the raw string form is kept for messages and DTOs.
        ConsensusAction action = decision.getAction();
        String finalActionStr = decision.getFinalAction();
        if (finalActionStr == null || finalActionStr.trim().isEmpty()) {
            return ExecutionResult.tradeFailed("UNKNOWN", "Final action is null or empty");
        }

        Optional<RecommendationType> resolved = action.type().toRecommendationType();
        if (resolved.isEmpty()) {
            // Failure-message parity with the historical parser: the extracted token when
            // one exists, otherwise the whole raw string.
            String actionValue = action.actionToken() != null ? action.actionToken() : finalActionStr.trim();
            LOG.warn("Invalid final action: {}", actionValue);
            return ExecutionResult.tradeFailed(actionValue, "Invalid action type: " + actionValue);
        }
        RecommendationType recommendationType = resolved.get();

        // HOLD doesn't require trade execution, but we still update portfolio valuation
        if (recommendationType == RecommendationType.HOLD) {
            LOG.info("Decision is HOLD - no trade required, updating portfolio valuation");

            // Mark decision as no trade required
            decision.setExecutionOutcome(ExecutionOutcome.NO_TRADE_REQUIRED);
            consensusDecisionRepository.save(decision);

            // Still update portfolio valuation to track value over time
            Long snapshotId = null;
            try {
                PortfolioSnapshot savedSnapshot = updatePortfolioWithMarketValue(
                    decision.getSimulationId(),
                    decision.getDecisionTimestamp()
                );
                snapshotId = savedSnapshot.getId();
            } catch (Exception e) {
                LOG.debug("Could not update portfolio valuation for HOLD decision: {}", e.getMessage());
            }

            // Fire event for HOLD decision
            fireTradeExecutionEvent(
                decision,
                "HOLD",
                null,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                true,
                "No trade required for HOLD decision",
                snapshotId
            );

            return ExecutionResult.noTradeRequired("HOLD", snapshotId);
        }

        // SKIP means agents/orchestrator couldn't analyze — no trade should execute
        if (recommendationType == RecommendationType.SKIP) {
            LOG.info("Decision is SKIP for simulation {} - no trade required",
                    decision.getSimulationId().value().toString().substring(0, 8));

            decision.setExecutionOutcome(ExecutionOutcome.SKIPPED);
            consensusDecisionRepository.save(decision);

            Long snapshotId = null;
            try {
                PortfolioSnapshot savedSnapshot = updatePortfolioWithMarketValue(
                    decision.getSimulationId(),
                    decision.getDecisionTimestamp()
                );
                if (savedSnapshot != null) {
                    snapshotId = savedSnapshot.getId();
                }
            } catch (Exception e) {
                LOG.debug("Could not update portfolio valuation for SKIP decision: {}", e.getMessage());
            }

            fireTradeExecutionEvent(
                decision,
                "SKIP",
                null,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                true,
                "No trade required - analysis could not be completed",
                snapshotId
            );

            return ExecutionResult.noTradeRequired("SKIP", snapshotId);
        }

        // Get symbol from decision
        Symbol symbol = decision.getSymbol();
        if (symbol == null) {
            LOG.error("Symbol is null for consensus decision {}", decision.getId());
            return ExecutionResult.tradeFailed(finalActionStr, "Symbol is missing from decision");
        }

        // Convert RecommendationType to TradeAction
        TradeAction tradeAction = recommendationType == RecommendationType.BUY
                ? TradeAction.BUY
                : TradeAction.SELL;

        // Determine quantity: cap agent's recommendation to what's actually affordable
        BigDecimal maxQuantity = computeMaxQuantity(decision.getSimulationId(), symbol,
            tradeAction, decision.getDecisionTimestamp());
        BigDecimal agentQuantity = (decision.getQuantity() != null && decision.getQuantity().compareTo(BigDecimal.ZERO) > 0)
            ? decision.getQuantity() : null;
        BigDecimal quantity;
        if (agentQuantity != null) {
            quantity = agentQuantity.min(maxQuantity);
            if (quantity.compareTo(agentQuantity) < 0) {
                LOG.info("Agent quantity {} capped to max affordable {} for {}", agentQuantity, quantity, symbol.ticker());
                // Record the clamp on the decision itself. Silently substituting the largest
                // permitted size for an unaffordable request is how a malformed quantity — an
                // agent writing a cash amount into this units field — turned into a maximum-size
                // trade with nothing in the audit trail explaining the gap between the stated
                // intent and the fill.
                decision.addNote(String.format(
                        "Requested quantity %s exceeded the maximum permitted %s for %s; "
                                + "order reduced to the maximum. Note: quantity is in units, not cash.",
                        agentQuantity.toPlainString(), quantity.toPlainString(), symbol.ticker()));
            }
        } else {
            quantity = maxQuantity;
            LOG.info("No agent quantity — using max affordable: {} for {}", quantity, symbol.ticker());
            // The capped-down branch above has always written an audit note; this branch — the
            // one that silently turns "BUY" into "BUY as much as the strategy permits" — wrote
            // only a server log line. That asymmetry is how a tranche-based strategy's first
            // BUY consumed its entire position limit with nothing in the decision explaining
            // who chose the size.
            if (tradeAction == TradeAction.BUY) {
                decision.addNote(String.format(
                        "No quantity in the consensus — sized to the maximum the strategy permits: "
                                + "%s units of %s. For partial entries (tranches, scaling in), the "
                                + "agents must state an explicit quantity.",
                        quantity.toPlainString(), symbol.ticker()));
            }
        }

        Instant portfolioUpdateTimestamp = decision.getDecisionTimestamp();

        // STRATEGY RISK CONTROLS: Enforce order-size policy, position limits and reserve cash
        if (tradeAction == TradeAction.BUY) {
            StrategyConfig strategyConfig = loadStrategyConfig(decision.getSimulationId());
            boolean strategyControlsActive = strategyConfig != null;
            quantity = applyOrderSizePolicy(
                    decision, symbol, quantity, agentQuantity == null, strategyConfig, portfolioUpdateTimestamp);
            String riskBlockReason = null;
            if (strategyControlsActive) {
                RiskCap positionCap = enforcePositionLimit(
                        decision.getSimulationId(), symbol, quantity, strategyConfig, portfolioUpdateTimestamp);
                quantity = positionCap.quantity();
                riskBlockReason = positionCap.blockReason();
                if (riskBlockReason == null) {
                    RiskCap reserveCap = enforceReserveCash(
                            decision.getSimulationId(), quantity, strategyConfig, symbol, portfolioUpdateTimestamp);
                    quantity = reserveCap.quantity();
                    riskBlockReason = reserveCap.blockReason();
                }
            }
            if (quantity.compareTo(BigDecimal.ZERO) <= 0) {
                // The candidate quantity is already sized to the routed broker's lot granularity
                // (computeMaxQuantity / validateAndCapBuyQuantity round to whole shares for a
                // whole-share broker). Report the real cause — and the real outcome — rather
                // than one label for every way a BUY can die.
                if (riskBlockReason != null) {
                    LOG.info("BUY blocked by risk control for {}: {}", symbol.ticker(), riskBlockReason);
                    return convertToHold(decision, "BUY converted to HOLD: " + riskBlockReason,
                            ExecutionOutcome.BLOCKED_BY_RISK_LIMITS);
                }
                if (strategyControlsActive) {
                    // Controls ran but neither reported a block: the order was unsizeable to
                    // begin with (no cash, or market data unavailable in computeMaxQuantity).
                    String reason = "BUY could not be sized for " + symbol.ticker()
                            + " (no cash available, or market data unavailable)";
                    LOG.info("BUY quantity reduced to zero for {}: {}", symbol.ticker(), reason);
                    return convertToHold(decision, "BUY converted to HOLD: " + reason,
                            ExecutionOutcome.REJECTED);
                }
                String reason = String.format(
                        "BUY rounds to 0 whole shares for %s (broker fills whole shares only; budget below one share's price)",
                        symbol.ticker());
                LOG.info("BUY quantity reduced to zero for {}: {}", symbol.ticker(), reason);
                return convertToHold(decision, "BUY converted to HOLD: " + reason,
                        ExecutionOutcome.BELOW_MINIMUM_TRADE_VALUE);
            }
        }

        // DEFENSIVE VALIDATION: Cap BUY quantity based on available cash
        // LLMs sometimes ignore cash constraints in prompts, so we validate here
        if (tradeAction == TradeAction.BUY) {
            quantity = validateAndCapBuyQuantity(
                    decision.getSimulationId(),
                    symbol,
                    quantity,
                    portfolioUpdateTimestamp
            );
            LOG.info("Trade quantity (after cash validation): {}", quantity);

            // FINAL lot rounding: every upstream constraint (position-limit / reserve-cash
            // re-division at scale 8) can re-introduce a fractional component, so we round the
            // BUY quantity down to the routed broker's lot granularity as the LAST sizing step.
            // Fractional brokers (Alpaca / Mock) floor to scale 8 — byte-identical to before.
            // Whole-share brokers (Saxo EU equities) floor to an integral share count, leaving
            // the cash residue unspent (carried to the next tick). The min-amount check below
            // then runs on this final, broker-fillable quantity rather than a phantom fraction.
            BrokerCapabilities capabilities = capabilitiesForSimulation(decision.getSimulationId());
            BigDecimal roundedQuantity = ShareSizing.roundToLot(quantity, symbol.type(), capabilities);
            if (roundedQuantity.compareTo(quantity) != 0) {
                LOG.info("Rounded BUY quantity {} -> {} to broker lot granularity for {} (fractionalEquities={}, fractionalCrypto={})",
                        quantity, roundedQuantity, symbol.ticker(),
                        capabilities.fractionalEquities(), capabilities.fractionalCrypto());
            }
            quantity = roundedQuantity;

            // HOLD-on-zero: a whole-share broker may round a sub-one-share budget down to 0.
            // There is nothing fillable to execute, so convert to HOLD with a clear reason
            // (the cash stays put and is re-attempted next tick) rather than sending a 0-share
            // order to the broker.
            if (quantity.compareTo(BigDecimal.ZERO) <= 0) {
                String reason = String.format(
                        "BUY rounds to 0 whole shares for %s (broker fills whole shares only; budget below one share's price)",
                        symbol.ticker());
                LOG.info("Converting BUY to HOLD for simulation {}: {}",
                        decision.getSimulationId().value().toString().substring(0, 8), reason);
                return convertToHold(decision, "BUY converted to HOLD: " + reason,
                        ExecutionOutcome.BELOW_MINIMUM_TRADE_VALUE);
            }
        }

        // DEFENSIVE VALIDATION: Cap SELL quantity based on current holdings
        // If no holdings exist, convert to HOLD instead of trying to sell 0
        if (tradeAction == TradeAction.SELL) {
            quantity = validateAndCapSellQuantity(
                    decision.getSimulationId(),
                    symbol,
                    quantity
            );
            LOG.info("Trade quantity (after holdings validation): {}", quantity);

            // Convert SELL with 0 quantity to HOLD (can't sell what you don't have)
            if (quantity.compareTo(BigDecimal.ZERO) <= 0) {
                LOG.info("Converting SELL to HOLD: no holdings for {} (LLM ignored portfolio state)", symbol.ticker());
                // A SELL of nothing genuinely requires no trade — unlike a refused BUY.
                return convertToHold(decision, "SELL converted to HOLD: no holdings for " + symbol.ticker(),
                        ExecutionOutcome.NO_TRADE_REQUIRED);
            }
        }

        // DEFENSIVE VALIDATION: Check minimum trade amount BEFORE executing
        // Convert sub-minimum trades to HOLD instead of rejecting them
        ValidationResult minAmountValidation = validateMinimumTradeAmount(symbol, quantity, portfolioUpdateTimestamp);
        if (!minAmountValidation.isValid()) {
            String reason = minAmountValidation.getRejectionReason();
            LOG.info("Converting {} to HOLD: {}", tradeAction, reason);
            return convertToHold(decision, tradeAction + " converted to HOLD: " + reason,
                    ExecutionOutcome.BELOW_MINIMUM_TRADE_VALUE);
        }

        // Create trade command
        ExecuteTradeCommand command = ExecuteTradeCommand.market(
                decision.getSimulationId(),
                tradeAction,
                symbol,
                quantity,
                portfolioUpdateTimestamp
        );

        LOG.info("Executing {} trade: {} x {} @ {}",
                tradeAction, symbol.ticker(), quantity, portfolioUpdateTimestamp);

        // Execute trade
        TradeResult result = brokerPort.executeTrade(command);

        // Handle rejected trade
        if (result.isRejected()) {
            String reason = result.getRejectionReason().orElse("Unknown reason");
            LOG.warn("Trade rejected: {}", reason);
            decision.setExecutionOutcome(ExecutionOutcome.REJECTED);
            decision.addNote("Trade rejected: " + reason);
            consensusDecisionRepository.save(decision);

            // Fire event for rejected trade
            fireTradeExecutionEvent(
                decision,
                tradeAction.name(),
                symbol,
                quantity,
                BigDecimal.ZERO,
                false,
                "Trade rejected: " + reason,
                null
            );

            return ExecutionResult.tradeFailed(tradeAction.name(), reason);
        }

        // Handle pending trade (accepted but not filled - low liquidity or market closed)
        if (result.executionPrice() == null || result.filledQuantity() == null) {
            String reason = String.format("Order %s accepted but not filled (status: %s). " +
                "This typically indicates low liquidity or pending fill. " +
                "Order will be tracked and may fill later.",
                result.orderId(), result.status());
            LOG.warn("Trade pending: {} x {} - {}", tradeAction, quantity, reason);
            decision.setExecutionOutcome(ExecutionOutcome.REJECTED); // Treat as rejected for now
            decision.addNote("Trade pending (not filled): " + reason);
            consensusDecisionRepository.save(decision);

            // Fire event for pending trade (treated as failed for now)
            fireTradeExecutionEvent(
                decision,
                tradeAction.name(),
                symbol,
                quantity,
                BigDecimal.ZERO,
                false,
                reason,
                null
            );

            return ExecutionResult.tradeFailed(tradeAction.name(), reason);
        }

        // Trade executed (fully or partially)
        boolean isPartialFill = result.status() == TradeStatus.PARTIAL_FILL;
        BigDecimal actualFilledQuantity = result.filledQuantity();
        if (isPartialFill) {
            LOG.info("Trade partially filled: {} (requested: {}, filled: {})", result.orderId(), quantity, actualFilledQuantity);
        } else {
            LOG.info("Trade executed successfully: {}", result.orderId());
        }

        PortfolioSnapshot savedSnapshot = updatePortfolioWithMarketValue(decision.getSimulationId(), portfolioUpdateTimestamp);

        // Mark decision as executed (use actual filled quantity for record-keeping)
        decision.setExecutionOutcome(ExecutionOutcome.EXECUTED);
        decision.setQuantity(actualFilledQuantity);
        decision.markAsExecuted(result.executionPrice());
        String formattedPrice = String.format("$%.2f", result.executionPrice().amount());
        if (isPartialFill) {
            decision.addNote(String.format("Partial fill: %s x %s @ %s (requested: %s, remainder cancelled, Order: %s)",
                    tradeAction, actualFilledQuantity, formattedPrice, quantity, result.orderId()));
        } else {
            decision.addNote(String.format("Executed: %s x %s @ %s (Order: %s)",
                    tradeAction, actualFilledQuantity, formattedPrice, result.orderId()));
        }
        consensusDecisionRepository.save(decision);

        String message = isPartialFill
                ? String.format("%s partially filled: %s x %s @ %s (requested: %s)",
                        tradeAction, symbol.ticker(), actualFilledQuantity, formattedPrice, quantity)
                : String.format("%s executed: %s x %s @ %s",
                        tradeAction, symbol.ticker(), actualFilledQuantity, formattedPrice);

        // Fire event for successful trade (use actual filled quantity)
        fireTradeExecutionEvent(
            decision,
            tradeAction.name(),
            symbol,
            actualFilledQuantity,
            result.executionPrice().amount(),
            true,
            message,
            savedSnapshot != null ? savedSnapshot.getId() : null
        );

        return ExecutionResult.tradeExecuted(tradeAction.name(), message,
                savedSnapshot != null ? savedSnapshot.getId() : null);
    }

    /**
     * Return an existing snapshot at the same instant carrying the same portfolio, or null.
     *
     * <p>One snapshot is written per decision, and a multi-ticker evaluation produces one decision
     * per symbol at the same simulated instant. When those decisions are all HOLD nothing about
     * the portfolio changes between them, so each wrote a byte-identical row: a two-ticker backtest
     * produced two rows per quiet evaluation. Same-instant rows that genuinely differ — the states
     * after each leg of a multi-symbol trade — are still written, because those describe real
     * intermediate portfolios and the charts step through them.</p>
     *
     * <p>Values are compared with {@code compareTo}, not {@code equals}: these are BigDecimals
     * arriving from different code paths and 3017.4 must count as the same cash as 3017.40.</p>
     */
    private PortfolioSnapshot findIdenticalSnapshotAt(PortfolioSnapshot candidate) {
        List<PortfolioSnapshot> sameInstant = portfolioRepository.findBySimulationIdAndTimeRange(
                candidate.getSimulationId(), candidate.getTimestamp(), candidate.getTimestamp());
        for (PortfolioSnapshot existing : sameInstant) {
            if (describesSamePortfolio(existing, candidate)) {
                LOG.debug("Snapshot {} at {} already records this portfolio — not writing a duplicate",
                        existing.getId(), candidate.getTimestamp());
                return existing;
            }
        }
        return null;
    }

    private boolean describesSamePortfolio(PortfolioSnapshot a, PortfolioSnapshot b) {
        if (a.getTotalValue().amount().compareTo(b.getTotalValue().amount()) != 0
                || a.getCashAvailable().amount().compareTo(b.getCashAvailable().amount()) != 0) {
            return false;
        }
        Map<Symbol, Position> left = a.getPositions();
        Map<Symbol, Position> right = b.getPositions();
        if (!left.keySet().equals(right.keySet())) {
            return false;
        }
        for (Map.Entry<Symbol, Position> entry : left.entrySet()) {
            Position other = right.get(entry.getKey());
            if (entry.getValue().quantity().compareTo(other.quantity()) != 0) {
                return false;
            }
        }
        return true;
    }

    // NOW IMPLEMENTED: MarketDataCollectionJob calls this every 5min for LIVE simulations
    // See: quorumtrading-bootstrap/src/main/java/com/quorumtrading/bootstrap/scheduler/MarketDataCollectionJob.java:updateLivePortfolios()
    @Override
    public PortfolioSnapshot updatePortfolioWithMarketValue(SimulationId simulationId, Instant portfolioUpdateTimestamp) {
        // Guard: Do not create snapshots after the simulation's end date
        Optional<Simulation> simOpt = simulationRepository.findById(simulationId);
        if (simOpt.isPresent()) {
            Simulation sim = simOpt.get();
            if (sim.getEndDate() != null && portfolioUpdateTimestamp.isAfter(sim.getEndDate())) {
                LOG.warn("Rejecting portfolio snapshot for simulation {} at {} — past end date {}",
                        simulationId.value().toString().substring(0, 8),
                        portfolioUpdateTimestamp,
                        sim.getEndDate());
                return null;
            }
        }

        // Get updated portfolio from broker (positions + cash)
        Portfolio portfolio = brokerPort.getCurrentPortfolio(simulationId);

        // Special case: Portfolio with only cash (no positions) - we CAN create snapshot
        if (portfolio.positions().isEmpty()) {
            Money cashOnlyValue = portfolio.cashAvailable();
            LOG.debug("Portfolio has only cash (no positions), creating snapshot with value: {}", cashOnlyValue);

            PortfolioSnapshot snapshot = new PortfolioSnapshot(
                simulationId,
                portfolioUpdateTimestamp,
                Map.of(), // Empty positions
                cashOnlyValue,
                cashOnlyValue
            );

            PortfolioSnapshot existing = findIdenticalSnapshotAt(snapshot);
            if (existing != null) {
                return existing;
            }

            PortfolioSnapshot savedSnapshot = portfolioRepository.save(snapshot);
            LOG.info("Portfolio snapshot saved (cash-only): {} (value: {})", savedSnapshot.getId(), cashOnlyValue);

            portfolioChangeEventPublisher.publish(new PortfolioChangeEvent(
                savedSnapshot.getSimulationId(),
                snapshot.getTotalValue().amount(),
                snapshot.getCashAvailable().amount(),
                portfolioUpdateTimestamp,
                snapshot.getPositions()
            ));

            return savedSnapshot;
        }

        // Get BROKER-REPORTED total value (not calculated by us)
        // Broker now returns NULL if market prices are missing (no more fallbacks)
        Money brokerReportedValue = brokerPort.getPortfolioValue(simulationId, portfolioUpdateTimestamp);

        // CRITICAL: If broker returns null, it means market data is incomplete
        // SKIP snapshot creation to maintain data integrity
        if (brokerReportedValue == null) {
            LOG.warn("Cannot create portfolio snapshot for simulation {} at {}: " +
                     "broker returned null (missing market prices for one or more positions). " +
                     "Skipping snapshot to maintain data integrity.",
                     simulationId.value().toString().substring(0, 8),
                     portfolioUpdateTimestamp);
            return null; // Signal that snapshot was skipped
        }

        // CRITICAL: If broker returns $0 or negative, it indicates corrupted data
        // (API failure, missing prices, market closed, etc.) - SKIP to prevent data corruption
        if (!brokerReportedValue.isPositive()) {
            LOG.error("Cannot create portfolio snapshot for simulation {} at {}: " +
                      "broker returned invalid value {} (likely API failure or missing market data). " +
                      "Skipping snapshot to prevent data corruption.",
                      simulationId.value().toString().substring(0, 8),
                      portfolioUpdateTimestamp,
                      brokerReportedValue.amount());
            return null;
        }

        LOG.debug("Broker-reported portfolio value: {} (cash: {}) for simulation {}",
            brokerReportedValue,
            portfolio.cashAvailable(),
            simulationId.value().toString().substring(0, 8)
        );

        // Fetch current market prices for all positions (used for snapshot detail/UI display)
        // Note: We don't need fallback prices anymore - broker already validated all prices exist
        Map<Symbol, Money> currentPrices = fetchCurrentMarketPrices(portfolio, portfolioUpdateTimestamp, Map.of());

        // Create portfolio snapshot with BROKER'S total value
        PortfolioSnapshot snapshot = createPortfolioSnapshot(
                portfolio,
                portfolioUpdateTimestamp,
                currentPrices,
                brokerReportedValue
        );

        PortfolioSnapshot existing = findIdenticalSnapshotAt(snapshot);
        if (existing != null) {
            return existing;
        }

        // Save snapshot
        PortfolioSnapshot savedSnapshot = portfolioRepository.save(snapshot);
        LOG.info("Portfolio snapshot saved: {} (value: {})", savedSnapshot.getId(), brokerReportedValue);

        // Fire portfolio change event with positions Map (WebSocket adapter will serialize)
        portfolioChangeEventPublisher.publish(new PortfolioChangeEvent(
            savedSnapshot.getSimulationId(),
            snapshot.getTotalValue().amount(),
            snapshot.getCashAvailable().amount(),
                portfolioUpdateTimestamp,
            snapshot.getPositions()
        ));
        return savedSnapshot;
    }

    /**
     * Fetch current market prices for all positions in the portfolio.
     * Looks back 24 hours from the decision timestamp to find recent price events.
     *
     * NOTE: This is only used for snapshot detail/UI display purposes.
     * The actual portfolio valuation uses broker's getPortfolioValue() which has its own
     * market data fetching with temporal validation.
     *
     * NO FALLBACKS - if price is missing, it simply won't be in the returned map.
     */
    private Map<Symbol, Money> fetchCurrentMarketPrices(Portfolio portfolio, Instant timestamp, Map<Symbol, Money> lastKnownPrices) {
        if (portfolio.positions().isEmpty()) {
            return new HashMap<>();
        }

        Map<Symbol, Money> currentPrices =
            LatestMarketPrices.resolve(marketEventRepository, portfolio.positions().keySet(), timestamp);

        return currentPrices;
    }


    /**
     * Create a portfolio snapshot from the current broker portfolio state.
     * Uses broker-reported total value to ensure consistency with broker's view.
     *
     * @param portfolio The portfolio positions and cash
     * @param timestamp The snapshot timestamp
     * @param currentPrices Current market prices (for UI display in positions JSON)
     * @param brokerReportedValue The total value as reported by the broker
     */
    private PortfolioSnapshot createPortfolioSnapshot(
            Portfolio portfolio,
            Instant timestamp,
            Map<Symbol, Money> currentPrices,
            Money brokerReportedValue) {

        // Use broker-reported total value (already calculated by broker)
        // This ensures our snapshots match what the broker actually sees
        Money totalValue = brokerReportedValue;

        // Create Position objects WITH current market prices for snapshot persistence.
        // Seed the running positions-value accumulator in the portfolio's settlement
        // currency (USD for Alpaca, EUR for a Saxo/EU account) so the per-position
        // Money.add() below stays single-currency and never trips Money's same-currency
        // guard. This block only runs under debug logging, but the currency must still match.
        Map<Symbol, Position> positionsWithCurrentPrice = new HashMap<>();
        Money calculatedPositionsValue = Money.zero(portfolio.cashAvailable().currency());

        for (Map.Entry<Symbol, Position> entry : portfolio.positions().entrySet()) {
            Position position = entry.getValue();
            Money currentPrice = currentPrices.get(position.symbol());  // Can be null if market data missing

            // Create new Position with currentPrice included
            Position positionWithPrice = new Position(
                position.symbol(),
                position.quantity(),
                position.averagePrice(),
                currentPrice  // Store current market price in snapshot (can be null)
            );
            positionsWithCurrentPrice.put(entry.getKey(), positionWithPrice);

            if (LOG.isDebugEnabled()) {
                Money priceForCalc = currentPrice != null ? currentPrice : position.averagePrice();
                Money positionValue = priceForCalc.multiply(position.quantity());
                calculatedPositionsValue = calculatedPositionsValue.add(positionValue);

                LOG.debug("Position {}: {} x {} (market) = {}",
                    position.symbol().ticker(),
                    position.quantity(),
                    priceForCalc,
                    positionValue);
            }
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("Portfolio snapshot - Broker value: {} | Cash: {} | Calculated positions: {} | Our total: {}",
                brokerReportedValue,
                portfolio.cashAvailable(),
                calculatedPositionsValue,
                portfolio.cashAvailable().add(calculatedPositionsValue));
        }

        // Portfolio positions WITH current prices are stored - PortfolioMapper handles JSON serialization
        return new PortfolioSnapshot(
                portfolio.simulationId(),
                timestamp,
                positionsWithCurrentPrice,  // Now includes currentPrice field
                totalValue,
                portfolio.cashAvailable()
        );
    }


    /**
     * Convert an invalid trade (SELL with no holdings, BUY below minimum or refused by risk
     * controls) to HOLD. This is smarter than rejecting because the intent is captured (LLM
     * wanted to trade) but the system safely prevents impossible trades.
     *
     * <p>The {@code outcome} records WHY nothing traded — {@code BLOCKED_BY_RISK_LIMITS},
     * {@code BELOW_MINIMUM_TRADE_VALUE}, or {@code NO_TRADE_REQUIRED} for a genuinely vacuous
     * action — so a refused BUY is no longer indistinguishable from a deliberate HOLD.</p>
     */
    private ExecutionResult convertToHold(ConsensusDecision decision, String reason, ExecutionOutcome outcome) {
        LOG.info("Converting trade to HOLD for simulation {}: {}",
                decision.getSimulationId().value().toString().substring(0, 8), reason);

        decision.setExecutionOutcome(outcome);
        decision.addNote(reason);
        consensusDecisionRepository.save(decision);

        // Still update portfolio valuation
        Long snapshotId = null;
        try {
            PortfolioSnapshot savedSnapshot = updatePortfolioWithMarketValue(
                decision.getSimulationId(),
                decision.getDecisionTimestamp()
            );
            if (savedSnapshot != null) {
                snapshotId = savedSnapshot.getId();
            }
        } catch (Exception e) {
            LOG.debug("Could not update portfolio valuation for converted HOLD: {}", e.getMessage());
        }

        // Fire event for the converted HOLD
        fireTradeExecutionEvent(
            decision,
            "HOLD",
            decision.getSymbol(),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            true,
            reason,
            snapshotId
        );

        return ExecutionResult.noTradeRequired("HOLD (converted)", snapshotId);
    }

    /**
     * Helper method to fire trade execution events.
     */
    private void fireTradeExecutionEvent(
            ConsensusDecision decision,
            String action,
            Symbol symbol,
            BigDecimal quantity,
            BigDecimal executionPrice,
            boolean success,
            String message,
            Long portfolioSnapshotId) {

        TradeExecutionEvent event = new TradeExecutionEvent(
            decision.getSimulationId(),
            decision.getId(),
            action,
            symbol != null ? symbol.ticker() : null,
            quantity,
            executionPrice,
            success,
            message,
            portfolioSnapshotId,
            decision.getDecisionTimestamp()
        );

        tradeExecutionEventPublisher.publish(event);
        LOG.debug("Fired TradeExecutionEvent: action={}, success={}, simulationId={}",
            action, success, decision.getSimulationId());
    }

    /**
     * Validate and cap BUY quantity based on available cash.
     * Prevents LLM from requesting more quantity than we can afford.
     * Uses a 5% cash buffer to avoid edge cases.
     */
    private BigDecimal validateAndCapBuyQuantity(
            com.quorumtrading.model.valueobject.SimulationId simulationId,
            Symbol symbol,
            BigDecimal requestedQuantity,
            Instant timestamp) {

        try {
            // Get available cash from broker
            Money availableCash = brokerPort.getAvailableBalance(simulationId);

            // Get market price for this symbol
            Money marketPrice = getMarketPrice(symbol, timestamp);
            if (marketPrice == null) {
                LOG.warn("No market price found for {}. Cannot validate quantity. Using requested: {}",
                        symbol.ticker(), requestedQuantity);
                return requestedQuantity;
            }

            // Calculate max affordable quantity (with 5% buffer for safety). Sizing to the
            // routed broker's fractional capability keeps the fractional path byte-identical
            // (scale-8 floor) while letting whole-share brokers floor to integral shares.
            BigDecimal cashBuffer = BigDecimal.valueOf(0.95); // Use only 95% of available cash
            Money affordableCash = availableCash.multiply(cashBuffer);
            BigDecimal maxAffordableQuantity = ShareSizing.maxAffordableQuantity(
                    affordableCash, marketPrice, symbol.type(), capabilitiesForSimulation(simulationId));

            // Cap quantity if requested exceeds affordable
            if (requestedQuantity.compareTo(maxAffordableQuantity) > 0) {
                LOG.warn("LLM requested BUY quantity {} but only {} is affordable (cash: {}, price: {}). Capping to affordable amount.",
                        requestedQuantity,
                        maxAffordableQuantity,
                        availableCash,
                        marketPrice);
                return maxAffordableQuantity;
            }

            LOG.debug("BUY quantity {} is within affordable limit {} (cash: {}, price: {})",
                    requestedQuantity, maxAffordableQuantity, availableCash, marketPrice);
            return requestedQuantity;

        } catch (Exception e) {
            LOG.error("Failed to validate BUY quantity. Using requested quantity: {}", requestedQuantity, e);
            return requestedQuantity;
        }
    }

    /**
     * Validate and cap SELL quantity based on current holdings.
     * Prevents LLM from requesting to sell more than we own.
     */
    private BigDecimal validateAndCapSellQuantity(
            com.quorumtrading.model.valueobject.SimulationId simulationId,
            Symbol symbol,
            BigDecimal requestedQuantity) {

        try {
            // Get current portfolio from broker
            Portfolio portfolio = brokerPort.getCurrentPortfolio(simulationId);

            // Check if we have a position for this symbol
            Position position = portfolio.positions().get(symbol);
            if (position == null || position.quantity().compareTo(BigDecimal.ZERO) <= 0) {
                LOG.warn("LLM requested SELL {} but no position exists for {}. Setting quantity to 0.",
                        requestedQuantity, symbol.ticker());
                return BigDecimal.ZERO;
            }

            BigDecimal currentHoldings = position.quantity();

            // Cap quantity if requested exceeds holdings
            if (requestedQuantity.compareTo(currentHoldings) > 0) {
                LOG.warn("LLM requested SELL quantity {} but only {} is held for {}. Capping to held amount.",
                        requestedQuantity,
                        currentHoldings,
                        symbol.ticker());
                return currentHoldings;
            }

            LOG.debug("SELL quantity {} is within holdings limit {} for {}",
                    requestedQuantity, currentHoldings, symbol.ticker());
            return requestedQuantity;

        } catch (Exception e) {
            LOG.error("Failed to validate SELL quantity. Using requested quantity: {}", requestedQuantity, e);
            return requestedQuantity;
        }
    }

    /**
     * Get market price for a symbol at a specific timestamp.
     * Looks back 1 hour to find recent price data.
     */
    private Money getMarketPrice(Symbol symbol, Instant timestamp) {
        // Use a 4-day lookback to handle daily bars and weekends.
        // Daily bars have timestamps at market close, which may be >1 hour before
        // the decision timestamp. Weekend gaps also need coverage.
        Instant lookbackStart = timestamp.minus(4, ChronoUnit.DAYS);

        Set<String> tickers = Set.of(symbol.ticker());
        List<MarketEvent> priceEvents = marketEventRepository.findByTickersAndTimeRange(
                tickers,  // crypto tickers
                tickers,  // stock tickers
                lookbackStart,
                timestamp,
                EventType.PRICE,
                1 // limit to 1 (most recent)
        );

        if (priceEvents.isEmpty()) {
            LOG.warn("No price data found for {} near timestamp {}", symbol.ticker(), timestamp);
            return null;
        }

        MarketEvent event = priceEvents.get(0);
        Object priceObj = event.rawData().get("price");

        // Price events carry only a numeric value, no currency — infer it from the
        // instrument's venue (USD for US, EUR for an EU MIC) so a EUR position's price
        // arithmetic (cash / value / capping) stays single-currency.
        Money.Currency priceCurrency = SymbolParser.currencyFor(symbol);
        if (priceObj instanceof Number) {
            return Money.of(BigDecimal.valueOf(((Number) priceObj).doubleValue()), priceCurrency);
        } else if (priceObj instanceof String) {
            try {
                return Money.of(new BigDecimal((String) priceObj), priceCurrency);
            } catch (NumberFormatException e) {
                LOG.error("Invalid price format for {}: {}", symbol.ticker(), priceObj);
                return null;
            }
        }

        LOG.warn("Unexpected price data type for {}: {}", symbol.ticker(), priceObj.getClass());
        return null;
    }

    /**
     * Load the StrategyConfig for a simulation by looking up the strategy's markdown content.
     * Returns null if the config cannot be loaded (missing simulation, strategy, or parse error).
     */
    private StrategyConfig loadStrategyConfig(SimulationId simulationId) {
        try {
            Optional<Simulation> simOpt = simulationRepository.findById(simulationId);
            if (simOpt.isEmpty()) {
                LOG.warn("Cannot load strategy config: simulation {} not found", simulationId);
                return null;
            }
            Optional<Strategy> stratOpt = strategyRepository.findById(simOpt.get().getStrategyId());
            if (stratOpt.isEmpty()) {
                LOG.warn("Cannot load strategy config: strategy {} not found", simOpt.get().getStrategyId());
                return null;
            }
            return strategyConfigParser.parse(stratOpt.get().getContent());
        } catch (Exception e) {
            LOG.warn("Failed to load strategy config for simulation {}: {}", simulationId, e.getMessage());
            return null;
        }
    }

    /**
     * Cap a single BUY order to a fraction of total portfolio value.
     *
     * <p>Two distinct cases share this ceiling:</p>
     * <ul>
     *   <li>The strategy sets {@code max_order_fraction}: a hard per-order ceiling for every
     *       BUY, whether the agents sized it or the platform did.</li>
     *   <li>The agents returned no quantity ({@code platformSized}): the platform must pick a
     *       size, and "everything we can afford" proved to be the wrong default — it consumed
     *       the entire position_limits allowance on day one, regardless of what the strategy
     *       body said about tranches. Default to {@link #DEFAULT_ORDER_FRACTION} of portfolio
     *       value instead, and say so on the decision.</li>
     * </ul>
     *
     * <p>Fails open (returns the quantity unchanged) when portfolio value or price are
     * unavailable, matching the other enforcement helpers. Package-private for direct testing.</p>
     */
    BigDecimal applyOrderSizePolicy(
            ConsensusDecision decision, Symbol symbol, BigDecimal quantity,
            boolean platformSized, StrategyConfig config, Instant timestamp) {
        Double configuredFraction = config != null ? config.getMaxOrderFraction() : null;
        if (!platformSized && configuredFraction == null) {
            return quantity;
        }
        BigDecimal fraction = configuredFraction != null
                ? BigDecimal.valueOf(configuredFraction)
                : DEFAULT_ORDER_FRACTION;
        try {
            Money portfolioValue = brokerPort.getPortfolioValue(decision.getSimulationId(), timestamp);
            Money marketPrice = getMarketPrice(symbol, timestamp);
            BigDecimal capQuantity = null;
            if (portfolioValue != null && portfolioValue.isPositive() && marketPrice != null) {
                BigDecimal executionPrice = marketPrice.amount()
                        .multiply(BigDecimal.ONE.add(EXECUTION_COST_BUFFER));
                capQuantity = portfolioValue.amount().multiply(fraction)
                        .divide(executionPrice, 8, java.math.RoundingMode.DOWN);
            }
            boolean capBinds = capQuantity != null && quantity.compareTo(capQuantity) > 0;
            BigDecimal sized = capBinds ? capQuantity : quantity;
            if (sized.compareTo(BigDecimal.ZERO) <= 0) {
                // Nothing to size; the zero-quantity HOLD conversion downstream explains itself.
                return sized;
            }
            String fractionPct = fraction.multiply(BigDecimal.valueOf(100)).stripTrailingZeros().toPlainString();
            if (platformSized) {
                String basis = capBinds
                        ? fractionPct + "% of portfolio value ($" + displayAmount(portfolioValue.amount()) + ")"
                        : "the affordable maximum";
                String control = configuredFraction != null
                        ? "max_order_fraction (" + configuredFraction + ")"
                        : "the platform default of " + fractionPct + "% per order"
                                + " — set max_order_fraction in the strategy frontmatter to change it";
                decision.addNote(String.format(
                        "No quantity from consensus — platform-sized this BUY to %s: %s %s. Ceiling: %s.",
                        basis, sized.toPlainString(), symbol.ticker(), control));
                LOG.info("No agent quantity for {} — platform-sized to {} ({})",
                        symbol.ticker(), sized, basis);
            } else if (capBinds) {
                decision.addNote(String.format(
                        "max_order_fraction (%s) caps this BUY at %s%% of portfolio value ($%s): "
                                + "quantity reduced from %s to %s.",
                        configuredFraction, fractionPct, displayAmount(portfolioValue.amount()),
                        quantity.toPlainString(), sized.toPlainString()));
                LOG.info("max_order_fraction {} caps BUY for {} from {} to {}",
                        configuredFraction, symbol.ticker(), quantity, sized);
            }
            return sized;
        } catch (Exception e) {
            LOG.warn("Failed to apply order-size policy for {}: {}", symbol.ticker(), e.getMessage());
            return quantity;
        }
    }

    /** Round a money amount to two decimals for note/display purposes. */
    private static BigDecimal displayAmount(BigDecimal amount) {
        return amount.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    /**
     * Enforce position limit for a BUY order.
     * Caps quantity so the position value after purchase does not exceed the position limit
     * fraction of total portfolio value. When the limit leaves no room at all, the returned
     * {@link RiskCap} carries a reason naming the limit and the current holding.
     *
     * <p>Package-private for direct testing of the cap arithmetic.</p>
     */
    RiskCap enforcePositionLimit(
            SimulationId simulationId, Symbol symbol, BigDecimal requestedQuantity,
            StrategyConfig config, Instant timestamp) {
        Map<String, Double> limits = config.getPositionLimits();
        if (limits == null || limits.isEmpty()) {
            return RiskCap.of(requestedQuantity);
        }

        Double limitFraction = limits.get(symbol.ticker());
        if (limitFraction == null) {
            return RiskCap.of(requestedQuantity);
        }

        try {
            Money portfolioValue = brokerPort.getPortfolioValue(simulationId, timestamp);
            if (portfolioValue == null || !portfolioValue.isPositive()) {
                return RiskCap.of(requestedQuantity);
            }

            Money marketPrice = getMarketPrice(symbol, timestamp);
            if (marketPrice == null) {
                return RiskCap.of(requestedQuantity);
            }

            // Calculate max allowed position value
            BigDecimal maxPositionValue = portfolioValue.amount().multiply(BigDecimal.valueOf(limitFraction));

            // Get current position value
            Portfolio portfolio = brokerPort.getCurrentPortfolio(simulationId);
            Position currentPosition = portfolio.positions().get(symbol);
            BigDecimal currentPositionValue = BigDecimal.ZERO;
            if (currentPosition != null) {
                currentPositionValue = currentPosition.quantity().multiply(marketPrice.amount());
            }

            // Calculate remaining allowed purchase value
            BigDecimal remainingAllowedValue = maxPositionValue.subtract(currentPositionValue);
            if (remainingAllowedValue.compareTo(BigDecimal.ZERO) <= 0) {
                BigDecimal holdingPct = currentPositionValue
                        .multiply(BigDecimal.valueOf(100))
                        .divide(portfolioValue.amount(), 1, java.math.RoundingMode.HALF_UP);
                String reason = String.format(
                        "position_limits.%s (%s) reached: holding %s%% of portfolio ($%s of $%s allowed)",
                        symbol.ticker(), limitFraction, holdingPct.toPlainString(),
                        displayAmount(currentPositionValue), displayAmount(maxPositionValue));
                LOG.info("Position limit blocks BUY for {}: {}", symbol.ticker(), reason);
                return RiskCap.blocked(reason);
            }

            // Cap quantity based on remaining allowed value.
            // Size against the worst-case EXECUTION price, not the market price: the fill lands
            // at market + slippage, so dividing by the unslipped price hands back exactly the
            // cap-hugging quantity and the settled position lands just above the limit. The
            // broker's slippage is randomised and adapter-specific, so assume the same
            // execution-cost buffer used when sizing a BUY against available cash.
            BigDecimal executionPrice = marketPrice.amount()
                    .multiply(BigDecimal.ONE.add(EXECUTION_COST_BUFFER));
            BigDecimal maxQuantity = remainingAllowedValue.divide(
                    executionPrice, 8, java.math.RoundingMode.DOWN);

            if (requestedQuantity.compareTo(maxQuantity) > 0) {
                LOG.info("Position limit caps BUY for {} from {} to {} (limit: {}%, portfolio: ${})",
                        symbol.ticker(), requestedQuantity, maxQuantity,
                        limitFraction * 100, portfolioValue.amount());
                return RiskCap.of(maxQuantity);
            }

            return RiskCap.of(requestedQuantity);
        } catch (Exception e) {
            LOG.warn("Failed to enforce position limit for {}: {}", symbol.ticker(), e.getMessage());
            return RiskCap.of(requestedQuantity);
        }
    }

    /**
     * Enforce reserve cash percentage for a BUY order.
     * Caps quantity so cash after purchase remains >= totalPortfolioValue * reserveCashPercentage.
     * When the reserve leaves nothing to spend, the returned {@link RiskCap} carries a reason
     * naming the reserve and both cash numbers.
     *
     * <p>Package-private for direct testing of the cap arithmetic.</p>
     */
    RiskCap enforceReserveCash(
            SimulationId simulationId, BigDecimal requestedQuantity,
            StrategyConfig config, Symbol symbol, Instant timestamp) {
        double reservePct = config.getReserveCashPercentage();
        if (reservePct <= 0.0) {
            return RiskCap.of(requestedQuantity);
        }

        try {
            Money portfolioValue = brokerPort.getPortfolioValue(simulationId, timestamp);
            if (portfolioValue == null || !portfolioValue.isPositive()) {
                return RiskCap.of(requestedQuantity);
            }

            Money availableCash = brokerPort.getAvailableBalance(simulationId);
            Money marketPrice = getMarketPrice(symbol, timestamp);
            if (marketPrice == null) {
                return RiskCap.of(requestedQuantity);
            }

            // Required reserve: total portfolio * reservePct
            BigDecimal requiredReserve = portfolioValue.amount().multiply(BigDecimal.valueOf(reservePct));
            BigDecimal tradeValue = requestedQuantity.multiply(marketPrice.amount());
            BigDecimal cashAfterTrade = availableCash.amount().subtract(tradeValue);

            if (cashAfterTrade.compareTo(requiredReserve) < 0) {
                // Cap: cash - reserve = max spendable
                BigDecimal maxSpendable = availableCash.amount().subtract(requiredReserve);
                if (maxSpendable.compareTo(BigDecimal.ZERO) <= 0) {
                    String reason = String.format(
                            "reserve_cash_percentage (%s) leaves nothing to spend: cash $%s minus required reserve $%s",
                            reservePct, displayAmount(availableCash.amount()), displayAmount(requiredReserve));
                    LOG.info("Reserve cash blocks BUY for {}: {}", symbol.ticker(), reason);
                    return RiskCap.blocked(reason);
                }
                BigDecimal maxQuantity = maxSpendable.divide(
                        marketPrice.amount(), 8, java.math.RoundingMode.DOWN);
                LOG.info("Reserve cash caps BUY for {} from {} to {} (reserve: {}%, required: ${})",
                        symbol.ticker(), requestedQuantity, maxQuantity, reservePct * 100, requiredReserve);
                return RiskCap.of(maxQuantity);
            }

            return RiskCap.of(requestedQuantity);
        } catch (Exception e) {
            LOG.warn("Failed to enforce reserve cash for {}: {}", symbol.ticker(), e.getMessage());
            return RiskCap.of(requestedQuantity);
        }
    }

    /**
     * Resolve the fractional-share capabilities of the broker that will execute the given
     * simulation's trades.
     *
     * <p>When the {@link BrokerRouter} is wired (production + integration), this delegates to
     * {@link BrokerRouter#capabilitiesForSimulation(SimulationId)}, which returns the linked
     * account's broker capabilities — whole-share equities for a Saxo (EU) account, fully
     * fractional for an Alpaca / Mock account, and fully fractional for legacy (unlinked)
     * simulations. That makes the entire BUY sizing path account-aware: a Saxo equity order is
     * sized and rounded to whole shares, while the Alpaca / Mock path stays byte-identical to
     * the historical scale-8 floor.</p>
     *
     * <p>When the router is absent (bare unit tests wiring only a {@link BrokerPort} mock), this
     * falls back to {@link BrokerPort#capabilities()} — the default method that returns
     * {@link BrokerCapabilities#fractional() fractional} for every real adapter. The
     * null-coalesce only matters for bare port mocks that don't stub the default method; those
     * behave exactly like a fractional broker.</p>
     *
     * @param simulationId the simulation whose executing broker's capabilities are needed
     * @return the fractional-share capabilities to size and round this simulation's orders by
     */
    private BrokerCapabilities capabilitiesForSimulation(SimulationId simulationId) {
        if (brokerRouter != null) {
            BrokerCapabilities capabilities = brokerRouter.capabilitiesForSimulation(simulationId);
            return capabilities != null ? capabilities : BrokerCapabilities.fractional();
        }
        BrokerCapabilities capabilities = brokerPort.capabilities();
        return capabilities != null ? capabilities : BrokerCapabilities.fractional();
    }

    /**
     * Compute the maximum quantity for a trade when the agent didn't specify one.
     * For BUY: uses all available cash (position limits and reserve cash are enforced separately).
     * For SELL: sells the entire current position.
     */
    private BigDecimal computeMaxQuantity(SimulationId simulationId, Symbol symbol,
            TradeAction action, Instant timestamp) {
        try {
            if (action == TradeAction.SELL) {
                Portfolio portfolio = brokerPort.getCurrentPortfolio(simulationId);
                Position position = portfolio.positions().get(symbol);
                if (position != null && position.quantity().compareTo(BigDecimal.ZERO) > 0) {
                    return position.quantity();
                }
                return BigDecimal.ZERO;
            }

            // BUY: compute max from available cash and market price
            Money availableCash = brokerPort.getAvailableBalance(simulationId);
            Money marketPrice = getMarketPrice(symbol, timestamp);
            if (availableCash == null || marketPrice == null || !marketPrice.isPositive()) {
                LOG.warn("Cannot compute max quantity for {} — missing cash or price data", symbol.ticker());
                return BigDecimal.ZERO;
            }
            // Buffer for slippage + commission, then size to what the routed broker can
            // actually fill: fractional brokers floor to scale 8 (byte-identical to the prior
            // inlined behaviour), whole-share brokers floor to integral shares.
            Money affordableCash = availableCash.multiply(BigDecimal.ONE.subtract(EXECUTION_COST_BUFFER));
            return ShareSizing.maxAffordableQuantity(
                affordableCash, marketPrice, symbol.type(), capabilitiesForSimulation(simulationId));
        } catch (Exception e) {
            LOG.warn("Failed to compute max quantity for {}: {}", symbol.ticker(), e.getMessage());
            return BigDecimal.ZERO;
        }
    }

    /**
     * Validate trade meets minimum trade amount requirements.
     * Prevents meaningless micro-trades (e.g., $0.16 worth of stock).
     *
     * @param symbol Symbol to trade
     * @param quantity Requested trade quantity
     * @param timestamp Timestamp for price lookup
     * @return ValidationResult with isValid flag and rejection reason if invalid
     */
    private ValidationResult validateMinimumTradeAmount(Symbol symbol, BigDecimal quantity, Instant timestamp) {
        try {
            // Get market price for this symbol
            Money marketPrice = getMarketPrice(symbol, timestamp);
            if (marketPrice == null) {
                LOG.warn("No market price found for {}. Cannot validate minimum trade amount. Allowing trade.", symbol.ticker());
                return ValidationResult.valid();
            }

            // Compute the trade value in the instrument's own settlement currency (USD for a
            // US venue, EUR for an EU/Saxo venue) — marketPrice already carries that currency,
            // so the trade-value Money never trips Money's same-currency guard.
            Money tradeValue = marketPrice.multiply(quantity);

            // The configured floor is denominated in the system base unit; stamp it into the
            // same currency as the trade value so the comparison and the user-facing reason are
            // currency-correct (a €-priced order is judged against a €-formatted floor).
            InvestmentType investmentType = symbol.type();
            BigDecimal minTradeAmount = investmentType == InvestmentType.CRYPTO
                    ? tradingLimitsConfig.crypto()
                    : tradingLimitsConfig.stock();
            Money minTradeMoney = Money.of(minTradeAmount, marketPrice.currency());

            // Check if trade meets minimum (numeric comparison; both sides same currency).
            if (tradeValue.amount().compareTo(minTradeAmount) < 0) {
                String reason = String.format(
                        "Trade value %s is below minimum %s for %s assets (quantity: %s, price: %s)",
                        tradeValue.format(), minTradeMoney.format(), investmentType, quantity, marketPrice.format()
                );
                LOG.warn("Trade rejected: {} for symbol {}", reason, symbol.ticker());
                return ValidationResult.invalid(reason);
            }

            LOG.debug("Trade value {} meets minimum {} for {} ({})",
                    tradeValue.format(), minTradeMoney.format(), symbol.ticker(), investmentType);
            return ValidationResult.valid();

        } catch (Exception e) {
            LOG.error("Failed to validate minimum trade amount. Allowing trade by default.", e);
            return ValidationResult.valid(); // Fail open to avoid blocking legitimate trades on errors
        }
    }

    /**
     * Result of trade validation.
     */
    private static class ValidationResult {
        private final boolean valid;
        private final String rejectionReason;

        private ValidationResult(boolean valid, String rejectionReason) {
            this.valid = valid;
            this.rejectionReason = rejectionReason;
        }

        static ValidationResult valid() {
            return new ValidationResult(true, null);
        }

        static ValidationResult invalid(String reason) {
            return new ValidationResult(false, reason);
        }

        boolean isValid() {
            return valid;
        }

        String getRejectionReason() {
            return rejectionReason;
        }
    }
}
