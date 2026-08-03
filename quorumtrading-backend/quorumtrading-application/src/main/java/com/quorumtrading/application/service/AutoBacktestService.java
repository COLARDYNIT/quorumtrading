package com.quorumtrading.application.service;

import com.quorumtrading.application.config.AutoBacktestConfig;
import com.quorumtrading.application.event.ResearchExecutionCompleted;
import com.quorumtrading.application.event.listener.ResearchExecutionEventListener;
import com.quorumtrading.application.port.out.BackfillJobRepository;
import com.quorumtrading.application.port.out.StrategyRepository;
import com.quorumtrading.application.usecase.AnalyzeDataAvailabilityUseCase;
import com.quorumtrading.application.usecase.CreateSimulationUseCase;
import com.quorumtrading.application.usecase.RequestBackfillUseCase;
import com.quorumtrading.application.usecase.StartSimulationUseCase;
import com.quorumtrading.application.usecase.command.AnalyzeDataAvailabilityCommand;
import com.quorumtrading.application.usecase.command.CreateSimulationCommand;
import com.quorumtrading.application.usecase.command.RequestBackfillCommand;
import com.quorumtrading.application.usecase.command.StartSimulationCommand;
import com.quorumtrading.application.usecase.result.RequestBackfillResult;
import com.quorumtrading.model.entity.Strategy;
import com.quorumtrading.model.enums.BackfillStatus;
import com.quorumtrading.model.enums.EventType;
import com.quorumtrading.model.marketdata.BackfillJob;
import com.quorumtrading.model.marketdata.DataAvailability;
import com.quorumtrading.model.orchestration.StrategyConfig;
import com.quorumtrading.model.orchestration.StrategyConfigParser;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.SimulationId;
import com.quorumtrading.model.valueobject.StrategyId;
import com.quorumtrading.model.valueobject.Symbol;
import com.quorumtrading.model.valueobject.TimeRange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Service that automatically creates and executes backtests when research execution completes.
 *
 * <p><b>DDD Classification:</b> Application Service</p>
 * Coordinates use cases and domain logic without containing business rules itself.
 *
 * <p><b>Hexagonal Architecture:</b> Pure application service - no framework dependencies</p>
 * Uses ports (CreateSimulationUseCase, StartSimulationUseCase, StrategyRepository) for dependencies.
 * Adapter layer handles CDI event observation.
 *
 * <p><b>Responsibilities:</b></p>
 * <ul>
 *   <li>Handle ResearchExecutionCompleted events (called by adapter)</li>
 *   <li>Check if auto-backtesting is enabled via event.shouldTriggerBacktest()</li>
 *   <li>Retrieve strategy and parse configuration to extract tickers</li>
 *   <li>Check data availability for the backtest period (if enabled)</li>
 *   <li>Request backfill for missing data (if enabled)</li>
 *   <li>Wait for backfill completion (if enabled)</li>
 *   <li>Create backtest simulation for configured period (e.g., past week)</li>
 *   <li>Start simulation execution</li>
 * </ul>
 *
 * <p><b>Data Availability Integration:</b></p>
 * <ul>
 *   <li>Checks market data coverage before creating simulations</li>
 *   <li>Automatically requests backfill when data is insufficient</li>
 *   <li>Optionally waits for backfill to complete before starting simulation</li>
 *   <li>Configurable via AutoBacktestConfig (checkDataAvailability, autoBackfill, waitForBackfill)</li>
 * </ul>
 *
 * <p><b>Design Notes:</b></p>
 * <ul>
 *   <li>Creates ONE simulation per research execution (uses first ticker from strategy config)</li>
 *   <li>Backtest period and initial cash are configurable via AutoBacktestConfig</li>
 *   <li>Delegates simulation creation and execution to use cases (maintains separation of concerns)</li>
 *   <li>Fails gracefully with logging if strategy not found, parsing fails, or data unavailable</li>
 * </ul>
 */
public class AutoBacktestService implements ResearchExecutionEventListener {

    private static final Logger logger = LoggerFactory.getLogger(AutoBacktestService.class);
    private static final int BACKFILL_POLL_INTERVAL_MS = 1000; // 1 second

    private final CreateSimulationUseCase createSimulationUseCase;
    private final StartSimulationUseCase startSimulationUseCase;
    private final StrategyRepository strategyRepository;
    private final AutoBacktestConfig autoBacktestConfig;
    private final StrategyConfigParser strategyConfigParser;
    private final AnalyzeDataAvailabilityUseCase analyzeDataAvailabilityUseCase;
    private final RequestBackfillUseCase requestBackfillUseCase;
    private final BackfillJobRepository backfillJobRepository;

    public AutoBacktestService(
        CreateSimulationUseCase createSimulationUseCase,
        StartSimulationUseCase startSimulationUseCase,
        StrategyRepository strategyRepository,
        AutoBacktestConfig autoBacktestConfig,
        AnalyzeDataAvailabilityUseCase analyzeDataAvailabilityUseCase,
        RequestBackfillUseCase requestBackfillUseCase,
        BackfillJobRepository backfillJobRepository
    ) {
        this.createSimulationUseCase = createSimulationUseCase;
        this.startSimulationUseCase = startSimulationUseCase;
        this.strategyRepository = strategyRepository;
        this.autoBacktestConfig = autoBacktestConfig;
        this.strategyConfigParser = new StrategyConfigParser();
        this.analyzeDataAvailabilityUseCase = analyzeDataAvailabilityUseCase;
        this.requestBackfillUseCase = requestBackfillUseCase;
        this.backfillJobRepository = backfillJobRepository;
    }

    /**
     * Handles ResearchExecutionCompleted events by creating and starting auto-backtests.
     * Only triggers if event.shouldTriggerBacktest() returns true (auto-enabled + strategy created).
     *
     * <p><b>Event-Driven Architecture:</b></p>
     * Pure application service - adapter layer observes CDI events and delegates to this method.
     *
     * <p><b>Data Availability Integration:</b></p>
     * Before creating the simulation, this method:
     * <ol>
     *   <li>Analyzes data availability for the backtest period (if enabled)</li>
     *   <li>Requests backfill if coverage is below threshold (if autoBackfill enabled)</li>
     *   <li>Waits for backfill completion (if waitForBackfill enabled)</li>
     *   <li>Proceeds with simulation creation only if data is sufficient</li>
     * </ol>
     *
     * @param event The research execution completed event
     */
    @Override
    public void onExecutionCompleted(ResearchExecutionCompleted event) {
        if (!event.shouldTriggerBacktest()) {
            logger.debug("Auto-backtest not triggered for research execution: {} (autoBacktestEnabled={}, strategyCreated={})",
                event.executionId(), event.autoBacktestEnabled(), event.createdStrategyId() != null);
            return;
        }

        logger.info("Auto-backtest triggered for research execution: {} (strategy: {})",
            event.executionId(), event.createdStrategyId());

        try {
            // Retrieve strategy from repository
            StrategyId strategyId = new StrategyId(event.createdStrategyId());
            Strategy strategy = strategyRepository.findById(strategyId)
                .orElseThrow(() -> new IllegalStateException(
                    "Strategy not found for auto-backtest: " + strategyId.value()));

            // Parse strategy configuration to extract tickers
            StrategyConfig config = strategyConfigParser.parse(strategy.getContent());

            // Collect all tickers (stock + crypto)
            Set<Symbol> allTickers = new HashSet<>();
            allTickers.addAll(config.getStockTickers());
            allTickers.addAll(config.getCryptoTickers());

            if (allTickers.isEmpty()) {
                logger.warn("No tickers found in strategy configuration for auto-backtest: {}", strategyId.value());
                return;
            }

            logger.info("Creating auto-backtest simulation for strategy with {} tickers", allTickers.size());

            // Calculate date range: now - configured weeks
            Instant endDate = Instant.now();
            Instant startDate = endDate.minus(autoBacktestConfig.periodWeeks() * 7L, ChronoUnit.DAYS);
            // IMPORTANT: Must use TimeRange.of(LocalDate, LocalDate) factory method, NOT the
            // direct Instant constructor. The factory method correctly handles end-date inclusivity.
            LocalDate startLocalDate = startDate.atZone(ZoneOffset.UTC).toLocalDate();
            LocalDate endLocalDate = endDate.atZone(ZoneOffset.UTC).toLocalDate();
            TimeRange timeRange = TimeRange.of(startLocalDate, endLocalDate);

            // Check data availability before creating simulation (if enabled)
            if (autoBacktestConfig.checkDataAvailability()) {
                boolean dataReady = ensureDataAvailability(allTickers, timeRange, strategyId);
                if (!dataReady) {
                    logger.warn("Auto-backtest cancelled for strategy {} - insufficient data availability", strategyId.value());
                    return;
                }
            }

            // Create simulation
            CreateSimulationCommand createCommand = new CreateSimulationCommand(
                generateSimulationName(strategy),
                strategyId,
                startDate,
                endDate,
                new Money(autoBacktestConfig.initialCash(), Money.Currency.USD)
            );

            SimulationId simulationId = createSimulationUseCase.execute(createCommand);
            logger.info("Auto-backtest simulation created: {} for strategy: {}", simulationId.value(), strategyId.value());

            // Start simulation execution
            StartSimulationCommand startCommand = StartSimulationCommand.system(simulationId);
            startSimulationUseCase.execute(startCommand);
            logger.info("Auto-backtest simulation started: {}", simulationId.value());

        } catch (Exception e) {
            logger.error("Failed to create auto-backtest for research execution: {} (strategy: {})",
                event.executionId(), event.createdStrategyId(), e);
            // Don't rethrow - auto-backtest failure should not break research execution workflow
        }
    }

    /**
     * Ensures data availability for the specified symbols and time range.
     * Analyzes current coverage, requests backfill if needed, and optionally waits for completion.
     *
     * @param symbols The symbols to check data for
     * @param timeRange The time range for the backtest
     * @param strategyId The strategy ID (for logging)
     * @return true if data is available or backfill completed successfully, false otherwise
     */
    private boolean ensureDataAvailability(Set<Symbol> symbols, TimeRange timeRange, StrategyId strategyId) {
        // Determine which data types are needed (for now, we check all types that are available)
        Set<EventType> requiredDataTypes = Set.of(EventType.PRICE, EventType.NEWS, EventType.SOCIAL);

        // Analyze current data availability
        logger.info("Analyzing data availability for strategy {} - symbols: {}, range: {} to {}",
            strategyId.value(), symbols, timeRange.start(), timeRange.end());

        AnalyzeDataAvailabilityCommand analysisCommand = new AnalyzeDataAvailabilityCommand(
            timeRange,
            symbols,
            requiredDataTypes
        );

        DataAvailability availability = analyzeDataAvailabilityUseCase.execute(analysisCommand);
        BigDecimal coverage = availability.getOverallCoverage();

        logger.info("Current data coverage for strategy {}: {}%", strategyId.value(), coverage);

        // Check if coverage meets threshold
        if (coverage.compareTo(autoBacktestConfig.coverageThreshold()) >= 0) {
            logger.info("Data coverage sufficient for strategy {} - proceeding with auto-backtest", strategyId.value());
            return true;
        }

        // Coverage is insufficient
        logger.warn("Data coverage insufficient for strategy {} - {}% (threshold: {}%)",
            strategyId.value(), coverage, autoBacktestConfig.coverageThreshold());

        // Request backfill if enabled
        if (!autoBacktestConfig.autoBackfill()) {
            logger.info("Auto-backfill disabled - skipping auto-backtest for strategy {}", strategyId.value());
            return false;
        }

        // Request backfill
        logger.info("Requesting backfill for strategy {} - symbols: {}, range: {} to {}",
            strategyId.value(), symbols, timeRange.start(), timeRange.end());

        RequestBackfillCommand backfillCommand = new RequestBackfillCommand(
            symbols,
            requiredDataTypes,
            timeRange,
            false // Don't force backfill if data exists
        );

        RequestBackfillResult backfillResult = requestBackfillUseCase.execute(backfillCommand);

        if (!backfillResult.backfillTriggered()) {
            logger.warn("Backfill not started for strategy {} - data may already be complete", strategyId.value());
            return true; // Data is already complete
        }

        logger.info("Backfill request created for strategy {}: job ID {}", strategyId.value(), backfillResult.jobId());

        // If waiting is not enabled, return false (simulation will not proceed)
        if (!autoBacktestConfig.waitForBackfill()) {
            logger.info("Wait for backfill disabled - auto-backtest will not proceed for strategy {}", strategyId.value());
            return false;
        }

        // Wait for backfill completion
        return waitForBackfillCompletion(backfillResult.jobId(), strategyId);
    }

    /**
     * Waits for a backfill job to complete.
     * Polls the backfill job status until it completes, fails, or timeout is reached.
     *
     * @param jobId The backfill job ID
     * @param strategyId The strategy ID (for logging)
     * @return true if backfill completed successfully, false if failed or timed out
     */
    private boolean waitForBackfillCompletion(UUID jobId, StrategyId strategyId) {
        logger.info("Waiting for backfill completion for strategy {} - job ID: {}, max wait: {}s",
            strategyId.value(), jobId, autoBacktestConfig.maxWaitSeconds());

        Instant startTime = Instant.now();
        Duration maxWait = Duration.ofSeconds(autoBacktestConfig.maxWaitSeconds());

        while (true) {
            // Check timeout
            Duration elapsed = Duration.between(startTime, Instant.now());
            if (elapsed.compareTo(maxWait) > 0) {
                logger.warn("Backfill wait timeout for strategy {} - job ID: {}, elapsed: {}s",
                    strategyId.value(), jobId, elapsed.getSeconds());
                return false;
            }

            // Check backfill status
            BackfillJob backfillRequest = backfillJobRepository.findById(jobId).orElse(null);
            if (backfillRequest == null) {
                logger.error("Backfill job not found for strategy {} - job ID: {}", strategyId.value(), jobId);
                return false;
            }

            BackfillStatus status = backfillRequest.getStatus();
            logger.debug("Backfill status for strategy {} - job ID: {}, status: {}, progress: {}%",
                strategyId.value(), jobId, status, backfillRequest.getProgressPercentage());

            switch (status) {
                case COMPLETED:
                    logger.info("Backfill completed successfully for strategy {} - job ID: {}", strategyId.value(), jobId);
                    return true;

                case FAILED:
                    logger.error("Backfill failed for strategy {} - job ID: {}, error: {}",
                        strategyId.value(), jobId, backfillRequest.getErrorMessage());
                    return false;

                case PENDING:
                case IN_PROGRESS:
                    // Still processing, wait and poll again
                    try {
                        Thread.sleep(BACKFILL_POLL_INTERVAL_MS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        logger.error("Backfill wait interrupted for strategy {} - job ID: {}", strategyId.value(), jobId);
                        return false;
                    }
                    break;

                default:
                    logger.error("Unknown backfill status for strategy {} - job ID: {}, status: {}",
                        strategyId.value(), jobId, status);
                    return false;
            }
        }
    }

    /**
     * Generates a descriptive simulation name for auto-backtests.
     * Format: "Auto-Backtest: {strategy-name} ({period} weeks)"
     *
     * @param strategy The strategy entity
     * @return Generated simulation name
     */
    private String generateSimulationName(Strategy strategy) {
        return String.format("Auto-Backtest: %s (%d weeks)",
            strategy.getName(),
            autoBacktestConfig.periodWeeks()
        );
    }
}
