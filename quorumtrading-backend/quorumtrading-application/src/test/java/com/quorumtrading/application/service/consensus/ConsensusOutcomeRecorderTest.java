package com.quorumtrading.application.service.consensus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.quorumtrading.application.event.LLMDecisionCreatedEvent;
import com.quorumtrading.application.port.out.ConsensusDecisionRepository;
import com.quorumtrading.application.port.out.EventPublisher;
import com.quorumtrading.application.port.out.LLMDecisionRepository;
import com.quorumtrading.application.usecase.budget.RecordLLMUsageUseCase;
import com.quorumtrading.model.entity.ConsensusDecision;
import com.quorumtrading.model.entity.LLMDecision;
import com.quorumtrading.model.entity.Simulation;
import com.quorumtrading.model.enums.ConsensusMethod;
import com.quorumtrading.model.enums.InvestmentType;
import com.quorumtrading.model.orchestration.StrategyConfig;
import com.quorumtrading.model.valueobject.ConsensusAction;
import com.quorumtrading.model.valueobject.LLMUsage;
import com.quorumtrading.model.valueobject.SimulationId;
import com.quorumtrading.model.valueobject.UsageSource;
import com.quorumtrading.model.valueobject.UserId;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Regression guard for the crypto-classification bug: {@link ConsensusOutcomeRecorder} only
 * receives the ticker as a String, so it must derive the asset class via
 * {@code InvestmentType.fromTicker(ticker)} rather than hardcoding STOCK. A BTC decision must be
 * recorded as CRYPTO (the wrong value flowed into the persisted decision and the traded position /
 * portfolio snapshot). No such test existed, which is why the bug shipped.
 */
@ExtendWith(MockitoExtension.class)
class ConsensusOutcomeRecorderTest {

    @Mock LLMDecisionRepository llmDecisionRepository;
    @Mock ConsensusDecisionRepository consensusDecisionRepository;
    @Mock EventPublisher<LLMDecisionCreatedEvent> llmDecisionEventPublisher;
    @Mock RecordLLMUsageUseCase recordLLMUsageUseCase;
    @Mock Simulation simulation;
    @Mock StrategyConfig strategyConfig;

    private ConsensusOutcomeRecorder recorder;

    private static final UserId OWNER_ID = new UserId(UUID.randomUUID());

    @BeforeEach
    void setUp() {
        recorder = new ConsensusOutcomeRecorder(
            llmDecisionRepository, consensusDecisionRepository,
            llmDecisionEventPublisher, recordLLMUsageUseCase);
        when(simulation.getSimulationId()).thenReturn(new SimulationId(UUID.randomUUID()));
        when(strategyConfig.getConsensusMethod()).thenReturn(ConsensusMethod.MAJORITY);
    }

    @Test
    void recordsCryptoTypeForBtc() {
        recorder.saveTickerSkip(simulation, strategyConfig, "BTC",
            Instant.parse("2026-02-07T00:00:00Z"), "cooldown");

        ConsensusDecision saved = captureSavedDecision();
        assertThat(saved.getInvestmentType()).isEqualTo(InvestmentType.CRYPTO);
        assertThat(saved.getSymbol().type()).isEqualTo(InvestmentType.CRYPTO);
    }

    @Test
    void recordsStockTypeForAapl() {
        recorder.saveTickerSkip(simulation, strategyConfig, "AAPL",
            Instant.parse("2026-02-07T00:00:00Z"), "cooldown");

        ConsensusDecision saved = captureSavedDecision();
        assertThat(saved.getInvestmentType()).isEqualTo(InvestmentType.STOCK);
        assertThat(saved.getSymbol().type()).isEqualTo(InvestmentType.STOCK);
    }

    // --- The participation note must survive the all-failed branch ---

    @Test
    void namesEachNonVotingAgentAndWhy() {
        // The defect: the caller computed this note, handed it to the majority and orchestrator
        // branches, and DROPPED it here — so the one case where the user most needs to know why
        // nothing happened was the only case that told them nothing. "All specialized agents
        // failed" named no agent and no cause, and an audit had to read container logs to
        // discover the selected LLM engine was simply unreachable.
        String note = " [SENTIMENT — no decision returned: no local model server responded]";

        recorder.createSkipConsensusForTicker("AAPL", simulation, java.util.List.of(),
            strategyConfig, Instant.parse("2026-02-07T00:00:00Z"), note);

        ConsensusDecision saved = captureSavedDecision();
        assertThat(saved.getNotes())
            .contains("no local model server responded")
            .contains("SENTIMENT");
        assertThat(saved.getFinalAction())
            .as("the reason must also reach the action payload, which is what the timeline renders")
            .contains("no local model server responded");
    }

    @Test
    void doesNotCallAnAbstentionAFailure() {
        // Every agent abstaining for lack of news is an ordinary outcome on a crypto backtest,
        // not a malfunction, and calling it "failed" sent users hunting for a broken system.
        recorder.createSkipConsensusForTicker("BTC", simulation, java.util.List.of(),
            strategyConfig, Instant.parse("2026-02-07T00:00:00Z"),
            " [SENTIMENT abstained — 0 of 3 required news events stored]");

        ConsensusDecision saved = captureSavedDecision();
        assertThat(saved.getNotes())
            .contains("No agent voted")
            .doesNotContain("failed");
    }

    @Test
    void stillReadsSensiblyWithNoNoteAtAll() {
        recorder.createSkipConsensusForTicker("AAPL", simulation, java.util.List.of(),
            strategyConfig, Instant.parse("2026-02-07T00:00:00Z"), null);

        ConsensusDecision saved = captureSavedDecision();
        assertThat(saved.getNotes()).contains("No agent returned a usable opinion for AAPL");
    }

    private ConsensusDecision captureSavedDecision() {
        ArgumentCaptor<ConsensusDecision> captor = ArgumentCaptor.forClass(ConsensusDecision.class);
        verify(consensusDecisionRepository).save(captor.capture());
        return captor.getValue();
    }

    // --- The spend ledger records activity, not just spend ---

    /**
     * Drives {@code recordConsensusOutcome} with one agent decision that consumed tokens at no
     * cost — a local engine. The recorder used to write a ledger row only when cost was positive,
     * so a whole free-engine simulation left no trace in {@code llm_usage_log} at all and its
     * activity was unreconstructable once the run was tidied up.
     */
    private ConsensusDecision recordFreeEngineConsensus() {
        LLMDecision freeAgentDecision = org.mockito.Mockito.mock(LLMDecision.class);
        when(freeAgentDecision.getLLMUsage())
            .thenReturn(new LLMUsage("llama3.2", 1200, 300, 1500, java.math.BigDecimal.ZERO));

        when(consensusDecisionRepository.existsBySimulationIdAndSymbolAndTimestampWindow(
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt()))
            .thenReturn(false);
        when(consensusDecisionRepository.save(org.mockito.ArgumentMatchers.any(ConsensusDecision.class)))
            .thenAnswer(call -> call.getArgument(0));

        ConsensusOutcome outcome = new ConsensusOutcome(
            ConsensusAction.unknown("HOLD", "HOLD"), null, null,
            java.util.List.of(freeAgentDecision), "free engine");

        return recorder.recordConsensusOutcome("AAPL", outcome, simulation, strategyConfig,
            Instant.parse("2026-02-07T00:00:00Z"), OWNER_ID);
    }

    @Test
    void writesALedgerRowForAFreeEngineRunThatCostNothing() {
        recordFreeEngineConsensus();

        ArgumentCaptor<java.math.BigDecimal> billed = ArgumentCaptor.forClass(java.math.BigDecimal.class);
        ArgumentCaptor<java.math.BigDecimal> raw = ArgumentCaptor.forClass(java.math.BigDecimal.class);
        verify(recordLLMUsageUseCase).recordUsage(
            org.mockito.ArgumentMatchers.eq(OWNER_ID), billed.capture(), raw.capture(),
            org.mockito.ArgumentMatchers.eq(1500),
            org.mockito.ArgumentMatchers.eq(UsageSource.SIMULATION));

        // Compared by value, not by scale: a free call bills 0E-8, which is still zero.
        assertThat(billed.getValue()).isEqualByComparingTo("0");
        assertThat(raw.getValue()).isEqualByComparingTo("0");
    }

    @Test
    void aFailedLedgerWriteDoesNotLoseTheConsensusDecision() {
        org.mockito.Mockito.doThrow(new IllegalStateException("ledger unavailable"))
            .when(recordLLMUsageUseCase).recordUsage(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.any());

        // The trade decision is the point of this method; a bookkeeping failure must not discard it.
        assertThat(recordFreeEngineConsensus()).isNotNull();
    }
}
