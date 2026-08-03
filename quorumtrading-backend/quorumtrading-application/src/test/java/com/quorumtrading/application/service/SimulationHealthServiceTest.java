package com.quorumtrading.application.service;

import com.quorumtrading.application.orchestration.agents.ConsensusActionParser;
import com.quorumtrading.application.port.out.ConsensusDecisionRepository;
import com.quorumtrading.application.port.out.SimulationRepository;
import com.quorumtrading.application.port.out.StrategyRepository;
import com.quorumtrading.model.valueobject.ExternalPauseReason;
import com.quorumtrading.application.usecase.health.SimulationExecutionHealth;
import com.quorumtrading.application.usecase.health.SimulationExecutionHealth.Verdict;
import com.quorumtrading.application.usecase.query.GetSimulationQuery;
import com.quorumtrading.model.entity.ConsensusDecision;
import com.quorumtrading.model.entity.Simulation;
import com.quorumtrading.model.entity.Strategy;
import com.quorumtrading.model.enums.ConsensusMethod;
import com.quorumtrading.model.enums.ExecutionOutcome;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.SimulationId;
import com.quorumtrading.model.valueobject.StrategyId;
import com.quorumtrading.model.valueobject.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

/**
 * The health read model must answer "why is this run not progressing?" while the run is
 * still in flight — the exact question the post-mortem {@code noDecisionsReason} field
 * refuses to answer before COMPLETED.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SimulationHealthServiceTest {

    @Mock private SimulationRepository simulationRepository;
    @Mock private StrategyRepository strategyRepository;
    @Mock private ConsensusDecisionRepository consensusDecisionRepository;
    @Mock private AuthorizationService authorizationService;

    private SimulationHealthService service;
    private Simulation simulation;
    private GetSimulationQuery query;

    private static final Instant T1 = Instant.parse("2024-03-01T15:00:00Z");
    private static final Instant T2 = Instant.parse("2024-03-04T15:00:00Z");
    private static final Instant T3 = Instant.parse("2024-03-05T15:00:00Z");

    @BeforeEach
    void setUp() {
        service = new SimulationHealthService(
            simulationRepository, strategyRepository, consensusDecisionRepository, authorizationService);

        SimulationId simulationId = new SimulationId(UUID.randomUUID());
        simulation = new Simulation(
            simulationId, "Health Sim", new StrategyId(UUID.randomUUID()),
            Instant.parse("2024-01-01T00:00:00Z"), Instant.parse("2024-06-01T00:00:00Z"),
            Money.usd(new BigDecimal("10000")));
        simulation.start(); // → SIMULATING

        Strategy strategy = new Strategy(
            simulation.getStrategyId(), "Health Strategy", "content", new UserId(UUID.randomUUID()));

        query = new GetSimulationQuery(simulationId, new UserId(UUID.randomUUID()));

        when(simulationRepository.findById(simulationId)).thenReturn(Optional.of(simulation));
        when(strategyRepository.findById(simulation.getStrategyId())).thenReturn(Optional.of(strategy));
        when(consensusDecisionRepository.findBySimulationIdAndTicker(
            any(), any(), any(), any(), any(), any())).thenReturn(List.of());
    }

    private ConsensusDecision skipRow(Instant at, String reason) {
        ConsensusDecision row = new ConsensusDecision(
            simulation.getSimulationId(), at, ConsensusMethod.MAJORITY,
            ConsensusActionParser.skip("AAPL", reason));
        row.setExecutionOutcome(ExecutionOutcome.SKIPPED);
        row.setNotes(reason);
        row.setExecuted(false);
        return row;
    }

    private ConsensusDecision holdRow(Instant at) {
        return new ConsensusDecision(
            simulation.getSimulationId(), at, ConsensusMethod.MAJORITY,
            ConsensusActionParser.hold("AAPL", 0.0, 0.8, "steady"));
    }

    private void recentRows(ConsensusDecision... newestFirst) {
        when(consensusDecisionRepository.findBySimulationIdAndTicker(
            any(), any(), any(), any(), any(), any())).thenReturn(List.of(newestFirst));
    }

    @Test
    @DisplayName("every recent round skipped → STALLED, with the dominant reason named")
    void allRecentSkippedIsStalled() {
        String reason = "Consensus pipeline for AAPL did not complete within 360s (slow or failed LLM engine)";
        recentRows(skipRow(T3, reason), skipRow(T2, reason), skipRow(T1, reason));

        SimulationExecutionHealth health = service.execute(query);

        assertThat(health.verdict()).isEqualTo(Verdict.STALLED);
        assertThat(health.consecutiveSkippedRows()).isEqualTo(3);
        assertThat(health.recentSkippedRows()).isEqualTo(3);
        assertThat(health.lastDecisionAt()).isNull();
        assertThat(health.lastRoundAt()).isEqualTo(T3);
        assertThat(health.topSkipReasons()).hasSize(1);
        assertThat(health.topSkipReasons().get(0).count()).isEqualTo(3);
        assertThat(health.detail()).contains("slow or failed LLM engine");
    }

    @Test
    @DisplayName("decisions among recent rows → HEALTHY with lastDecisionAt")
    void decidingRunIsHealthy() {
        recentRows(holdRow(T3), skipRow(T2, "market closed"), holdRow(T1));

        SimulationExecutionHealth health = service.execute(query);

        assertThat(health.verdict()).isEqualTo(Verdict.HEALTHY);
        assertThat(health.lastDecisionAt()).isEqualTo(T3);
        assertThat(health.recentSkippedRows()).isEqualTo(1);
        assertThat(health.consecutiveSkippedRows()).isZero();
    }

    @Test
    @DisplayName("skip streak below the threshold with no decision yet → IDLE, not STALLED")
    void shortSkipStreakWithoutDecisionsIsIdle() {
        recentRows(skipRow(T2, "x"), skipRow(T1, "x"));

        SimulationExecutionHealth health = service.execute(query);

        assertThat(health.verdict()).isEqualTo(Verdict.IDLE);
        assertThat(health.detail()).contains("2 round(s)");
    }

    @Test
    @DisplayName("no rounds at all → IDLE")
    void noRoundsIsIdle() {
        SimulationExecutionHealth health = service.execute(query);

        assertThat(health.verdict()).isEqualTo(Verdict.IDLE);
        assertThat(health.lastRoundAt()).isNull();
    }

    @Test
    @DisplayName("system pause for LLM failure → PAUSED_EXTERNAL naming the engine")
    void llmFailurePauseIsExplained() {
        simulation.pauseForExternalReason(ExternalPauseReason.LLM_FAILURE_DETECTED);

        SimulationExecutionHealth health = service.execute(query);

        assertThat(health.verdict()).isEqualTo(Verdict.PAUSED_EXTERNAL);
        assertThat(health.externalPauseReason()).isEqualTo("LLM_FAILURE_DETECTED");
        assertThat(health.detail()).contains("LLM engine");
    }

    @Test
    @DisplayName("system pause for a depleted budget → PAUSED_EXTERNAL naming the budget")
    void budgetPauseIsExplained() {
        simulation.pauseForExternalReason(ExternalPauseReason.BUDGET_DEPLETED);

        SimulationExecutionHealth health = service.execute(query);

        assertThat(health.verdict()).isEqualTo(Verdict.PAUSED_EXTERNAL);
        assertThat(health.externalPauseReason()).isEqualTo("BUDGET_DEPLETED");
        assertThat(health.detail()).contains("budget is depleted");
    }

    @Test
    @DisplayName("system pause for a stalled executor → PAUSED_EXTERNAL, and it says so plainly")
    void executionStalledPauseIsExplained() {
        // This is the verdict a reaper-killed run used to be denied. It reported COMPLETED with
        // the detail "The run finished" — the most reassuring answer available — on a run that
        // covered 8.5 of its 12 months. Anything here is better than that, but the detail must
        // still say three things: the system did it, nothing finished, and one resume fixes it.
        simulation.pauseForExternalReason(ExternalPauseReason.EXECUTION_STALLED);

        SimulationExecutionHealth health = service.execute(query);

        assertThat(health.verdict()).isEqualTo(Verdict.PAUSED_EXTERNAL);
        assertThat(health.externalPauseReason()).isEqualTo("EXECUTION_STALLED");
        assertThat(health.detail())
                .contains("Paused by the system")
                .contains("stopped making progress")
                .contains("resume");
        assertThat(health.status()).isEqualTo("SIMULATING");
    }

    @Test
    @DisplayName("failed run → FAILED with the recorded reason")
    void failedRunCarriesReason() {
        simulation.fail("Strategy not found: xyz");

        SimulationExecutionHealth health = service.execute(query);

        assertThat(health.verdict()).isEqualTo(Verdict.FAILED);
        assertThat(health.failureReason()).isEqualTo("Strategy not found: xyz");
        assertThat(health.detail()).contains("Strategy not found");
    }

    @Test
    @DisplayName("reasons differing only in an embedded timestamp group as one")
    void timestampsAreNormalizedOutOfReasons() {
        recentRows(
            skipRow(T3, "No market data available for tickers at 2024-03-05T15:00:00Z"),
            skipRow(T2, "No market data available for tickers at 2024-03-04T15:00:00Z"),
            skipRow(T1, "No market data available for tickers at 2024-03-01T15:00:00Z"));

        SimulationExecutionHealth health = service.execute(query);

        assertThat(health.topSkipReasons()).hasSize(1);
        assertThat(health.topSkipReasons().get(0).count()).isEqualTo(3);
        assertThat(health.topSkipReasons().get(0).reason()).doesNotContain("2024-03-05");
    }

    @Test
    @DisplayName("ownership is enforced in the use case, not left to adapters")
    void unauthorizedAccessPropagates() {
        doThrow(new SecurityException("not yours")).when(authorizationService)
            .requireView(any(), any(Simulation.class), any(Strategy.class));

        assertThatThrownBy(() -> service.execute(query)).isInstanceOf(SecurityException.class);
    }
}
