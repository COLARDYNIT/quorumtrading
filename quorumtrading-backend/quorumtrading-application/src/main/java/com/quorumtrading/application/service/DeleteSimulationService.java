package com.quorumtrading.application.service;

import com.quorumtrading.application.port.out.BrokerAccountRepository;
import com.quorumtrading.application.port.out.SimulationRepository;
import com.quorumtrading.application.port.out.StrategyRepository;
import com.quorumtrading.application.usecase.DeleteSimulationUseCase;
import com.quorumtrading.application.usecase.command.DeleteSimulationCommand;
import com.quorumtrading.model.entity.BrokerAccount;
import com.quorumtrading.model.entity.Simulation;
import com.quorumtrading.model.entity.Strategy;
import com.quorumtrading.model.valueobject.Money;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service implementation for deleting simulations.
 * Framework-agnostic, uses constructor injection.
 */
public class DeleteSimulationService implements DeleteSimulationUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(DeleteSimulationService.class);

    private final SimulationRepository simulationRepository;
    private final StrategyRepository strategyRepository;
    private final AuthorizationService authorizationService;
    private final BrokerAccountRepository brokerAccountRepository;

    public DeleteSimulationService(SimulationRepository simulationRepository, StrategyRepository strategyRepository, AuthorizationService authorizationService, BrokerAccountRepository brokerAccountRepository) {
        this.simulationRepository = simulationRepository;
        this.strategyRepository = strategyRepository;
        this.authorizationService = authorizationService;
        this.brokerAccountRepository = brokerAccountRepository;
    }

    @Override
    public void execute(DeleteSimulationCommand command) {
        Simulation simulation = simulationRepository.findById(command.simulationId())
            .orElseThrow(() -> new IllegalArgumentException("Simulation not found: " + command.simulationId()));

        Strategy strategy = strategyRepository.findById(simulation.getStrategyId())
            .orElseThrow(() -> new IllegalArgumentException("Strategy not found: " + simulation.getStrategyId()));

        authorizationService.requireDelete(command.requestingUserId(), simulation, strategy);

        // Always release reserved capital before deleting, regardless of simulation state.
        // Terminal simulations *should* have been released by End/ForceEnd services, but
        // if that release failed (e.g., exception), we must clean up here to prevent leaks.
        releaseReservedCapital(simulation);

        if (!simulationRepository.deleteById(command.simulationId())) {
            throw new IllegalArgumentException("Simulation not found: " + command.simulationId());
        }

        deleteInlineBrokerAccount(simulation);
    }

    /**
     * Delete the broker account backing this simulation if it was created inline for it.
     *
     * <p>An inline mock account has no meaning once its simulation is gone; leaving it behind
     * accumulated one orphan ACTIVE account per backtest. User-created accounts are never
     * touched — they outlive any individual simulation by design.</p>
     *
     * <p>Runs after the simulation row is gone so the FK reference is already cleared. A
     * failure here is logged rather than thrown: the simulation is already deleted, and
     * failing the call would misreport a completed deletion as a failure.</p>
     */
    private void deleteInlineBrokerAccount(Simulation simulation) {
        if (simulation.getBrokerAccountId() == null) {
            return;
        }
        try {
            BrokerAccount account = brokerAccountRepository.findById(simulation.getBrokerAccountId())
                .orElse(null);
            if (account == null || !account.isInline()) {
                return;
            }
            brokerAccountRepository.delete(account.getId());
            LOG.info("Deleted inline mock broker account {} with its simulation {}",
                account.getId().value(), simulation.getSimulationId().value());
        } catch (Exception e) {
            LOG.error("Failed to delete inline broker account {} for deleted simulation {}",
                simulation.getBrokerAccountId().value(), simulation.getSimulationId().value(), e);
        }
    }

    private void releaseReservedCapital(Simulation simulation) {
        if (simulation.getBrokerAccountId() == null || simulation.getTradingLimit() == null) {
            return;
        }
        try {
            BrokerAccount account = brokerAccountRepository.findById(simulation.getBrokerAccountId())
                .orElse(null);
            if (account == null) {
                LOG.warn("Broker account {} not found when releasing capital for deleted simulation {}",
                    simulation.getBrokerAccountId().value(), simulation.getSimulationId().value());
                return;
            }

            // Cap release at current reserved balance to handle cases where capital
            // was already partially or fully released (e.g., by End/ForceEnd services)
            Money releaseAmount = simulation.getTradingLimit();
            Money currentReserved = account.getReservedBalance();

            if (currentReserved.amount().compareTo(java.math.BigDecimal.ZERO) == 0) {
                LOG.debug("No reserved capital to release for simulation {} (already released)",
                    simulation.getSimulationId().value());
                return;
            }

            if (releaseAmount.compareTo(currentReserved) > 0) {
                LOG.warn("Trading limit {} exceeds reserved balance {} for simulation {}. Releasing reserved amount only.",
                    releaseAmount, currentReserved, simulation.getSimulationId().value());
                releaseAmount = currentReserved;
            }

            account.release(releaseAmount);
            brokerAccountRepository.save(account);
            LOG.info("Released {} from broker account {} for deleted simulation {}",
                releaseAmount, simulation.getBrokerAccountId().value(),
                simulation.getSimulationId().value());
        } catch (Exception e) {
            LOG.error("Failed to release capital for simulation {} from broker account {}",
                simulation.getSimulationId().value(), simulation.getBrokerAccountId().value(), e);
        }
    }
}
