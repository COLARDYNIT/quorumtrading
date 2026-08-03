package com.quorumtrading.bootstrap.scheduler;

import com.quorumtrading.application.port.out.InvestmentPlanRepository;
import com.quorumtrading.application.usecase.command.ExecuteInvestmentPlanCommand;
import com.quorumtrading.application.usecase.investmentplan.ExecuteInvestmentPlanUseCase;
import com.quorumtrading.model.entity.InvestmentPlan;
import com.quorumtrading.model.enums.BudgetMode;
import com.quorumtrading.model.enums.PickerPolicy;
import com.quorumtrading.model.valueobject.BrokerAccountId;
import com.quorumtrading.model.valueobject.InvestmentPlanId;
import com.quorumtrading.model.valueobject.InvestmentPlanPreferences;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

class DcaExecutionSchedulerTest {

    private InvestmentPlanRepository planRepository;
    private ExecuteInvestmentPlanUseCase executeUseCase;

    @BeforeEach
    void setUp() {
        planRepository = mock(InvestmentPlanRepository.class);
        executeUseCase = mock(ExecuteInvestmentPlanUseCase.class);
    }

    private DcaExecutionScheduler scheduler(boolean enabled) {
        return new DcaExecutionScheduler(planRepository, executeUseCase, enabled, 100);
    }

    private InvestmentPlan plan() {
        return InvestmentPlan.create(
                InvestmentPlanId.generate(),
                new UserId(UUID.randomUUID()),
                new BrokerAccountId(UUID.randomUUID()),
                BudgetMode.FIXED_AMOUNT,
                Money.usd(new BigDecimal("200")),
                1,
                PickerPolicy.AI_PICK,
                InvestmentPlanPreferences.EMPTY,
                Instant.now());
    }

    @Test
    void doesNotQueryWhenDisabled() {
        scheduler(false).runDuePlans();
        verifyNoInteractions(planRepository, executeUseCase);
    }

    @Test
    void noOpWhenNothingDue() {
        when(planRepository.findDueForExecution(any(), anyInt())).thenReturn(List.of());
        scheduler(true).runDuePlans();
        verify(planRepository).findDueForExecution(any(), anyInt());
        verifyNoInteractions(executeUseCase);
    }

    @Test
    void firesEachDuePlan() {
        InvestmentPlan a = plan();
        InvestmentPlan b = plan();
        when(planRepository.findDueForExecution(any(), anyInt())).thenReturn(List.of(a, b));

        scheduler(true).runDuePlans();

        verify(executeUseCase).execute(argThat(cmd -> cmd.planId().equals(a.getId())));
        verify(executeUseCase).execute(argThat(cmd -> cmd.planId().equals(b.getId())));
    }

    @Test
    void oneBadPlanDoesNotAbortBatch() {
        InvestmentPlan a = plan();
        InvestmentPlan b = plan();
        when(planRepository.findDueForExecution(any(), anyInt())).thenReturn(List.of(a, b));
        when(executeUseCase.execute(argThat(cmd -> cmd != null && cmd.planId().equals(a.getId()))))
                .thenThrow(new RuntimeException("simulated infra failure"));

        // Should not throw.
        scheduler(true).runDuePlans();

        // Both plans were attempted, even though the first threw.
        verify(executeUseCase).execute(argThat(cmd -> cmd.planId().equals(a.getId())));
        verify(executeUseCase).execute(argThat(cmd -> cmd.planId().equals(b.getId())));
    }

    @Test
    void passesAdvanceScheduleTrueAndOwningUser() {
        InvestmentPlan a = plan();
        when(planRepository.findDueForExecution(any(), anyInt())).thenReturn(List.of(a));

        scheduler(true).runDuePlans();

        verify(executeUseCase).execute(argThat((ExecuteInvestmentPlanCommand cmd) ->
                cmd != null
                && cmd.planId().equals(a.getId())
                && cmd.userId().equals(a.getUserId())
                && cmd.advanceSchedule()));
    }
}
