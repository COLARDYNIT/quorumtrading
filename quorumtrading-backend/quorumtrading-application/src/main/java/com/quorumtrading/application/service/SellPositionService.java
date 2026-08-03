package com.quorumtrading.application.service;

import com.quorumtrading.application.port.out.SimulationRepository;
import com.quorumtrading.application.port.out.StrategyRepository;
import com.quorumtrading.application.usecase.SellPositionUseCase;
import com.quorumtrading.application.usecase.UpdatePortfolioWithMarketValueUseCase;
import com.quorumtrading.application.usecase.result.SellPositionResult;
import com.quorumtrading.model.broker.ExecuteTradeCommand;
import com.quorumtrading.model.broker.TradeResult;
import com.quorumtrading.model.entity.Portfolio;
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

public class SellPositionService implements SellPositionUseCase {

    private static final Logger LOG = Logger.getLogger(SellPositionService.class);

    private final SimulationRepository simulationRepository;
    private final StrategyRepository strategyRepository;
    private final BrokerRouter brokerRouter;
    private final UpdatePortfolioWithMarketValueUseCase updatePortfolioWithMarketValueUseCase;
    private final AuthorizationService authorizationService;

    public SellPositionService(
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
    public SellPositionResult execute(SimulationId simulationId, Symbol symbol, UserId requestingUserId) {
        Simulation simulation = simulationRepository.findById(simulationId)
            .orElseThrow(() -> new java.util.NoSuchElementException(
                "Simulation not found: " + simulationId.value()));

        // Authorization check
        Strategy strategy = strategyRepository.findById(simulation.getStrategyId())
            .orElseThrow(() -> new java.util.NoSuchElementException("Strategy not found"));
        authorizationService.requireEdit(requestingUserId, simulation, strategy);

        // Reject backtests outright, not only broker-less ones. Every backtest now has a broker
        // account — an inline mock created for it — so the old "backtest AND no broker account"
        // condition could never be true and the guard let manual sells run against replayed
        // history. Worse, the fill would be priced at TODAY's market, silently injecting a
        // present-day price into a simulation of the past.
        if (simulation.isBacktest()) {
            return SellPositionResult.failure(
                "Cannot sell positions in a backtest simulation — a backtest replays historical "
                + "data and a manual sell would be filled at today's market price. Use "
                + "restart_simulation_from_decision to explore a different path.");
        }

        // A terminal simulation is a finished record. Trading into it changes a result that has
        // already been reported.
        if (simulation.isTerminal()) {
            return SellPositionResult.failure(
                "Cannot sell positions in a " + simulation.getStatus() + " simulation — it has "
                + "already ended.");
        }

        Portfolio portfolio = brokerRouter.getCurrentPortfolio(simulationId);
        Position position = portfolio.positions().get(symbol);

        if (position == null || position.quantity().compareTo(BigDecimal.ZERO) <= 0) {
            return SellPositionResult.failure(
                "No position found for symbol " + symbol.ticker() + " in simulation " + simulationId.value());
        }

        LOG.infof("Selling entire position of %s (%s) for simulation %s",
            symbol.ticker(), position.quantity().toPlainString(),
            simulationId.value().toString().substring(0, 8));

        ExecuteTradeCommand command = ExecuteTradeCommand.market(
            simulationId, TradeAction.SELL, symbol, position.quantity(), Instant.now());

        TradeResult result = brokerRouter.executeTrade(command);

        if (result.isRejected()) {
            return SellPositionResult.failure("Sell order failed: " + result.getRejectionReason().orElse("unknown"));
        }

        // Without this snapshot the position is gone at the broker while get_simulation and
        // query_portfolio_snapshots still report the pre-sale value, so a failure here is an
        // error the operator needs to see, not a debug aside.
        try {
            if (updatePortfolioWithMarketValueUseCase
                    .updatePortfolioWithMarketValue(simulationId, Instant.now()) == null) {
                LOG.errorf("Sold %s for simulation %s but no portfolio snapshot was written — "
                        + "reported value will be stale",
                    symbol.ticker(), simulationId.value().toString().substring(0, 8));
            }
        } catch (Exception e) {
            LOG.errorf("Could not update portfolio valuation after selling %s for simulation %s: %s",
                symbol.ticker(), simulationId.value().toString().substring(0, 8), e.getMessage());
        }

        return SellPositionResult.success(result.filledQuantity(), result.executionPrice().amount());
    }
}
