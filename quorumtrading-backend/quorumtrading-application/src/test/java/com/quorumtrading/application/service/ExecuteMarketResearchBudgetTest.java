package com.quorumtrading.application.service;

import com.quorumtrading.application.event.ResearchExecutionCompleted;
import com.quorumtrading.application.event.ResearchExecutionStartedEvent;
import com.quorumtrading.application.exception.BudgetDepletedException;
import com.quorumtrading.application.exception.DailySpendLimitExceededException;
import com.quorumtrading.application.port.out.EventPublisher;
import com.quorumtrading.application.port.out.MarketResearchRepository;
import com.quorumtrading.application.usecase.budget.RecordLLMUsageUseCase;
import com.quorumtrading.model.entity.MarketResearch;
import com.quorumtrading.model.valueobject.ExternalPauseReason;
import com.quorumtrading.model.valueobject.ResearchStatus;
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
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * A research run refused for budget must change the research, not just fail.
 *
 * <p>Production evidence: 8 research items owned by 3 users with a depleted lifetime budget threw
 * {@link BudgetDepletedException} every hour for 30 days (5,392 ERRORs). The refusal left the
 * research ACTIVE with its next run still in the past, so every scheduler tick picked it up again.
 * Per ADR 0004 the allowed action is a system pause with a named reason; per ADR 0003 a daily cap
 * refreshes at 00:00 UTC, so that refusal defers the run to the next UTC day instead.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ExecuteMarketResearchService — budget refusals change the research's state")
class ExecuteMarketResearchBudgetTest {

    @Mock MarketResearchRepository repository;
    @Mock MarketResearchOrchestrator orchestrator;
    @Mock EventPublisher<ResearchExecutionStartedEvent> startedPublisher;
    @Mock EventPublisher<ResearchExecutionCompleted> completedPublisher;
    @Mock ExecutorService executorService;
    @Mock RecordLLMUsageUseCase recordLLMUsageUseCase;
    @Mock AuthorizationService authorizationService;
    @Mock MarketResearch research;

    private ExecuteMarketResearchService service;

    private static final UUID RESEARCH_ID = UUID.randomUUID();
    private static final UserId OWNER_ID = new UserId(UUID.randomUUID());

    @BeforeEach
    void setUp() {
        service = new ExecuteMarketResearchService(
            repository, orchestrator, startedPublisher, completedPublisher,
            executorService, recordLLMUsageUseCase, authorizationService);

        when(repository.findById(RESEARCH_ID)).thenReturn(Optional.of(research));
        when(research.getId()).thenReturn(RESEARCH_ID);
        when(research.getStatus()).thenReturn(ResearchStatus.ACTIVE);
        when(research.getOwnerId()).thenReturn(OWNER_ID);
    }

    @Test
    @DisplayName("a depleted budget pauses the research with reason BUDGET_DEPLETED and rethrows")
    void depletedBudgetPausesTheResearch() {
        doThrow(new BudgetDepletedException(OWNER_ID))
            .when(recordLLMUsageUseCase).requireAvailableBudget(OWNER_ID);

        assertThatThrownBy(() -> service.execute(RESEARCH_ID, OWNER_ID))
            .isInstanceOf(BudgetDepletedException.class);

        verify(research).pauseForExternalReason(ExternalPauseReason.BUDGET_DEPLETED);
        verify(repository).save(research);
        verifyNoInteractions(orchestrator, startedPublisher, executorService);
    }

    @Test
    @DisplayName("a research already paused for budget is not re-paused or re-saved")
    void alreadyPausedResearchIsLeftAlone() {
        when(research.isPausedForExternalReason()).thenReturn(true);
        doThrow(new BudgetDepletedException(OWNER_ID))
            .when(recordLLMUsageUseCase).requireAvailableBudget(OWNER_ID);

        assertThatThrownBy(() -> service.execute(RESEARCH_ID, OWNER_ID))
            .isInstanceOf(BudgetDepletedException.class);

        verify(research, never()).pauseForExternalReason(any());
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("a spent daily cap defers the next run to 00:00 UTC tomorrow and does not pause")
    void dailyCapDefersToTheNextUtcDay() {
        doThrow(new DailySpendLimitExceededException(OWNER_ID, BigDecimal.ONE, BigDecimal.TEN))
            .when(recordLLMUsageUseCase).requireAvailableBudget(OWNER_ID);
        Instant before = nextUtcMidnight();

        assertThatThrownBy(() -> service.execute(RESEARCH_ID, OWNER_ID))
            .isInstanceOf(DailySpendLimitExceededException.class);

        ArgumentCaptor<Instant> deferredTo = ArgumentCaptor.forClass(Instant.class);
        verify(research).deferNextRunUntil(deferredTo.capture());
        // Two reads bracket the call, so a run straddling midnight still matches one of them.
        assertThat(deferredTo.getValue()).isIn(before, nextUtcMidnight());
        verify(research, never()).pauseForExternalReason(any());
        verify(repository).save(research);
        verifyNoInteractions(orchestrator, executorService);
    }

    @Test
    @DisplayName("with budget available the run proceeds and nothing is paused")
    void availableBudgetRunsNormally() {
        UUID executionId = service.execute(RESEARCH_ID, OWNER_ID);

        assertThat(executionId).isNotNull();
        verify(research, never()).pauseForExternalReason(any());
        verify(research, never()).deferNextRunUntil(any());
        verify(executorService).submit(any(Runnable.class));
    }

    private static Instant nextUtcMidnight() {
        return LocalDate.now(ZoneOffset.UTC).plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
    }
}
