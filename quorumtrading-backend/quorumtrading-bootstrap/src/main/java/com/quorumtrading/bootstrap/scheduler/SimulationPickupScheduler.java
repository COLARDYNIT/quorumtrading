package com.quorumtrading.bootstrap.scheduler;

import com.quorumtrading.application.orchestration.SimulationExecutor;
import com.quorumtrading.application.port.out.AsyncExecutionPort;
import com.quorumtrading.application.port.out.SimulationRepository;
import com.quorumtrading.application.port.out.StrategyRepository;
import com.quorumtrading.model.entity.Simulation;
import com.quorumtrading.model.entity.Strategy;
import com.quorumtrading.model.enums.SimulationStatus;
import io.quarkus.scheduler.Scheduled;
import io.quarkus.scheduler.Scheduled.ConcurrentExecution;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.Optional;

/**
 * Periodic scheduler that picks up SIMULATING simulations that are not currently locked.
 * Enables horizontal scaling: when instance A's executor is full, instance B picks up work.
 *
 * SimulationExecutor.startSimulation() already uses distributed locking via SimulationLockPort,
 * so this scheduler simply finds eligible simulations and submits them for execution.
 * Duplicate execution is prevented by the lock.
 */
@ApplicationScoped
public class SimulationPickupScheduler {

    private static final Logger LOG = Logger.getLogger(SimulationPickupScheduler.class);

    @Inject
    SimulationRepository simulationRepository;

    @Inject
    StrategyRepository strategyRepository;

    @Inject
    SimulationExecutor simulationExecutor;

    @Inject
    AsyncExecutionPort asyncExecutionPort;

    @ConfigProperty(name = "quorumtrading.scheduler.simulation-pickup.enabled", defaultValue = "true")
    boolean enabled;

    @Scheduled(every = "${quorumtrading.scheduler.simulation-pickup.interval:30s}", concurrentExecution = ConcurrentExecution.SKIP)
    public void pickupSimulations() {
        if (!enabled) {
            return;
        }

        try {
            // Runnable = SIMULATING and neither user-paused nor system-paused, asked of the
            // database rather than filtered in Java. The system-paused half is the part that was
            // missing: the circuit breaker sets pausedForExternalReason while leaving paused
            // false, so every data-gap / LLM-failure / stall-paused run was re-offered here
            // twice a minute forever — a strategy parse, a resume-point query and three writes
            // per run per tick, for a run that breaks out at its first check. A pause is only
            // worth anything to the fleet if the schedulers actually skip it.
            // The Java filter is belt-and-braces on top of the SQL predicate, not redundancy:
            // it keeps the guarantee locally checkable, and it survives someone later swapping
            // the query for a broader one.
            List<Simulation> eligible = simulationRepository.findRunnable(SimulationStatus.SIMULATING)
                .stream()
                .filter(sim -> !sim.isPaused() && !sim.isPausedForExternalReason())
                .toList();

            if (eligible.isEmpty()) {
                return;
            }

            LOG.infof("Simulation pickup: found %d eligible SIMULATING simulations", eligible.size());

            for (Simulation simulation : eligible) {
                try {
                    Strategy strategy = strategyRepository.findById(simulation.getStrategyId()).orElse(null);
                    if (strategy == null) {
                        LOG.warnf("Strategy not found for simulation %s, skipping pickup",
                            simulation.getSimulationId().value().toString().substring(0, 8));
                        continue;
                    }

                    // Submit asynchronously — SimulationExecutor.startSimulation() will attempt
                    // to acquire the distributed lock. If another instance already holds it,
                    // the call returns false immediately (idempotent).
                    String strategyContent = strategy.getContent();
                    asyncExecutionPort.submitAsync(() -> {
                        try {
                            // Save only when this call actually ran the simulation: on a refused
                            // or lost lease the entity in hand is a stale copy of state some
                            // other executor owns, and saving it clobbered their updates.
                            if (simulationExecutor.startSimulation(simulation, strategyContent)) {
                                simulationRepository.save(simulation);
                            }
                        } catch (Exception e) {
                            LOG.errorf(e, "Failed to execute picked-up simulation %s",
                                simulation.getSimulationId().value().toString().substring(0, 8));
                        }
                    });

                } catch (Exception e) {
                    LOG.errorf(e, "Failed to pick up simulation %s",
                        simulation.getSimulationId().value().toString().substring(0, 8));
                }
            }

        } catch (Exception e) {
            LOG.errorf(e, "Simulation pickup scheduler failed");
        }
    }
}
