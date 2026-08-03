package com.quorumtrading.application.service;

import com.quorumtrading.application.event.SimulationCompletedEvent;
import com.quorumtrading.application.port.out.BrokerAccountRepository;
import com.quorumtrading.application.port.out.EventPublisher;
import com.quorumtrading.application.port.out.PortfolioRepository;
import com.quorumtrading.application.port.out.SimulationContributionRepository;
import com.quorumtrading.application.port.out.SimulationRepository;
import com.quorumtrading.application.port.out.StrategyRepository;
import com.quorumtrading.application.usecase.ForceEndSimulationUseCase;
import com.quorumtrading.application.usecase.LiquidateAllPositionsUseCase;
import com.quorumtrading.application.usecase.command.ForceEndSimulationCommand;
import com.quorumtrading.application.usecase.result.ForceEndSimulationResult;
import com.quorumtrading.application.usecase.result.LiquidateAllResult;
import com.quorumtrading.model.entity.BrokerAccount;
import com.quorumtrading.model.entity.Contribution;
import com.quorumtrading.model.entity.PortfolioSnapshot;
import com.quorumtrading.model.entity.Simulation;
import com.quorumtrading.model.entity.Strategy;
import com.quorumtrading.model.enums.SimulationStatus;
import com.quorumtrading.model.service.PerformanceCalculator;
import org.jboss.logging.Logger;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public class ForceEndSimulationService implements ForceEndSimulationUseCase {

    private static final Logger LOG = Logger.getLogger(ForceEndSimulationService.class);

    private final SimulationRepository simulationRepository;
    private final StrategyRepository strategyRepository;
    private final LiquidateAllPositionsUseCase liquidateAllPositionsUseCase;
    private final PortfolioRepository portfolioRepository;
    private final EventPublisher<SimulationCompletedEvent> simulationCompletedEventPublisher;
    private final BrokerAccountRepository brokerAccountRepository;
    private final AuthorizationService authorizationService;
    private final SimulationContributionRepository contributionRepository;

    public ForceEndSimulationService(
            SimulationRepository simulationRepository,
            StrategyRepository strategyRepository,
            LiquidateAllPositionsUseCase liquidateAllPositionsUseCase,
            PortfolioRepository portfolioRepository,
            EventPublisher<SimulationCompletedEvent> simulationCompletedEventPublisher,
            BrokerAccountRepository brokerAccountRepository,
            AuthorizationService authorizationService,
            SimulationContributionRepository contributionRepository) {
        this.simulationRepository = simulationRepository;
        this.strategyRepository = strategyRepository;
        this.liquidateAllPositionsUseCase = liquidateAllPositionsUseCase;
        this.portfolioRepository = portfolioRepository;
        this.simulationCompletedEventPublisher = simulationCompletedEventPublisher;
        this.brokerAccountRepository = brokerAccountRepository;
        this.authorizationService = authorizationService;
        this.contributionRepository = contributionRepository;
    }

    @Override
    public ForceEndSimulationResult execute(ForceEndSimulationCommand command) {
        Simulation simulation = simulationRepository.findById(command.simulationId())
            .orElseThrow(() -> new java.util.NoSuchElementException(
                "Simulation not found: " + command.simulationId().value()));

        // Authorization check
        Strategy strategy = strategyRepository.findById(simulation.getStrategyId())
            .orElseThrow(() -> new java.util.NoSuchElementException("Strategy not found"));
        authorizationService.requireEdit(command.requestingUserId(), simulation, strategy);

        if (simulation.isTerminal()) {
            throw new IllegalStateException(
                "Simulation is already " + simulation.getStatus().name());
        }

        LOG.infof("Force-ending simulation %s (liquidate=%s, status=%s)",
            command.simulationId().value().toString().substring(0, 8),
            command.liquidatePositions(),
            simulation.getStatus());

        LiquidateAllResult liquidateResult = null;
        String liquidationSkippedReason = null;
        if (command.liquidatePositions()) {
            try {
                // Forward the caller — the single-argument overload passes a null user, which
                // the nested authorization check reports as "User anonymous does not have
                // access", failing every force-end that asked to liquidate.
                liquidateResult = liquidateAllPositionsUseCase.execute(
                    command.simulationId(), command.requestingUserId());
            } catch (IllegalStateException e) {
                // Liquidation not possible (e.g., a backtest) — proceed with the end, but the
                // caller asked for liquidation and must be told it did not happen rather than
                // reading "ended successfully" and assuming positions were closed.
                liquidationSkippedReason = e.getMessage();
                LOG.infof("Skipping liquidation for simulation %s: %s",
                    command.simulationId().value().toString().substring(0, 8), e.getMessage());
            }
        }

        // Captured before forceComplete so the message can say how far the run actually got.
        int progressAtEnd = simulation.getProgress();

        SimulationStatus statusBeforeEnd = simulation.getStatus();
        simulation.forceComplete();
        // Narrow compare-and-set, not save(). While save() still copied lifecycle columns, the
        // user's "end this run now" was reverted by the executor's very next progress write —
        // the button appeared to do nothing, and the run carried on trading.
        // Note there is deliberately no progress write here: forceComplete() leaves progress
        // where the run got to, because stamping 100 would claim a period that was not simulated.
        simulationRepository.updateStatus(simulation.getSimulationId(), statusBeforeEnd,
                simulation.getStatus(), simulation.getCompletedAt(), simulation.getFailureReason());

        // Release reserved trading capital back to broker account
        releaseReservedCapital(simulation);

        publishCompletionEvent(simulation);

        String endedAt = progressAtEnd >= 100
            ? "Simulation ended."
            : String.format("Simulation ended early at %d%% of its period.", progressAtEnd);

        if (liquidateResult != null && liquidateResult.positionsLiquidated() > 0) {
            return ForceEndSimulationResult.completedWithLiquidation(
                liquidateResult.positionsLiquidated(),
                String.format("%s Liquidated %d position(s), %d failed.%s",
                    endedAt,
                    liquidateResult.positionsLiquidated(),
                    liquidateResult.positionsFailed(),
                    liquidateResult.errors().isEmpty()
                        ? "" : " Errors: " + String.join("; ", liquidateResult.errors())));
        }

        if (liquidationSkippedReason != null) {
            return ForceEndSimulationResult.completed(String.format(
                "%s Positions were NOT liquidated: %s", endedAt, liquidationSkippedReason));
        }

        if (command.liquidatePositions()) {
            return ForceEndSimulationResult.completed(endedAt + " No open positions to liquidate.");
        }

        return ForceEndSimulationResult.completed(
            endedAt + " Positions were left open, as requested.");
    }

    private void publishCompletionEvent(Simulation simulation) {
        BigDecimal finalValue = BigDecimal.ZERO;
        BigDecimal totalReturn = BigDecimal.ZERO;

        try {
            var latestSnapshot = portfolioRepository.findLatestBySimulationId(simulation.getSimulationId());
            if (latestSnapshot.isPresent()) {
                PortfolioSnapshot snapshot = latestSnapshot.get();
                finalValue = snapshot.getTotalValue().amount();
                // Return basis is total invested (seed + Σ contributions); equals
                // initialCash when there are no contributions, so non-DCA is unchanged.
                List<Contribution> contributions =
                    contributionRepository.findBySimulationId(simulation.getSimulationId());
                BigDecimal invested = PerformanceCalculator
                    .totalInvested(simulation.getInitialCash(), contributions).amount();
                if (invested.compareTo(BigDecimal.ZERO) > 0) {
                    totalReturn = finalValue.subtract(invested)
                        .divide(invested, 6, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100));
                }
            } else {
                LOG.warnf("No portfolio snapshot found for completed simulation %s, publishing with zero values",
                    simulation.getSimulationId().value());
            }
        } catch (Exception e) {
            LOG.errorf(e, "Failed to calculate portfolio metrics for simulation %s, publishing with zero values",
                simulation.getSimulationId().value());
        }

        SimulationCompletedEvent event = new SimulationCompletedEvent(
            simulation.getSimulationId(),
            simulation.getStrategyId(),
            finalValue,
            totalReturn,
            simulation.getCompletedAt()
        );
        simulationCompletedEventPublisher.publish(event);
    }

    private void releaseReservedCapital(Simulation simulation) {
        if (simulation.getBrokerAccountId() == null || simulation.getTradingLimit() == null) {
            return;
        }
        try {
            BrokerAccount account = brokerAccountRepository.findById(simulation.getBrokerAccountId())
                .orElse(null);
            if (account == null) {
                LOG.warnf("Broker account %s not found when releasing capital for simulation %s",
                    simulation.getBrokerAccountId().value(), simulation.getSimulationId().value());
                return;
            }
            account.release(simulation.getTradingLimit());
            brokerAccountRepository.save(account);
            LOG.infof("Released %s from broker account %s for force-ended simulation %s",
                simulation.getTradingLimit(), simulation.getBrokerAccountId().value(),
                simulation.getSimulationId().value());
        } catch (Exception e) {
            LOG.errorf(e, "Failed to release capital for simulation %s from broker account %s",
                simulation.getSimulationId().value(), simulation.getBrokerAccountId().value());
        }
    }
}
