package com.quorumtrading.application.service;

import com.quorumtrading.application.port.out.ConsensusDecisionRepository;
import com.quorumtrading.application.port.out.SimulationRepository;
import com.quorumtrading.application.port.out.StrategyRepository;
import com.quorumtrading.application.usecase.health.GetSimulationHealthUseCase;
import com.quorumtrading.application.usecase.health.SimulationExecutionHealth;
import com.quorumtrading.application.usecase.health.SimulationExecutionHealth.SkipReason;
import com.quorumtrading.application.usecase.health.SimulationExecutionHealth.Verdict;
import com.quorumtrading.application.usecase.query.GetSimulationQuery;
import com.quorumtrading.model.entity.ConsensusDecision;
import com.quorumtrading.model.entity.Simulation;
import com.quorumtrading.model.entity.Strategy;
import com.quorumtrading.model.enums.ConsensusActionType;
import com.quorumtrading.model.enums.ExecutionOutcome;
import com.quorumtrading.model.valueobject.ExternalPauseReason;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Computes {@link SimulationExecutionHealth} from the recent consensus decision rows plus the
 * simulation's own counters. Framework-agnostic POJO wired in {@code ApplicationConfig}.
 *
 * <p>Deliberately read-only and side-effect free: it inspects what the consensus pipeline
 * already persisted (skip rows carry their reason in {@code notes}) instead of introducing a
 * second bookkeeping path that could drift from the first.</p>
 */
public class SimulationHealthService implements GetSimulationHealthUseCase {

    /** How many of the newest decision rows to inspect. */
    static final int WINDOW = 20;
    /** Leading skips (newest-first) from which a running simulation is called STALLED. */
    static final int STALL_STREAK = 3;
    /** Reasons are truncated to keep the read model wire-friendly. */
    private static final int REASON_MAX_CHARS = 200;
    /**
     * Collapses embedded ISO-8601 timestamps so per-round reasons ("No market data available
     * for tickers at 2024-03-01T15:00:00Z") group into one reason instead of one per round.
     */
    private static final java.util.regex.Pattern TIMESTAMPS =
            java.util.regex.Pattern.compile("\\d{4}-\\d{2}-\\d{2}T[\\d:.]+(?:Z|[+-]\\d{2}:?\\d{2})?");

    private final SimulationRepository simulationRepository;
    private final StrategyRepository strategyRepository;
    private final ConsensusDecisionRepository consensusDecisionRepository;
    private final AuthorizationService authorizationService;

    public SimulationHealthService(
            SimulationRepository simulationRepository,
            StrategyRepository strategyRepository,
            ConsensusDecisionRepository consensusDecisionRepository,
            AuthorizationService authorizationService) {
        this.simulationRepository = simulationRepository;
        this.strategyRepository = strategyRepository;
        this.consensusDecisionRepository = consensusDecisionRepository;
        this.authorizationService = authorizationService;
    }

    @Override
    public SimulationExecutionHealth execute(GetSimulationQuery query) {
        Simulation simulation = simulationRepository.findById(query.simulationId())
                .orElseThrow(() -> new IllegalArgumentException("Simulation not found: " + query.simulationId()));
        Strategy strategy = strategyRepository.findById(simulation.getStrategyId())
                .orElseThrow(() -> new IllegalArgumentException("Strategy not found: " + simulation.getStrategyId()));
        authorizationService.requireView(query.requestingUserId(), simulation, strategy);

        List<ConsensusDecision> recent = consensusDecisionRepository.findBySimulationIdAndTicker(
                simulation.getSimulationId().value(), null, null, WINDOW, null, null);

        int recentSkipped = 0;
        int streak = 0;
        boolean streakBroken = false;
        Instant lastRoundAt = null;
        Instant lastDecisionAt = null;
        List<String> skipReasons = new ArrayList<>();
        for (ConsensusDecision row : recent) { // newest first
            boolean skip = isSkip(row);
            if (lastRoundAt == null) {
                lastRoundAt = row.getDecisionTimestamp();
            }
            if (skip) {
                recentSkipped++;
                skipReasons.add(normalizeReason(skipReason(row)));
                if (!streakBroken) {
                    streak++;
                }
            } else {
                streakBroken = true;
                if (lastDecisionAt == null) {
                    lastDecisionAt = row.getDecisionTimestamp();
                }
            }
        }

        List<SkipReason> topReasons = topReasons(skipReasons);
        Verdict verdict = verdictFor(simulation, lastDecisionAt, streak);
        String detail = detailFor(verdict, simulation, recent.size(), recentSkipped, streak,
                lastDecisionAt, topReasons);

        return new SimulationExecutionHealth(
                simulation.getStatus().name(),
                verdict,
                detail,
                lastDecisionAt,
                lastRoundAt,
                recent.size(),
                recentSkipped,
                streak,
                simulation.getTotalEvaluationCount(),
                simulation.getTotalSkippedEvaluationCount(),
                topReasons,
                simulation.isPausedForExternalReason() ? simulation.getExternalPauseReason() : null,
                simulation.getFailureReason());
    }

    private Verdict verdictFor(Simulation simulation, Instant lastDecisionAt, int streak) {
        return switch (simulation.getStatus()) {
            case PENDING -> Verdict.NOT_STARTED;
            case WAITING_FOR_DATA -> Verdict.WAITING_FOR_DATA;
            case COMPLETED -> Verdict.COMPLETED;
            case FAILED -> Verdict.FAILED;
            case SIMULATING, LIVE -> {
                if (simulation.isPausedForExternalReason()) {
                    yield Verdict.PAUSED_EXTERNAL;
                }
                if (simulation.isPaused()) {
                    yield Verdict.PAUSED_BY_USER;
                }
                if (streak >= STALL_STREAK) {
                    yield Verdict.STALLED;
                }
                if (lastDecisionAt == null) {
                    // No decision yet: either nothing has run, or the few rows so far are skips
                    yield Verdict.IDLE;
                }
                yield Verdict.HEALTHY;
            }
        };
    }

    private String detailFor(Verdict verdict, Simulation simulation, int recentRows, int recentSkipped,
                             int streak, Instant lastDecisionAt, List<SkipReason> topReasons) {
        return switch (verdict) {
            case NOT_STARTED -> "The simulation has not started yet.";
            case WAITING_FOR_DATA -> "Waiting for the historical price backfill to complete before evaluating.";
            case COMPLETED -> simulation.getTotalEvaluationCount() > 0
                    ? String.format("The run finished; %d of %d evaluations were skipped.",
                            simulation.getTotalSkippedEvaluationCount(), simulation.getTotalEvaluationCount())
                    : "The run finished.";
            case FAILED -> simulation.getFailureReason() != null
                    ? "The run failed: " + simulation.getFailureReason()
                    : "The run failed before recording a reason.";
            case PAUSED_BY_USER -> "Paused by the user; resuming continues the run.";
            case PAUSED_EXTERNAL -> externalPauseDetail(simulation.getExternalPauseReason());
            case IDLE -> recentRows == 0
                    ? "No consensus rounds have been recorded yet."
                    : String.format("No decision yet — %d round(s) recorded so far, %d skipped.",
                            recentRows, recentSkipped);
            case STALLED -> String.format(
                    "The engine is not producing decisions: the last %d consensus row(s) are all skips%s. %s",
                    streak,
                    lastDecisionAt != null ? " (last real decision at " + lastDecisionAt + ")" : "",
                    topReasons.isEmpty() ? "" : "Dominant reason: " + topReasons.get(0).reason());
            case HEALTHY -> recentSkipped == 0
                    ? String.format("Recent rounds are producing decisions (last at %s).", lastDecisionAt)
                    : String.format("Producing decisions (last at %s); %d of the last %d rows were skipped.",
                            lastDecisionAt, recentSkipped, recentRows);
        };
    }

    private String externalPauseDetail(String reason) {
        if (reason == null) {
            return "Paused by the system.";
        }
        return switch (reason) {
            case ExternalPauseReason.BUDGET_DEPLETED -> "Paused by the system: the AI budget is depleted.";
            case ExternalPauseReason.DATA_GAP_DETECTED ->
                    "Paused by the system after repeated no-market-data skips; a re-backfill was requested.";
            case ExternalPauseReason.LLM_FAILURE_DETECTED ->
                    "Paused by the system after repeated rounds in which the LLM engine produced no decision "
                    + "(failed or timed out). Fix or switch the engine, then resume.";
            case ExternalPauseReason.EXECUTION_STALLED ->
                    "Paused by the system: the run stopped making progress and no executor is holding it "
                    + "(no new decision, and its execution lease has expired). Nothing was completed and the "
                    + "progress shown is where it actually got to — resume to continue from the last decision.";
            default -> "Paused by the system: " + reason;
        };
    }

    private static boolean isSkip(ConsensusDecision row) {
        boolean skipAction = row.getAction() != null && row.getAction().type() == ConsensusActionType.SKIP;
        return skipAction || row.getExecutionOutcome() == ExecutionOutcome.SKIPPED;
    }

    private static String skipReason(ConsensusDecision row) {
        if (row.getNotes() != null && !row.getNotes().isBlank()) {
            return row.getNotes();
        }
        if (row.getAction() != null && row.getAction().reasoning() != null
                && !row.getAction().reasoning().isBlank()) {
            return row.getAction().reasoning();
        }
        return "no reason recorded";
    }

    private static String normalizeReason(String reason) {
        String normalized = TIMESTAMPS.matcher(reason).replaceAll("<time>").strip();
        return normalized.length() > REASON_MAX_CHARS
                ? normalized.substring(0, REASON_MAX_CHARS) + "…"
                : normalized;
    }

    private static List<SkipReason> topReasons(List<String> reasons) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String reason : reasons) {
            counts.merge(reason, 1, Integer::sum);
        }
        return counts.entrySet().stream()
                .sorted(Comparator.<Map.Entry<String, Integer>>comparingInt(Map.Entry::getValue).reversed())
                .limit(3)
                .map(e -> new SkipReason(e.getKey(), e.getValue()))
                .toList();
    }
}
