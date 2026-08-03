package com.quorumtrading.application.service;

import com.quorumtrading.application.port.out.BrokerAccountRepository;
import com.quorumtrading.application.port.out.InvestmentAllocationRepository;
import com.quorumtrading.application.port.out.InvestmentPlanRepository;
import com.quorumtrading.application.port.out.SimulationRepository;
import com.quorumtrading.application.port.out.StrategyPickerPort;
import com.quorumtrading.application.port.out.StrategyRepository;
import com.quorumtrading.application.usecase.CreateSimulationUseCase;
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
import com.quorumtrading.model.enums.BudgetMode;
import com.quorumtrading.model.enums.InvestmentPlanStatus;
import com.quorumtrading.model.enums.BrokerType;
import com.quorumtrading.model.enums.SimulationStatus;
import com.quorumtrading.model.valueobject.BrokerAccountId;
import com.quorumtrading.model.valueobject.ContributionId;
import com.quorumtrading.model.valueobject.InvestmentPlanId;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * FX5 — surfacing the non-fractional broker constraint in DCA orchestration.
 *
 * <p>When the plan's broker fills whole shares only (Saxo equities/ETFs, D3), one plan tick
 * must:</p>
 * <ul>
 *   <li>record the <b>full</b> contribution amount as cash (no clipping at the contribution
 *       layer — the cash always lands in the simulation);</li>
 *   <li>flag the audit row with {@code fractionalConstraint=true};</li>
 *   <li>when a material sub-whole-unit residue would be left un-deployable under whole-share
 *       rounding, mark the row {@code EXECUTED_PARTIAL_DEPLOY} with {@code deployedAmount} set
 *       to the whole-unit-floored deployable portion, honouring a currency-aware dust floor;</li>
 *   <li>keep the {@code (planId, contributionDate)} idempotency intact — the contribution
 *       command still carries the plan id + execution date, and a retry records the same
 *       full amount against the same key.</li>
 * </ul>
 *
 * <p>A fractional broker (Alpaca/Mock) path stays a plain {@code EXECUTED} with no constraint,
 * proving the surfacing is opt-in to whole-share brokers only.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ExecuteInvestmentPlanService — FX5 non-fractional DCA surfacing")
class ExecuteInvestmentPlanFractionalTest {

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
    private SimulationId liveSimId;
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
        liveSimId = new SimulationId(UUID.randomUUID());
        executionDate = Instant.parse("2026-06-01T00:00:00Z");

        // Saved allocation echoes back the argument so the test inspects the row as built.
        // The service's same-day guard probes for an already-funded allocation before it funds.
        // Empty is the honest default here: these fixtures each describe a plan's first tick of
        // the day.
        lenient().when(allocationRepository.findFundedOnDay(any(), any(), any()))
                .thenReturn(Optional.empty());
        lenient().when(allocationRepository.save(any(InvestmentAllocation.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        // Plan advance is a no-op for these tests.
        lenient().when(planRepository.save(any(InvestmentPlan.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    private InvestmentPlan fixedPlan(Money amount) {
        InvestmentPlan plan = org.mockito.Mockito.mock(InvestmentPlan.class);
        lenient().when(plan.getId()).thenReturn(planId);
        lenient().when(plan.getUserId()).thenReturn(userId);
        lenient().when(plan.getBrokerAccountId()).thenReturn(brokerAccountId);
        lenient().when(plan.getBudgetMode()).thenReturn(BudgetMode.FIXED_AMOUNT);
        lenient().when(plan.getAmount()).thenReturn(amount);
        // The service refuses to execute anything that is not ACTIVE, so a mocked plan has to say
        // which status it is in — an unstubbed getStatus() returns null and is refused.
        lenient().when(plan.getStatus()).thenReturn(InvestmentPlanStatus.ACTIVE);
        return plan;
    }

    private BrokerAccount broker(BrokerType type, Money available) {
        BrokerAccount account = org.mockito.Mockito.mock(BrokerAccount.class);
        lenient().when(account.getBrokerType()).thenReturn(type);
        lenient().when(account.getAvailableBalance()).thenReturn(available);
        return account;
    }

    private Simulation liveSimulation() {
        Simulation sim = org.mockito.Mockito.mock(Simulation.class);
        lenient().when(sim.getStatus()).thenReturn(SimulationStatus.LIVE);
        lenient().when(sim.getBrokerAccountId()).thenReturn(brokerAccountId);
        lenient().when(sim.getSimulationId()).thenReturn(liveSimId);
        return sim;
    }

    private void wireHappyTopUp(InvestmentPlan plan, BrokerAccount account, Contribution contribution) {
        // Build the simulation mock OUTSIDE the thenReturn(...) below — nesting a mock's stubbing
        // inside another stub's argument trips Mockito's UnfinishedStubbing guard.
        Simulation sim = liveSimulation();
        when(planRepository.findById(planId)).thenReturn(Optional.of(plan));
        when(brokerAccountRepository.findById(brokerAccountId)).thenReturn(Optional.of(account));
        when(strategyPicker.pick(plan))
                .thenReturn(StrategyPickerPort.Outcome.chose(strategyId, "picked", BigDecimal.ZERO));
        // Empty → skip the defensive TickerPolicyEnforcer pass, keeping the test focused.
        when(strategyRepository.findById(strategyId)).thenReturn(Optional.empty());
        when(simulationRepository.findByStrategyId(strategyId)).thenReturn(List.of(sim));
        when(recordContributionUseCase.execute(any(RecordSimulationContributionCommand.class)))
                .thenReturn(ContributionOutcome.created(contribution));
    }

    private Contribution contributionOf(Money amount) {
        return new Contribution(
                ContributionId.generate(),
                liveSimId,
                amount,
                executionDate,
                planId.value(),
                UUID.randomUUID(),
                Instant.now());
    }

    private ExecuteInvestmentPlanCommand command() {
        return new ExecuteInvestmentPlanCommand(planId, userId, executionDate, true);
    }

    @Test
    @DisplayName("Non-fractional plan tick: full contribution recorded, fractionalConstraint=true, partial-deploy + dust")
    void nonFractionalTickRecordsFullContributionAndPartialDeploy() {
        // €500.50 contribution on a whole-share (Saxo) broker. The full €500.50 is recorded as
        // cash; €500 is the whole-unit-floored deployable portion and the €0.50 residue (≥ the
        // €0.01 dust floor) is carried forward → EXECUTED_PARTIAL_DEPLOY.
        Money amount = Money.of(new BigDecimal("500.50"), Money.Currency.EUR);
        InvestmentPlan plan = fixedPlan(amount);
        BrokerAccount account = broker(BrokerType.SAXO_LIVE, Money.of(new BigDecimal("10000"), Money.Currency.EUR));
        wireHappyTopUp(plan, account, contributionOf(amount));

        InvestmentAllocation row = service.execute(command());

        // Full contribution recorded as cash via the contribution use case.
        ArgumentCaptor<RecordSimulationContributionCommand> contribCmd =
                ArgumentCaptor.forClass(RecordSimulationContributionCommand.class);
        verify(recordContributionUseCase).execute(contribCmd.capture());
        assertEquals(0, contribCmd.getValue().amount().amount().compareTo(new BigDecimal("500.50")),
                "the FULL contribution amount must be recorded, not the deployable floor");

        // Audit row: full actualAmount, fractional constraint flagged, partial-deploy status.
        assertEquals(AllocationStatus.EXECUTED_PARTIAL_DEPLOY, row.getStatus());
        assertTrue(row.isFractionalConstraint(), "non-fractional broker must flag fractionalConstraint");
        assertEquals(0, row.getActualAmount().amount().compareTo(new BigDecimal("500.50")),
                "actualAmount records the full contribution");
        assertEquals(0, row.getDeployedAmount().amount().compareTo(new BigDecimal("500")),
                "deployedAmount is the whole-unit-floored deployable portion");
        assertEquals(Money.Currency.EUR, row.getDeployedAmount().currency(),
                "deployedAmount carries the contribution currency (no FX, no same-currency throw)");
        assertTrue(row.wasExecuted(), "partial deploy still counts as executed");
    }

    @Test
    @DisplayName("Non-fractional whole-amount tick: fractionalConstraint=true but plain EXECUTED (sub-dust residue)")
    void nonFractionalWholeAmountStaysExecutedWithConstraintFlag() {
        // €600.00 — already a whole number of currency units, so the residue is €0.00 (< dust
        // floor): the constraint is still flagged, but there is nothing under-deployed → EXECUTED.
        Money amount = Money.of(new BigDecimal("600.00"), Money.Currency.EUR);
        InvestmentPlan plan = fixedPlan(amount);
        BrokerAccount account = broker(BrokerType.SAXO_LIVE, Money.of(new BigDecimal("10000"), Money.Currency.EUR));
        wireHappyTopUp(plan, account, contributionOf(amount));

        InvestmentAllocation row = service.execute(command());

        assertEquals(AllocationStatus.EXECUTED, row.getStatus(),
                "a whole-unit contribution has no under-deployed residue → plain EXECUTED");
        assertTrue(row.isFractionalConstraint(),
                "the whole-share broker constraint is still surfaced on the row");
        assertNull(row.getDeployedAmount(), "no deployedAmount when fully deployed");
        assertEquals(0, row.getActualAmount().amount().compareTo(new BigDecimal("600.00")));
    }

    @Test
    @DisplayName("Fractional broker tick: full contribution, no constraint, plain EXECUTED")
    void fractionalBrokerTickHasNoConstraint() {
        // $500.50 on a fractional broker (Alpaca paper) — the residue can deploy, so no constraint.
        Money amount = Money.usd(new BigDecimal("500.50"));
        InvestmentPlan plan = fixedPlan(amount);
        BrokerAccount account = broker(BrokerType.ALPACA_PAPER, Money.usd(new BigDecimal("10000")));
        wireHappyTopUp(plan, account, contributionOf(amount));

        InvestmentAllocation row = service.execute(command());

        assertEquals(AllocationStatus.EXECUTED, row.getStatus());
        assertFalse(row.isFractionalConstraint(), "fractional broker must NOT flag a constraint");
        assertNull(row.getDeployedAmount());
        assertEquals(0, row.getActualAmount().amount().compareTo(new BigDecimal("500.50")));
    }

    @Test
    @DisplayName("Idempotent retry: same plan + execution date keeps the (planId, contributionDate) key")
    void retryKeepsIdempotencyKey() {
        Money amount = Money.of(new BigDecimal("500.50"), Money.Currency.EUR);
        InvestmentPlan plan = fixedPlan(amount);
        BrokerAccount account = broker(BrokerType.SAXO_LIVE, Money.of(new BigDecimal("10000"), Money.Currency.EUR));
        wireHappyTopUp(plan, account, contributionOf(amount));

        // Two ticks for the SAME plan + execution date (a scheduler retry).
        service.execute(command());
        service.execute(command());

        ArgumentCaptor<RecordSimulationContributionCommand> contribCmd =
                ArgumentCaptor.forClass(RecordSimulationContributionCommand.class);
        verify(recordContributionUseCase, times(2)).execute(contribCmd.capture());

        // Both contribution commands carry the idempotency key (planId, contributionDate) and the
        // SAME full amount — the persistence layer's UNIQUE (source_plan_id, contribution_date)
        // de-dups the retry; surfacing the fractional constraint does not perturb that key.
        for (RecordSimulationContributionCommand cmd : contribCmd.getAllValues()) {
            assertEquals(planId.value(), cmd.sourcePlanId(),
                    "contribution must carry the plan id (half the idempotency key)");
            assertEquals(executionDate, cmd.contributionDate(),
                    "contribution must carry the execution date (other half of the key)");
            assertEquals(0, cmd.amount().amount().compareTo(new BigDecimal("500.50")),
                    "the full contribution amount is recorded identically on retry");
        }
    }
}
