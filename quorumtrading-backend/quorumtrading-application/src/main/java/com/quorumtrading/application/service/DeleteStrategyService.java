package com.quorumtrading.application.service;

import com.quorumtrading.application.port.out.BrokerAccountRepository;
import com.quorumtrading.application.port.out.SimulationRepository;
import com.quorumtrading.application.port.out.StrategyRepository;
import com.quorumtrading.application.usecase.DeleteStrategyUseCase;
import com.quorumtrading.application.usecase.command.DeleteStrategyCommand;
import com.quorumtrading.model.entity.BrokerAccount;
import com.quorumtrading.model.entity.Simulation;
import com.quorumtrading.model.entity.Strategy;
import com.quorumtrading.model.enums.SimulationStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.NoSuchElementException;

/**
 * Service implementation for deleting strategies.
 * Framework-agnostic, uses constructor injection.
 * Releases reserved capital from broker accounts before cascade delete removes simulations.
 */
public class DeleteStrategyService implements DeleteStrategyUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(DeleteStrategyService.class);

    private final StrategyRepository strategyRepository;
    private final SimulationRepository simulationRepository;
    private final BrokerAccountRepository brokerAccountRepository;
    private final AuthorizationService authorizationService;

    public DeleteStrategyService(StrategyRepository strategyRepository, SimulationRepository simulationRepository, BrokerAccountRepository brokerAccountRepository, AuthorizationService authorizationService) {
        this.strategyRepository = strategyRepository;
        this.simulationRepository = simulationRepository;
        this.brokerAccountRepository = brokerAccountRepository;
        this.authorizationService = authorizationService;
    }

    @Override
    public void execute(DeleteStrategyCommand command) {
        Strategy strategy = strategyRepository.findById(command.strategyId())
            .orElseThrow(() -> new NoSuchElementException("Strategy not found: " + command.strategyId()));

        authorizationService.requireDelete(command.requestingUserId(), strategy);

        // Reject deletion if any simulation is actively running (LIVE or SIMULATING, not paused)
        rejectIfRunningSimulations(command);

        // Release reserved capital for all non-terminal simulations before cascade delete
        releaseCapitalForSimulations(command);

        if (!strategyRepository.deleteById(command.strategyId())) {
            throw new NoSuchElementException("Strategy not found: " + command.strategyId());
        }
    }

    /**
     * Refuse to delete a strategy that any simulation is actively executing.
     *
     * <p>This used to check {@code LIVE} only, which left running backtests unprotected: a
     * {@code SIMULATING} run was cascade-deleted mid-flight while its executor carried on against
     * rows that no longer existed — burning LLM budget on a simulation nobody would ever read,
     * then failing every insert with a foreign-key violation that reads like data corruption in
     * the log. The executor is never signalled, so the only safe answer is not to delete
     * underneath it.</p>
     *
     * <p>A paused simulation is fair game: nothing is executing it, so the cascade is clean.</p>
     */
    private void rejectIfRunningSimulations(DeleteStrategyCommand command) {
        List<Simulation> simulations = simulationRepository.findByStrategyId(command.strategyId());
        List<Simulation> running = simulations.stream()
            .filter(sim -> !sim.isTerminal() && !sim.isPaused())
            .filter(sim -> sim.getStatus() == SimulationStatus.LIVE
                        || sim.getStatus() == SimulationStatus.SIMULATING)
            .toList();
        if (!running.isEmpty()) {
            Simulation first = running.getFirst();
            throw new IllegalStateException(
                "Cannot delete strategy: " + running.size() + " simulation(s) are still running (e.g. "
                + first.getSimulationId().value() + ", status " + first.getStatus() + "). "
                + "Pause them, force-end them, or wait for them to finish — deleting now would "
                + "cascade the running simulation away underneath its executor.");
        }
    }

    private void releaseCapitalForSimulations(DeleteStrategyCommand command) {
        List<Simulation> simulations = simulationRepository.findByStrategyId(command.strategyId());
        for (Simulation simulation : simulations) {
            if (simulation.isTerminal()) {
                continue;
            }
            if (simulation.getBrokerAccountId() == null || simulation.getTradingLimit() == null) {
                continue;
            }
            try {
                BrokerAccount account = brokerAccountRepository.findById(simulation.getBrokerAccountId())
                    .orElse(null);
                if (account == null) {
                    continue;
                }
                account.release(simulation.getTradingLimit());
                brokerAccountRepository.save(account);
                LOG.info("Released {} from broker account {} for simulation {} (strategy delete)",
                    simulation.getTradingLimit(), simulation.getBrokerAccountId().value(),
                    simulation.getSimulationId().value());
            } catch (Exception e) {
                LOG.error("Failed to release capital for simulation {} from broker account {}",
                    simulation.getSimulationId().value(), simulation.getBrokerAccountId().value(), e);
            }
        }
    }
}
