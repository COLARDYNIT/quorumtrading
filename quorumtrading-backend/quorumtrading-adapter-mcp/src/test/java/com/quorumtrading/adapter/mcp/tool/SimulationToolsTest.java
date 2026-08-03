package com.quorumtrading.adapter.mcp.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quorumtrading.adapter.mcp.auth.McpUserContext;
import com.quorumtrading.application.usecase.*;
import com.quorumtrading.application.usecase.command.CreateSimulationCommand;
import com.quorumtrading.application.usecase.command.DeleteSimulationCommand;
import com.quorumtrading.application.usecase.command.PauseSimulationCommand;
import com.quorumtrading.application.usecase.command.StartSimulationCommand;
import com.quorumtrading.application.usecase.query.GetSimulationQuery;
import com.quorumtrading.application.usecase.query.ListSimulationsQuery;
import com.quorumtrading.application.port.out.BrokerAccountRepository;
import com.quorumtrading.application.port.out.PortfolioRepository;
import com.quorumtrading.application.port.out.StrategyRepository;
import com.quorumtrading.model.entity.Simulation;
import com.quorumtrading.model.entity.Strategy;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.SimulationId;
import com.quorumtrading.model.valueobject.StrategyId;
import com.quorumtrading.model.valueobject.UserId;
import io.quarkiverse.mcp.server.ToolResponse;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@QuarkusTest
class SimulationToolsTest {

    @Inject
    SimulationTools simulationTools;

    @InjectMock
    ListSimulationsUseCase listSimulationsUseCase;

    @InjectMock
    GetSimulationUseCase getSimulationUseCase;

    @InjectMock
    com.quorumtrading.application.usecase.health.GetSimulationHealthUseCase getSimulationHealthUseCase;

    @InjectMock
    CreateSimulationUseCase createSimulationUseCase;

    @InjectMock
    StartSimulationUseCase startSimulationUseCase;

    @InjectMock
    PauseSimulationUseCase pauseSimulationUseCase;

    @InjectMock
    com.quorumtrading.application.usecase.ResumeSimulationUseCase resumeSimulationUseCase;

    @InjectMock
    DeleteSimulationUseCase deleteSimulationUseCase;

    @InjectMock
    StrategyRepository strategyRepository;

    @InjectMock
    BrokerAccountRepository brokerAccountRepository;

    @InjectMock
    PortfolioRepository portfolioRepository;

    @InjectMock
    com.quorumtrading.application.port.out.ConsensusDecisionRepository consensusDecisionRepository;

    @InjectMock
    McpUserContext mcpUserContext;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final UUID SIM_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID STRATEGY_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID USER_ID = UUID.fromString("77777777-7777-7777-7777-777777777777");

    @BeforeEach
    void setUp() {
        when(mcpUserContext.getUserId()).thenReturn(new UserId(USER_ID));
        Strategy strategy = new Strategy(
            new StrategyId(STRATEGY_ID), "Test Strategy",
            "---\nconsensus_method: MAJORITY\n---\n# Test", new UserId(USER_ID));
        when(strategyRepository.findById(new StrategyId(STRATEGY_ID))).thenReturn(Optional.of(strategy));
    }

    @Test
    void listSimulations_returnsJsonWithPagination() throws Exception {
        Simulation sim = createTestSimulation();
        when(listSimulationsUseCase.execute(any(ListSimulationsQuery.class)))
            .thenReturn(List.of(sim));

        ToolResponse response = simulationTools.listSimulations(Optional.empty(), Optional.empty(), Optional.empty());

        assertThat(response.isError()).isFalse();
        String result = response.firstContent().asText().text();
        JsonNode json = objectMapper.readTree(result);
        assertThat(json.get("count").asInt()).isEqualTo(1);
        assertThat(json.get("hasMore").asBoolean()).isFalse();
        assertThat(json.get("items")).isNotNull();
        assertThat(json.get("items").size()).isEqualTo(1);

        JsonNode item = json.get("items").get(0);
        assertThat(item.get("id").asText()).isEqualTo(SIM_ID.toString());
        assertThat(item.get("name").asText()).isEqualTo("Test Simulation");
        assertThat(item.get("status").asText()).isEqualTo("PENDING");
        assertThat(item.get("strategyName").asText()).isEqualTo("Test Strategy");
        assertThat(item.get("tradingMode").asText()).isEqualTo("BACKTEST");
        assertThat(item.has("symbols")).isTrue();
        assertThat(item.get("currentValue").isNull()).isTrue();
    }

    @Test
    void listSimulations_withStatusFilter() throws Exception {
        when(listSimulationsUseCase.execute(any(ListSimulationsQuery.class)))
            .thenReturn(List.of());

        ToolResponse response = simulationTools.listSimulations(Optional.of("COMPLETED"), Optional.of(10), Optional.empty());

        assertThat(response.isError()).isFalse();
        String result = response.firstContent().asText().text();
        JsonNode json = objectMapper.readTree(result);
        assertThat(json.get("count").asInt()).isEqualTo(0);
        assertThat(json.get("items").size()).isEqualTo(0);

        verify(listSimulationsUseCase).execute(any(ListSimulationsQuery.class));
    }

    @Test
    void getSimulation_returnsJsonWithDetails() throws Exception {
        Simulation sim = createTestSimulation();
        when(getSimulationUseCase.execute(any(GetSimulationQuery.class)))
            .thenReturn(sim);

        ToolResponse response = simulationTools.getSimulation(SIM_ID.toString());

        assertThat(response.isError()).isFalse();
        String result = response.firstContent().asText().text();
        JsonNode json = objectMapper.readTree(result);
        assertThat(json.get("id").asText()).isEqualTo(SIM_ID.toString());
        assertThat(json.get("strategyId").asText()).isEqualTo(STRATEGY_ID.toString());
        assertThat(json.get("strategyName").asText()).isEqualTo("Test Strategy");
        assertThat(json.get("tradingMode").asText()).isEqualTo("BACKTEST");
        assertThat(json.get("initialCash").asText()).isEqualTo("10000");
        assertThat(json.get("currency").asText()).isEqualTo("USD");
        assertThat(json.has("symbols")).isTrue();
        assertThat(json.get("currentValue").isNull()).isTrue();
    }

    @Test
    void getSimulation_carriesTheLiveHealthBlock() throws Exception {
        // The health block is the in-flight answer to "why is this run not progressing?" —
        // the question the post-mortem noDecisionsReason field refuses to answer before
        // COMPLETED. get_simulation must surface it verbatim from the use case.
        when(getSimulationUseCase.execute(any(GetSimulationQuery.class))).thenReturn(createTestSimulation());
        when(getSimulationHealthUseCase.execute(any(GetSimulationQuery.class))).thenReturn(
            new com.quorumtrading.application.usecase.health.SimulationExecutionHealth(
                "SIMULATING",
                com.quorumtrading.application.usecase.health.SimulationExecutionHealth.Verdict.STALLED,
                "The engine is not producing decisions: the last 5 consensus row(s) are all skips.",
                null,
                Instant.parse("2024-03-05T15:00:00Z"),
                5, 5, 5, 12, 12,
                List.of(new com.quorumtrading.application.usecase.health.SimulationExecutionHealth.SkipReason(
                    "Consensus pipeline for AAPL did not complete within 360s (slow or failed LLM engine)", 5)),
                null, null));

        ToolResponse response = simulationTools.getSimulation(SIM_ID.toString());

        assertThat(response.isError()).isFalse();
        JsonNode json = objectMapper.readTree(response.firstContent().asText().text());
        JsonNode health = json.get("health");
        assertThat(health).isNotNull();
        assertThat(health.get("verdict").asText()).isEqualTo("STALLED");
        assertThat(health.get("consecutiveSkippedRows").asInt()).isEqualTo(5);
        assertThat(health.get("lastDecisionAt").isNull()).isTrue();
        assertThat(health.get("topSkipReasons").get(0).get("reason").asText())
            .contains("slow or failed LLM engine");
        assertThat(health.get("topSkipReasons").get(0).get("count").asInt()).isEqualTo(5);
    }

    @Test
    void getSimulation_omitsHealthWhenTheAdvisoryLookupFails() throws Exception {
        // Health is advisory: a failure computing it must never break the core projection.
        when(getSimulationUseCase.execute(any(GetSimulationQuery.class))).thenReturn(createTestSimulation());
        when(getSimulationHealthUseCase.execute(any(GetSimulationQuery.class)))
            .thenThrow(new RuntimeException("health blew up"));

        ToolResponse response = simulationTools.getSimulation(SIM_ID.toString());

        assertThat(response.isError()).isFalse();
        JsonNode json = objectMapper.readTree(response.firstContent().asText().text());
        assertThat(json.has("health")).isFalse();
        assertThat(json.get("id").asText()).isEqualTo(SIM_ID.toString());
    }

    @Test
    void createSimulation_returnsIdAndMessage() throws Exception {
        SimulationId newId = new SimulationId(SIM_ID);
        when(createSimulationUseCase.execute(any(CreateSimulationCommand.class)))
            .thenReturn(newId);

        ToolResponse response = simulationTools.createSimulation(
            Optional.of("My Backtest"),
            STRATEGY_ID.toString(),
            "2025-01-01T00:00:00Z",
            "2025-06-01T00:00:00Z",
            "10000",
            Optional.empty(),
            Optional.empty(),
            Optional.of("true"),
            Optional.empty()
        );

        assertThat(response.isError()).isFalse();
        String result = response.firstContent().asText().text();
        JsonNode json = objectMapper.readTree(result);
        assertThat(json.get("id").asText()).isEqualTo(SIM_ID.toString());
        assertThat(json.get("message").asText()).contains("My Backtest");
        assertThat(json.get("message").asText()).contains("BACKTEST");
        // The web-app page for the new run — "show it in the UI" must be answerable from
        // the create response alone.
        assertThat(json.get("uiUrl").asText()).isEqualTo("http://localhost:3000/simulations/" + SIM_ID);

        verify(createSimulationUseCase).execute(any(CreateSimulationCommand.class));
    }

    @Test
    void createSimulation_longDuration_withoutCostFlag_returnsError() {
        ToolResponse response = simulationTools.createSimulation(
            Optional.of("Long Backtest"),
            STRATEGY_ID.toString(),
            "2025-01-01T00:00:00Z",
            "2025-03-02T00:00:00Z",   // 60 days > 31
            "10000",
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty()
        );

        assertThat(response.isError()).isTrue();
        String errorText = response.firstContent().asText().text();
        assertThat(errorText).contains("userInformedOfCost");
        assertThat(errorText).containsIgnoringCase("cost");
        verify(createSimulationUseCase, never()).execute(any(CreateSimulationCommand.class));
    }

    @Test
    void createSimulation_longDuration_withCostFlagTrue_succeeds() throws Exception {
        SimulationId newId = new SimulationId(SIM_ID);
        when(createSimulationUseCase.execute(any(CreateSimulationCommand.class)))
            .thenReturn(newId);

        ToolResponse response = simulationTools.createSimulation(
            Optional.of("Long Backtest"),
            STRATEGY_ID.toString(),
            "2025-01-01T00:00:00Z",
            "2025-03-02T00:00:00Z",   // 60 days > 31
            "10000",
            Optional.empty(),
            Optional.empty(),
            Optional.of("true"),
            Optional.empty()
        );

        assertThat(response.isError()).isFalse();
        verify(createSimulationUseCase).execute(any(CreateSimulationCommand.class));
    }

    @Test
    void createSimulation_shortDuration_noCostFlagNeeded() throws Exception {
        SimulationId newId = new SimulationId(SIM_ID);
        when(createSimulationUseCase.execute(any(CreateSimulationCommand.class)))
            .thenReturn(newId);

        ToolResponse response = simulationTools.createSimulation(
            Optional.of("Short Backtest"),
            STRATEGY_ID.toString(),
            "2025-01-01T00:00:00Z",
            "2025-01-15T00:00:00Z",   // 14 days <= 31
            "10000",
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty()
        );

        assertThat(response.isError()).isFalse();
        verify(createSimulationUseCase).execute(any(CreateSimulationCommand.class));
    }

    @Test
    void startSimulation_returnsStatusMessage() throws Exception {
        doNothing().when(startSimulationUseCase).execute(any(StartSimulationCommand.class));
        when(getSimulationUseCase.execute(any(GetSimulationQuery.class))).thenReturn(createTestSimulation());

        ToolResponse response = simulationTools.startSimulation(SIM_ID.toString());

        assertThat(response.isError()).isFalse();
        String result = response.firstContent().asText().text();
        JsonNode json = objectMapper.readTree(result);
        assertThat(json.get("simulationId").asText()).isEqualTo(SIM_ID.toString());
        assertThat(json.get("message").asText()).contains("started");

        verify(startSimulationUseCase).execute(any(StartSimulationCommand.class));
    }

    @Test
    void pauseSimulation_returnsStatusMessage() throws Exception {
        doNothing().when(pauseSimulationUseCase).execute(any(PauseSimulationCommand.class));
        when(getSimulationUseCase.execute(any(GetSimulationQuery.class))).thenReturn(createTestSimulation());

        ToolResponse response = simulationTools.pauseSimulation(SIM_ID.toString());

        assertThat(response.isError()).isFalse();
        String result = response.firstContent().asText().text();
        JsonNode json = objectMapper.readTree(result);
        assertThat(json.get("simulationId").asText()).isEqualTo(SIM_ID.toString());
        assertThat(json.get("message").asText()).contains("paused");

        verify(pauseSimulationUseCase).execute(any(PauseSimulationCommand.class));
    }

    // --- start/resume must say when nothing happened ---

    @Test
    void startSimulation_onCompletedRun_saysNoActionTaken() throws Exception {
        // "Simulation started — status: COMPLETED" was a fabrication: the use case is silently
        // idempotent and nothing happened. Say so, and do not even call it.
        when(getSimulationUseCase.execute(any(GetSimulationQuery.class))).thenReturn(completedSimulation());

        JsonNode json = objectMapper.readTree(
            simulationTools.startSimulation(SIM_ID.toString()).firstContent().asText().text());

        assertThat(json.get("message").asText())
            .contains("No action taken")
            .contains("COMPLETED")
            .doesNotContain("Simulation started");
        verify(startSimulationUseCase, never()).execute(any(StartSimulationCommand.class));
    }

    @Test
    void startSimulation_onPausedRun_pointsAtResume() throws Exception {
        Simulation paused = createTestSimulation();
        paused.setStatus(com.quorumtrading.model.enums.SimulationStatus.SIMULATING);
        // Simulations are flagged paused from creation, so a SIMULATING run still carrying the
        // flag is exactly the "start_simulation does nothing to a paused run" trap.
        when(getSimulationUseCase.execute(any(GetSimulationQuery.class))).thenReturn(paused);

        JsonNode json = objectMapper.readTree(
            simulationTools.startSimulation(SIM_ID.toString()).firstContent().asText().text());

        assertThat(json.get("message").asText())
            .contains("No action taken")
            .contains("resume_simulation");
        verify(startSimulationUseCase, never()).execute(any(StartSimulationCommand.class));
    }

    @Test
    void resumeSimulation_onCompletedRun_saysNoActionTaken() throws Exception {
        when(getSimulationUseCase.execute(any(GetSimulationQuery.class))).thenReturn(completedSimulation());

        JsonNode json = objectMapper.readTree(
            simulationTools.resumeSimulation(SIM_ID.toString()).firstContent().asText().text());

        assertThat(json.get("message").asText())
            .contains("No action taken")
            .contains("COMPLETED")
            .doesNotContain("Simulation resumed");
        verify(resumeSimulationUseCase, never())
            .execute(any(com.quorumtrading.application.usecase.command.ResumeSimulationCommand.class));
    }

    @Test
    void resumeSimulation_onPausedRun_saysResumed() throws Exception {
        Simulation paused = createTestSimulation();
        paused.setStatus(com.quorumtrading.model.enums.SimulationStatus.SIMULATING);
        when(getSimulationUseCase.execute(any(GetSimulationQuery.class))).thenReturn(paused);

        JsonNode json = objectMapper.readTree(
            simulationTools.resumeSimulation(SIM_ID.toString()).firstContent().asText().text());

        assertThat(json.get("message").asText()).contains("Simulation resumed");
        verify(resumeSimulationUseCase)
            .execute(any(com.quorumtrading.application.usecase.command.ResumeSimulationCommand.class));
    }

    @Test
    void resumeSimulation_onRunningUnpausedRun_doesNotClaimItResumedAnything() throws Exception {
        Simulation running = createTestSimulation();
        running.setStatus(com.quorumtrading.model.enums.SimulationStatus.SIMULATING);
        running.resume(); // clears the created-paused flag: genuinely running, nothing to resume
        when(getSimulationUseCase.execute(any(GetSimulationQuery.class))).thenReturn(running);

        JsonNode json = objectMapper.readTree(
            simulationTools.resumeSimulation(SIM_ID.toString()).firstContent().asText().text());

        assertThat(json.get("message").asText())
            .contains("was not paused")
            .doesNotContain("Simulation resumed");
        // The use case still runs: re-submitting execution is what un-sticks a stalled run.
        verify(resumeSimulationUseCase)
            .execute(any(com.quorumtrading.application.usecase.command.ResumeSimulationCommand.class));
    }

    // --- create_simulation warns when the risk numbers make trading impossible ---

    @Test
    void createSimulation_warnsWhenRiskSettingsLeaveNothingToTrade() throws Exception {
        Strategy tight = new Strategy(
            new StrategyId(STRATEGY_ID), "Tight Strategy",
            """
            ---
            crypto_tickers:
              - BTC
            live_schedule:
              interval: 1h
            backtest_schedule:
              interval: 1w
            reserve_cash_percentage: 0.95
            position_limits:
              BTC: 0.02
            ---
            # Tight
            """,
            new UserId(USER_ID));
        when(strategyRepository.findById(new StrategyId(STRATEGY_ID))).thenReturn(Optional.of(tight));
        when(createSimulationUseCase.execute(any(CreateSimulationCommand.class)))
            .thenReturn(new SimulationId(SIM_ID));

        // $10,000 × (1 − 0.95) × 0.02 = $10 headroom against a $100 minimum: every BUY would
        // convert to HOLD, and the first sign used to be a finished run full of refusal notes.
        JsonNode json = objectMapper.readTree(simulationTools.createSimulation(
            Optional.of("Tight Backtest"),
            STRATEGY_ID.toString(),
            "2025-01-01T00:00:00Z",
            "2025-01-15T00:00:00Z",
            "10000",
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()
        ).firstContent().asText().text());

        assertThat(json.get("message").asText())
            .contains("WARNING: the risk settings leave too little to trade")
            .contains("BTC")
            .contains("$100");
    }

    @Test
    void createSimulation_doesNotWarnWhenHeadroomClearsTheMinimum() throws Exception {
        // The default setUp strategy has no tickers (parse fails → no warning computed), so use
        // a healthy parseable one to prove the warning stays quiet on sane numbers.
        Strategy healthy = new Strategy(
            new StrategyId(STRATEGY_ID), "Healthy Strategy",
            """
            ---
            crypto_tickers:
              - BTC
            live_schedule:
              interval: 1h
            backtest_schedule:
              interval: 1w
            reserve_cash_percentage: 0.2
            position_limits:
              BTC: 0.5
            ---
            # Healthy
            """,
            new UserId(USER_ID));
        when(strategyRepository.findById(new StrategyId(STRATEGY_ID))).thenReturn(Optional.of(healthy));
        when(createSimulationUseCase.execute(any(CreateSimulationCommand.class)))
            .thenReturn(new SimulationId(SIM_ID));

        JsonNode json = objectMapper.readTree(simulationTools.createSimulation(
            Optional.of("Healthy Backtest"),
            STRATEGY_ID.toString(),
            "2025-01-01T00:00:00Z",
            "2025-01-15T00:00:00Z",
            "10000",
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()
        ).firstContent().asText().text());

        assertThat(json.get("message").asText())
            .doesNotContain("risk settings leave too little to trade");
    }

    // --- decided plenty, traded nothing ---

    @Test
    void getSimulation_completedRun_reportsDecisionsNotExecuted() throws Exception {
        when(getSimulationUseCase.execute(any(GetSimulationQuery.class))).thenReturn(completedSimulation());
        when(consensusDecisionRepository.findBySimulationId(any(), any(), any(), any(), any(),
            eq(List.of("BUY", "SELL", "HOLD")), any())).thenReturn(List.of(skipDecision(null)));
        when(consensusDecisionRepository.countNotExecutedBySimulationId(SIM_ID)).thenReturn(5L);

        JsonNode json = objectMapper.readTree(
            simulationTools.getSimulation(SIM_ID.toString()).firstContent().asText().text());

        assertThat(json.get("decisionsNotExecuted").asLong())
            .as("a run that decided plenty and traded nothing must say so on the headline object")
            .isEqualTo(5L);
    }

    @Test
    void listSimulations_withStrategyIdFilter() throws Exception {
        Simulation sim = createTestSimulation();
        when(listSimulationsUseCase.execute(any(ListSimulationsQuery.class)))
            .thenReturn(List.of(sim));

        ToolResponse response = simulationTools.listSimulations(
            Optional.empty(), Optional.empty(), Optional.of(STRATEGY_ID.toString())
        );

        assertThat(response.isError()).isFalse();
        String result = response.firstContent().asText().text();
        JsonNode json = objectMapper.readTree(result);
        assertThat(json.get("count").asInt()).isEqualTo(1);
        assertThat(json.get("items").get(0).get("strategyId").asText()).isEqualTo(STRATEGY_ID.toString());

        verify(listSimulationsUseCase).execute(any(ListSimulationsQuery.class));
    }

    @Test
    void deleteSimulation_returnsStatusMessage() throws Exception {
        doNothing().when(deleteSimulationUseCase).execute(any(DeleteSimulationCommand.class));

        ToolResponse response = simulationTools.deleteSimulation(SIM_ID.toString());

        assertThat(response.isError()).isFalse();
        String result = response.firstContent().asText().text();
        JsonNode json = objectMapper.readTree(result);
        assertThat(json.get("simulationId").asText()).isEqualTo(SIM_ID.toString());
        assertThat(json.get("message").asText()).contains("deleted");

        verify(deleteSimulationUseCase).execute(any(DeleteSimulationCommand.class));
    }

    @Test
    void createSimulation_returnsErrorForInvalidDate() {
        ToolResponse response = simulationTools.createSimulation(
            Optional.of("Bad Date Test"),
            STRATEGY_ID.toString(),
            "not-a-date",
            "2025-06-01T00:00:00Z",
            "10000",
            Optional.empty(),
            Optional.empty(),
            Optional.of("true"),
            Optional.empty()
        );

        assertThat(response.isError()).isTrue();
        String errorText = response.firstContent().asText().text();
        assertThat(errorText).contains("Invalid date format");
    }

    private Simulation createTestSimulation() {
        return new Simulation(
            new SimulationId(SIM_ID),
            "Test Simulation",
            new StrategyId(STRATEGY_ID),
            Instant.parse("2025-01-01T00:00:00Z"),
            Instant.parse("2025-06-01T00:00:00Z"),
            Money.usd(new BigDecimal("10000"))
        );
    }

    // --- A finished run that decided nothing must say so ---

    private Simulation completedSimulation() {
        Simulation sim = createTestSimulation();
        sim.setStatus(com.quorumtrading.model.enums.SimulationStatus.COMPLETED);
        return sim;
    }

    private com.quorumtrading.model.entity.ConsensusDecision skipDecision(String notes) {
        com.quorumtrading.model.entity.ConsensusDecision cd =
            new com.quorumtrading.model.entity.ConsensusDecision(
                new SimulationId(SIM_ID),
                Instant.parse("2025-03-15T10:00:00Z"),
                com.quorumtrading.model.enums.ConsensusMethod.MAJORITY,
                com.quorumtrading.application.orchestration.agents.ConsensusActionParser
                    .parse("{\"action\":\"SKIP\"}"));
        cd.setNotes(notes);
        return cd;
    }

    @Test
    void getSimulation_completedRunThatSkippedEverything_saysTheReturnIsNotAResult() throws Exception {
        // A run whose every evaluation was skipped completes exactly like a healthy one —
        // COMPLETED, progress 100, 0.0000% — so a broken backtest was indistinguishable from
        // one that held throughout, and the evidence lived only on individual decision rows.
        when(getSimulationUseCase.execute(any(GetSimulationQuery.class))).thenReturn(completedSimulation());
        when(consensusDecisionRepository.findBySimulationId(any(), any(), any(), any(), any(),
            eq(List.of("BUY", "SELL", "HOLD")), any())).thenReturn(List.of());
        when(consensusDecisionRepository.findBySimulationId(any(), any(), any(), any(), any(),
            eq(List.of("SKIP")), any()))
            .thenReturn(List.of(skipDecision("Consensus skipped for TM: no price bar on 2026-06-06 (market closed)")));
        when(consensusDecisionRepository.countBySimulationIdAfter(any(), any())).thenReturn(14L);

        JsonNode json = objectMapper.readTree(
            simulationTools.getSimulation(SIM_ID.toString()).firstContent().asText().text());

        assertThat(json.get("noDecisionsReason").asText())
            .contains("All 14 evaluation(s)")
            .contains("the reported return is not a result")
            .contains("no price bar on 2026-06-06")
            .contains("query_consensus_decisions");
    }

    @Test
    void getSimulation_completedRunWithNoEvaluationsAtAll_saysNothingRan() throws Exception {
        when(getSimulationUseCase.execute(any(GetSimulationQuery.class))).thenReturn(completedSimulation());
        when(consensusDecisionRepository.findBySimulationId(any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(List.of());
        when(consensusDecisionRepository.countBySimulationIdAfter(any(), any())).thenReturn(0L);

        JsonNode json = objectMapper.readTree(
            simulationTools.getSimulation(SIM_ID.toString()).firstContent().asText().text());

        assertThat(json.get("noDecisionsReason").asText())
            .contains("without evaluating anything")
            .contains("not a result");
    }

    @Test
    void getSimulation_completedRunThatDecidedSomething_carriesNoWarning() throws Exception {
        // One real decision is enough to prove the run evaluated something. A HOLD-only run is
        // a legitimate result and must not be labelled broken.
        when(getSimulationUseCase.execute(any(GetSimulationQuery.class))).thenReturn(completedSimulation());
        when(consensusDecisionRepository.findBySimulationId(any(), any(), any(), any(), any(),
            eq(List.of("BUY", "SELL", "HOLD")), any())).thenReturn(List.of(skipDecision(null)));

        JsonNode json = objectMapper.readTree(
            simulationTools.getSimulation(SIM_ID.toString()).firstContent().asText().text());

        assertThat(json.get("noDecisionsReason").isNull()).isTrue();
    }

    @Test
    void listSimulations_flagsACompletedRunThatDecidedNothing() throws Exception {
        // The field was added to the detail view only, so the list carried it as a hard null —
        // on exactly the screen where a wall of 0.0000% returns gets scanned. Present-but-null
        // reads as "checked, not flagged", which is worse than omitting it.
        when(listSimulationsUseCase.execute(any(ListSimulationsQuery.class)))
            .thenReturn(List.of(completedSimulation()));
        when(consensusDecisionRepository.findBySimulationId(any(), any(), any(), any(), any(),
            eq(List.of("BUY", "SELL", "HOLD")), any())).thenReturn(List.of());

        JsonNode json = objectMapper.readTree(
            simulationTools.listSimulations(Optional.empty(), Optional.empty(), Optional.empty())
                .firstContent().asText().text());

        assertThat(json.get("items").get(0).get("noDecisionsReason").asText())
            .contains("made no trading decision")
            .contains("not a result")
            .contains("get_simulation");
    }

    @Test
    void listSimulations_leavesAHealthyRunUnflagged() throws Exception {
        when(listSimulationsUseCase.execute(any(ListSimulationsQuery.class)))
            .thenReturn(List.of(completedSimulation()));
        when(consensusDecisionRepository.findBySimulationId(any(), any(), any(), any(), any(),
            eq(List.of("BUY", "SELL", "HOLD")), any())).thenReturn(List.of(skipDecision(null)));

        JsonNode json = objectMapper.readTree(
            simulationTools.listSimulations(Optional.empty(), Optional.empty(), Optional.empty())
                .firstContent().asText().text());

        assertThat(json.get("items").get(0).get("noDecisionsReason").isNull()).isTrue();
    }

    @Test
    void getSimulation_runStillInFlight_carriesNoWarning() throws Exception {
        // An unfinished run legitimately has no decisions yet; warning here would fire on
        // every poll of a perfectly healthy simulation.
        when(getSimulationUseCase.execute(any(GetSimulationQuery.class))).thenReturn(createTestSimulation());

        JsonNode json = objectMapper.readTree(
            simulationTools.getSimulation(SIM_ID.toString()).firstContent().asText().text());

        assertThat(json.get("noDecisionsReason").isNull()).isTrue();
        verifyNoInteractions(consensusDecisionRepository);
    }

    /**
     * A list row used to null out totalInvested and contributions for the same simulation
     * get_simulation reported {@code "10000.0"} and {@code []} for, at the same instant. Both are
     * free here — the contributions are already batch-loaded to compute the return — so the row was
     * telling the caller no capital was invested purely as an artefact of which tool they asked.
     */
    @Test
    void listSimulations_carriesTheInvestedBasisAndContributionsLikeTheDetailView() throws Exception {
        when(listSimulationsUseCase.execute(any(ListSimulationsQuery.class)))
            .thenReturn(List.of(createTestSimulation()));

        JsonNode item = objectMapper.readTree(
            simulationTools.listSimulations(Optional.empty(), Optional.empty(), Optional.empty())
                .firstContent().asText().text()).get("items").get(0);

        assertThat(item.get("totalInvested").isNull())
            .as("the invested basis is free here and must not read as 'nothing invested'").isFalse();
        assertThat(item.get("contributions").isArray())
            .as("an empty array is 'no contributions'; null was 'unknown'").isTrue();
    }

    /**
     * twrPercent/mwrPercent genuinely need every snapshot of a run, so the list omits them rather
     * than sending null — an absent key says "ask get_simulation", where a null said "this run has
     * no time-weighted return".
     */
    @Test
    void listSimulations_omitsRatesItCannotComputeRatherThanSendingNull() throws Exception {
        when(listSimulationsUseCase.execute(any(ListSimulationsQuery.class)))
            .thenReturn(List.of(createTestSimulation()));

        JsonNode item = objectMapper.readTree(
            simulationTools.listSimulations(Optional.empty(), Optional.empty(), Optional.empty())
                .firstContent().asText().text()).get("items").get(0);

        assertThat(item.has("twrPercent")).isFalse();
        assertThat(item.has("mwrPercent")).isFalse();
    }

    /**
     * "LIVE" names both a lifecycle state and real-money trading. Every row must carry the
     * unambiguous answer, because filtering by status is what the live-trading walkthrough
     * prescribes for "find your live runs" and it returns backtests.
     */
    @Test
    void listSimulations_saysPlainlyWhetherRealMoneyIsAtStake() throws Exception {
        when(listSimulationsUseCase.execute(any(ListSimulationsQuery.class)))
            .thenReturn(List.of(createTestSimulation()));

        JsonNode item = objectMapper.readTree(
            simulationTools.listSimulations(Optional.empty(), Optional.empty(), Optional.empty())
                .firstContent().asText().text()).get("items").get(0);

        assertThat(item.get("tradingMode").asText()).isEqualTo("BACKTEST");
        assertThat(item.get("realMoney").asBoolean()).isFalse();
    }
}
