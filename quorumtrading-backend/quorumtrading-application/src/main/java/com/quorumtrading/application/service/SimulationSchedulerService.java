package com.quorumtrading.application.service;

import com.quorumtrading.application.port.out.ScheduledSimulationRepository;
import com.quorumtrading.application.port.out.StrategyRepository;
import com.quorumtrading.application.usecase.CreateSimulationUseCase;
import com.quorumtrading.application.usecase.TriggerSimulationUseCase;
import com.quorumtrading.application.usecase.command.CreateSimulationCommand;
import com.quorumtrading.model.entity.ScheduledSimulation;
import com.quorumtrading.model.entity.Strategy;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.SimulationId;
import com.quorumtrading.model.valueobject.StrategyId;
import com.quorumtrading.model.valueobject.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Service that manages scheduled simulation execution.
 *
 * <p><b>DDD Classification:</b> Application Service</p>
 * Contains business logic for:
 * <ul>
 *   <li>Processing due schedules</li>
 *   <li>Triggering manual simulations</li>
 * </ul>
 *
 * <p><b>Hexagonal Architecture:</b></p>
 * Pure application service - no framework dependencies.
 * Bootstrap layer scheduler adapter calls this service periodically.
 */
public class SimulationSchedulerService implements TriggerSimulationUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(SimulationSchedulerService.class);
    private static final BigDecimal DEFAULT_INITIAL_CASH = new BigDecimal("100000");
    private static final int DEFAULT_BACKTEST_WEEKS = 1;

    private final ScheduledSimulationRepository scheduledSimulationRepository;
    private final StrategyRepository strategyRepository;
    private final CreateSimulationUseCase createSimulationUseCase;
    private final AuthorizationService authorizationService;

    public SimulationSchedulerService(
            ScheduledSimulationRepository scheduledSimulationRepository,
            StrategyRepository strategyRepository,
            CreateSimulationUseCase createSimulationUseCase,
            AuthorizationService authorizationService) {
        this.scheduledSimulationRepository = scheduledSimulationRepository;
        this.strategyRepository = strategyRepository;
        this.createSimulationUseCase = createSimulationUseCase;
        this.authorizationService = authorizationService;
    }

    /**
     * Process all due scheduled simulations.
     * Called periodically by the scheduler adapter.
     */
    @Override
    public void processScheduledSimulations() {
        Instant now = Instant.now();
        List<ScheduledSimulation> dueSchedules = scheduledSimulationRepository.findDueSchedules(now);

        if (dueSchedules.isEmpty()) {
            LOG.debug("No scheduled simulations due");
            return;
        }

        LOG.info("Processing {} due scheduled simulations", dueSchedules.size());

        for (ScheduledSimulation schedule : dueSchedules) {
            try {
                processSchedule(schedule, now);
            } catch (Exception e) {
                LOG.error("Failed to process schedule for strategy: {}", schedule.getStrategyId(), e);
            }
        }
    }

    /**
     * Trigger a manual simulation for a strategy.
     * Creates a backtest simulation with default parameters.
     *
     * @param strategyId       The strategy ID to create a simulation for
     * @param requestingUserId The user requesting the trigger
     * @return The created simulation ID
     */
    @Override
    public SimulationId triggerManually(StrategyId strategyId, UserId requestingUserId) {
        LOG.info("Manually triggering simulation for strategy: {}", strategyId);

        Strategy strategy = strategyRepository.findById(strategyId)
            .orElseThrow(() -> new IllegalArgumentException("Strategy not found: " + strategyId));

        // Authorization check — only for manual triggers, not scheduled processing
        authorizationService.requireEdit(requestingUserId, strategy);

        return createSimulationForStrategy(strategy);
    }

    private void processSchedule(ScheduledSimulation schedule, Instant now) {
        LOG.info("Processing schedule for strategy: {}", schedule.getStrategyId());

        Strategy strategy = strategyRepository.findById(schedule.getStrategyId())
            .orElseThrow(() -> new IllegalArgumentException("Strategy not found: " + schedule.getStrategyId()));

        // Phase 4: Skip disabled strategies
        if (!strategy.isEnabled()) {
            LOG.info("Skipping scheduled simulation for disabled strategy: {}", schedule.getStrategyId());
            return;
        }

        createSimulationForStrategy(strategy);

        // Update schedule
        schedule.recordRun(now);
        scheduledSimulationRepository.save(schedule);

        // Update strategy's last tested timestamp
        strategy.recordTestRun(now);
        strategyRepository.save(strategy);

        LOG.info("Scheduled simulation created for strategy: {}, next run: {}",
            schedule.getStrategyId(), schedule.getNextRunAt());
    }

    private SimulationId createSimulationForStrategy(Strategy strategy) {
        Instant endDate = Instant.now();
        Instant startDate = endDate.minus(DEFAULT_BACKTEST_WEEKS * 7, ChronoUnit.DAYS);

        String simulationName = strategy.getName() + " - Scheduled Backtest";

        // Attribute the scheduled backtest to the strategy's owner rather than to nobody:
        // this runs for their strategy, and a null user is now denied by the authorization
        // layer instead of quietly skipping the check.
        CreateSimulationCommand command = new CreateSimulationCommand(
            simulationName,
            strategy.getId(),
            startDate,
            endDate,
            new Money(DEFAULT_INITIAL_CASH, Money.Currency.USD),
            null,
            null,
            null,
            strategy.getOwnerId()
        );

        return createSimulationUseCase.execute(command);
    }
}
