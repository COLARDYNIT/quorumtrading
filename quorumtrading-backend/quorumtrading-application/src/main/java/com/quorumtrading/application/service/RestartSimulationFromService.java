package com.quorumtrading.application.service;

import com.quorumtrading.application.port.out.ConsensusDecisionRepository;
import com.quorumtrading.application.port.out.ConsensusDecisionRepository.AggregatedLLMCost;
import com.quorumtrading.application.port.out.LLMDecisionRepository;
import com.quorumtrading.application.port.out.PortfolioRepository;
import com.quorumtrading.application.port.out.SimulationRepository;
import com.quorumtrading.application.port.out.StrategyRepository;
import com.quorumtrading.model.entity.ConsensusDecision;
import com.quorumtrading.model.entity.PortfolioSnapshot;
import com.quorumtrading.model.entity.Simulation;
import com.quorumtrading.model.entity.Strategy;
import com.quorumtrading.model.enums.SimulationStatus;
import com.quorumtrading.model.valueobject.RestartFromPreview;
import com.quorumtrading.model.valueobject.SimulationId;
import com.quorumtrading.model.valueobject.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Service to restart a simulation from a specific consensus decision.
 * Archives LLM costs from deleted decisions, then deletes consensus decisions,
 * LLM decisions, and portfolio snapshots at or after the cutoff timestamp.
 * Restricted to BACKTEST simulations only (paper/live trading has real broker state).
 */
public class RestartSimulationFromService {

    private static final Logger log = LoggerFactory.getLogger(RestartSimulationFromService.class);

    private final SimulationRepository simulationRepository;
    private final StrategyRepository strategyRepository;
    private final ConsensusDecisionRepository consensusDecisionRepository;
    private final LLMDecisionRepository llmDecisionRepository;
    private final PortfolioRepository portfolioRepository;
    private final AuthorizationService authorizationService;

    public RestartSimulationFromService(
            SimulationRepository simulationRepository,
            StrategyRepository strategyRepository,
            ConsensusDecisionRepository consensusDecisionRepository,
            LLMDecisionRepository llmDecisionRepository,
            PortfolioRepository portfolioRepository,
            AuthorizationService authorizationService) {
        this.simulationRepository = simulationRepository;
        this.strategyRepository = strategyRepository;
        this.consensusDecisionRepository = consensusDecisionRepository;
        this.llmDecisionRepository = llmDecisionRepository;
        this.portfolioRepository = portfolioRepository;
        this.authorizationService = authorizationService;
    }

    /**
     * Preview what will happen when restarting from a given consensus decision.
     */
    public RestartFromPreview preview(UUID simulationId, long consensusDecisionId, UserId requestingUserId) {
        Simulation simulation = loadAndValidate(simulationId, requestingUserId, Access.READ);
        Instant cutoff = resolveCutoff(simulation, consensusDecisionId);

        SimulationId simId = simulation.getSimulationId();

        long consensusCount = consensusDecisionRepository.countBySimulationIdAfter(simulationId, cutoff);
        long llmCount = llmDecisionRepository.countBySimulationIdAfter(simulationId, cutoff);
        long snapshotCount = portfolioRepository.countBySimulationIdAfter(simId, cutoff);

        AggregatedLLMCost costToArchive = consensusDecisionRepository.sumCostBySimulationIdAfter(simulationId, cutoff);

        // Get the last portfolio snapshot BEFORE the cutoff for preview
        BigDecimal cashAtRestart = BigDecimal.ZERO;
        BigDecimal valueAtRestart = BigDecimal.ZERO;
        var snapshots = portfolioRepository.findBySimulationId(simId, null, cutoff.minusMillis(1), 1);
        if (!snapshots.isEmpty()) {
            PortfolioSnapshot lastBefore = snapshots.get(0);
            cashAtRestart = lastBefore.getCashAvailable().amount();
            valueAtRestart = lastBefore.getTotalValue().amount();
        }

        return new RestartFromPreview(
            consensusCount,
            llmCount,
            snapshotCount,
            costToArchive.rawCost(),
            costToArchive.totalTokens(),
            cashAtRestart,
            valueAtRestart,
            cutoff
        );
    }

    /**
     * Execute the restart: archive costs, delete data, and update simulation state.
     */
    public RestartFromPreview execute(UUID simulationId, long consensusDecisionId, UserId requestingUserId) {
        Simulation simulation = loadAndValidate(simulationId, requestingUserId, Access.MUTATE);
        Instant cutoff = resolveCutoff(simulation, consensusDecisionId);

        SimulationId simId = simulation.getSimulationId();

        log.info("Restarting simulation {} from decision {} (cutoff: {})",
            simulationId, consensusDecisionId, cutoff);

        // 1. Archive costs from decisions being deleted
        AggregatedLLMCost costToArchive = consensusDecisionRepository.sumCostBySimulationIdAfter(simulationId, cutoff);
        simulation.archiveLLMCost(
            costToArchive.rawCost(),
            costToArchive.promptTokens(),
            costToArchive.completionTokens(),
            costToArchive.totalTokens(),
            costToArchive.decisionCount()
        );

        // 2. Get preview counts before deletion
        long consensusCount = consensusDecisionRepository.countBySimulationIdAfter(simulationId, cutoff);
        long llmCount = llmDecisionRepository.countBySimulationIdAfter(simulationId, cutoff);
        long snapshotCount = portfolioRepository.countBySimulationIdAfter(simId, cutoff);

        // 3. Get the last portfolio snapshot BEFORE cutoff (for the preview response)
        BigDecimal cashAtRestart = BigDecimal.ZERO;
        BigDecimal valueAtRestart = BigDecimal.ZERO;
        var snapshots = portfolioRepository.findBySimulationId(simId, null, cutoff.minusMillis(1), 1);
        if (!snapshots.isEmpty()) {
            PortfolioSnapshot lastBefore = snapshots.get(0);
            cashAtRestart = lastBefore.getCashAvailable().amount();
            valueAtRestart = lastBefore.getTotalValue().amount();
        }

        // 4. Delete data (order: LLM decisions first, then consensus, then snapshots)
        long llmDeleted = llmDecisionRepository.deleteBySimulationIdAfter(simulationId, cutoff);
        long consensusDeleted = consensusDecisionRepository.deleteBySimulationIdAfter(simulationId, cutoff);
        long snapshotsDeleted = portfolioRepository.deleteBySimulationIdAfter(simId, cutoff);

        log.info("Deleted {} consensus decisions, {} LLM decisions, {} snapshots for simulation {}",
            consensusDeleted, llmDeleted, snapshotsDeleted, simulationId);

        // 5. Calculate new progress
        int newProgress = calculateProgress(cutoff, simulation.getStartDate(), simulation.getEndDate());

        // 6. Update simulation state.
        //
        // The pause must be written through updatePauseState(), not save(): save() deliberately
        // skips the pause columns so a long-lived in-memory Simulation held by the executor cannot
        // clobber them. restartFrom() sets paused=true in memory, so without this call the flag
        // never reached the database, SimulationPickupScheduler saw SIMULATING && !paused on its
        // next 30s tick, and the run the user asked to hold restarted itself — re-spending LLM
        // budget on the decisions that were just deleted.
        //
        // Order matters: pause first, then flip the status. The row is still COMPLETED/FAILED (or
        // already paused) while the pause lands, so the scheduler has no window in which to see a
        // runnable simulation.
        SimulationStatus statusBeforeRestart = simulation.getStatus();
        simulation.restartFrom(newProgress);
        simulation.unlock(); // Clear any stale locks
        simulationRepository.updatePauseState(
            simId, true, simulation.isPausedForExternalReason(), simulation.getExternalPauseReason());
        simulationRepository.save(simulation);
        // The status flip and the cleared completedAt are lifecycle columns, so they go through
        // the narrow compare-and-set rather than save(); the rewound progress follows it.
        simulationRepository.updateStatus(simId, statusBeforeRestart, simulation.getStatus(),
            simulation.getCompletedAt(), simulation.getFailureReason());
        simulationRepository.updateProgress(simId, simulation.getProgress());

        log.info("Simulation {} restarted from {} — progress set to {}%, archived cost ${}",
            simulationId, cutoff, newProgress, costToArchive.rawCost());

        return new RestartFromPreview(
            consensusCount,
            llmCount,
            snapshotCount,
            costToArchive.rawCost(),
            costToArchive.totalTokens(),
            cashAtRestart,
            valueAtRestart,
            cutoff
        );
    }

    /** Whether the caller is only reading the plan, or actually carrying it out. */
    private enum Access { READ, MUTATE }

    private Simulation loadAndValidate(UUID simulationId, UserId requestingUserId, Access access) {
        SimulationId simId = new SimulationId(simulationId);
        Simulation simulation = simulationRepository.findById(simId)
            .orElseThrow(() -> new NoSuchElementException("Simulation not found: " + simulationId));

        // Authorization check.
        //
        // preview() and execute() used to share a single requireView here — but execute()
        // deletes LLM decisions, consensus decisions and portfolio snapshots. requireView on a
        // simulation honours the parent strategy's public visibility, so publishing a strategy
        // handed every authenticated user the ability to destroy its simulations' history.
        // A destructive call must demand edit rights, which never carry a public bypass.
        Strategy strategy = strategyRepository.findById(simulation.getStrategyId())
            .orElseThrow(() -> new NoSuchElementException("Strategy not found"));
        if (access == Access.MUTATE) {
            authorizationService.requireEdit(requestingUserId, simulation, strategy);
        } else {
            authorizationService.requireView(requestingUserId, simulation, strategy);
        }

        // Must be a backtest — paper/live trading has real broker state
        if (!simulation.isBacktest()) {
            throw new IllegalStateException("Can only restart backtest simulations, not live/paper trading");
        }

        return simulation;
    }

    private Instant resolveCutoff(Simulation simulation, long consensusDecisionId) {
        ConsensusDecision decision = consensusDecisionRepository.findById(consensusDecisionId)
            .orElseThrow(() -> new NoSuchElementException("Consensus decision not found: " + consensusDecisionId));

        // Verify the decision belongs to this simulation
        SimulationId simId = simulation.getSimulationId();
        if (!simId.equals(decision.getSimulationId())) {
            throw new IllegalArgumentException(
                "Decision " + consensusDecisionId + " does not belong to simulation " + simId.value());
        }

        return decision.getDecisionTimestamp();
    }

    private int calculateProgress(Instant cutoff, Instant startDate, Instant endDate) {
        if (startDate == null || endDate == null || !endDate.isAfter(startDate)) {
            return 0;
        }
        long totalDuration = Duration.between(startDate, endDate).toMillis();
        long elapsed = Duration.between(startDate, cutoff).toMillis();
        if (totalDuration <= 0) return 0;
        return (int) Math.min(100, Math.max(0, (elapsed * 100) / totalDuration));
    }
}
