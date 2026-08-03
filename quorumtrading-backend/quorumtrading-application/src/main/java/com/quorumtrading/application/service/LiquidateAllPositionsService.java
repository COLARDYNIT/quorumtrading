package com.quorumtrading.application.service;

import com.quorumtrading.application.port.out.SimulationRepository;
import com.quorumtrading.application.port.out.StrategyRepository;
import com.quorumtrading.application.usecase.LiquidateAllPositionsUseCase;
import com.quorumtrading.application.usecase.UpdatePortfolioWithMarketValueUseCase;
import com.quorumtrading.application.usecase.result.LiquidateAllResult;
import com.quorumtrading.model.broker.ExecuteTradeCommand;
import com.quorumtrading.model.broker.TradeResult;
import com.quorumtrading.model.entity.Portfolio;
import com.quorumtrading.model.entity.PortfolioSnapshot;
import com.quorumtrading.model.entity.Position;
import com.quorumtrading.model.entity.Simulation;
import com.quorumtrading.model.entity.Strategy;
import com.quorumtrading.model.enums.TradeAction;
import com.quorumtrading.model.valueobject.SimulationId;
import com.quorumtrading.model.valueobject.Symbol;
import com.quorumtrading.model.valueobject.UserId;
import org.jboss.logging.Logger;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class LiquidateAllPositionsService implements LiquidateAllPositionsUseCase {

    private static final Logger LOG = Logger.getLogger(LiquidateAllPositionsService.class);

    private final SimulationRepository simulationRepository;
    private final StrategyRepository strategyRepository;
    private final BrokerRouter brokerRouter;
    private final UpdatePortfolioWithMarketValueUseCase updatePortfolioWithMarketValueUseCase;
    private final AuthorizationService authorizationService;

    public LiquidateAllPositionsService(
            SimulationRepository simulationRepository,
            StrategyRepository strategyRepository,
            BrokerRouter brokerRouter,
            UpdatePortfolioWithMarketValueUseCase updatePortfolioWithMarketValueUseCase,
            AuthorizationService authorizationService) {
        this.simulationRepository = simulationRepository;
        this.strategyRepository = strategyRepository;
        this.brokerRouter = brokerRouter;
        this.updatePortfolioWithMarketValueUseCase = updatePortfolioWithMarketValueUseCase;
        this.authorizationService = authorizationService;
    }

    @Override
    public LiquidateAllResult execute(SimulationId simulationId, UserId requestingUserId) {
        Simulation simulation = simulationRepository.findById(simulationId)
            .orElseThrow(() -> new java.util.NoSuchElementException(
                "Simulation not found: " + simulationId.value()));

        // Authorization check
        Strategy strategy = strategyRepository.findById(simulation.getStrategyId())
            .orElseThrow(() -> new java.util.NoSuchElementException("Strategy not found"));
        authorizationService.requireEdit(requestingUserId, simulation, strategy);

        // Reject backtests outright, not only broker-less ones. Every backtest now has a broker
        // account — an inline mock created for it — so the old "backtest AND no broker account"
        // condition could never be true and the guard let a bulk liquidation run against
        // replayed history, filled at TODAY's market price.
        if (simulation.isBacktest()) {
            throw new IllegalStateException(
                "Cannot liquidate positions in a backtest simulation — a backtest replays "
                + "historical data and a manual liquidation would be filled at today's market "
                + "price. Use force_end_simulation to end it, or restart_simulation_from_decision "
                + "to explore a different path.");
        }

        if (simulation.isTerminal()) {
            throw new IllegalStateException(
                "Cannot liquidate positions in a " + simulation.getStatus()
                + " simulation — it has already ended.");
        }

        // Cancel open orders first
        brokerRouter.cancelAllOpenOrders(simulationId);

        Portfolio portfolio = brokerRouter.getCurrentPortfolio(simulationId);
        Map<Symbol, Position> positions = portfolio.positions();

        List<Map.Entry<Symbol, Position>> openPositions = positions.entrySet().stream()
            .filter(e -> e.getValue().quantity().compareTo(BigDecimal.ZERO) > 0)
            .toList();

        if (openPositions.isEmpty()) {
            // Same source as the post-liquidation path below — see simulationCashAfter. This
            // branch is the one a caller hits most often, and it was reporting the shared broker
            // account's balance.
            List<String> noPositionErrors = new ArrayList<>();
            return new LiquidateAllResult(0, 0, noPositionErrors,
                simulationCashAfter(simulationId, noPositionErrors));
        }

        LOG.infof("Liquidating %d position(s) for simulation %s",
            openPositions.size(), simulationId.value().toString().substring(0, 8));

        int liquidated = 0;
        int failed = 0;
        List<String> errors = new ArrayList<>();
        Instant now = Instant.now();

        for (Map.Entry<Symbol, Position> entry : openPositions) {
            Symbol symbol = entry.getKey();
            Position position = entry.getValue();

            try {
                ExecuteTradeCommand command = ExecuteTradeCommand.market(
                    simulationId, TradeAction.SELL, symbol, position.quantity(), now);

                TradeResult result = brokerRouter.executeTrade(command);

                if (result.isRejected()) {
                    failed++;
                    errors.add(symbol.ticker() + ": " + result.getRejectionReason().orElse("unknown"));
                } else {
                    liquidated++;
                }
            } catch (Exception e) {
                failed++;
                errors.add(symbol.ticker() + ": " + e.getMessage());
                LOG.warnf("Failed to liquidate %s for simulation %s: %s",
                    symbol.ticker(), simulationId.value().toString().substring(0, 8), e.getMessage());
            }
        }

        // Update portfolio snapshot once after all sells. If this does not happen the positions
        // are gone at the broker while every read path — get_simulation, query_portfolio_snapshots
        // — still reports the pre-liquidation value, so the caller must be told rather than left
        // with a success message over stale numbers.
        BigDecimal cashAfterLiquidation = simulationCashAfter(simulationId, errors);

        return new LiquidateAllResult(liquidated, failed, errors, cashAfterLiquidation);
    }

    /**
     * This simulation's cash, taken from a freshly written portfolio snapshot.
     *
     * <p>Deliberately NOT {@code brokerRouter.getCurrentPortfolio(...).cashAvailable()}. A MOCK
     * broker account is shared by every simulation that uses it, so the broker's answer is the
     * ACCOUNT balance: one measured case reported <b>$12,000</b> for a simulation whose tracked
     * cash was <b>$1,000</b> — twelve times the truth — to a caller who had just liquidated it.
     *
     * <p>Both call sites use this one method on purpose. The defect existed twice, once in each
     * branch, and the first fix only corrected the post-liquidation copy while the empty-positions
     * branch — the one most callers actually hit — kept reporting the broker figure.
     *
     * @return the simulation's cash, or {@code null} when no snapshot could be written. Null says
     *     "unknown", which beats a confident wrong number; the caller also gets an error explaining
     *     that reported values are stale.
     */
    private BigDecimal simulationCashAfter(SimulationId simulationId, List<String> errors) {
        try {
            PortfolioSnapshot snapshot = updatePortfolioWithMarketValueUseCase
                .updatePortfolioWithMarketValue(simulationId, Instant.now());
            if (snapshot == null) {
                LOG.errorf("No portfolio snapshot was written for simulation %s — reported value "
                        + "will be stale", simulationId.value().toString().substring(0, 8));
                errors.add("The portfolio snapshot could not be written; reported value and return "
                    + "are stale until the next evaluation.");
                return null;
            }
            return snapshot.getCashAvailable() == null ? null : snapshot.getCashAvailable().amount();
        } catch (Exception e) {
            LOG.errorf("Could not update portfolio valuation for simulation %s: %s",
                simulationId.value().toString().substring(0, 8), e.getMessage());
            errors.add("The portfolio valuation failed to update (" + e.getMessage()
                + "); reported value and return are stale.");
            return null;
        }
    }
}
