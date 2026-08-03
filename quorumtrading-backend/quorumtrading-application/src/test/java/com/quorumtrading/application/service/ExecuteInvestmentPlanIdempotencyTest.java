package com.quorumtrading.application.service;

import com.quorumtrading.application.port.out.BrokerAccountRepository;
import com.quorumtrading.application.port.out.InvestmentAllocationRepository;
import com.quorumtrading.application.port.out.InvestmentPlanRepository;
import com.quorumtrading.application.port.out.SimulationRepository;
import com.quorumtrading.application.port.out.StrategyPickerPort;
import com.quorumtrading.application.port.out.StrategyRepository;
import com.quorumtrading.application.usecase.CreateSimulationUseCase;
import com.quorumtrading.application.usecase.command.DispatchNotificationCommand;
import com.quorumtrading.application.usecase.command.ExecuteInvestmentPlanCommand;
import com.quorumtrading.application.usecase.command.RecordSimulationContributionCommand;
import com.quorumtrading.application.usecase.contribution.ContributionOutcome;
import com.quorumtrading.application.usecase.contribution.RecordSimulationContributionUseCase;
import com.quorumtrading.application.usecase.notification.DispatchNotificationUseCase;
import com.quorumtrading.model.entity.BrokerAccount;
import com.quorumtrading.model.entity.Contribution;
import com.quorumtrading.model.entity.InvestmentAllocation;
import com.quorumtrading.model.entity.InvestmentPlan;
import com.quorumtrading.model.entity.Simulation;
import com.quorumtrading.model.enums.AllocationStatus;
import com.quorumtrading.model.enums.BrokerType;
import com.quorumtrading.model.enums.BudgetMode;
import com.quorumtrading.model.enums.NotificationType;
import com.quorumtrading.model.enums.PickerPolicy;
import com.quorumtrading.model.enums.SimulationStatus;
import com.quorumtrading.model.valueobject.BrokerAccountId;
import com.quorumtrading.model.valueobject.ContributionId;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * What a second execution on the same day reports, and whether the audit trail can be joined.
 *
 * <p>The money was always right: {@code (planId, contributionDate)} is UNIQUE, the contribution
 * service probes before inserting, and a repeated tick moves nothing. What was wrong is what the
 * product then <em>said</em> about it. Three same-day executions of a $500 plan wrote three
 * {@code EXECUTED / $500} allocation rows — a timeline reading $1500 against one real $500
 * contribution — and each one dispatched "Investment plan funded — Allocated $500.00" for a call
 * that moved nothing.</p>
 *
 * <p>The service could not have told the truth: {@code AllocationStatus} had no value able to
 * express "no-op", and the contribution use case returned a bare {@code Contribution} with no
 * created-versus-existing signal. Both are fixed here — the dedupe is a real outcome and now has
 * a name.</p>
 *
 * <p>Also covered: the contribution's {@code sourceAllocationId}. It was filled with
 * {@code InvestmentAllocationId.generate()} as a placeholder, a fresh UUID matching no row, so
 * every contribution pointed at an allocation that did not exist and the two halves of the audit
 * record could not be joined at all — defeating the stated purpose of the allocation row.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ExecuteInvestmentPlanService — same-day repeats and audit linkage")
class ExecuteInvestmentPlanIdempotencyTest {

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
    private StrategyId strategyId;
    private SimulationId liveSimId;
    private Instant executionDate;

    private static final Money PLAN_AMOUNT = Money.usd(new BigDecimal("500.00"));

    @BeforeEach
    void setUp() {
        service = new ExecuteInvestmentPlanService(
                planRepository, allocationRepository, brokerAccountRepository, simulationRepository,
                strategyRepository, strategyPicker, createSimulationUseCase,
                recordContributionUseCase, dispatchNotificationUseCase);

        userId = UserId.generate();
        planId = InvestmentPlanId.generate();
        brokerAccountId = BrokerAccountId.generate();
        strategyId = StrategyId.generate();
        liveSimId = SimulationId.generate();
        executionDate = Instant.parse("2026-06-01T06:00:00Z");

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

    private InvestmentPlan plan() {
        return InvestmentPlan.create(
                planId, userId, brokerAccountId, BudgetMode.FIXED_AMOUNT, PLAN_AMOUNT,
                1, PickerPolicy.AI_PICK,
                new InvestmentPlanPreferences(null, List.of(), List.of(), null, true),
                Instant.parse("2026-05-15T00:00:00Z"));
    }

    private BrokerAccount broker() {
        BrokerAccount account = org.mockito.Mockito.mock(BrokerAccount.class);
        lenient().when(account.getBrokerType()).thenReturn(BrokerType.ALPACA_PAPER);
        lenient().when(account.getAvailableBalance())
                .thenReturn(Money.usd(new BigDecimal("10000.00")));
        return account;
    }

    private Contribution contribution() {
        return new Contribution(
                ContributionId.generate(), liveSimId, PLAN_AMOUNT,
                executionDate, planId.value(), null, Instant.now());
    }

    /** A plan with one existing LIVE simulation, so the tick takes the top-up path. */
    private void wireTopUp(ContributionOutcome outcome) {
        Simulation sim = org.mockito.Mockito.mock(Simulation.class);
        lenient().when(sim.getStatus()).thenReturn(SimulationStatus.LIVE);
        lenient().when(sim.getBrokerAccountId()).thenReturn(brokerAccountId);
        lenient().when(sim.getSimulationId()).thenReturn(liveSimId);
        lenient().when(sim.getStrategyId()).thenReturn(strategyId);

        // Build the mocks OUTSIDE the thenReturn(...) calls — stubbing one mock inside another
        // stub's argument trips Mockito's UnfinishedStubbing guard.
        InvestmentPlan plan = plan();
        BrokerAccount account = broker();
        when(planRepository.findById(planId)).thenReturn(Optional.of(plan));
        when(brokerAccountRepository.findById(brokerAccountId)).thenReturn(Optional.of(account));
        when(strategyPicker.pick(any()))
                .thenReturn(StrategyPickerPort.Outcome.chose(strategyId, "picked", BigDecimal.ZERO));
        when(strategyRepository.findById(strategyId)).thenReturn(Optional.empty());
        when(simulationRepository.findByStrategyId(strategyId)).thenReturn(List.of(sim));
        when(recordContributionUseCase.execute(any(RecordSimulationContributionCommand.class)))
                .thenReturn(outcome);
    }

    private InvestmentAllocation savedAllocation() {
        ArgumentCaptor<InvestmentAllocation> captor =
                ArgumentCaptor.forClass(InvestmentAllocation.class);
        verify(allocationRepository).save(captor.capture());
        return captor.getValue();
    }

    private DispatchNotificationCommand dispatchedNotification() {
        ArgumentCaptor<DispatchNotificationCommand> captor =
                ArgumentCaptor.forClass(DispatchNotificationCommand.class);
        verify(dispatchNotificationUseCase).execute(captor.capture());
        return captor.getValue();
    }

    private ExecuteInvestmentPlanCommand command() {
        return new ExecuteInvestmentPlanCommand(planId, userId, executionDate, true);
    }

    @Test
    @DisplayName("a deduplicated repeat is not recorded as a funded execution")
    void repeatIsNotRecordedAsExecuted() {
        wireTopUp(ContributionOutcome.alreadyRecorded(contribution()));

        service.execute(command());

        InvestmentAllocation allocation = savedAllocation();
        assertThat(allocation.getStatus())
                .as("three same-day ticks wrote three EXECUTED/$500 rows for one real $500 "
                    + "contribution, so the timeline read $1500")
                .isEqualTo(AllocationStatus.SKIPPED_ALREADY_EXECUTED_TODAY);
    }

    @Test
    @DisplayName("a deduplicated repeat reports zero deployed, not the plan amount")
    void repeatReportsZeroAmount() {
        wireTopUp(ContributionOutcome.alreadyRecorded(contribution()));

        service.execute(command());

        assertThat(savedAllocation().getActualAmount().amount())
                .as("no money moved on this tick, so the audit row must not claim any did")
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("a deduplicated repeat does not tell the user their plan was funded")
    void repeatDoesNotNotifyAsFunded() {
        wireTopUp(ContributionOutcome.alreadyRecorded(contribution()));

        service.execute(command());

        assertThat(dispatchedNotification().type())
                .as("\"Investment plan funded — Allocated $500.00\" for a call that moved $0 is "
                    + "the kind of message that destroys trust in every other message")
                .isNotEqualTo(NotificationType.INVESTMENT_EXECUTED);
    }

    /**
     * The notification switch handles EXECUTED and FAILED_BROKER_ERROR by name and sends
     * everything else down a {@code default} branch titled "Investment plan skipped". A partial
     * deploy is not a skip — the contribution was recorded in full and part of it is at work —
     * so a user who was told their investment was skipped would go looking for money that is
     * exactly where it should be.
     */
    @Test
    @DisplayName("a partial deploy is not announced as a skip")
    void partialDeployIsNotAnnouncedAsASkip() {
        // A whole-share broker forces rounding, leaving a material residue → EXECUTED_PARTIAL_DEPLOY.
        Simulation sim = org.mockito.Mockito.mock(Simulation.class);
        lenient().when(sim.getStatus()).thenReturn(SimulationStatus.LIVE);
        lenient().when(sim.getBrokerAccountId()).thenReturn(brokerAccountId);
        lenient().when(sim.getSimulationId()).thenReturn(liveSimId);
        BrokerAccount saxo = org.mockito.Mockito.mock(BrokerAccount.class);
        lenient().when(saxo.getBrokerType()).thenReturn(BrokerType.SAXO_SIM);
        lenient().when(saxo.getAvailableBalance()).thenReturn(Money.usd(new BigDecimal("10000.00")));
        // $500.50 leaves a $0.50 residue above the $0.01 dust floor once floored to whole units.
        Money fractional = Money.usd(new BigDecimal("500.50"));
        InvestmentPlan plan = InvestmentPlan.create(
                planId, userId, brokerAccountId, BudgetMode.FIXED_AMOUNT, fractional,
                1, PickerPolicy.AI_PICK,
                new InvestmentPlanPreferences(null, List.of(), List.of(), null, true),
                Instant.parse("2026-05-15T00:00:00Z"));

        when(planRepository.findById(planId)).thenReturn(Optional.of(plan));
        when(brokerAccountRepository.findById(brokerAccountId)).thenReturn(Optional.of(saxo));
        when(strategyPicker.pick(any()))
                .thenReturn(StrategyPickerPort.Outcome.chose(strategyId, "picked", BigDecimal.ZERO));
        when(strategyRepository.findById(strategyId)).thenReturn(Optional.empty());
        when(simulationRepository.findByStrategyId(strategyId)).thenReturn(List.of(sim));
        when(recordContributionUseCase.execute(any(RecordSimulationContributionCommand.class)))
                .thenReturn(ContributionOutcome.created(contribution()));

        service.execute(command());

        assertThat(savedAllocation().getStatus())
                .as("precondition: this fixture must actually produce a partial deploy")
                .isEqualTo(AllocationStatus.EXECUTED_PARTIAL_DEPLOY);
        assertThat(dispatchedNotification().title())
                .as("the money was contributed; only its deployment was rounded")
                .doesNotContain("skipped");
    }

    @Test
    @DisplayName("a genuine first execution is still reported as funded")
    void firstExecutionStillReportsExecuted() {
        wireTopUp(ContributionOutcome.created(contribution()));

        service.execute(command());

        assertThat(savedAllocation().getStatus()).isEqualTo(AllocationStatus.EXECUTED);
        assertThat(savedAllocation().getActualAmount().amount())
                .isEqualByComparingTo(PLAN_AMOUNT.amount());
    }

    /**
     * The allocation row is documented as the immutable audit record of one execution attempt, and
     * the contribution is meant to cite it. Passing a freshly generated id made every citation a
     * dead reference — {@code get_investment_allocation} answered "Allocation not found" for the
     * id on every contribution row.
     */
    @Test
    @DisplayName("the contribution cites the allocation row that actually records it")
    void contributionCitesTheRealAllocationRow() {
        wireTopUp(ContributionOutcome.created(contribution()));

        service.execute(command());

        ArgumentCaptor<RecordSimulationContributionCommand> captor =
                ArgumentCaptor.forClass(RecordSimulationContributionCommand.class);
        verify(recordContributionUseCase).execute(captor.capture());

        assertThat(captor.getValue().sourceAllocationId())
                .as("a contribution pointing at an allocation that does not exist cannot be "
                    + "joined to anything, which is the whole purpose of the row")
                .isEqualTo(savedAllocation().getId().value());
    }
}
