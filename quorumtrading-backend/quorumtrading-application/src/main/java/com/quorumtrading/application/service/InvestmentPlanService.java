package com.quorumtrading.application.service;

import com.quorumtrading.application.port.out.BrokerAccountRepository;
import com.quorumtrading.application.port.out.InvestmentAllocationRepository;
import com.quorumtrading.application.port.out.InvestmentPlanRepository;
import com.quorumtrading.application.usecase.command.CreateInvestmentPlanCommand;
import com.quorumtrading.application.usecase.command.UpdateInvestmentPlanCommand;
import com.quorumtrading.application.usecase.investmentplan.CreateInvestmentPlanUseCase;
import com.quorumtrading.application.usecase.investmentplan.GetInvestmentAllocationUseCase;
import com.quorumtrading.application.usecase.investmentplan.GetInvestmentPlanUseCase;
import com.quorumtrading.application.usecase.investmentplan.ListInvestmentAllocationsUseCase;
import com.quorumtrading.application.usecase.investmentplan.ListInvestmentPlansUseCase;
import com.quorumtrading.application.usecase.investmentplan.PauseInvestmentPlanUseCase;
import com.quorumtrading.application.usecase.investmentplan.ResumeInvestmentPlanUseCase;
import com.quorumtrading.application.usecase.investmentplan.TerminateInvestmentPlanUseCase;
import com.quorumtrading.application.usecase.investmentplan.UpdateInvestmentPlanUseCase;
import com.quorumtrading.model.entity.BrokerAccount;
import com.quorumtrading.model.entity.InvestmentAllocation;
import com.quorumtrading.model.entity.InvestmentPlan;
import com.quorumtrading.model.enums.BrokerType;
import com.quorumtrading.model.valueobject.BrokerAccountId;
import com.quorumtrading.model.enums.InvestmentPlanStatus;
import com.quorumtrading.model.valueobject.InvestmentAllocationId;
import com.quorumtrading.model.valueobject.InvestmentPlanId;
import com.quorumtrading.model.valueobject.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * CRUD + read service for {@code InvestmentPlan} and its allocation audit
 * trail. Execution orchestration lives in {@code ExecuteInvestmentPlanService}
 * because it has a much wider dependency footprint.
 */
public class InvestmentPlanService implements
        CreateInvestmentPlanUseCase,
        UpdateInvestmentPlanUseCase,
        PauseInvestmentPlanUseCase,
        ResumeInvestmentPlanUseCase,
        TerminateInvestmentPlanUseCase,
        GetInvestmentPlanUseCase,
        ListInvestmentPlansUseCase,
        ListInvestmentAllocationsUseCase,
        GetInvestmentAllocationUseCase {

    private static final Logger logger = LoggerFactory.getLogger(InvestmentPlanService.class);

    private final InvestmentPlanRepository planRepository;
    private final InvestmentAllocationRepository allocationRepository;
    private final BrokerAccountRepository brokerAccountRepository;

    public InvestmentPlanService(InvestmentPlanRepository planRepository,
                                  InvestmentAllocationRepository allocationRepository,
                                  BrokerAccountRepository brokerAccountRepository) {
        this.planRepository = planRepository;
        this.allocationRepository = allocationRepository;
        this.brokerAccountRepository = brokerAccountRepository;
    }

    // ----- Create / Update / Lifecycle -----

    @Override
    public InvestmentPlan execute(CreateInvestmentPlanCommand command) {
        BrokerAccount broker = requireUsableBroker(
                command.brokerAccountId(), command.userId(), command.confirmLive());

        InvestmentPlan plan = InvestmentPlan.create(
                InvestmentPlanId.generate(),
                command.userId(),
                command.brokerAccountId(),
                command.budgetMode(),
                command.amount(),
                command.dayOfMonth(),
                command.pickerPolicy(),
                command.preferences(),
                Instant.now());

        InvestmentPlan saved = planRepository.save(plan);
        logger.info("Created investment plan {} for user {} (broker={}, day={})",
                saved.getId(), command.userId(), broker.getBrokerType(), command.dayOfMonth());
        return saved;
    }

    @Override
    public InvestmentPlan execute(UpdateInvestmentPlanCommand command) {
        InvestmentPlan plan = loadOwnedPlan(command.planId(), command.userId());
        Instant now = Instant.now();
        // Repointing a plan at another broker account is exactly as consequential as choosing one at
        // creation: the next scheduled tick spends from whatever account the plan names. This path
        // previously accepted any UUID with no existence, ownership or live-money check, so a plan
        // created against a mock account could be moved onto a live one and start trading real money
        // without ever acknowledging confirmLive.
        command.brokerAccountId().ifPresent(b -> {
            requireUsableBroker(b, command.userId(), command.confirmLive());
            plan.updateBrokerAccount(b, now);
        });
        if (command.budgetMode().isPresent() || command.amount().isPresent()) {
            plan.updateBudget(
                    command.budgetMode().orElse(plan.getBudgetMode()),
                    command.amount().orElse(plan.getAmount()),
                    now);
        }
        command.dayOfMonth().ifPresent(d -> plan.updateSchedule(d, now));
        // Merge, never replace. The command carries a patch precisely because both driving adapters
        // used to rebuild the whole preferences object from the arguments they happened to receive,
        // so naming one preference wiped the others — an allow-list confining a plan to BTC vanished
        // during an unrelated amount edit, widening the picker to the user's entire strategy pool.
        // The entity validates the merged result, so a patch cannot introduce a policy creation
        // would refuse.
        command.preferences()
                .filter(patch -> !patch.isEmpty())
                .ifPresent(patch -> plan.updatePreferences(patch.applyTo(plan.getPreferences()), now));
        return planRepository.save(plan);
    }

    @Override
    public InvestmentPlan pause(InvestmentPlanId planId, UserId userId) {
        InvestmentPlan plan = loadOwnedPlan(planId, userId);
        plan.pause(Instant.now());
        return planRepository.save(plan);
    }

    @Override
    public InvestmentPlan resume(InvestmentPlanId planId, UserId userId) {
        InvestmentPlan plan = loadOwnedPlan(planId, userId);
        plan.resume(Instant.now());
        return planRepository.save(plan);
    }

    @Override
    public void terminate(InvestmentPlanId planId, UserId userId) {
        InvestmentPlan plan = loadOwnedPlan(planId, userId);
        plan.terminate(Instant.now());
        planRepository.save(plan);
    }

    // ----- Queries -----

    @Override
    public InvestmentPlan getOwnedBy(InvestmentPlanId planId, UserId userId) {
        return loadOwnedPlan(planId, userId);
    }

    @Override
    public List<InvestmentPlan> listForUser(UserId userId, Optional<InvestmentPlanStatus> status) {
        return planRepository.findByUserId(userId, status);
    }

    @Override
    public List<InvestmentAllocation> forPlan(InvestmentPlanId planId, UserId userId, int limit) {
        loadOwnedPlan(planId, userId); // ownership check
        return allocationRepository.findByPlanId(planId, limit);
    }

    @Override
    public List<InvestmentAllocation> forUser(UserId userId, int limit) {
        return allocationRepository.findByUserId(userId, limit);
    }

    @Override
    public InvestmentAllocation getOwnedBy(InvestmentAllocationId id, UserId userId) {
        InvestmentAllocation allocation = allocationRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Allocation not found: " + id));
        if (!allocation.getUserId().equals(userId)) {
            throw new SecurityException("Allocation does not belong to user: " + userId);
        }
        return allocation;
    }

    /**
     * Resolve a broker account and assert it is safe for this user to point a plan at.
     * <p>
     * Shared by create and update so the two cannot drift: a plan's broker account decides where a
     * scheduled contribution spends from, so both entry points must check existence, ownership and
     * live-money consent identically.
     *
     * @param brokerAccountId the account the plan will use
     * @param userId          the plan's owner
     * @param confirmLive     explicit acknowledgement that the plan may trade real money
     * @return the validated broker account
     */
    private BrokerAccount requireUsableBroker(
            BrokerAccountId brokerAccountId, UserId userId, boolean confirmLive) {
        BrokerAccount broker = brokerAccountRepository.findById(brokerAccountId)
                .orElseThrow(() -> new NoSuchElementException(
                        "Broker account not found: " + brokerAccountId));
        if (!broker.getUserId().equals(userId)) {
            throw new SecurityException("Broker account does not belong to user: " + userId);
        }
        // isLive() rather than an ALPACA_LIVE equality check, so every live venue is covered —
        // the old comparison let a SAXO_LIVE account through without any acknowledgement.
        if (broker.getBrokerType().isLive() && !confirmLive) {
            throw new IllegalStateException(
                    "Broker is " + broker.getBrokerType()
                            + "; pass confirmLive=true to acknowledge that this plan will trade real money.");
        }
        return broker;
    }

    private InvestmentPlan loadOwnedPlan(InvestmentPlanId planId, UserId userId) {
        InvestmentPlan plan = planRepository.findById(planId)
                .orElseThrow(() -> new NoSuchElementException("Investment plan not found: " + planId));
        if (!plan.getUserId().equals(userId)) {
            throw new SecurityException("Investment plan does not belong to user: " + userId);
        }
        return plan;
    }
}
