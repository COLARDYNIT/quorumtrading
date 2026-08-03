package com.quorumtrading.application.usecase;

import com.quorumtrading.application.port.out.LLMUsageRepository;
import com.quorumtrading.application.port.out.UserBudgetRepository;
import com.quorumtrading.application.usecase.dto.UserBudgetDto;
import com.quorumtrading.model.entity.UserBudget;
import com.quorumtrading.model.valueobject.UserId;
import com.quorumtrading.model.service.PlatformMarkup;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetUserBudgetUseCaseTest {

    @Mock private UserBudgetRepository userBudgetRepository;
    @Mock private LLMUsageRepository llmUsageRepository;

    private GetUserBudgetUseCase useCase;
    private final UserId userId = new UserId(UUID.randomUUID());

    @AfterEach
    void resetMarkup() {
        PlatformMarkup.reset();
    }

    @BeforeEach
    void setUp() {
        // Tests in this class assert billed figures at the historical 1.50 hosted rate
        PlatformMarkup.configure(new java.math.BigDecimal("1.50"));
        useCase = new GetUserBudgetUseCase(userBudgetRepository, llmUsageRepository);
    }

    /**
     * The spend day runs 00:00–23:59:59 UTC (ADR-0003), not the host's day. Bracketing the call
     * keeps the assertion honest if it happens to straddle UTC midnight, while still failing if the
     * use case ever goes back to reading {@code LocalDate.now()} in a zone that is not UTC.
     */
    @Test
    @DisplayName("today's spend window is the UTC day, not the host's day")
    void todayWindowIsTheUtcDay() {
        when(userBudgetRepository.findByUserId(userId))
            .thenReturn(Optional.of(UserBudget.createUnlimited(userId)));
        when(llmUsageRepository.getTotalRawCost(any(), any(), any())).thenReturn(BigDecimal.ZERO);

        LocalDate utcBefore = LocalDate.now(ZoneOffset.UTC);
        useCase.execute(userId);
        LocalDate utcAfter = LocalDate.now(ZoneOffset.UTC);

        ArgumentCaptor<LocalDate> from = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<LocalDate> to = ArgumentCaptor.forClass(LocalDate.class);
        verify(llmUsageRepository, org.mockito.Mockito.atLeastOnce())
            .getTotalRawCost(eq(userId), from.capture(), to.capture());

        LocalDate today = to.getAllValues().get(0);
        assertThat(today).isIn(utcBefore, utcAfter);
        assertThat(from.getAllValues().get(0))
            .as("today's window is a single UTC day")
            .isEqualTo(today);
        assertThat(from.getAllValues().get(1))
            .as("the weekly window covers seven UTC days ending today — inclusive at both ends, "
                + "so it starts six days back, and the page divides this total by 7")
            .isEqualTo(today.minusDays(6));
    }

    @Test
    @DisplayName("a user with no budget row gets an unlimited one, saved")
    void createsUnlimitedBudgetForNewUser() {
        when(userBudgetRepository.findByUserId(userId)).thenReturn(Optional.empty());
        when(userBudgetRepository.save(any(UserBudget.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(llmUsageRepository.getTotalRawCost(any(), any(), any())).thenReturn(BigDecimal.ZERO);

        UserBudgetDto dto = useCase.execute(userId);

        assertThat(dto.isUnlimited()).isTrue();
        verify(userBudgetRepository).save(any(UserBudget.class));
    }

    @Test
    @DisplayName("raw cost is marked up, and a null raw cost reads as zero rather than blowing up")
    void appliesMarkupAndToleratesNullRawCost() {
        when(userBudgetRepository.findByUserId(userId))
            .thenReturn(Optional.of(UserBudget.createUnlimited(userId)));
        when(llmUsageRepository.getTotalRawCost(any(), any(), any()))
            .thenReturn(new BigDecimal("2.00"), null);

        UserBudgetDto dto = useCase.execute(userId);

        assertThat(dto.todaySpend())
            .as("$2.00 raw at the 1.50 platform markup")
            .isEqualByComparingTo("3.00");
        assertThat(dto.weeklySpend())
            .as("no usage rows yet must not surface as a null spend")
            .isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("an existing budget is used as-is and never re-saved")
    void existingBudgetIsNotOverwritten() {
        UserBudget existing = UserBudget.createUnlimited(userId);
        when(userBudgetRepository.findByUserId(userId)).thenReturn(Optional.of(existing));
        when(llmUsageRepository.getTotalRawCost(any(), any(), any())).thenReturn(BigDecimal.ZERO);

        UserBudgetDto dto = useCase.execute(userId);

        assertThat(dto.userId()).isEqualTo(userId.value().toString());
        verify(userBudgetRepository, org.mockito.Mockito.never()).save(any());
    }
}
