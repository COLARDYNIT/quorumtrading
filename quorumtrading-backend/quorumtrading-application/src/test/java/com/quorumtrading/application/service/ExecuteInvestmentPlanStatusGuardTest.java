package com.quorumtrading.application.service;

import com.quorumtrading.application.port.out.BrokerAccountRepository;
import com.quorumtrading.application.port.out.InvestmentAllocationRepository;
import com.quorumtrading.application.port.out.InvestmentPlanRepository;
import com.quorumtrading.application.port.out.SimulationRepository;
import com.quorumtrading.application.port.out.StrategyPickerPort;
import com.quorumtrading.application.port.out.StrategyRepository;
import com.quorumtrading.application.usecase.CreateSimulationUseCase;
import com.quorumtrading.application.usecase.command.ExecuteInvestmentPlanCommand;
import com.quorumtrading.application.usecase.contribution.RecordSimulationContributionUseCase;
import com.quorumtrading.application.usecase.notification.DispatchNotificationUseCase;
import com.quorumtrading.model.entity.InvestmentPlan;
import com.quorumtrading.model.enums.BudgetMode;
import com.quorumtrading.model.enums.PickerPolicy;
import com.quorumtrading.model.valueobject.BrokerAccountId;
import com.quorumtrading.model.valueobject.InvestmentPlanId;
import com.quorumtrading.model.valueobject.InvestmentPlanPreferences;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A manual execute may only run an ACTIVE plan.
 *
 * <p>The service's javadoc claimed it validated "ownership and ACTIVE status", but only ownership
 * was ever checked. The scheduler filters on {@code status='ACTIVE'} inside its own query, so it
 * could never reach the service with a paused plan and the gap stayed invisible — while
 * {@code execute_investment_plan_now} had no such filter. Pausing a plan therefore did not stop
 * the one operation that spends money; against an ALPACA_LIVE broker that is a real order placed
 * from a plan the user believed was halted.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ExecuteInvestmentPlanService — plan status guard")
class ExecuteInvestmentPlanStatusGuardTest {

    @Mock private InvestmentPlanRepository planRepository;
    @Mock private InvestmentAllocationRepository allocationRepository;
    @Mock private BrokerAccountRepository brokerAccountRepository;
    @Mock private SimulationRepository simulationRepository;
    @Mock private StrategyRepository strategyRepository;
    @Mock private StrategyPickerPort strategyPicker;
    @Mock private CreateSimulationUseCase createSimulationUseCase;
    @Mock private RecordSimulationContributionUseCase recordContributionUseCase;
    @Mock private DispatchNotificationUseCase dispatchNotificationUseCase;

    private ExecuteInvestmentPlanService service;

    private UserId userId;
    private InvestmentPlanId planId;
    private BrokerAccountId brokerAccountId;
    private Instant executionDate;

    @BeforeEach
    void setUp() {
        service = new ExecuteInvestmentPlanService(
                planRepository, allocationRepository, brokerAccountRepository, simulationRepository,
                strategyRepository, strategyPicker, createSimulationUseCase,
                recordContributionUseCase, dispatchNotificationUseCase);

        userId = UserId.generate();
        planId = InvestmentPlanId.generate();
        brokerAccountId = BrokerAccountId.generate();
        executionDate = Instant.parse("2026-06-01T06:00:00Z");
    }

    private InvestmentPlan realPlan() {
        return InvestmentPlan.create(
                planId, userId, brokerAccountId, BudgetMode.FIXED_AMOUNT,
                Money.usd(new BigDecimal("250.00")), 1, PickerPolicy.AI_PICK,
                InvestmentPlanPreferences.EMPTY, Instant.parse("2026-05-15T00:00:00Z"));
    }

    private ExecuteInvestmentPlanCommand command() {
        return new ExecuteInvestmentPlanCommand(planId, userId, executionDate, true);
    }

    @Test
    @DisplayName("a PAUSED plan is refused and nothing is spent")
    void shouldRefuseToExecuteAPausedPlan() {
        InvestmentPlan plan = realPlan();
        plan.pause(Instant.parse("2026-05-20T00:00:00Z"));
        when(planRepository.findById(planId)).thenReturn(Optional.of(plan));

        assertThatThrownBy(() -> service.execute(command()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PAUSED")
                .hasMessageContaining("Resume it first");

        // Refused before any money moves: no broker lookup, no picker, no simulation, no allocation.
        verify(brokerAccountRepository, never()).findById(any());
        verify(strategyPicker, never()).pick(any());
        verify(createSimulationUseCase, never()).execute(any());
        verify(recordContributionUseCase, never()).execute(any());
        verify(allocationRepository, never()).save(any());
        verify(dispatchNotificationUseCase, never()).execute(any());
    }

    @Test
    @DisplayName("a TERMINATED plan is refused too, and is told it cannot be resumed")
    void shouldRefuseToExecuteATerminatedPlan() {
        InvestmentPlan plan = realPlan();
        plan.terminate(Instant.parse("2026-05-20T00:00:00Z"));
        when(planRepository.findById(planId)).thenReturn(Optional.of(plan));

        assertThatThrownBy(() -> service.execute(command()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TERMINATED")
                .hasMessageContaining("cannot be executed or resumed");

        verify(createSimulationUseCase, never()).execute(any());
        verify(allocationRepository, never()).save(any());
    }

    @Test
    @DisplayName("the refusal names the plan so the caller knows which one")
    void shouldNameThePlanInTheRefusal() {
        InvestmentPlan plan = realPlan();
        plan.pause(Instant.parse("2026-05-20T00:00:00Z"));
        when(planRepository.findById(planId)).thenReturn(Optional.of(plan));

        assertThatThrownBy(() -> service.execute(command()))
                .hasMessageContaining(planId.value().toString());
    }

    @Test
    @DisplayName("a resumed plan executes again — the guard reads current status, not history")
    void shouldAllowExecutionAfterResume() {
        InvestmentPlan plan = realPlan();
        plan.pause(Instant.parse("2026-05-20T00:00:00Z"));
        plan.resume(Instant.parse("2026-05-21T00:00:00Z"));
        when(planRepository.findById(planId)).thenReturn(Optional.of(plan));
        when(brokerAccountRepository.findById(brokerAccountId)).thenReturn(Optional.empty());

        // Gets past the status guard and fails later, on the missing broker account.
        assertThatThrownBy(() -> service.execute(command()))
                .isInstanceOf(java.util.NoSuchElementException.class)
                .hasMessageContaining("Broker account not found");

        assertThat(plan.getStatus()).isEqualTo(com.quorumtrading.model.enums.InvestmentPlanStatus.ACTIVE);
    }
}
