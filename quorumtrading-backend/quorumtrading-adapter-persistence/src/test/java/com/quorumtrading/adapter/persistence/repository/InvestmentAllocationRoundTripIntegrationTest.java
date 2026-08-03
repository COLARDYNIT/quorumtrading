package com.quorumtrading.adapter.persistence.repository;

import com.quorumtrading.application.port.out.BrokerAccountRepository;
import com.quorumtrading.application.port.out.InvestmentAllocationRepository;
import com.quorumtrading.application.port.out.InvestmentPlanRepository;
import com.quorumtrading.application.port.out.UserRepository;
import com.quorumtrading.model.entity.BrokerAccount;
import com.quorumtrading.model.entity.InvestmentAllocation;
import com.quorumtrading.model.entity.InvestmentPlan;
import com.quorumtrading.model.entity.User;
import com.quorumtrading.model.enums.AllocationAction;
import com.quorumtrading.model.enums.AllocationStatus;
import com.quorumtrading.model.enums.BrokerType;
import com.quorumtrading.model.enums.BudgetMode;
import com.quorumtrading.model.enums.OAuthProvider;
import com.quorumtrading.model.enums.PickerPolicy;
import com.quorumtrading.model.valueobject.ApiKeyCredentials;
import com.quorumtrading.model.valueobject.BrokerAccountId;
import com.quorumtrading.model.valueobject.InvestmentAllocationId;
import com.quorumtrading.model.valueobject.InvestmentPlanId;
import com.quorumtrading.model.valueobject.InvestmentPlanPreferences;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.SimulationId;
import com.quorumtrading.model.valueobject.UserId;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Database round-trip for the FX4 fractional-execution audit columns: an
 * {@link InvestmentAllocation} carrying {@code deployedAmount} + {@code fractionalConstraint}
 * (and the new {@link AllocationStatus#EXECUTED_PARTIAL_DEPLOY} status) must persist and reload
 * unchanged — proving the V118 {@code deployed_amount} / {@code fractional_constraint} columns +
 * JPA entity + mapper carry the new fields through the real repository and the Flyway-migrated
 * {@code quorumtrading_test} schema.
 *
 * <p>The legacy/fully-deployed path (no deployed amount, no constraint) is the byte-identical
 * regression — it must round-trip with {@code deployedAmount == null} and
 * {@code fractionalConstraint == false}, so existing allocation rows stay green.</p>
 *
 * <p>Exercises the FK chain user → broker account → investment plan → allocation, so the test
 * also confirms the V118 migration applied cleanly on a fresh test database.</p>
 */
@QuarkusTest
class InvestmentAllocationRoundTripIntegrationTest {

    @Inject
    InvestmentAllocationRepository allocationRepository;

    @Inject
    InvestmentPlanRepository planRepository;

    @Inject
    BrokerAccountRepository brokerAccountRepository;

    @Inject
    UserRepository userRepository;

    @Test
    void partialDeployAllocationRoundTrips() {
        UserId userId = createUser();
        InvestmentPlanId planId = createPlan(userId);

        Money intended = Money.usd(new BigDecimal("1000.00"));
        Money actual = Money.usd(new BigDecimal("900.00"));
        Money deployed = Money.usd(new BigDecimal("900.00"));
        InvestmentAllocationId allocId = InvestmentAllocationId.generate();
        Instant now = Instant.parse("2026-06-04T06:00:00Z");

        InvestmentAllocation allocation = InvestmentAllocation.create(
                allocId, planId, userId, now,
                AllocationStatus.EXECUTED_PARTIAL_DEPLOY,
                intended, actual,
                null, "whole-share rounding carried €100 residue", BigDecimal.ZERO,
                AllocationAction.NEW_SIMULATION, new SimulationId(UUID.randomUUID()),
                null, List.of("saxo-order-1"), null,
                deployed, true, now);

        allocationRepository.save(allocation);

        InvestmentAllocation reloaded = allocationRepository.findById(allocId).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(AllocationStatus.EXECUTED_PARTIAL_DEPLOY);
        assertThat(reloaded.wasExecuted()).isTrue();
        assertThat(reloaded.isFractionalConstraint()).isTrue();
        assertThat(reloaded.getDeployedAmount()).isNotNull();
        assertThat(reloaded.getDeployedAmount().amount()).isEqualByComparingTo(new BigDecimal("900.00"));
        assertThat(reloaded.getDeployedAmount().currency()).isEqualTo(Money.Currency.USD);
        assertThat(reloaded.getActualAmount().amount()).isEqualByComparingTo(new BigDecimal("900.00"));
    }

    @Test
    void fullyDeployedAllocationRoundTripsWithoutFractionalColumns() {
        UserId userId = createUser();
        InvestmentPlanId planId = createPlan(userId);

        Money intended = Money.usd(new BigDecimal("500.00"));
        InvestmentAllocationId allocId = InvestmentAllocationId.generate();
        Instant now = Instant.parse("2026-06-04T06:00:00Z");

        // Legacy-shaped row via the original (pre-FX4) factory overload — must default to
        // deployedAmount == null + fractionalConstraint == false.
        InvestmentAllocation allocation = InvestmentAllocation.create(
                allocId, planId, userId, now,
                AllocationStatus.EXECUTED,
                intended, Money.usd(new BigDecimal("500.00")),
                null, "fully deployed", BigDecimal.ZERO,
                AllocationAction.NEW_SIMULATION, new SimulationId(UUID.randomUUID()),
                null, List.of(), null, now);

        allocationRepository.save(allocation);

        InvestmentAllocation reloaded = allocationRepository.findById(allocId).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(AllocationStatus.EXECUTED);
        assertThat(reloaded.getDeployedAmount()).isNull();
        assertThat(reloaded.isFractionalConstraint()).isFalse();
    }

    /**
     * The same-day funding probe that stops an investment plan funding twice in a day.
     *
     * <p>It has to be exact in two directions and the SQL is the only place either can go wrong.
     * <b>Over-matching</b> would freeze a plan: a {@code SKIPPED_INSUFFICIENT_FUNDS} tick at 06:00
     * must stay retryable at 18:00 once the cash lands, and yesterday's funding must not block
     * today's. <b>Under-matching</b> is the defect being fixed: {@code execution_date} is a
     * {@code timestamptz}, so a probe that compared instants rather than covering the whole day
     * would miss the morning's funding and let the evening retry fund again.</p>
     */
    @Test
    void fundedProbeMatchesOnlyFundedRowsWithinTheDay() {
        UserId userId = createUser();
        InvestmentPlanId planId = createPlan(userId);
        Instant dayStart = Instant.parse("2026-06-04T00:00:00Z");
        Instant nextDayStart = Instant.parse("2026-06-05T00:00:00Z");

        allocationRepository.save(skipped(planId, userId, Instant.parse("2026-06-04T06:00:00Z")));
        allocationRepository.save(funded(planId, userId, Instant.parse("2026-06-03T18:00:00Z")));

        assertThat(allocationRepository.findFundedOnDay(planId, dayStart, nextDayStart))
                .as("a skip is not a funding, and yesterday's funding is not today's")
                .isEmpty();

        // Deliberately mid-afternoon with sub-second precision: the window, not the instant, is
        // what makes this match.
        InvestmentAllocation todaysFunding =
                funded(planId, userId, Instant.parse("2026-06-04T18:42:11.123456Z"));
        allocationRepository.save(todaysFunding);

        assertThat(allocationRepository.findFundedOnDay(planId, dayStart, nextDayStart))
                .map(InvestmentAllocation::getId)
                .contains(todaysFunding.getId());
    }

    private static InvestmentAllocation funded(InvestmentPlanId planId, UserId userId, Instant at) {
        Money amount = Money.usd(new BigDecimal("500.00"));
        return InvestmentAllocation.create(
                InvestmentAllocationId.generate(), planId, userId, at,
                AllocationStatus.EXECUTED, amount, amount, null, "funded", BigDecimal.ZERO,
                AllocationAction.NEW_SIMULATION, new SimulationId(UUID.randomUUID()),
                null, List.of(), null, at);
    }

    private static InvestmentAllocation skipped(InvestmentPlanId planId, UserId userId, Instant at) {
        return InvestmentAllocation.create(
                InvestmentAllocationId.generate(), planId, userId, at,
                AllocationStatus.SKIPPED_INSUFFICIENT_FUNDS,
                Money.usd(new BigDecimal("500.00")), null, null, "no cash", BigDecimal.ZERO,
                null, null, null, List.of(), "broker was empty", at);
    }

    private UserId createUser() {
        UserId userId = UserId.generate();
        userRepository.save(User.create(
                userId, "alloc-fx4-" + UUID.randomUUID() + "@example.com",
                "Allocation FX4", null, OAuthProvider.GOOGLE));
        return userId;
    }

    private InvestmentPlanId createPlan(UserId userId) {
        Money usdBalance = Money.usd(new BigDecimal("25000.00"));
        BrokerAccount account = brokerAccountRepository.save(BrokerAccount.createReal(
                userId, "Alpaca", BrokerType.ALPACA_PAPER,
                new ApiKeyCredentials("PKTEST", "secret"), usdBalance, usdBalance));

        InvestmentPlan plan = InvestmentPlan.create(
                InvestmentPlanId.generate(), userId, account.getId(),
                BudgetMode.FIXED_AMOUNT, Money.usd(new BigDecimal("1000.00")), 1,
                PickerPolicy.AI_PICK, InvestmentPlanPreferences.EMPTY,
                Instant.parse("2026-06-04T06:00:00Z"));
        return planRepository.save(plan).getId();
    }
}
