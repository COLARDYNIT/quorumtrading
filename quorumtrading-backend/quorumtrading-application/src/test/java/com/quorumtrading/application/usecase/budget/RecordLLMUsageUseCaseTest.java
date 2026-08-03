package com.quorumtrading.application.usecase.budget;

import com.quorumtrading.application.exception.BudgetDepletedException;
import com.quorumtrading.application.exception.DailySpendLimitExceededException;
import com.quorumtrading.application.port.out.EventPublisher;
import com.quorumtrading.application.port.out.LLMUsageRepository;
import com.quorumtrading.application.port.out.TransactionRunner;
import com.quorumtrading.application.port.out.UserBudgetRepository;
import com.quorumtrading.model.entity.UserBudget;
import com.quorumtrading.model.valueobject.BudgetLimit;
import com.quorumtrading.model.valueobject.UsageSource;
import com.quorumtrading.model.valueobject.UserId;
import com.quorumtrading.model.service.PlatformMarkup;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecordLLMUsageUseCaseTest {

    @Mock
    private UserBudgetRepository budgetRepository;

    @Mock
    private EventPublisher warningPublisher;

    @Mock
    private EventPublisher depletedPublisher;

    @Mock
    private LLMUsageRepository llmUsageRepository;

    private RecordLLMUsageUseCase useCase;

    private static final UserId USER_ID = new UserId(UUID.randomUUID());

    /**
     * Runs the unit of work inline, exactly as a real transaction would when nothing throws — and,
     * like a real one, lets an exception out so the caller sees a failed (rolled-back) boundary.
     */
    private static final TransactionRunner INLINE_TRANSACTION = new TransactionRunner() {
        @Override
        public <T> T inTransaction(java.util.function.Supplier<T> work) {
            return work.get();
        }
    };

    @AfterEach
    void resetMarkup() {
        PlatformMarkup.reset();
    }

    @BeforeEach
    void setUp() {
        // Tests in this class assert billed figures at the historical 1.50 hosted rate
        PlatformMarkup.configure(new java.math.BigDecimal("1.50"));
        useCase = new RecordLLMUsageUseCase(
            budgetRepository, warningPublisher, depletedPublisher, llmUsageRepository, INLINE_TRANSACTION);
    }

    @Test
    void recordUsage_chat_shouldLogUsageAndUpdateBudget() {
        UserBudget budget = UserBudget.createUnlimited(USER_ID);
        when(budgetRepository.findByUserId(USER_ID)).thenReturn(Optional.of(budget));
        when(budgetRepository.save(any())).thenReturn(budget);

        BigDecimal billedCost = new BigDecimal("0.015");
        BigDecimal rawCost = new BigDecimal("0.01");
        int totalTokens = 500;

        UserBudget result = useCase.recordUsage(USER_ID, billedCost, rawCost, totalTokens, UsageSource.CHAT);

        assertThat(result).isNotNull();
        verify(llmUsageRepository).logUsage(USER_ID, UsageSource.CHAT, rawCost, totalTokens);
        verify(budgetRepository).save(any());
    }

    @Test
    void recordUsage_simulation_shouldLogUsageAndUpdateBudget() {
        UserBudget budget = UserBudget.createUnlimited(USER_ID);
        when(budgetRepository.findByUserId(USER_ID)).thenReturn(Optional.of(budget));
        when(budgetRepository.save(any())).thenReturn(budget);

        BigDecimal billedCost = new BigDecimal("0.045");
        BigDecimal rawCost = new BigDecimal("0.03");
        int totalTokens = 1500;

        UserBudget result = useCase.recordUsage(USER_ID, billedCost, rawCost, totalTokens, UsageSource.SIMULATION);

        assertThat(result).isNotNull();
        verify(llmUsageRepository).logUsage(USER_ID, UsageSource.SIMULATION, rawCost, totalTokens);
        verify(budgetRepository).save(any());
    }

    @Test
    void recordUsage_research_shouldLogUsageAndUpdateBudget() {
        UserBudget budget = UserBudget.createUnlimited(USER_ID);
        when(budgetRepository.findByUserId(USER_ID)).thenReturn(Optional.of(budget));
        when(budgetRepository.save(any())).thenReturn(budget);

        BigDecimal billedCost = new BigDecimal("0.030");
        BigDecimal rawCost = new BigDecimal("0.02");
        int totalTokens = 1000;

        UserBudget result = useCase.recordUsage(USER_ID, billedCost, rawCost, totalTokens, UsageSource.RESEARCH);

        assertThat(result).isNotNull();
        verify(llmUsageRepository).logUsage(USER_ID, UsageSource.RESEARCH, rawCost, totalTokens);
        verify(budgetRepository).save(any());
    }

    @Test
    void hasBudgetAvailable_shouldReturnTrueForUnlimitedBudget() {
        UserBudget budget = UserBudget.createUnlimited(USER_ID);
        when(budgetRepository.findByUserId(USER_ID)).thenReturn(Optional.of(budget));

        assertThat(useCase.hasBudgetAvailable(USER_ID)).isTrue();
    }

    @Test
    void hasBudgetAvailable_shouldReturnTrueWhenNoBudgetConfigured() {
        when(budgetRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

        assertThat(useCase.hasBudgetAvailable(USER_ID)).isTrue();
    }

    @Test
    void requireAvailableBudget_shouldThrowWhenBudgetDepleted() {
        UserBudget budget = UserBudget.createWithLimit(USER_ID, BudgetLimit.usd(10.00));
        budget.recordUsage(new BigDecimal("10.00"));
        when(budgetRepository.findByUserId(USER_ID)).thenReturn(Optional.of(budget));

        assertThatThrownBy(() -> useCase.requireAvailableBudget(USER_ID))
            .isInstanceOf(BudgetDepletedException.class);
    }

    @Test
    void requireAvailableBudget_shouldThrowWhenDailyLimitExceeded() {
        UserBudget budget = UserBudget.createUnlimited(USER_ID);
        budget.setDailySpendLimit(new BigDecimal("5.00"));
        when(budgetRepository.findByUserId(USER_ID)).thenReturn(Optional.of(budget));

        // raw cost of $4.00 * 1.50 markup = $6.00 billed, exceeds $5 daily limit
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        when(llmUsageRepository.getTotalRawCost(eq(USER_ID), eq(today), eq(today)))
            .thenReturn(new BigDecimal("4.00"));

        assertThatThrownBy(() -> useCase.requireAvailableBudget(USER_ID))
            .isInstanceOf(DailySpendLimitExceededException.class);
    }

    @Test
    void requireAvailableBudget_shouldPassWhenDailyLimitNotReached() {
        UserBudget budget = UserBudget.createUnlimited(USER_ID);
        budget.setDailySpendLimit(new BigDecimal("5.00"));
        when(budgetRepository.findByUserId(USER_ID)).thenReturn(Optional.of(budget));

        // raw cost of $1.00 * 1.50 markup = $1.50 billed, under $5 daily limit
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        when(llmUsageRepository.getTotalRawCost(eq(USER_ID), eq(today), eq(today)))
            .thenReturn(new BigDecimal("1.00"));

        useCase.requireAvailableBudget(USER_ID); // should not throw
    }

    @Test
    void hasBudgetAvailable_shouldReturnFalseWhenDailyLimitExceeded() {
        UserBudget budget = UserBudget.createUnlimited(USER_ID);
        budget.setDailySpendLimit(new BigDecimal("5.00"));
        when(budgetRepository.findByUserId(USER_ID)).thenReturn(Optional.of(budget));

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        when(llmUsageRepository.getTotalRawCost(eq(USER_ID), eq(today), eq(today)))
            .thenReturn(new BigDecimal("4.00")); // $6.00 billed > $5.00 limit

        assertThat(useCase.hasBudgetAvailable(USER_ID)).isFalse();
    }

    // ------------------------------------------------------------------ ledger atomicity
    // The ledger row and the budget update describe one event and must commit together.

    @Test
    void recordUsage_shouldWriteLedgerAndBudgetInsideOneTransaction() {
        UserBudget budget = UserBudget.createUnlimited(USER_ID);
        when(budgetRepository.findByUserId(USER_ID)).thenReturn(Optional.of(budget));
        when(budgetRepository.save(any())).thenReturn(budget);

        AtomicBoolean insideBoundary = new AtomicBoolean(false);
        AtomicBoolean ledgerWrittenInside = new AtomicBoolean(false);
        AtomicBoolean budgetSavedInside = new AtomicBoolean(false);

        doAnswer(call -> {
            ledgerWrittenInside.set(insideBoundary.get());
            return null;
        }).when(llmUsageRepository).logUsage(any(), any(), any(), anyInt());
        when(budgetRepository.save(any())).thenAnswer(call -> {
            budgetSavedInside.set(insideBoundary.get());
            return budget;
        });

        TransactionRunner recordingRunner = new TransactionRunner() {
            @Override
            public <T> T inTransaction(java.util.function.Supplier<T> work) {
                insideBoundary.set(true);
                try {
                    return work.get();
                } finally {
                    insideBoundary.set(false);
                }
            }
        };
        RecordLLMUsageUseCase subject = new RecordLLMUsageUseCase(
            budgetRepository, warningPublisher, depletedPublisher, llmUsageRepository, recordingRunner);

        subject.recordUsage(USER_ID, new BigDecimal("0.015"), new BigDecimal("0.01"), 500, UsageSource.CHAT);

        assertThat(ledgerWrittenInside).as("the ledger row is written inside the boundary").isTrue();
        assertThat(budgetSavedInside).as("the budget is saved inside the same boundary").isTrue();
    }

    @Test
    void recordUsage_whenLedgerWriteFails_shouldNotFailTheOriginatingCall() {
        UserBudget budget = UserBudget.createUnlimited(USER_ID);
        when(budgetRepository.findByUserId(USER_ID)).thenReturn(Optional.of(budget));
        doThrow(new IllegalStateException("ledger unavailable"))
            .when(llmUsageRepository).logUsage(any(), any(), any(), anyInt());

        // The model already answered and the provider already charged: failing here would lose
        // the answer and still incur the cost.
        UserBudget result = useCase.recordUsage(
            USER_ID, new BigDecimal("0.015"), new BigDecimal("0.01"), 500, UsageSource.CHAT);

        assertThat(result).as("a budget is still returned rather than an exception").isNotNull();
    }

    @Test
    void recordUsage_whenLedgerWriteFails_shouldNotCommitTheBudgetUpdate() {
        UserBudget budget = UserBudget.createWithLimit(USER_ID, BudgetLimit.usd(new BigDecimal("2.00")));
        when(budgetRepository.findByUserId(USER_ID)).thenReturn(Optional.of(budget));
        doThrow(new IllegalStateException("ledger unavailable"))
            .when(llmUsageRepository).logUsage(any(), any(), any(), anyInt());

        useCase.recordUsage(USER_ID, new BigDecimal("0.015"), new BigDecimal("0.01"), 500, UsageSource.CHAT);

        // The whole point of the shared boundary: no ledger row, so no charge either. A budget
        // saved here would be a user billed for a call nothing records.
        verify(budgetRepository, never()).save(any());
    }

    @Test
    void recordUsage_whenEvenTheRecoveryReadFails_shouldStillReturnABudget() {
        // Database wholly unavailable: the ledger write fails and so does the re-read that would
        // report the budget's true state. The originating LLM call still must not blow up.
        TransactionRunner alwaysFailing = new TransactionRunner() {
            @Override
            public <T> T inTransaction(java.util.function.Supplier<T> work) {
                throw new IllegalStateException("database unavailable");
            }
        };
        RecordLLMUsageUseCase subject = new RecordLLMUsageUseCase(
            budgetRepository, warningPublisher, depletedPublisher, llmUsageRepository, alwaysFailing);

        UserBudget result = subject.recordUsage(
            USER_ID, new BigDecimal("0.015"), new BigDecimal("0.01"), 500, UsageSource.CHAT);

        assertThat(result).isNotNull();
        verify(budgetRepository, never()).save(any());
    }

    @Test
    void recordUsage_whenLedgerWriteFails_shouldNotPublishBudgetEvents() {
        UserBudget budget = UserBudget.createWithLimit(USER_ID, BudgetLimit.usd(new BigDecimal("1.00")));
        when(budgetRepository.findByUserId(USER_ID)).thenReturn(Optional.of(budget));
        doThrow(new IllegalStateException("ledger unavailable"))
            .when(llmUsageRepository).logUsage(any(), any(), any(), anyInt());

        useCase.recordUsage(USER_ID, new BigDecimal("5.00"), new BigDecimal("3.33"), 500, UsageSource.CHAT);

        // A "budget depleted" notification for spend that was rolled back would be a false alarm.
        verifyNoInteractions(warningPublisher);
        verifyNoInteractions(depletedPublisher);
    }
}
