package com.quorumtrading.application.service;

import com.quorumtrading.application.port.out.BrokerAccountRepository;
import com.quorumtrading.application.port.out.InvestmentAllocationRepository;
import com.quorumtrading.application.port.out.InvestmentPlanRepository;
import com.quorumtrading.application.port.out.PortfolioRepository;
import com.quorumtrading.application.port.out.SimulationContributionRepository;
import com.quorumtrading.application.port.out.SimulationPortfolioRepository;
import com.quorumtrading.application.port.out.SimulationRepository;
import com.quorumtrading.application.port.out.StrategyPickerPort;
import com.quorumtrading.application.port.out.StrategyRepository;
import com.quorumtrading.application.usecase.CreateSimulationUseCase;
import com.quorumtrading.application.usecase.command.CreateSimulationCommand;
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
import com.quorumtrading.model.entity.PortfolioSnapshot;
import com.quorumtrading.model.entity.Simulation;
import com.quorumtrading.model.entity.SimulationPortfolio;
import com.quorumtrading.model.entity.Strategy;
import com.quorumtrading.model.enums.AllocationAction;
import com.quorumtrading.model.enums.AllocationStatus;
import com.quorumtrading.model.enums.BrokerType;
import com.quorumtrading.model.enums.BudgetMode;
import com.quorumtrading.model.enums.PickerPolicy;
import com.quorumtrading.model.enums.SimulationStatus;
import com.quorumtrading.model.valueobject.BrokerAccountId;
import com.quorumtrading.model.valueobject.ContributionId;
import com.quorumtrading.model.valueobject.InvestmentAllocationId;
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
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A plan must fund at most once per day <em>whichever</em> funding path it takes.
 *
 * <p>Idempotency was documented as resting entirely on the contribution key: {@code UNIQUE
 * (source_plan_id, contribution_date)} plus a probe before insert. That guard is real, but the
 * {@code NEW_SIMULATION} branch of find-or-create writes <b>no contribution row at all</b> — the
 * cash becomes the new simulation's initial capital. The first funding was therefore invisible to
 * the only guard that existed, and exactly one extra funding always slipped through:</p>
 *
 * <pre>
 *   call 1 → EXECUTED, NEW_SIMULATION,  $500, contributionId = null   ← invisible to the guard
 *   call 2 → EXECUTED, TOP_UP_EXISTING, $500, contribution written    ← the double
 *   call 3 → SKIPPED_ALREADY_EXECUTED_TODAY, $0                       ← guard finally sees it
 * </pre>
 *
 * <p>One row in {@code simulation_contributions}; two EXECUTED allocation rows summing $1000 on a
 * day the plan intends to deploy $500. And it is not a first-ever-execution problem: the create
 * branch is taken again every time the previously funded simulation stops being LIVE (completed,
 * force-ended, deleted), so the window reopens.</p>
 *
 * <p>These tests drive the service against a hand-rolled allocation repository rather than a mock,
 * because the defect is about what the <em>second</em> call sees of what the first call wrote —
 * which a stateless stub cannot express.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ExecuteInvestmentPlanService — one funding per day on both funding paths")
class ExecuteInvestmentPlanSameDayFundingTest {

    @Mock private InvestmentPlanRepository planRepository;
    @Mock private BrokerAccountRepository brokerAccountRepository;
    @Mock private SimulationRepository simulationRepository;
    @Mock private StrategyRepository strategyRepository;
    @Mock private StrategyPickerPort strategyPicker;
    @Mock private CreateSimulationUseCase createSimulationUseCase;
    @Mock private RecordSimulationContributionUseCase recordContributionUseCase;
    @Mock private DispatchNotificationUseCase dispatchNotificationUseCase;

    private final RecordingAllocationRepository allocations = new RecordingAllocationRepository();

    private ExecuteInvestmentPlanService service;

    private UserId userId;
    private InvestmentPlanId planId;
    private BrokerAccountId brokerAccountId;
    private StrategyId strategyId;
    private SimulationId createdSimId;
    private InvestmentPlan plan;

    /** The reproduction's plan: MOCK broker, FIXED_AMOUNT $500. */
    private static final Money PLAN_AMOUNT = Money.usd(new BigDecimal("500.00"));

    /** Deliberately not midnight: the guard must key on the day, not the instant. */
    private static final Instant TICK = Instant.parse("2026-07-30T18:42:11.123456Z");
    private static final Instant DAY_START = Instant.parse("2026-07-30T00:00:00Z");
    private static final Instant NEXT_DAY_START = Instant.parse("2026-07-31T00:00:00Z");

    /** Live simulations visible to {@code findByStrategyId}, grown as the plan creates them. */
    private final List<Simulation> liveSimulations = new ArrayList<>();

    /** Contribution keys {@code (planId, day)} already written — the real UNIQUE index. */
    private final Set<String> contributionKeys = new HashSet<>();

    @BeforeEach
    void setUp() {
        service = new ExecuteInvestmentPlanService(
                planRepository, allocations, brokerAccountRepository, simulationRepository,
                strategyRepository, strategyPicker, createSimulationUseCase,
                recordContributionUseCase, dispatchNotificationUseCase);

        userId = UserId.generate();
        planId = InvestmentPlanId.generate();
        brokerAccountId = BrokerAccountId.generate();
        strategyId = StrategyId.generate();
        createdSimId = SimulationId.generate();

        plan = InvestmentPlan.create(
                planId, userId, brokerAccountId, BudgetMode.FIXED_AMOUNT, PLAN_AMOUNT,
                28, PickerPolicy.AI_PICK, InvestmentPlanPreferences.EMPTY,
                Instant.parse("2026-07-01T00:00:00Z"));

        BrokerAccount broker = mock(BrokerAccount.class);
        lenient().when(broker.getBrokerType()).thenReturn(BrokerType.MOCK);
        lenient().when(broker.getAvailableBalance()).thenReturn(Money.usd(new BigDecimal("10000.00")));

        lenient().when(planRepository.findById(planId)).thenReturn(Optional.of(plan));
        lenient().when(planRepository.save(any(InvestmentPlan.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        lenient().when(brokerAccountRepository.findById(brokerAccountId))
                .thenReturn(Optional.of(broker));
        lenient().when(strategyPicker.pick(any()))
                .thenReturn(StrategyPickerPort.Outcome.chose(strategyId, "picked", BigDecimal.ZERO));
        lenient().when(strategyRepository.findById(strategyId)).thenReturn(Optional.empty());

        // Only simulations the plan has actually created are visible, so call 1 takes the
        // NEW_SIMULATION branch and later calls see the live simulation it produced.
        lenient().when(simulationRepository.findByStrategyId(strategyId))
                .thenAnswer(inv -> List.copyOf(liveSimulations));
        lenient().when(simulationRepository.findById(createdSimId))
                .thenAnswer(inv -> liveSimulations.stream()
                        .filter(s -> createdSimId.equals(s.getSimulationId()))
                        .findFirst());

        lenient().when(createSimulationUseCase.execute(any(CreateSimulationCommand.class)))
                .thenAnswer(inv -> {
                    liveSimulations.add(liveSimulation());
                    return createdSimId;
                });

        // Stands in for the UNIQUE (source_plan_id, contribution_date) index and the probe in
        // SimulationContributionService: first write for a (plan, day) creates, the rest dedupe.
        lenient().when(recordContributionUseCase.execute(any(RecordSimulationContributionCommand.class)))
                .thenAnswer(inv -> {
                    RecordSimulationContributionCommand cmd = inv.getArgument(0);
                    String key = cmd.sourcePlanId() + "@"
                            + cmd.contributionDate().truncatedTo(ChronoUnit.DAYS);
                    Contribution row = new Contribution(
                            ContributionId.generate(), cmd.simulationId(), cmd.amount(),
                            cmd.contributionDate(), cmd.sourcePlanId(),
                            cmd.sourceAllocationId(), Instant.now());
                    return contributionKeys.add(key)
                            ? ContributionOutcome.created(row)
                            : ContributionOutcome.alreadyRecorded(row);
                });
    }

    private Simulation liveSimulation() {
        Simulation sim = mock(Simulation.class);
        lenient().when(sim.getStatus()).thenReturn(SimulationStatus.LIVE);
        lenient().when(sim.getBrokerAccountId()).thenReturn(brokerAccountId);
        lenient().when(sim.getSimulationId()).thenReturn(createdSimId);
        lenient().when(sim.getStrategyId()).thenReturn(strategyId);
        return sim;
    }

    private InvestmentAllocation tick(Instant at) {
        return service.execute(new ExecuteInvestmentPlanCommand(planId, userId, at, true));
    }

    /** What the plan actually deployed across every allocation row written so far. */
    private BigDecimal totalFunded() {
        return allocations.rows.stream()
                .filter(InvestmentAllocation::wasExecuted)
                .map(a -> a.getActualAmount().amount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * The reported three-call sequence, verbatim: a new-simulation funding followed by two
     * retries on the same day. Before the fix, call 2 funded again and the plan deployed $1000.
     */
    @Test
    @DisplayName("a new-simulation funding blocks the same day's retries — $500 total, not $1000")
    void newSimulationFundingBlocksSameDayRetries() {
        InvestmentAllocation first = tick(TICK);
        InvestmentAllocation second = tick(TICK);
        InvestmentAllocation third = tick(TICK);

        assertThat(first.getStatus())
                .as("precondition: the first tick must take the create branch, which writes no "
                    + "contribution row and is therefore invisible to the contribution guard")
                .isEqualTo(AllocationStatus.EXECUTED);
        assertThat(first.getAction()).isEqualTo(AllocationAction.NEW_SIMULATION);
        assertThat(first.getContributionId()).isNull();

        assertThat(totalFunded())
                .as("three ticks of a $500 plan on one day deployed $1000 — one contribution row "
                    + "but two EXECUTED allocation rows")
                .isEqualByComparingTo(new BigDecimal("500.00"));

        assertThat(second.getStatus())
                .as("the retry that used to fund a second time")
                .isEqualTo(AllocationStatus.SKIPPED_ALREADY_EXECUTED_TODAY);
        assertThat(third.getStatus()).isEqualTo(AllocationStatus.SKIPPED_ALREADY_EXECUTED_TODAY);
    }

    @Test
    @DisplayName("a same-day retry creates no second simulation and moves no cash")
    void sameDayRetryTouchesNothing() {
        tick(TICK);
        tick(TICK);

        verify(createSimulationUseCase, times(1))
                .execute(any()); // tick 1 only; the retry must not spawn a second simulation
        // Neither tick contributes: the create branch seeds initial capital instead, and the
        // retry is stopped before it reaches the funding step at all.
        verify(recordContributionUseCase, never()).execute(any());
        assertThat(allocations.rows).hasSize(2);
    }

    /**
     * The create branch is not only reached on a plan's first ever tick. When the funded
     * simulation stops being LIVE the plan takes it again — and before the fix that reopened the
     * double-funding window on a day the plan had already funded.
     */
    @Test
    @DisplayName("the window stays shut when the funded simulation is no longer LIVE")
    void windowStaysShutAfterTheFundedSimulationEnds() {
        tick(TICK);
        liveSimulations.clear(); // force-ended / completed / deleted

        InvestmentAllocation retry = tick(TICK);

        assertThat(retry.getStatus()).isEqualTo(AllocationStatus.SKIPPED_ALREADY_EXECUTED_TODAY);
        assertThat(totalFunded()).isEqualByComparingTo(new BigDecimal("500.00"));
    }

    @Test
    @DisplayName("the guard keys on the calendar day, not the instant")
    void guardKeysOnTheDayNotTheInstant() {
        tick(Instant.parse("2026-07-30T06:00:00Z"));

        InvestmentAllocation later = tick(Instant.parse("2026-07-30T23:30:00Z"));

        assertThat(later.getStatus())
                .as("execution_date is a timestamptz; keying on the raw instant makes every "
                    + "retry a distinct key, which is how the contribution guard was broken once")
                .isEqualTo(AllocationStatus.SKIPPED_ALREADY_EXECUTED_TODAY);
    }

    @Test
    @DisplayName("the next day funds again")
    void nextDayFundsAgain() {
        tick(TICK);

        InvestmentAllocation tomorrow = tick(Instant.parse("2026-07-31T06:00:00Z"));

        assertThat(tomorrow.wasExecuted())
                .as("over-rejection guard: a daily probe must not freeze the plan forever")
                .isTrue();
        assertThat(totalFunded()).isEqualByComparingTo(new BigDecimal("1000.00"));
    }

    /**
     * Over-rejection guard. Skips and failures are not fundings: a plan that skipped at 06:00 for
     * want of cash has to be able to fund at 18:00 once the money lands, and a
     * {@code FAILED_BROKER_ERROR} row is documented as reattemptable.
     */
    @Test
    @DisplayName("an earlier skip on the same day does not block a later funding")
    void anEarlierSkipDoesNotBlockFunding() {
        allocations.save(InvestmentAllocation.create(
                InvestmentAllocationId.generate(), planId, userId,
                Instant.parse("2026-07-30T06:00:00Z"),
                AllocationStatus.SKIPPED_INSUFFICIENT_FUNDS,
                PLAN_AMOUNT, null, null, "no cash", BigDecimal.ZERO,
                null, null, null, List.of(), "broker was empty at 06:00", TICK));

        assertThat(tick(TICK).wasExecuted())
                .as("only EXECUTED / EXECUTED_PARTIAL_DEPLOY rows mean money moved")
                .isTrue();
    }

    @Test
    @DisplayName("the skip reason no longer claims more than the guard can know")
    void skipReasonIsHonest() {
        tick(TICK);
        InvestmentAllocation retry = tick(TICK);

        assertThat(retry.getFailureReason())
                .as("the old copy said \"nothing was charged twice\" on the very run that had "
                    + "just charged twice — a claim the guard was in no position to make")
                .doesNotContain("charged twice");
        assertThat(retry.getFailureReason())
                .as("the row must name the funding it deduplicated against so the timeline can "
                    + "be reconciled")
                .contains(allocations.rows.getFirst().getId().value().toString());

        ArgumentCaptor<DispatchNotificationCommand> notifications =
                ArgumentCaptor.forClass(DispatchNotificationCommand.class);
        verify(dispatchNotificationUseCase, org.mockito.Mockito.atLeastOnce())
                .execute(notifications.capture());
        assertThat(notifications.getAllValues().getLast().body())
                .as("the user-facing copy repeated the same unearned reassurance")
                .doesNotContain("charged twice");
    }

    /**
     * The two same-day guards must not be able to disagree about what "today" is: if the
     * allocation probe used a different day basis from the contribution probe, one of them would
     * let a second funding through — the exact failure being fixed here. Both derive their day
     * from {@code PlanFundingDay}, and this pins the observable consequence for one raw instant.
     */
    @Test
    @DisplayName("both same-day guards key on the same UTC day")
    void bothGuardsUseTheSameDayBasis() {
        tick(TICK);

        assertThat(allocations.probes)
                .as("the allocation probe must ask about the whole calendar day")
                .contains(new Window(DAY_START, NEXT_DAY_START));
        assertThat(contributionProbeDateFor(TICK))
                .as("the contribution probe must ask about the same day start")
                .isEqualTo(DAY_START);
    }

    /**
     * What {@link SimulationContributionService} probes {@code (source_plan_id,
     * contribution_date)} with, given a raw execution instant. Exercised through the real service
     * so the comparison is against behaviour, not against a copy of the truncation.
     */
    private Instant contributionProbeDateFor(Instant rawExecutionInstant) {
        SimulationRepository sims = mock(SimulationRepository.class);
        SimulationPortfolioRepository portfolios = mock(SimulationPortfolioRepository.class);
        BrokerAccountRepository brokers = mock(BrokerAccountRepository.class);
        SimulationContributionRepository contributions = mock(SimulationContributionRepository.class);
        StrategyRepository strategies = mock(StrategyRepository.class);
        PortfolioRepository snapshots = mock(PortfolioRepository.class);

        Simulation sim = mock(Simulation.class);
        lenient().when(sim.getSimulationId()).thenReturn(createdSimId);
        lenient().when(sim.getStrategyId()).thenReturn(strategyId);
        lenient().when(sim.buildContribution(any(), any(), any(), any())).thenAnswer(inv ->
                new Contribution(ContributionId.generate(), createdSimId, PLAN_AMOUNT,
                        inv.getArgument(1), planId.value(), null, Instant.now()));
        when(sims.findById(createdSimId)).thenReturn(Optional.of(sim));
        Strategy strategy = mock(Strategy.class);
        lenient().when(strategy.getOwnerId()).thenReturn(userId);
        when(strategies.findById(strategyId)).thenReturn(Optional.of(strategy));
        SimulationPortfolio portfolio = mock(SimulationPortfolio.class);
        lenient().when(portfolio.getBrokerAccountId()).thenReturn(null);
        when(portfolios.findById(createdSimId)).thenReturn(Optional.of(portfolio));
        when(snapshots.findLatestBySimulationId(createdSimId)).thenReturn(Optional.of(
                new PortfolioSnapshot(createdSimId, rawExecutionInstant.minusSeconds(60),
                        Map.of(), PLAN_AMOUNT, PLAN_AMOUNT)));
        lenient().when(contributions.findByPlanAndDate(any(), any())).thenReturn(Optional.empty());
        lenient().when(contributions.save(any())).thenAnswer(inv -> inv.getArgument(0));

        new SimulationContributionService(sims, portfolios, brokers, contributions, strategies,
                new AuthorizationService(), snapshots)
                .execute(new RecordSimulationContributionCommand(
                        createdSimId, PLAN_AMOUNT, rawExecutionInstant, planId.value(), null, userId));

        ArgumentCaptor<Instant> probed = ArgumentCaptor.forClass(Instant.class);
        verify(contributions).findByPlanAndDate(any(), probed.capture());
        return probed.getValue();
    }

    /** A probe window, so the assertion can compare one value instead of two. */
    private record Window(Instant fromInclusive, Instant toExclusive) {}

    /**
     * A stateful stand-in for the allocation table. {@code findFundedOnDay} mirrors the JPQL in
     * {@code InvestmentAllocationRepositoryImpl}; the real query is covered against Postgres in
     * {@code InvestmentAllocationRoundTripIntegrationTest}.
     */
    private static final class RecordingAllocationRepository implements InvestmentAllocationRepository {

        private final List<InvestmentAllocation> rows = new ArrayList<>();
        private final List<Window> probes = new ArrayList<>();

        @Override
        public InvestmentAllocation save(InvestmentAllocation allocation) {
            rows.add(allocation);
            return allocation;
        }

        @Override
        public Optional<InvestmentAllocation> findById(InvestmentAllocationId id) {
            return rows.stream().filter(r -> r.getId().equals(id)).findFirst();
        }

        @Override
        public List<InvestmentAllocation> findByPlanId(InvestmentPlanId plan, int limit) {
            return rows.stream()
                    .filter(r -> r.getPlanId().equals(plan))
                    .sorted(Comparator.comparing(InvestmentAllocation::getExecutionDate).reversed())
                    .limit(limit)
                    .toList();
        }

        @Override
        public List<InvestmentAllocation> findByUserId(UserId user, int limit) {
            return rows.stream()
                    .filter(r -> r.getUserId().equals(user))
                    .sorted(Comparator.comparing(InvestmentAllocation::getExecutionDate).reversed())
                    .limit(limit)
                    .toList();
        }

        @Override
        public Optional<InvestmentAllocation> findFundedOnDay(InvestmentPlanId plan,
                                                             Instant fromInclusive,
                                                             Instant toExclusive) {
            probes.add(new Window(fromInclusive, toExclusive));
            return rows.stream()
                    .filter(r -> r.getPlanId().equals(plan))
                    .filter(InvestmentAllocation::wasExecuted)
                    .filter(r -> !r.getExecutionDate().isBefore(fromInclusive)
                            && r.getExecutionDate().isBefore(toExclusive))
                    .min(Comparator.comparing(InvestmentAllocation::getExecutionDate));
        }
    }
}
