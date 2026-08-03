package com.quorumtrading.application.service;

import com.quorumtrading.application.event.ResearchExecutionCompleted;
import com.quorumtrading.application.event.ResearchExecutionStartedEvent;
import com.quorumtrading.application.port.out.EventPublisher;
import com.quorumtrading.application.port.out.MarketResearchRepository;
import com.quorumtrading.application.service.AuthorizationService;
import com.quorumtrading.application.usecase.budget.RecordLLMUsageUseCase;
import com.quorumtrading.model.entity.MarketResearch;
import com.quorumtrading.model.entity.ResearchExecution;
import com.quorumtrading.model.valueobject.LLMUsage;
import com.quorumtrading.model.valueobject.ResearchStatus;
import com.quorumtrading.model.valueobject.UsageSource;
import com.quorumtrading.model.valueobject.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A research execution on a free engine must still leave a row in the spend ledger.
 *
 * <p>The row used to be written only when cost was positive, so a self-hoster running Ollama got
 * no {@code llm_usage_log} entry at all — and deleting the research then destroyed every trace it
 * had ever run. The surviving rows from the audited install survived only because they had cost
 * money. The ledger is meant to record activity and spend, not spend alone.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ExecuteMarketResearchService — the spend ledger records free runs too")
class ExecuteMarketResearchLedgerTest {

    @Mock MarketResearchRepository repository;
    @Mock MarketResearchOrchestrator orchestrator;
    @Mock EventPublisher<ResearchExecutionStartedEvent> startedPublisher;
    @Mock EventPublisher<ResearchExecutionCompleted> completedPublisher;
    @Mock RecordLLMUsageUseCase recordLLMUsageUseCase;
    @Mock AuthorizationService authorizationService;
    @Mock MarketResearch research;
    @Mock ResearchExecution execution;

    private ExecuteMarketResearchService service;

    private static final UUID RESEARCH_ID = UUID.randomUUID();
    private static final UserId OWNER_ID = new UserId(UUID.randomUUID());

    /** Runs the submitted task on the calling thread, so the async arm is observable. */
    private static final ExecutorService SAME_THREAD = new java.util.concurrent.AbstractExecutorService() {
        @Override public void shutdown() { /* nothing to wind down */ }
        @Override public java.util.List<Runnable> shutdownNow() { return java.util.List.of(); }
        @Override public boolean isShutdown() { return false; }
        @Override public boolean isTerminated() { return false; }
        @Override public boolean awaitTermination(long timeout, java.util.concurrent.TimeUnit unit) { return true; }
        @Override public void execute(Runnable command) { command.run(); }
    };

    @BeforeEach
    void setUp() {
        // No UserBudgetRepository: research model preferences resolve through the shared chain in
        // ResearchModelResolver now, so this service no longer reads budgets for anything. Budget
        // enforcement and ledger writes both go through RecordLLMUsageUseCase.
        service = new ExecuteMarketResearchService(
            repository, orchestrator, startedPublisher, completedPublisher,
            SAME_THREAD, recordLLMUsageUseCase, authorizationService);

        when(repository.findById(RESEARCH_ID)).thenReturn(Optional.of(research));
        when(research.getId()).thenReturn(RESEARCH_ID);
        when(research.getStatus()).thenReturn(ResearchStatus.ACTIVE);
        when(research.getOwnerId()).thenReturn(OWNER_ID);
        // Two-arg: the service passes no caller-supplied model override any more. Handing the
        // user's GLOBAL preference down as an override is what made per-agent research preferences
        // inert, so each research agent now resolves its own model through the shared chain.
        when(orchestrator.executeResearch(any(), any())).thenReturn(execution);
        when(execution.getId()).thenReturn(UUID.randomUUID());
    }

    /** A local engine: real tokens consumed, nothing charged. */
    private void givenFreeEngineUsage() {
        when(execution.getLLMUsage())
            .thenReturn(new LLMUsage("llama3.2", 1200, 300, 1500, BigDecimal.ZERO));
    }

    @Test
    @DisplayName("a free run that consumed tokens still writes a ledger row")
    void freeRunWritesALedgerRow() {
        givenFreeEngineUsage();

        service.execute(RESEARCH_ID, OWNER_ID);

        ArgumentCaptor<BigDecimal> billed = ArgumentCaptor.forClass(BigDecimal.class);
        ArgumentCaptor<BigDecimal> raw = ArgumentCaptor.forClass(BigDecimal.class);
        verify(recordLLMUsageUseCase).recordUsage(
            eq(OWNER_ID), billed.capture(), raw.capture(), eq(1500), eq(UsageSource.RESEARCH));

        // Compared by value: a free call bills 0E-8, which is still zero.
        assertThat(billed.getValue()).isEqualByComparingTo("0");
        assertThat(raw.getValue()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("a run that consumed nothing writes no row")
    void runWithoutTokensWritesNothing() {
        when(execution.getLLMUsage()).thenReturn(LLMUsage.ZERO);

        service.execute(RESEARCH_ID, OWNER_ID);

        // Tokens are the evidence of activity. No tokens, nothing to record.
        verify(recordLLMUsageUseCase, never())
            .recordUsage(any(), any(), any(), anyInt(), any());
    }

    @Test
    @DisplayName("a failed ledger write does not fail the research execution")
    void failedLedgerWriteDoesNotFailTheExecution() {
        givenFreeEngineUsage();
        doThrow(new IllegalStateException("ledger unavailable"))
            .when(recordLLMUsageUseCase).recordUsage(any(), any(), any(), anyInt(), any());

        service.execute(RESEARCH_ID, OWNER_ID);

        // The research itself completed; bookkeeping must not undo it.
        verify(research).markAsExecuted();
        verify(repository).save(research);
    }
}
