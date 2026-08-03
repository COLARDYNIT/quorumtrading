package com.quorumtrading.application.service;

import com.quorumtrading.application.orchestration.SimulationExecutor;
import com.quorumtrading.application.port.out.AsyncExecutionPort;
import com.quorumtrading.application.port.out.PortfolioRepository;
import com.quorumtrading.application.port.out.SimulationRepository;
import com.quorumtrading.application.port.out.StrategyRepository;
import com.quorumtrading.application.usecase.StartSimulationUseCase;
import com.quorumtrading.application.usecase.command.StartSimulationCommand;
import com.quorumtrading.model.entity.PortfolioSnapshot;
import com.quorumtrading.model.entity.Simulation;
import com.quorumtrading.model.entity.Strategy;
import com.quorumtrading.model.valueobject.Symbol;
import com.quorumtrading.model.entity.Position;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Service implementation for starting simulation execution.
 * Coordinates between repositories and the simulation executor.
 *
 * <p><b>Zombie Prevention:</b></p>
 * Creates an initial portfolio snapshot immediately after transitioning to LIVE/SIMULATING status,
 * before the executor runs. This ensures a snapshot always exists for started simulations,
 * preventing "zombie" simulations (LIVE status with no data) if the executor fails to start.
 */
public class StartSimulationService implements StartSimulationUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(StartSimulationService.class);

    private final SimulationRepository simulationRepository;
    private final StrategyRepository strategyRepository;
    private final PortfolioRepository portfolioRepository;
    private final SimulationExecutor simulationExecutor;
    private final AuthorizationService authorizationService;
    private final AsyncExecutionPort asyncExecutionPort;

    public StartSimulationService(
            SimulationRepository simulationRepository,
            StrategyRepository strategyRepository,
            PortfolioRepository portfolioRepository,
            SimulationExecutor simulationExecutor,
            AuthorizationService authorizationService,
            AsyncExecutionPort asyncExecutionPort) {
        this.simulationRepository = simulationRepository;
        this.strategyRepository = strategyRepository;
        this.portfolioRepository = portfolioRepository;
        this.simulationExecutor = simulationExecutor;
        this.authorizationService = authorizationService;
        this.asyncExecutionPort = asyncExecutionPort;
    }

    @Override
    public void execute(StartSimulationCommand command) {
        // Find simulation
        Simulation simulation = simulationRepository.findById(command.simulationId())
                .orElseThrow(() -> new java.util.NoSuchElementException(
                        "Simulation not found with ID: " + command.simulationId()));

        // Idempotency check: If simulation is already started, skip execution
        // This prevents race conditions when multiple async event handlers process the same simulation
        if (simulation.getStatus() != com.quorumtrading.model.enums.SimulationStatus.PENDING) {
            if (simulation.getStatus() == com.quorumtrading.model.enums.SimulationStatus.WAITING_FOR_DATA) {
                LOG.info("Simulation {} is waiting for data backfill, cannot start yet",
                    simulation.getSimulationId().value().toString().substring(0, 8));
            }
            return;
        }

        // Find strategy
        Strategy strategy = strategyRepository.findById(simulation.getStrategyId())
                .orElseThrow(() -> new java.util.NoSuchElementException(
                        "Strategy not found with ID: " + simulation.getStrategyId()));

        // Authorization check (skip for system-triggered starts)
        authorizationService.requireEdit(command.requestingUserId(), simulation, strategy);

        // Start the simulation (transitions from PENDING to SIMULATING/LIVE and unpauses)
        simulation.start();

        // ZOMBIE PREVENTION: Create initial portfolio snapshot immediately
        // This ensures a snapshot exists even if SimulationExecutor fails to start.
        // SimulationExecutor will detect the existing snapshot and skip re-initialization.
        // ReachConsensusService can restore broker state from this snapshot if needed.
        createInitialPortfolioSnapshot(simulation);

        // CRITICAL: Save the state BEFORE starting simulation execution
        // Otherwise ReachConsensusService will load old state from DB (PENDING, paused)
        simulationRepository.save(simulation);

        // The PENDING → SIMULATING/LIVE transition and its startedAt stamp go through their own
        // narrow compare-and-set: save() no longer writes lifecycle columns, because an executor
        // holding one entity for hours would otherwise write stale ones back over them.
        simulationRepository.markStarted(
            simulation.getSimulationId(), simulation.getStatus(), simulation.getStartedAt());

        // Explicitly persist unpause state — save() deliberately excludes pause fields
        // (see commit d3166c43) to prevent stale in-memory state from overwriting DB pause state.
        // Without this, benchmark sims created with paused=true stay paused in the DB.
        simulationRepository.updatePauseState(
            simulation.getSimulationId(), false, false, null);

        // Submit simulation execution asynchronously to avoid blocking CDI event threads.
        // The simulation is already in SIMULATING status and saved to DB.
        // SimulationExecutor handles its own state management (locking, completion, error recovery).
        String strategyContent = strategy.getContent();
        LOG.info("Submitting simulation {} for async execution",
            simulation.getSimulationId().value().toString().substring(0, 8));
        asyncExecutionPort.submitAsync(() -> {
            try {
                // Save only when this call actually ran the simulation — on a refused or lost
                // lease the entity here is a stale copy of state another executor owns.
                if (simulationExecutor.startSimulation(simulation, strategyContent)) {
                    simulationRepository.save(simulation);
                }
            } catch (Exception e) {
                LOG.error("Async simulation execution failed for {}: {}",
                    simulation.getSimulationId().value().toString().substring(0, 8),
                    e.getMessage(), e);
            }
        });
    }

    /**
     * Creates the initial portfolio snapshot for a simulation.
     * This is called immediately after start() to ensure a snapshot always exists,
     * preventing zombie simulations if the executor fails to run.
     *
     * @param simulation the simulation being started
     */
    private void createInitialPortfolioSnapshot(Simulation simulation) {
        // Check if snapshot already exists (idempotency - e.g., retry scenario)
        if (portfolioRepository.findLatestBySimulationId(simulation.getSimulationId()).isPresent()) {
            LOG.debug("Initial snapshot already exists for simulation {}, skipping creation",
                simulation.getSimulationId().value().toString().substring(0, 8));
            return;
        }

        PortfolioSnapshot initialSnapshot = new PortfolioSnapshot(
            simulation.getSimulationId(),
            simulation.getStartDate(),
            Map.<Symbol, Position>of(),  // No positions initially
            simulation.getInitialCash(), // Total value = initial cash
            simulation.getInitialCash()  // Cash available = initial cash
        );

        portfolioRepository.save(initialSnapshot);

        LOG.info("Created initial portfolio snapshot for simulation {} with cash {}",
            simulation.getSimulationId().value().toString().substring(0, 8),
            simulation.getInitialCash());
    }
}
