package com.quorumtrading.application.service;

import com.quorumtrading.application.port.out.BrokerAccountRepository;
import com.quorumtrading.application.port.out.InvestmentAllocationRepository;
import com.quorumtrading.application.port.out.InvestmentPlanRepository;
import com.quorumtrading.application.port.out.SimulationRepository;
import com.quorumtrading.application.port.out.StrategyPickerPort;
import com.quorumtrading.application.port.out.StrategyRepository;
import com.quorumtrading.application.usecase.CreateSimulationUseCase;
import com.quorumtrading.application.usecase.command.CreateSimulationCommand;
import com.quorumtrading.application.usecase.command.DispatchNotificationCommand;
import com.quorumtrading.application.usecase.command.ExecuteInvestmentPlanCommand;
import com.quorumtrading.application.usecase.command.RecordSimulationContributionCommand;
import com.quorumtrading.application.usecase.contribution.RecordSimulationContributionUseCase;
import com.quorumtrading.application.usecase.notification.DispatchNotificationUseCase;
import com.quorumtrading.model.entity.BrokerAccount;
import com.quorumtrading.model.entity.InvestmentAllocation;
import com.quorumtrading.model.entity.InvestmentPlan;
import com.quorumtrading.model.entity.Simulation;
import com.quorumtrading.model.entity.Strategy;
import com.quorumtrading.model.enums.AllocationStatus;
import com.quorumtrading.model.enums.BrokerType;
import com.quorumtrading.model.enums.BudgetMode;
import com.quorumtrading.model.enums.NotificationType;
import com.quorumtrading.model.enums.PickerPolicy;
import com.quorumtrading.model.enums.SimulationStatus;
import com.quorumtrading.model.valueobject.BrokerAccountId;
import com.quorumtrading.model.valueobject.InvestmentPlanId;
import com.quorumtrading.model.valueobject.InvestmentPlanPreferences;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.SimulationId;
import com.quorumtrading.model.valueobject.StrategyId;
import com.quorumtrading.model.valueobject.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Skip/fail branch coverage for {@link ExecuteInvestmentPlanService}.
 *
 * <p>The sibling {@link ExecuteInvestmentPlanFractionalTest} covers only EXECUTED
 * outcomes. This class exercises every non-EXECUTED exit of the orchestration:</p>
 * <ul>
 *   <li>{@code SKIPPED_INSUFFICIENT_FUNDS} — both branch sites: the FIXED_AMOUNT
 *       budget check (available &lt; plan amount) and the post-resolution minimum
 *       floor (resolved partial contribution below the $10 minimum);</li>
 *   <li>{@code SKIPPED_NO_ELIGIBLE_STRATEGY} — picker returns no choice;</li>
 *   <li>{@code SKIPPED_TICKER_POLICY_VIOLATION} — defense-in-depth rejection of a
 *       picker choice that violates the plan's ticker policy;</li>
 *   <li>{@code FAILED_BROKER_ERROR} — the fund/create step throws (with and without
 *       an exception message).</li>
 * </ul>
 *
 * <p>For each branch we assert the three contractual side effects: the
 * {@code InvestmentAllocation} audit row is written with the right status and
 * skip/failure reason, the right notification type is dispatched, and the plan's
 * schedule advances ({@code nextExecutionAt} moves, {@code lastExecutionAt} is
 * stamped) — plus, when the caller opts out via {@code advanceSchedule=false},
 * that the schedule does NOT move.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ExecuteInvestmentPlanService — skip and failure branches")
class ExecuteInvestmentPlanSkipFailTest {

    @Mock
    private InvestmentPlanRepository planRepository;
    @Mock
    private InvestmentAllocationRepository allocationRepository;
    @Mock
    private BrokerAccountRepository brokerAccountRepository;
    @Mock
    private SimulationRepository simulationRepository;
    @Mock
    private StrategyRepository strategyRepository;
    @Mock
    private StrategyPickerPort strategyPicker;
    @Mock
    private CreateSimulationUseCase createSimulationUseCase;
    @Mock
    private RecordSimulationContributionUseCase recordContributionUseCase;
    @Mock
    private DispatchNotificationUseCase dispatchNotificationUseCase;

    private ExecuteInvestmentPlanService service;

    private UserId userId;
    private InvestmentPlanId planId;
    private BrokerAccountId brokerAccountId;
    private StrategyId strategyId;
    private Instant executionDate;

    @BeforeEach
    void setUp() {
        service = new ExecuteInvestmentPlanService(
                planRepository,
                allocationRepository,
                brokerAccountRepository,
                simulationRepository,
                strategyRepository,
                strategyPicker,
                createSimulationUseCase,
                recordContributionUseCase,
                dispatchNotificationUseCase);

        userId = UserId.generate();
        planId = InvestmentPlanId.generate();
        brokerAccountId = BrokerAccountId.generate();
        strategyId = StrategyId.generate();
        executionDate = Instant.parse("2026-06-01T06:00:00Z");

        // Saved allocation echoes back the argument so the test inspects the row as built.
        // The service's same-day guard probes for an already-funded allocation before it funds.
        // Empty is the honest default here: these fixtures each describe a plan's first tick of
        // the day.
        lenient().when(allocationRepository.findFundedOnDay(any(), any(), any()))
                .thenReturn(Optional.empty());
        lenient().when(allocationRepository.save(any(InvestmentAllocation.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        lenient().when(planRepository.save(any(InvestmentPlan.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    // ----- Fixtures -----

    /** A REAL plan (not a mock) so schedule-advance assertions observe actual domain behavior. */
    private InvestmentPlan realPlan(BudgetMode mode, Money amount, InvestmentPlanPreferences prefs) {
        return InvestmentPlan.create(
                planId, userId, brokerAccountId, mode, amount,
                1, PickerPolicy.AI_PICK, prefs,
                Instant.parse("2026-05-15T00:00:00Z"));
    }

    private BrokerAccount broker(Money available) {
        BrokerAccount account = org.mockito.Mockito.mock(BrokerAccount.class);
        lenient().when(account.getBrokerType()).thenReturn(BrokerType.ALPACA_PAPER);
        lenient().when(account.getAvailableBalance()).thenReturn(available);
        return account;
    }

    private void wire(InvestmentPlan plan, BrokerAccount account) {
        when(planRepository.findById(planId)).thenReturn(Optional.of(plan));
        when(brokerAccountRepository.findById(brokerAccountId)).thenReturn(Optional.of(account));
    }

    private ExecuteInvestmentPlanCommand command(boolean advanceSchedule) {
        return new ExecuteInvestmentPlanCommand(planId, userId, executionDate, advanceSchedule);
    }

    private InvestmentAllocation savedAllocation() {
        ArgumentCaptor<InvestmentAllocation> captor = ArgumentCaptor.forClass(InvestmentAllocation.class);
        verify(allocationRepository).save(captor.capture());
        return captor.getValue();
    }

    private DispatchNotificationCommand dispatchedNotification() {
        ArgumentCaptor<DispatchNotificationCommand> captor =
                ArgumentCaptor.forClass(DispatchNotificationCommand.class);
        verify(dispatchNotificationUseCase).execute(captor.capture());
        return captor.getValue();
    }

    private void assertScheduleAdvanced(InvestmentPlan plan, Instant originalNext) {
        assertNotNull(plan.getLastExecutionAt(), "lastExecutionAt must be stamped on a finalized tick");
        assertNotNull(plan.getNextExecutionAt(), "nextExecutionAt must be recomputed, not cleared");
        assertNotEquals(originalNext, plan.getNextExecutionAt(),
                "nextExecutionAt must move to the next scheduled date");
        assertTrue(plan.getNextExecutionAt().isAfter(originalNext),
                "nextExecutionAt must advance forward in time");
        verify(planRepository).save(plan);
    }

    // ----- SKIPPED_INSUFFICIENT_FUNDS, branch 1: budget check (available < plan amount) -----

    @Test
    @DisplayName("FIXED_AMOUNT with available < plan amount → SKIPPED_INSUFFICIENT_FUNDS, skip notification, schedule advances")
    void fixedAmountInsufficientAvailableSkips() {
        InvestmentPlan plan = realPlan(BudgetMode.FIXED_AMOUNT,
                Money.usd(new BigDecimal("200.00")), InvestmentPlanPreferences.EMPTY);
        Instant originalNext = plan.getNextExecutionAt();
        wire(plan, broker(Money.usd(new BigDecimal("50.00"))));

        InvestmentAllocation row = service.execute(command(true));

        // Audit row: right status, reason from the budget-check branch, no strategy touched.
        assertEquals(AllocationStatus.SKIPPED_INSUFFICIENT_FUNDS, row.getStatus());
        assertNotNull(row.getFailureReason());
        assertTrue(row.getFailureReason().contains("is less than the plan amount"),
                "reason must come from the budget-check branch, got: " + row.getFailureReason());
        assertNull(row.getActualAmount(), "skip rows carry no actualAmount");
        assertNull(row.getChosenStrategyId(), "the picker must not have run");
        assertEquals(row, savedAllocation(), "the returned row is the persisted row");

        // Picker and downstream funding must never be reached.
        verify(strategyPicker, never()).pick(any());
        verify(recordContributionUseCase, never()).execute(any(RecordSimulationContributionCommand.class));
        verify(createSimulationUseCase, never()).execute(any(CreateSimulationCommand.class));

        // Notification: INVESTMENT_SKIPPED with the skip reason surfaced to the user.
        DispatchNotificationCommand notification = dispatchedNotification();
        assertEquals(NotificationType.INVESTMENT_SKIPPED, notification.type());
        assertEquals(userId, notification.userId());
        assertTrue(notification.body().contains("is less than the plan amount"));
        assertEquals(row.getStatus().name(), notification.payload().get("status"));

        // The skip reason is read by a human in their inbox, so amounts must be formatted.
        // Concatenating the Money record leaked its toString into the notification:
        // "Broker available balance Money[amount=9000.00, currency=USD] is less than the plan
        // amount Money[amount=20000.00000000, currency=USD]".
        assertFalse(notification.body().contains("Money["),
                "raw Money.toString() must never reach the user, got: " + notification.body());
        assertFalse(row.getFailureReason().contains("Money["),
                "raw Money.toString() must never reach the audit row, got: " + row.getFailureReason());
        assertTrue(notification.body().contains("$50.00"),
                "the broker balance should read as currency, got: " + notification.body());

        // Schedule advances — a skip is a finalized tick.
        assertScheduleAdvanced(plan, originalNext);
    }

    // ----- SKIPPED_INSUFFICIENT_FUNDS, branch 2: resolved contribution below the minimum -----

    @Test
    @DisplayName("FIXED_OR_PARTIAL resolved below the $10 minimum → SKIPPED_INSUFFICIENT_FUNDS via the minimum-floor branch")
    void partialResolvedBelowMinimumSkips() {
        // FIXED_OR_PARTIAL passes the budget check on ANY positive balance, then clamps the
        // contribution to what is available — $5 here, below the $10 minimum floor.
        InvestmentPlan plan = realPlan(BudgetMode.FIXED_OR_PARTIAL,
                Money.usd(new BigDecimal("200.00")), InvestmentPlanPreferences.EMPTY);
        Instant originalNext = plan.getNextExecutionAt();
        wire(plan, broker(Money.usd(new BigDecimal("5.00"))));

        InvestmentAllocation row = service.execute(command(true));

        assertEquals(AllocationStatus.SKIPPED_INSUFFICIENT_FUNDS, row.getStatus());
        assertNotNull(row.getFailureReason());
        assertTrue(row.getFailureReason().contains("is below the"),
                "reason must come from the minimum-floor branch, got: " + row.getFailureReason());
        assertTrue(row.getFailureReason().contains("minimum"),
                "reason must name the minimum, got: " + row.getFailureReason());

        verify(strategyPicker, never()).pick(any());
        verify(recordContributionUseCase, never()).execute(any(RecordSimulationContributionCommand.class));

        DispatchNotificationCommand notification = dispatchedNotification();
        assertEquals(NotificationType.INVESTMENT_SKIPPED, notification.type());

        assertScheduleAdvanced(plan, originalNext);
    }

    // ----- SKIPPED_NO_ELIGIBLE_STRATEGY -----

    @Test
    @DisplayName("Picker returns no choice → SKIPPED_NO_ELIGIBLE_STRATEGY with the picker's reasoning")
    void noEligibleStrategySkips() {
        InvestmentPlan plan = realPlan(BudgetMode.FIXED_AMOUNT,
                Money.usd(new BigDecimal("200.00")), InvestmentPlanPreferences.EMPTY);
        Instant originalNext = plan.getNextExecutionAt();
        wire(plan, broker(Money.usd(new BigDecimal("10000.00"))));
        when(strategyPicker.pick(plan))
                .thenReturn(StrategyPickerPort.Outcome.none("No active strategies in the user's pool"));

        InvestmentAllocation row = service.execute(command(true));

        assertEquals(AllocationStatus.SKIPPED_NO_ELIGIBLE_STRATEGY, row.getStatus());
        assertEquals("No active strategies in the user's pool", row.getFailureReason(),
                "the picker's reasoning IS the failure reason on this branch");
        assertEquals("No active strategies in the user's pool", row.getPickerReasoning());
        assertNull(row.getChosenStrategyId());
        assertNull(row.getActualAmount());

        verify(recordContributionUseCase, never()).execute(any(RecordSimulationContributionCommand.class));
        verify(createSimulationUseCase, never()).execute(any(CreateSimulationCommand.class));

        DispatchNotificationCommand notification = dispatchedNotification();
        assertEquals(NotificationType.INVESTMENT_SKIPPED, notification.type());
        assertTrue(notification.body().contains("No active strategies in the user's pool"));

        assertScheduleAdvanced(plan, originalNext);
    }

    // ----- SKIPPED_TICKER_POLICY_VIOLATION -----

    @Test
    @DisplayName("Picker choice violates ticker policy → SKIPPED_TICKER_POLICY_VIOLATION, strategy recorded but not funded")
    void tickerPolicyViolationSkips() {
        // An active allowlist arms TickerPolicyEnforcer; a strategy with no parseable
        // tickers cannot be certified against it and must be refused funding.
        InvestmentPlanPreferences allowlistPolicy = new InvestmentPlanPreferences(
                null, List.of("AAPL"), List.of(), null, true);
        InvestmentPlan plan = realPlan(BudgetMode.FIXED_AMOUNT,
                Money.usd(new BigDecimal("200.00")), allowlistPolicy);
        Instant originalNext = plan.getNextExecutionAt();
        wire(plan, broker(Money.usd(new BigDecimal("10000.00"))));
        when(strategyPicker.pick(plan))
                .thenReturn(StrategyPickerPort.Outcome.chose(strategyId, "picked by buggy picker", BigDecimal.ZERO));

        Strategy violating = org.mockito.Mockito.mock(Strategy.class);
        lenient().when(violating.getContent()).thenReturn(null); // no tickers declared → uncertifiable
        when(strategyRepository.findById(strategyId)).thenReturn(Optional.of(violating));

        InvestmentAllocation row = service.execute(command(true));

        assertEquals(AllocationStatus.SKIPPED_TICKER_POLICY_VIOLATION, row.getStatus());
        assertEquals(strategyId, row.getChosenStrategyId(),
                "the offending strategy is recorded on the audit row");
        assertEquals("picked by buggy picker", row.getPickerReasoning());
        assertNotNull(row.getFailureReason());
        assertTrue(row.getFailureReason().contains("violates ticker policy"),
                "got: " + row.getFailureReason());
        assertNull(row.getActualAmount());

        // The violating strategy must NOT be funded.
        verify(recordContributionUseCase, never()).execute(any(RecordSimulationContributionCommand.class));
        verify(createSimulationUseCase, never()).execute(any(CreateSimulationCommand.class));

        DispatchNotificationCommand notification = dispatchedNotification();
        assertEquals(NotificationType.INVESTMENT_SKIPPED, notification.type());
        assertEquals(strategyId.value().toString(), notification.payload().get("strategyId"));

        assertScheduleAdvanced(plan, originalNext);
    }

    // ----- FAILED_BROKER_ERROR -----

    @Test
    @DisplayName("Contribution top-up throws → FAILED_BROKER_ERROR with the exception message, failure notification")
    void topUpFailureRecordsFailedBrokerError() {
        InvestmentPlan plan = realPlan(BudgetMode.FIXED_AMOUNT,
                Money.usd(new BigDecimal("200.00")), InvestmentPlanPreferences.EMPTY);
        Instant originalNext = plan.getNextExecutionAt();
        wire(plan, broker(Money.usd(new BigDecimal("10000.00"))));
        when(strategyPicker.pick(plan))
                .thenReturn(StrategyPickerPort.Outcome.chose(strategyId, "picked", BigDecimal.ZERO));
        when(strategyRepository.findById(strategyId)).thenReturn(Optional.empty());

        Simulation liveSim = org.mockito.Mockito.mock(Simulation.class);
        lenient().when(liveSim.getStatus()).thenReturn(SimulationStatus.LIVE);
        lenient().when(liveSim.getBrokerAccountId()).thenReturn(brokerAccountId);
        lenient().when(liveSim.getSimulationId()).thenReturn(new SimulationId(UUID.randomUUID()));
        when(simulationRepository.findByStrategyId(strategyId)).thenReturn(List.of(liveSim));
        when(recordContributionUseCase.execute(any(RecordSimulationContributionCommand.class)))
                .thenThrow(new RuntimeException("Alpaca rejected the order: insufficient buying power"));

        InvestmentAllocation row = service.execute(command(true));

        assertEquals(AllocationStatus.FAILED_BROKER_ERROR, row.getStatus());
        assertEquals("Alpaca rejected the order: insufficient buying power", row.getFailureReason());
        assertEquals(strategyId, row.getChosenStrategyId());
        assertNull(row.getActualAmount(), "a failed tick records no actual amount");
        assertNull(row.getContributionId());

        DispatchNotificationCommand notification = dispatchedNotification();
        assertEquals(NotificationType.INVESTMENT_FAILED, notification.type());
        assertTrue(notification.body().contains("Alpaca rejected the order: insufficient buying power"));
        assertEquals(row.getStatus().name(), notification.payload().get("status"));

        // The code advances the schedule on FAILED too (retries opt out via advanceSchedule=false).
        assertScheduleAdvanced(plan, originalNext);
    }

    @Test
    @DisplayName("New-simulation create throws a message-less exception → failureReason falls back to the class name")
    void messageLessFailureFallsBackToExceptionClassName() {
        InvestmentPlan plan = realPlan(BudgetMode.FIXED_AMOUNT,
                Money.usd(new BigDecimal("200.00")), InvestmentPlanPreferences.EMPTY);
        wire(plan, broker(Money.usd(new BigDecimal("10000.00"))));
        when(strategyPicker.pick(plan))
                .thenReturn(StrategyPickerPort.Outcome.chose(strategyId, "picked", BigDecimal.ZERO));
        when(strategyRepository.findById(strategyId)).thenReturn(Optional.empty());
        when(simulationRepository.findByStrategyId(strategyId)).thenReturn(List.of()); // no LIVE sim → create path
        when(createSimulationUseCase.execute(any(CreateSimulationCommand.class)))
                .thenThrow(new IllegalStateException()); // null message

        InvestmentAllocation row = service.execute(command(true));

        assertEquals(AllocationStatus.FAILED_BROKER_ERROR, row.getStatus());
        assertEquals("IllegalStateException", row.getFailureReason(),
                "a message-less exception must fall back to its class simple name");

        DispatchNotificationCommand notification = dispatchedNotification();
        assertEquals(NotificationType.INVESTMENT_FAILED, notification.type());
    }

    // ----- advanceSchedule=false opt-out -----

    @Test
    @DisplayName("advanceSchedule=false (retry semantics): allocation + notification still happen, schedule does NOT move")
    void retryDoesNotAdvanceSchedule() {
        InvestmentPlan plan = realPlan(BudgetMode.FIXED_AMOUNT,
                Money.usd(new BigDecimal("200.00")), InvestmentPlanPreferences.EMPTY);
        Instant originalNext = plan.getNextExecutionAt();
        wire(plan, broker(Money.usd(new BigDecimal("50.00")))); // insufficient → skip

        InvestmentAllocation row = service.execute(command(false));

        assertEquals(AllocationStatus.SKIPPED_INSUFFICIENT_FUNDS, row.getStatus());
        verify(allocationRepository).save(any(InvestmentAllocation.class));
        assertEquals(NotificationType.INVESTMENT_SKIPPED, dispatchedNotification().type());

        // Schedule untouched: no save, nextExecutionAt unchanged, lastExecutionAt never stamped.
        verify(planRepository, never()).save(any(InvestmentPlan.class));
        assertEquals(originalNext, plan.getNextExecutionAt());
        assertNull(plan.getLastExecutionAt());
    }

    // ----- Notification failure isolation -----

    @Test
    @DisplayName("Notification dispatch failure must not roll back the allocation row")
    void notificationFailureDoesNotRollBackAllocation() {
        InvestmentPlan plan = realPlan(BudgetMode.FIXED_AMOUNT,
                Money.usd(new BigDecimal("200.00")), InvestmentPlanPreferences.EMPTY);
        Instant originalNext = plan.getNextExecutionAt();
        wire(plan, broker(Money.usd(new BigDecimal("50.00")))); // insufficient → skip
        when(dispatchNotificationUseCase.execute(any(DispatchNotificationCommand.class)))
                .thenThrow(new RuntimeException("SMTP down"));

        InvestmentAllocation row = service.execute(command(true));

        assertEquals(AllocationStatus.SKIPPED_INSUFFICIENT_FUNDS, row.getStatus());
        verify(allocationRepository).save(any(InvestmentAllocation.class));
        assertScheduleAdvanced(plan, originalNext);
    }
}
