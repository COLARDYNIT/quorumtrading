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
import com.quorumtrading.application.usecase.contribution.ContributionOutcome;
import com.quorumtrading.application.usecase.contribution.RecordSimulationContributionUseCase;
import com.quorumtrading.application.usecase.investmentplan.ExecuteInvestmentPlanUseCase;
import com.quorumtrading.application.usecase.notification.DispatchNotificationUseCase;
import com.quorumtrading.model.entity.BrokerAccount;
import com.quorumtrading.model.entity.Contribution;
import com.quorumtrading.model.entity.InvestmentAllocation;
import com.quorumtrading.model.entity.InvestmentPlan;
import com.quorumtrading.model.entity.Simulation;
import com.quorumtrading.model.entity.Strategy;
import com.quorumtrading.model.enums.AllocationAction;
import com.quorumtrading.model.enums.AllocationStatus;
import com.quorumtrading.model.enums.BudgetMode;
import com.quorumtrading.model.enums.InvestmentPlanStatus;
import com.quorumtrading.model.enums.NotificationType;
import com.quorumtrading.model.enums.SimulationStatus;
import com.quorumtrading.model.valueobject.ContributionId;
import com.quorumtrading.model.valueobject.InvestmentAllocationId;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.SimulationId;
import com.quorumtrading.model.valueobject.StrategyId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;

/**
 * Orchestrates one execution tick of an {@link InvestmentPlan}:
 *
 * <ol>
 *   <li>Validate ownership and ACTIVE status.</li>
 *   <li>Refuse to fund twice on the same day — see {@link #fundedEarlierToday}. The probe is
 *       against {@code investment_allocations}, not contributions, because the
 *       {@code NEW_SIMULATION} path writes no contribution row.</li>
 *   <li>Resolve contribution amount from broker buying power × budget mode.</li>
 *   <li>Pick a strategy via {@link StrategyPickerPort}.</li>
 *   <li>Find an existing LIVE simulation on (chosen strategy, broker) → top up; else create a
 *       new LIVE simulation, unless the plan's {@code maxStrategiesActive} cap is reached or
 *       {@code preferExistingHoldings} is set, in which case an existing live simulation funded
 *       by this plan is topped up instead.</li>
 *   <li>Persist an {@code InvestmentAllocation} audit row (EXECUTED / SKIPPED_* / FAILED_*).</li>
 *   <li>Dispatch a notification (fans out per-user preferences).</li>
 *   <li>Advance the plan's {@code nextExecutionAt} (unless caller opted out).</li>
 * </ol>
 *
 * <p>Skip and failure paths still write an allocation row and a notification —
 * the trail must be complete for the user to act on what happened.</p>
 */
public class ExecuteInvestmentPlanService implements ExecuteInvestmentPlanUseCase {

    private static final Logger logger = LoggerFactory.getLogger(ExecuteInvestmentPlanService.class);
    /**
     * Per-currency dust floor: below this we skip rather than fund, avoiding dust
     * contributions on {@code ALL_AVAILABLE} / partial budgets. The amount is the same
     * nominal magnitude in each currency — there is no FX in this system, so a EUR plan
     * is floored at €10 and a USD plan at $10. The USD value is byte-identical to the
     * pre-currency-thread {@code MIN_CONTRIBUTION_USD} constant.
     */
    private static final BigDecimal MIN_CONTRIBUTION_AMOUNT = new BigDecimal("10.00");

    /**
     * How far back to read a plan's allocation rows when working out which simulations it funds.
     * Deep enough to cover years of monthly ticks; the rows are then de-duplicated by simulation.
     */
    private static final int PLAN_HISTORY_LOOKBACK = 500;

    /**
     * The minimum fundable contribution expressed in the contribution's own currency, so
     * the comparison stays single-currency and never trips {@link Money}'s same-currency
     * guard. Keyed off {@code amount.currency()} rather than assuming USD.
     */
    private static Money minimumContributionFor(Money amount) {
        return Money.of(MIN_CONTRIBUTION_AMOUNT, amount.currency());
    }

    /**
     * Per-currency <em>dust floor</em> for the under-deployed residue of a whole-share
     * contribution. The full contribution is always recorded as cash, but a non-fractional
     * broker (Saxo equities/ETFs, D3) rounds equity orders down to whole shares, so a
     * sub-whole-unit remainder of the contribution cannot be put to work this tick and is
     * carried forward. Below this floor the residue is treated as fully deployed (no
     * {@code EXECUTED_PARTIAL_DEPLOY} noise on rounding crumbs); at or above it the allocation
     * is flagged partial with {@code deployedAmount} recorded.
     *
     * <p>One minor unit (a cent) — the same nominal magnitude in every currency, since there
     * is no FX in this system, so a EUR plan floors sub-€0.01 dust and a USD plan sub-$0.01.
     * The whole-unit deployable floor mirrors the scale-0 lot rounding that the share-sizing
     * pipeline (FX1/FX2) applies downstream once a price is known.</p>
     */
    private static final BigDecimal DEPLOY_DUST_FLOOR = new BigDecimal("0.01");

    /**
     * Project the portion of a whole-share contribution that can be put to work this tick.
     *
     * <p>This is the contribution floored to whole settlement-currency units: a
     * non-fractional broker rounds equity orders down to integral share counts, so the
     * sub-unit remainder cannot deploy and is carried to the next tick. Computed in the
     * contribution's own currency so it never trips {@link Money}'s same-currency guard. The
     * exact share-level residue is resolved later by the consensus sizing pipeline once a
     * price is known (FX2); this whole-unit floor is the conservative, price-free projection
     * the audit row can record at orchestration time.</p>
     *
     * @param actual the full contribution amount (its own currency)
     * @return the whole-unit-floored deployable amount, in {@code actual}'s currency
     */
    private static Money deployableFloor(Money actual) {
        BigDecimal whole = actual.amount().setScale(0, RoundingMode.DOWN);
        return Money.of(whole, actual.currency());
    }

    private final InvestmentPlanRepository planRepository;
    private final InvestmentAllocationRepository allocationRepository;
    private final BrokerAccountRepository brokerAccountRepository;
    private final SimulationRepository simulationRepository;
    private final StrategyRepository strategyRepository;
    private final StrategyPickerPort strategyPicker;
    private final CreateSimulationUseCase createSimulationUseCase;
    private final RecordSimulationContributionUseCase recordContributionUseCase;
    private final DispatchNotificationUseCase dispatchNotificationUseCase;

    public ExecuteInvestmentPlanService(
            InvestmentPlanRepository planRepository,
            InvestmentAllocationRepository allocationRepository,
            BrokerAccountRepository brokerAccountRepository,
            SimulationRepository simulationRepository,
            StrategyRepository strategyRepository,
            StrategyPickerPort strategyPicker,
            CreateSimulationUseCase createSimulationUseCase,
            RecordSimulationContributionUseCase recordContributionUseCase,
            DispatchNotificationUseCase dispatchNotificationUseCase) {
        this.planRepository = planRepository;
        this.allocationRepository = allocationRepository;
        this.brokerAccountRepository = brokerAccountRepository;
        this.simulationRepository = simulationRepository;
        this.strategyRepository = strategyRepository;
        this.strategyPicker = strategyPicker;
        this.createSimulationUseCase = createSimulationUseCase;
        this.recordContributionUseCase = recordContributionUseCase;
        this.dispatchNotificationUseCase = dispatchNotificationUseCase;
    }

    @Override
    public InvestmentAllocation execute(ExecuteInvestmentPlanCommand command) {
        InvestmentPlan plan = planRepository.findById(command.planId())
                .orElseThrow(() -> new NoSuchElementException("Plan not found: " + command.planId()));
        if (!plan.getUserId().equals(command.userId())) {
            throw new SecurityException("Investment plan does not belong to user: " + command.userId());
        }
        // Only an ACTIVE plan may execute — manual ticks included.
        //
        // The scheduler filters on status='ACTIVE' in its query, so it never reached this method
        // with a paused plan and the missing guard stayed invisible. A manual execute had no such
        // filter: pausing a plan to stop it spending money did not stop the one call that spends
        // money, which on an ALPACA_LIVE broker means a real order from a plan the user believed
        // was halted.
        if (plan.getStatus() != InvestmentPlanStatus.ACTIVE) {
            throw new IllegalStateException(
                "Investment plan " + plan.getId().value() + " is " + plan.getStatus()
                + " and cannot be executed. "
                + (plan.getStatus() == InvestmentPlanStatus.PAUSED
                    ? "Resume it first if you want this contribution to go ahead."
                    : "Only an ACTIVE plan can be executed, and a TERMINATED plan "
                      + "cannot be executed or resumed."));
        }

        BrokerAccount broker = brokerAccountRepository.findById(plan.getBrokerAccountId())
                .orElseThrow(() -> new NoSuchElementException(
                        "Broker account not found: " + plan.getBrokerAccountId()));
        Money intended = intendedAmountFor(plan, broker);

        // 0. Same-day guard. Runs before the budget check, before the picker (so a no-op tick
        // never burns LLM budget) and before anything moves money.
        Optional<InvestmentAllocation> fundedToday = fundedEarlierToday(plan, command);
        if (fundedToday.isPresent()) {
            return recordAlreadyFunded(plan, command, intended, fundedToday.get());
        }

        // 1. Budget check.
        Money available = broker.getAvailableBalance();
        if (!hasSufficientFunds(plan, intended, available)) {
            return recordOutcome(plan, command, AllocationStatus.SKIPPED_INSUFFICIENT_FUNDS,
                    intended, null, null, null, null, null, null, null,
                    // format(), not the record's toString(): this string is the skip reason shown
                    // in the user's notification and the allocation timeline, and concatenating a
                    // Money rendered it as "Money[amount=20000.00000000, currency=USD]".
                    "Broker available balance " + available.format()
                            + " is less than the plan amount " + intended.format());
        }
        Money actual = resolveActualAmount(plan, intended, available);
        Money minimum = minimumContributionFor(actual);
        if (actual.isLessThan(minimum)) {
            return recordOutcome(plan, command, AllocationStatus.SKIPPED_INSUFFICIENT_FUNDS,
                    intended, null, null, null, null, null, null, null,
                    "Resolved contribution " + actual.format() + " is below the " + minimum.format() + " minimum");
        }

        // 2. Picker.
        StrategyPickerPort.Outcome pick = strategyPicker.pick(plan);
        if (!pick.isChoice()) {
            return recordOutcome(plan, command, AllocationStatus.SKIPPED_NO_ELIGIBLE_STRATEGY,
                    intended, null, null, pick.reasoning(), pick.llmCostUsd(), null, null, null,
                    pick.reasoning());
        }
        StrategyId strategyId = pick.chosenStrategyId().get();

        // 3. Defense-in-depth: pickers pre-filter by ticker policy, but if a buggy
        // picker returns a violating strategy, refuse to fund it.
        Optional<Strategy> picked = strategyRepository.findById(strategyId);
        if (picked.isPresent()) {
            TickerPolicyEnforcer.Decision policyCheck =
                    TickerPolicyEnforcer.evaluate(picked.get(), plan.getPreferences());
            if (!policyCheck.allowed()) {
                logger.error("Picker returned strategy {} that violates ticker policy for plan {}: {}",
                        strategyId, plan.getId(), policyCheck.reason());
                return recordOutcome(plan, command, AllocationStatus.SKIPPED_TICKER_POLICY_VIOLATION,
                        intended, null, strategyId, pick.reasoning(), pick.llmCostUsd(),
                        null, null, null,
                        "Picker chose a strategy that violates ticker policy: " + policyCheck.reason());
            }
        }

        // 4. Top-up vs new simulation.
        //
        // The full contribution is always recorded as cash regardless of broker. Whether
        // that cash can fully deploy into shares this tick depends on the executing broker:
        // a non-fractional broker (Saxo equities/ETFs, D3) rounds equity orders down to whole
        // shares, so a sub-whole-unit remainder of the contribution is carried forward. We
        // surface that as a fractional-constraint flag (+ partial-deploy status + deployedAmount)
        // on the audit row; the cash contribution itself is untouched.
        boolean fractionalConstraint = !broker.getBrokerType().supportsFractionalShares();
        Optional<Simulation> existing = findLiveSimulation(strategyId, plan);

        // 4a. Enforce the two picker preferences the plan advertises.
        //
        // maxStrategiesActive and preferExistingHoldings were accepted, stored and echoed back by
        // the API but nothing acted on them: the default heuristic picker filters on ACTIVE status
        // and ticker policy only, and maxStrategiesActive appeared in main code solely inside the
        // LLM picker's prompt text, where it is advisory at best. A plan capped at one strategy was
        // observed running two live simulations, and because the heuristic picks the most-recently
        // *updated* strategy, merely editing any strategy silently redirected the next contribution
        // into a brand-new simulation. Enforced here rather than in the picker for the same reason
        // ticker policy is: this is the last point before money moves, and a picker cannot be
        // trusted to police it.
        String redirectNote = null;
        if (existing.isEmpty()) {
            List<Simulation> planLive = liveSimulationsFundedByPlan(plan);
            Integer cap = plan.getPreferences().maxStrategiesActive();
            boolean atCap = cap != null && planLive.size() >= cap;
            boolean prefersTopUp = plan.getPreferences().preferExistingHoldings();

            if ((atCap || prefersTopUp) && !planLive.isEmpty()) {
                Simulation topUp = planLive.getFirst();
                redirectNote = atCap
                        ? "Plan is at its maxStrategiesActive cap of " + cap
                          + ", so this contribution topped up the existing live simulation "
                          + topUp.getSimulationId().value() + " instead of starting a new one."
                        : "preferExistingHoldings is set, so this contribution topped up the existing "
                          + "live simulation " + topUp.getSimulationId().value()
                          + " instead of starting a new one.";
                logger.info("Plan {} redirected to existing simulation {} ({})",
                        plan.getId(), topUp.getSimulationId().value(), atCap ? "cap reached" : "prefers top-up");
                existing = Optional.of(topUp);
                strategyId = topUp.getStrategyId();
            }
        }
        final String topUpReason = redirectNote;

        // Mint the allocation id up front so the contribution can cite the row that records it.
        // This used to pass InvestmentAllocationId.generate() inline as a "placeholder", which
        // meant every contribution referenced an allocation that was never written: the two
        // halves of the audit trail could not be joined, and get_investment_allocation answered
        // "Allocation not found" for the id on every contribution row.
        final InvestmentAllocationId allocationId = InvestmentAllocationId.generate();

        try {
            return existing.isPresent()
                    ? topUpExisting(plan, command, existing.get(), intended, actual, strategyId,
                            pick, topUpReason, fractionalConstraint, allocationId)
                    : startNewSimulation(plan, command, intended, actual, strategyId, pick,
                            fractionalConstraint, allocationId);
        } catch (RuntimeException e) {
            logger.error("Plan {} execution failed at fund/create step", plan.getId(), e);
            // e.getMessage() is null for NPE and other message-less exceptions, which leaves the
            // allocation row blank and the "Your broker rejected …" copy misleadingly generic.
            // Fall back to the exception class name so failure investigations have a thread to pull.
            String reason = e.getMessage() != null && !e.getMessage().isBlank()
                    ? e.getMessage()
                    : e.getClass().getSimpleName();
            return recordOutcome(plan, command, AllocationStatus.FAILED_BROKER_ERROR,
                    intended, null, strategyId, pick.reasoning(), pick.llmCostUsd(), null, null, null,
                    reason);
        }
    }

    /** Fund an existing LIVE simulation, distinguishing a real contribution from a same-day repeat. */
    private InvestmentAllocation topUpExisting(InvestmentPlan plan, ExecuteInvestmentPlanCommand command,
                                               Simulation target, Money intended, Money actual,
                                               StrategyId strategyId, StrategyPickerPort.Outcome pick,
                                               String topUpReason, boolean fractionalConstraint,
                                               InvestmentAllocationId allocationId) {
        ContributionOutcome outcome = recordContributionUseCase.execute(
                new RecordSimulationContributionCommand(
                        target.getSimulationId(),
                        actual,
                        command.executionDate(),
                        plan.getId().value(),
                        allocationId.value(),
                        plan.getUserId()
                ));
        String reasoning = topUpReason == null
                ? pick.reasoning()
                : pick.reasoning() + " " + topUpReason;
        if (outcome.alreadyRecorded()) {
            return recordNoOpRepeat(plan, command, intended, actual, strategyId,
                    reasoning, pick.llmCostUsd(), target.getSimulationId(), outcome, allocationId);
        }
        return recordExecuted(plan, command, intended, actual, strategyId,
                reasoning, pick.llmCostUsd(), AllocationAction.TOP_UP_EXISTING,
                target.getSimulationId(), outcome.contribution().id(),
                fractionalConstraint, allocationId);
    }

    /** Start a new LIVE simulation seeded with the contribution as its initial cash. */
    private InvestmentAllocation startNewSimulation(InvestmentPlan plan, ExecuteInvestmentPlanCommand command,
                                                    Money intended, Money actual, StrategyId strategyId,
                                                    StrategyPickerPort.Outcome pick,
                                                    boolean fractionalConstraint,
                                                    InvestmentAllocationId allocationId) {
        SimulationId newSimId = createSimulationUseCase.execute(new CreateSimulationCommand(
                "Plan " + plan.getId().value() + " — " + command.executionDate(),
                strategyId,
                command.executionDate(),
                null, // null endDate → LIVE
                actual,
                plan.getBrokerAccountId(),
                actual,
                null,
                plan.getUserId()));
        return recordExecuted(plan, command, intended, actual, strategyId,
                pick.reasoning(), pick.llmCostUsd(), AllocationAction.NEW_SIMULATION,
                newSimId, null, fractionalConstraint, allocationId);
    }

    // ----- Helpers -----

    /**
     * The funding this plan already made today, if any — the same-day guard that covers
     * <em>both</em> funding paths.
     *
     * <p>Idempotency used to rest entirely on the contribution key {@code (source_plan_id,
     * contribution_date)}. That guard is real and still in place, but it can only see a tick that
     * wrote a contribution row, and the {@code NEW_SIMULATION} branch of find-or-create writes
     * none — the cash becomes the new simulation's initial capital, which is not a contribution
     * (and must not be recorded as one: {@code PerformanceCalculator} breaks TWR sub-periods at
     * contributions, so inventing one for initial capital would corrupt the performance maths).
     * The first funding was therefore invisible to the only guard that existed, and exactly one
     * extra funding always slipped through — $1000 deployed on a day a $500 plan intended $500.
     * Nor was that limited to a plan's first ever tick: the create branch is taken again every
     * time the previously funded simulation stops being LIVE, reopening the window.</p>
     *
     * <p>Probing {@code investment_allocations} instead fixes both paths at once, because that
     * table already holds exactly one row per execution attempt regardless of which branch ran.
     * Only {@code EXECUTED} / {@code EXECUTED_PARTIAL_DEPLOY} rows count as a funding: an earlier
     * {@code SKIPPED_INSUFFICIENT_FUNDS} tick must stay retryable once the cash lands, and a
     * {@code FAILED_BROKER_ERROR} row is documented as reattemptable.</p>
     *
     * <p><b>A race remains.</b> This is a read-then-write check, so two ticks running
     * concurrently can both find nothing and both fund. The database backstops one of the two
     * paths only: {@code UNIQUE (source_plan_id, contribution_date)} still makes a concurrent
     * pair of <em>top-ups</em> safe (the loser's insert fails and the plan is charged once), but
     * there is no equivalent constraint on {@code investment_allocations}, so two concurrent
     * ticks that both take the {@code NEW_SIMULATION} branch can still fund twice. In practice
     * the schedulers are singletons behind a distributed lock and manual executes are
     * user-driven, so the window is narrow — but it is open, and closing it needs either a
     * per-plan lock around this method or a unique index on (plan, funding day) that existing
     * duplicate rows would first have to be reconciled against.</p>
     */
    private Optional<InvestmentAllocation> fundedEarlierToday(InvestmentPlan plan,
                                                              ExecuteInvestmentPlanCommand command) {
        return allocationRepository.findFundedOnDay(
                plan.getId(),
                PlanFundingDay.startOf(command.executionDate()),
                PlanFundingDay.endOf(command.executionDate()));
    }

    /**
     * Record a tick that the same-day guard stopped before it could fund a second time.
     *
     * <p>Reports zero deployed and points at the earlier allocation, so the timeline can be
     * reconciled against the funding that did happen. The reason states only what this run did:
     * the previous copy claimed "nothing was charged twice", which was true of the guard's view
     * and false of reality on the very run where the second charge had already gone through.</p>
     */
    private InvestmentAllocation recordAlreadyFunded(InvestmentPlan plan,
                                                    ExecuteInvestmentPlanCommand command,
                                                    Money intended,
                                                    InvestmentAllocation earlier) {
        String day = PlanFundingDay.label(command.executionDate());
        logger.info("Plan {} already funded on {} (allocation {}); recording a no-op tick",
                plan.getId(), day, earlier.getId());
        StringBuilder reason = new StringBuilder("This plan already funded on ")
                .append(day).append(" (UTC): allocation ").append(earlier.getId().value());
        if (earlier.getActualAmount() != null) {
            reason.append(" deployed ").append(earlier.getActualAmount().format());
        }
        if (earlier.getTargetSimulationId() != null) {
            reason.append(" into simulation ").append(earlier.getTargetSimulationId().value());
        }
        reason.append(". This run deployed nothing and committed no further cash.");
        return recordOutcome(plan, command, AllocationStatus.SKIPPED_ALREADY_EXECUTED_TODAY,
                intended, Money.zero(intended.currency()), earlier.getChosenStrategyId(),
                null, null, null, earlier.getTargetSimulationId(), earlier.getContributionId(),
                reason.toString());
    }

    private Money intendedAmountFor(InvestmentPlan plan, BrokerAccount broker) {
        if (plan.getBudgetMode() == BudgetMode.ALL_AVAILABLE) {
            return broker.getAvailableBalance();
        }
        return plan.getAmount();
    }

    private boolean hasSufficientFunds(InvestmentPlan plan, Money intended, Money available) {
        if (plan.getBudgetMode() == BudgetMode.FIXED_AMOUNT) {
            return !available.isLessThan(intended);
        }
        // FIXED_OR_PARTIAL accepts any positive available; ALL_AVAILABLE is sufficient by construction.
        return available.isPositive();
    }

    private Money resolveActualAmount(InvestmentPlan plan, Money intended, Money available) {
        if (plan.getBudgetMode() == BudgetMode.ALL_AVAILABLE) {
            return available;
        }
        if (plan.getBudgetMode() == BudgetMode.FIXED_AMOUNT) {
            return intended;
        }
        // FIXED_OR_PARTIAL: clamp to available.
        return available.isLessThan(intended) ? available : intended;
    }

    /**
     * The LIVE simulations this plan has actually funded, newest contribution first.
     *
     * <p>Derived from the plan's own allocation rows rather than "every LIVE simulation on this
     * broker": a user may run simulations on the same broker that the plan never funded, and those
     * must not count against the plan's cap or become top-up targets.</p>
     */
    private List<Simulation> liveSimulationsFundedByPlan(InvestmentPlan plan) {
        return allocationRepository.findByPlanId(plan.getId(), PLAN_HISTORY_LOOKBACK).stream()
                .map(InvestmentAllocation::getTargetSimulationId)
                .filter(Objects::nonNull)
                .distinct()
                .map(simulationRepository::findById)
                .flatMap(Optional::stream)
                .filter(s -> s.getStatus() == SimulationStatus.LIVE)
                .toList();
    }

    private Optional<Simulation> findLiveSimulation(StrategyId strategyId, InvestmentPlan plan) {
        List<Simulation> matches = simulationRepository.findByStrategyId(strategyId);
        return matches.stream()
                .filter(s -> s.getStatus() == SimulationStatus.LIVE)
                .filter(s -> plan.getBrokerAccountId().equals(s.getBrokerAccountId()))
                .findFirst();
    }

    /**
     * Finalize an EXECUTED contribution, surfacing the executing broker's fractional
     * constraint on the audit row.
     *
     * <p>The full {@code actual} contribution is always recorded as {@code actualAmount}.
     * When the broker fills whole shares only, the sub-whole-unit remainder cannot deploy
     * this tick: if that residue clears the currency-aware {@link #DEPLOY_DUST_FLOOR}, the
     * row is {@code EXECUTED_PARTIAL_DEPLOY} with {@code deployedAmount} set to the
     * whole-unit-floored deployable portion; below the floor (or for a fractional broker)
     * it is a plain {@code EXECUTED} with {@code deployedAmount=null}. {@code fractionalConstraint}
     * tracks the broker capability independently of whether a partial deploy actually
     * occurred, so the timeline always reflects the venue's whole-share semantics.</p>
     */
    /**
     * Record a tick that matched an existing contribution and therefore moved no money.
     *
     * <p>This is a successful no-op — it is what makes a retried scheduler tick safe — but it was
     * recorded as {@code EXECUTED} with the full plan amount, so three same-day ticks read as
     * $1500 deployed against one real $500 contribution and each sent a "plan funded"
     * notification. The row names the contribution it deduplicated against and reports zero.</p>
     */
    private InvestmentAllocation recordNoOpRepeat(InvestmentPlan plan,
                                                  ExecuteInvestmentPlanCommand command,
                                                  Money intended, Money actual,
                                                  StrategyId strategyId, String reasoning,
                                                  BigDecimal llmCost, SimulationId targetSimulationId,
                                                  ContributionOutcome outcome,
                                                  InvestmentAllocationId allocationId) {
        logger.info("Plan {} already contributed on {}; recording no-op tick against existing "
                        + "contribution {}",
                plan.getId(), command.executionDate(), outcome.contribution().id());
        return recordOutcome(plan, command, AllocationStatus.SKIPPED_ALREADY_EXECUTED_TODAY,
                intended, Money.zero(actual.currency()), strategyId,
                reasoning, llmCost, AllocationAction.TOP_UP_EXISTING,
                targetSimulationId, outcome.contribution().id(),
                "This plan already contributed on "
                        + PlanFundingDay.label(command.executionDate())
                        + " (UTC), so this run deployed nothing. Contribution "
                        + outcome.contribution().id().value()
                        + " stands and this run committed no further cash.",
                allocationId);
    }

    private InvestmentAllocation recordExecuted(InvestmentPlan plan, ExecuteInvestmentPlanCommand command,
                                                Money intended, Money actual, StrategyId chosenStrategy,
                                                String reasoning, BigDecimal llmCost, AllocationAction action,
                                                SimulationId targetSimulationId, ContributionId contributionId,
                                                boolean fractionalConstraint,
                                                InvestmentAllocationId allocationId) {
        AllocationStatus status = AllocationStatus.EXECUTED;
        Money deployedAmount = null;
        if (fractionalConstraint) {
            Money deployable = deployableFloor(actual);
            Money dust = actual.subtract(deployable);
            // Only flag a partial deploy when the under-deployed residue is material; sub-floor
            // crumbs are treated as fully deployed to avoid EXECUTED_PARTIAL_DEPLOY noise.
            boolean dustIsMaterial = dust.amount().compareTo(DEPLOY_DUST_FLOOR) >= 0;
            if (dustIsMaterial) {
                status = AllocationStatus.EXECUTED_PARTIAL_DEPLOY;
                deployedAmount = deployable;
            }
        }
        return recordOutcome(plan, command, new AllocationDraft(status, intended, actual,
                chosenStrategy, reasoning, llmCost, action, targetSimulationId, contributionId,
                null, deployedAmount, fractionalConstraint, allocationId));
    }

    /**
     * Persist the allocation row, advance the plan (unless caller opted out), and
     * dispatch the appropriate notification. Skip/fail paths pass {@code deployedAmount=null}
     * and {@code fractionalConstraint=false}; EXECUTED paths route through {@link #recordExecuted}.
     */
    private InvestmentAllocation recordOutcome(InvestmentPlan plan, ExecuteInvestmentPlanCommand command,
                                          AllocationStatus status, Money intended, Money actual,
                                          StrategyId chosenStrategy, String reasoning,
                                          BigDecimal llmCost, AllocationAction action,
                                          SimulationId targetSimulationId, ContributionId contributionId,
                                          String failureReason) {
        return recordOutcome(plan, command, new AllocationDraft(status, intended, actual,
                chosenStrategy, reasoning, llmCost, action, targetSimulationId, contributionId,
                failureReason, null, false, InvestmentAllocationId.generate()));
    }

    /** Skip/no-op variant that needs to reuse an id already handed to a contribution. */
    private InvestmentAllocation recordOutcome(InvestmentPlan plan, ExecuteInvestmentPlanCommand command,
                                          AllocationStatus status, Money intended, Money actual,
                                          StrategyId chosenStrategy, String reasoning,
                                          BigDecimal llmCost, AllocationAction action,
                                          SimulationId targetSimulationId, ContributionId contributionId,
                                          String failureReason, InvestmentAllocationId allocationId) {
        return recordOutcome(plan, command, new AllocationDraft(status, intended, actual,
                chosenStrategy, reasoning, llmCost, action, targetSimulationId, contributionId,
                failureReason, null, false, allocationId));
    }

    /**
     * Everything one allocation row records, gathered so the write path takes an outcome rather
     * than fifteen positional arguments — which is both unreadable and easy to mis-order when two
     * neighbouring parameters share a type (there are three {@code Money} fields here).
     */
    private record AllocationDraft(
            AllocationStatus status,
            Money intended,
            Money actual,
            StrategyId chosenStrategy,
            String reasoning,
            BigDecimal llmCost,
            AllocationAction action,
            SimulationId targetSimulationId,
            ContributionId contributionId,
            String failureReason,
            Money deployedAmount,
            boolean fractionalConstraint,
            InvestmentAllocationId allocationId) {}

    private InvestmentAllocation recordOutcome(InvestmentPlan plan, ExecuteInvestmentPlanCommand command,
                                               AllocationDraft draft) {
        Instant now = Instant.now();
        InvestmentAllocation allocation = InvestmentAllocation.create(
                draft.allocationId(),
                plan.getId(),
                plan.getUserId(),
                command.executionDate(),
                draft.status(),
                draft.intended(),
                draft.actual(),
                draft.chosenStrategy(),
                draft.reasoning(),
                draft.llmCost(),
                draft.action(),
                draft.targetSimulationId(),
                draft.contributionId(),
                List.of(),
                draft.failureReason(),
                draft.deployedAmount(),
                draft.fractionalConstraint(),
                now);
        InvestmentAllocation saved = allocationRepository.save(allocation);

        if (command.advanceSchedule()) {
            plan.recordExecutionTick(now);
            planRepository.save(plan);
        }

        try {
            dispatchNotificationUseCase.execute(notificationFor(plan, saved));
        } catch (RuntimeException e) {
            // Notification failure must NOT roll back the allocation — log and move on.
            logger.warn("Failed to dispatch notification for allocation {} (plan {})",
                    saved.getId(), plan.getId(), e);
        }

        logger.info("Plan {} execution finalized: status={}, allocation={}",
                plan.getId(), draft.status(), saved.getId());
        return saved;
    }

    private DispatchNotificationCommand notificationFor(InvestmentPlan plan, InvestmentAllocation allocation) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("planId", plan.getId().value().toString());
        payload.put("allocationId", allocation.getId().value().toString());
        payload.put("status", allocation.getStatus().name());
        if (allocation.getActualAmount() != null) {
            payload.put("actualAmount", allocation.getActualAmount().amount().toPlainString());
            payload.put("currency", allocation.getActualAmount().currency().name());
        }
        if (allocation.getTargetSimulationId() != null) {
            payload.put("simulationId", allocation.getTargetSimulationId().value().toString());
        }
        if (allocation.getChosenStrategyId() != null) {
            payload.put("strategyId", allocation.getChosenStrategyId().value().toString());
        }
        if (allocation.getFailureReason() != null) {
            payload.put("failureReason", allocation.getFailureReason());
        }

        return switch (allocation.getStatus()) {
            case EXECUTED -> new DispatchNotificationCommand(
                    plan.getUserId(),
                    NotificationType.INVESTMENT_EXECUTED,
                    "Investment plan funded",
                    String.format("Allocated %s to %s.",
                            // Money's record toString() returns "Money[amount=...,currency=...]"
                            // which leaks into notification copy. Use the human-readable formatter.
                            allocation.getActualAmount().format(),
                            allocation.getAction() == AllocationAction.TOP_UP_EXISTING
                                    ? "an existing simulation"
                                    : "a new simulation"),
                    payload,
                    allocation.getId().value());
            // Money moved; only its deployment was rounded down by a whole-share broker. Routing
            // this to the default branch announced "Investment plan skipped" for a funded tick,
            // sending the user to look for money that is exactly where it should be.
            case EXECUTED_PARTIAL_DEPLOY -> new DispatchNotificationCommand(
                    plan.getUserId(),
                    NotificationType.INVESTMENT_EXECUTED,
                    "Investment plan funded",
                    String.format("Contributed %s to %s. Your broker trades whole shares, so %s "
                                    + "was put to work this run and the remainder stays as cash "
                                    + "for the next one.",
                            allocation.getActualAmount().format(),
                            allocation.getAction() == AllocationAction.TOP_UP_EXISTING
                                    ? "an existing simulation"
                                    : "a new simulation",
                            allocation.getDeployedAmount() != null
                                    ? allocation.getDeployedAmount().format()
                                    : "part of it"),
                    payload,
                    allocation.getId().value());
            // A benign no-op: the plan already contributed today and the retry changed nothing.
            // Worth telling the user precisely, because "skipped" reads as a missed investment.
            case SKIPPED_ALREADY_EXECUTED_TODAY -> new DispatchNotificationCommand(
                    plan.getUserId(),
                    NotificationType.INVESTMENT_SKIPPED,
                    "Investment plan already funded today",
                    "This plan had already funded today, so this run deployed nothing and "
                            + "committed no further cash. The earlier funding stands.",
                    payload,
                    allocation.getId().value());
            case FAILED_BROKER_ERROR -> new DispatchNotificationCommand(
                    plan.getUserId(),
                    NotificationType.INVESTMENT_FAILED,
                    "Investment plan execution failed",
                    "Your broker rejected this month's contribution. " +
                            (allocation.getFailureReason() != null ? allocation.getFailureReason() : ""),
                    payload,
                    allocation.getId().value());
            default -> new DispatchNotificationCommand(
                    plan.getUserId(),
                    NotificationType.INVESTMENT_SKIPPED,
                    "Investment plan skipped",
                    "Your scheduled investment was skipped: " +
                            (allocation.getFailureReason() != null ? allocation.getFailureReason() : allocation.getStatus().name()),
                    payload,
                    allocation.getId().value());
        };
    }
}
