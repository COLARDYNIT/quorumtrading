package com.quorumtrading.application.service;

import com.quorumtrading.application.port.out.BrokerPort;
import com.quorumtrading.application.port.out.MarketEventRepository;
import com.quorumtrading.application.port.out.PortfolioRepository;
import com.quorumtrading.application.port.out.SimulationRepository;
import com.quorumtrading.application.usecase.EvaluatePortfolioValueUseCase;
import com.quorumtrading.application.usecase.PortfolioEvaluationResult;
import com.quorumtrading.application.usecase.SimulationEvaluationResult;
import com.quorumtrading.model.entity.Portfolio;
import com.quorumtrading.model.entity.PortfolioSnapshot;
import com.quorumtrading.model.entity.Position;
import com.quorumtrading.model.entity.Simulation;
import com.quorumtrading.model.enums.EventType;
import com.quorumtrading.model.enums.SimulationStatus;
import com.quorumtrading.model.marketdata.MarketEvent;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.SimulationId;
import com.quorumtrading.model.valueobject.Symbol;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service implementation for evaluating portfolio values based on current market prices.
 * Updates portfolio snapshots when prices change.
 */
public class EvaluatePortfolioValueService implements EvaluatePortfolioValueUseCase {

    private static final Logger LOG = Logger.getLogger(EvaluatePortfolioValueService.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final SimulationRepository simulationRepository;
    private final PortfolioRepository portfolioRepository;
    private final MarketEventRepository marketEventRepository;
    private final BrokerPort brokerPort;

    public EvaluatePortfolioValueService(
            SimulationRepository simulationRepository,
            PortfolioRepository portfolioRepository,
            MarketEventRepository marketEventRepository,
            BrokerPort brokerPort) {
        this.simulationRepository = simulationRepository;
        this.portfolioRepository = portfolioRepository;
        this.marketEventRepository = marketEventRepository;
        this.brokerPort = brokerPort;
    }

    @Override
    public PortfolioEvaluationResult evaluateAllActive() {
        int processed = 0;
        int skipped = 0;
        int failed = 0;
        List<String> errors = new ArrayList<>();

        try {
            // Query all simulations and filter for SIMULATING status (historical catch-up) and not paused
            // LIVE simulations get portfolio updates from consensus execution, not from this scheduler
            // This prevents race conditions where the evaluator overwrites trade snapshots with stale data
            // EXCLUDE backtests (where startDate < createdAt) - backtests should not be evaluated by scheduler
            // Runnable rows only — the pause filter now covers system pauses too, so a run the
            // circuit breaker or the stall detector paused stops being evaluated. Asking the
            // database also replaces a findAll() that materialised every simulation ever created
            // (including every COMPLETED one) once a minute to keep a handful.
            // The pause filter is belt-and-braces on top of the SQL predicate, not redundancy:
            // it keeps the guarantee locally checkable, and it survives someone later swapping
            // the query for a broader one.
            List<Simulation> activeSimulations = simulationRepository
                    .findRunnable(SimulationStatus.SIMULATING).stream()
                    .filter(sim -> !sim.isPaused() && !sim.isPausedForExternalReason())
                    .filter(sim -> !sim.isBacktest())  // Exclude backtests from scheduler
                    .toList();

            if (activeSimulations.isEmpty()) {
                LOG.info("No active simulations found (SIMULATING status and not paused)");
                return new PortfolioEvaluationResult(0, 0, 0, List.of());
            }

            LOG.infof("Found %d SIMULATING simulation(s) to evaluate", activeSimulations.size());

            // Process each active simulation
            for (Simulation simulation : activeSimulations) {
                SimulationEvaluationResult result = evaluateSimulation(simulation.getSimulationId());

                if (result.success()) {
                    processed++;
                } else if (result.skipped()) {
                    skipped++;
                    if (result.errorMessage() != null) {
                        errors.add(result.errorMessage());
                    }
                } else {
                    failed++;
                    if (result.errorMessage() != null) {
                        errors.add(result.errorMessage());
                    }
                }
            }

            LOG.infof("Portfolio evaluation complete: processed=%d, skipped=%d, failed=%d",
                    processed, skipped, failed);
            return new PortfolioEvaluationResult(processed, skipped, failed, errors);

        } catch (Exception e) {
            LOG.errorf(e, "Failed to evaluate portfolios for all active simulations");
            errors.add("Global error: " + e.getMessage());
            return new PortfolioEvaluationResult(processed, skipped, failed, errors);
        }
    }

    @Override
    public SimulationEvaluationResult evaluateSimulation(SimulationId simulationId) {
        try {
            LOG.debugf("Evaluating portfolio for simulation ID: %s", simulationId.value());

            // Get the simulation
            Optional<Simulation> simulationOpt = simulationRepository.findById(simulationId);
            if (simulationOpt.isEmpty()) {
                String msg = String.format("Simulation not found: %s", simulationId.value());
                LOG.warnf(msg);
                return SimulationEvaluationResult.skipped(simulationId, msg);
            }
            Simulation simulation = simulationOpt.get();

            // Guard: Do not create snapshots after the simulation's end date
            // This prevents stale/incorrect valuations from polluting chart data
            if (simulation.getEndDate() != null && Instant.now().isAfter(simulation.getEndDate())) {
                String msg = String.format("Simulation %s has ended (end date: %s), skipping portfolio evaluation",
                        simulationId.value().toString().substring(0, 8), simulation.getEndDate());
                LOG.debugf(msg);
                return SimulationEvaluationResult.skipped(simulationId, msg);
            }

            // Verify simulation is in SIMULATING status (historical catch-up) and not paused
            // LIVE simulations are skipped to prevent overwriting consensus execution snapshots
            if (simulation.getStatus() != SimulationStatus.SIMULATING || simulation.isPaused()) {
                String msg = String.format("Simulation %s is not in SIMULATING status or is paused (status: %s, paused: %s)",
                        simulationId.value(), simulation.getStatus(), simulation.isPaused());
                LOG.debugf(msg);
                return SimulationEvaluationResult.skipped(simulationId, msg);
            }

            // Get latest portfolio snapshot
            Optional<PortfolioSnapshot> snapshotOpt = portfolioRepository.findLatestBySimulationId(simulationId);
            if (snapshotOpt.isEmpty()) {
                String msg = String.format("No portfolio snapshot found for simulation %s", simulationId.value());
                LOG.warnf(msg);
                return SimulationEvaluationResult.skipped(simulationId, msg);
            }
            PortfolioSnapshot latestSnapshot = snapshotOpt.get();

            // Get positions - already deserialized by PortfolioMapper
            Map<Symbol, Position> positions = latestSnapshot.getPositions();

            // If no positions (cash-only), create snapshot with current cash value
            if (positions.isEmpty()) {
                LOG.debugf("Simulation %s has no positions (cash-only), creating snapshot", simulationId.value());
                Instant now = Instant.now();
                Money cashOnlyValue = latestSnapshot.getCashAvailable();

                PortfolioSnapshot newSnapshot = new PortfolioSnapshot(
                        simulationId,
                        now,
                        Map.of(), // Empty positions
                        cashOnlyValue,
                        cashOnlyValue
                );

                portfolioRepository.save(newSnapshot);
                LOG.infof("Simulation %s: cash-only snapshot created with value %s",
                          simulationId.value(), cashOnlyValue);
                return SimulationEvaluationResult.success(simulationId);
            }

            // Use broker to get portfolio value at current time
            // Broker now returns NULL if market prices are missing (no more fallbacks)
            Instant now = Instant.now();
            Money brokerReportedValue = brokerPort.getPortfolioValue(simulationId, now);

            // CRITICAL: If broker returns null, it means market data is incomplete
            // SKIP snapshot creation to maintain data integrity
            if (brokerReportedValue == null) {
                String msg = String.format("Cannot evaluate portfolio: broker returned null (missing market prices for one or more positions)");
                LOG.warnf("Simulation %s: %s. Skipping evaluation to maintain data integrity.",
                         simulationId.value().toString().substring(0, 8), msg);
                return SimulationEvaluationResult.skipped(simulationId, msg);
            }

            // CRITICAL: If broker returns $0 or negative, it indicates corrupted data
            // (API failure, missing prices, market closed, etc.) - SKIP to prevent data corruption
            if (!brokerReportedValue.isPositive()) {
                String msg = String.format("Cannot evaluate portfolio: broker returned invalid value %s (likely API failure or missing market data)",
                    brokerReportedValue.amount());
                LOG.errorf("Simulation %s: %s. Skipping evaluation to prevent data corruption.",
                         simulationId.value().toString().substring(0, 8), msg);
                return SimulationEvaluationResult.skipped(simulationId, msg);
            }

            LOG.debugf("Broker-reported portfolio value: %s (cash: %s) for simulation %s",
                brokerReportedValue, latestSnapshot.getCashAvailable(),
                simulationId.value().toString().substring(0, 8)
            );

            // Check if value has changed
            if (brokerReportedValue.equals(latestSnapshot.getTotalValue())) {
                LOG.debugf("Simulation %s: portfolio value unchanged (%s), skipping snapshot",
                        simulationId.value(), brokerReportedValue);
                return SimulationEvaluationResult.skipped(simulationId, "Value unchanged");
            }

            // Reprice the positions as of NOW before persisting them. Copying the previous
            // snapshot's positions verbatim would carry their stale currentPrice into a row whose
            // totalValue is freshly broker-reported, so the row would not reconcile as
            // cash + sum(qty x currentPrice).
            Map<Symbol, Position> repricedPositions =
                    repricePositions(positions, now);

            // Create and save new snapshot with updated total value
            // Positions are stored as domain objects - PortfolioMapper handles JSON serialization
            PortfolioSnapshot newSnapshot = new PortfolioSnapshot(
                    simulationId,
                    now,
                    repricedPositions,
                    brokerReportedValue,
                    latestSnapshot.getCashAvailable()
            );

            portfolioRepository.save(newSnapshot);

            LOG.infof("Simulation %s: portfolio value updated from %s to %s",
                    simulationId.value(), latestSnapshot.getTotalValue(), brokerReportedValue);

            return SimulationEvaluationResult.success(simulationId);

        } catch (Exception e) {
            String msg = String.format("Failed to evaluate simulation %s: %s",
                    simulationId.value(), e.getMessage());
            LOG.errorf(e, msg);
            return SimulationEvaluationResult.failed(simulationId, msg);
        }
    }

    /**
     * Re-stamp each position with the latest market price as of {@code asOf}.
     *
     * <p>A position with no price in the look-back window keeps a null currentPrice rather than
     * carrying the previous snapshot's stale one — an absent price is honest, a stale one is
     * indistinguishable from a current one once persisted.</p>
     */
    private Map<Symbol, Position> repricePositions(Map<Symbol, Position> positions, Instant asOf) {
        Map<Symbol, Money> prices =
                LatestMarketPrices.resolve(marketEventRepository, positions.keySet(), asOf);

        Map<Symbol, Position> repriced = new HashMap<>();
        for (Map.Entry<Symbol, Position> entry : positions.entrySet()) {
            Position position = entry.getValue();
            repriced.put(entry.getKey(), new Position(
                    position.symbol(),
                    position.quantity(),
                    position.averagePrice(),
                    prices.get(entry.getKey())
            ));
        }
        return repriced;
    }

}
