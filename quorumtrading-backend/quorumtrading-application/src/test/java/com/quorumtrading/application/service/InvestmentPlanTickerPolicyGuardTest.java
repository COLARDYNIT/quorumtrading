package com.quorumtrading.application.service;

import com.quorumtrading.application.port.out.BrokerAccountRepository;
import com.quorumtrading.application.port.out.InvestmentAllocationRepository;
import com.quorumtrading.application.port.out.InvestmentPlanRepository;
import com.quorumtrading.application.usecase.command.CreateInvestmentPlanCommand;
import com.quorumtrading.application.usecase.command.UpdateInvestmentPlanCommand;
import com.quorumtrading.model.entity.BrokerAccount;
import com.quorumtrading.model.entity.InvestmentPlan;
import com.quorumtrading.model.enums.BrokerType;
import com.quorumtrading.model.enums.BudgetMode;
import com.quorumtrading.model.enums.PickerPolicy;
import com.quorumtrading.model.valueobject.BrokerAccountId;
import com.quorumtrading.model.valueobject.InvestmentPlanId;
import com.quorumtrading.model.valueobject.InvestmentPlanPreferences;
import com.quorumtrading.model.valueobject.InvestmentPlanPreferencesPatch;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A ticker listed as both allowed and blocked has no coherent reading, and the plan that carries it
 * is unfundable: {@code isTickerAllowed} lets the block win, so an allowlist that is entirely
 * blocked permits nothing. Such a plan was accepted as ACTIVE with a {@code nextExecutionAt} and,
 * because automatic execution is off by default on most instances, nothing ever contradicted it. The
 * only feedback arrived at execution time, per candidate strategy, phrased as if the strategy were
 * at fault.
 *
 * <p>Both entry points must refuse it. An update that can introduce what creation rejects is the
 * same defect with an extra step.</p>
 */
class InvestmentPlanTickerPolicyGuardTest {

    private static final UserId OWNER = new UserId(UUID.randomUUID());

    private InvestmentPlanRepository planRepository;
    private InvestmentPlanService service;
    private BrokerAccountId brokerId;
    private InvestmentPlanId planId;

    @BeforeEach
    void setUp() {
        planRepository = mock(InvestmentPlanRepository.class);
        BrokerAccountRepository brokerAccountRepository = mock(BrokerAccountRepository.class);
        service = new InvestmentPlanService(
                planRepository, mock(InvestmentAllocationRepository.class), brokerAccountRepository);

        brokerId = new BrokerAccountId(UUID.randomUUID());
        BrokerAccount account = mock(BrokerAccount.class);
        when(account.getUserId()).thenReturn(OWNER);
        when(account.getBrokerType()).thenReturn(BrokerType.ALPACA_PAPER);
        when(brokerAccountRepository.findById(brokerId)).thenReturn(Optional.of(account));

        // A real aggregate, not a mock: the invariant lives on the entity, and a mocked plan would
        // accept any preferences silently and make this test prove nothing.
        InvestmentPlan existing = InvestmentPlan.create(
                InvestmentPlanId.generate(), OWNER, brokerId,
                BudgetMode.FIXED_AMOUNT, Money.usd(new BigDecimal("200.00")), 15,
                PickerPolicy.AI_PICK,
                new InvestmentPlanPreferences(null, List.of("AAPL"), List.of("GME"), null, true),
                Instant.parse("2026-05-01T00:00:00Z"));
        planId = existing.getId();
        when(planRepository.findById(planId)).thenReturn(Optional.of(existing));
        when(planRepository.save(any(InvestmentPlan.class))).thenAnswer(i -> i.getArgument(0));
    }

    private CreateInvestmentPlanCommand createWith(InvestmentPlanPreferences prefs) {
        return new CreateInvestmentPlanCommand(OWNER, brokerId, BudgetMode.FIXED_AMOUNT,
                Money.usd(new BigDecimal("200.00")), 15, PickerPolicy.AI_PICK, prefs, false);
    }

    private UpdateInvestmentPlanCommand updateWith(InvestmentPlanPreferences prefs) {
        // The guard is about ticker policy, so the patch states both lists explicitly. The merged
        // result is what the entity validates; InvestmentPlanPreferenceMergeTest covers the merge.
        return new UpdateInvestmentPlanCommand(planId, OWNER, Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(),
                Optional.of(InvestmentPlanPreferencesPatch.ofTickerPolicy(
                        prefs.allowedTickers(), prefs.blockedTickers())), false);
    }

    @Test
    @DisplayName("create refuses a plan whose entire allowlist is blocked")
    void createRefusesUnfundablePolicy() {
        InvestmentPlanPreferences contradiction =
                new InvestmentPlanPreferences(null, List.of("AAPL"), List.of("AAPL"), null, true);

        assertThatThrownBy(() -> service.execute(createWith(contradiction)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("AAPL")
                .hasMessageContaining("ticker policy")
                .hasMessageContaining("not in any strategy");

        verify(planRepository, never()).save(any());
    }

    @Test
    @DisplayName("update cannot introduce the contradiction that create refuses")
    void updateRefusesUnfundablePolicy() {
        InvestmentPlanPreferences contradiction =
                new InvestmentPlanPreferences(null, List.of("AAPL"), List.of("AAPL"), null, true);

        assertThatThrownBy(() -> service.execute(updateWith(contradiction)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("AAPL")
                .hasMessageContaining("ticker policy");

        verify(planRepository, never()).save(any());
    }

    @Test
    @DisplayName("update refuses a partial overlap too, and says the remedy is different")
    void updateRefusesPartialOverlap() {
        InvestmentPlanPreferences overlap = new InvestmentPlanPreferences(
                null, List.of("AAPL", "MSFT"), List.of("aapl"), null, true);

        assertThatThrownBy(() -> service.execute(updateWith(overlap)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("AAPL")
                .hasMessageContaining("the rest of allowedTickers still can");

        verify(planRepository, never()).save(any());
    }

    @Test
    @DisplayName("a disjoint policy still goes through on both paths")
    void disjointPolicyIsAccepted() {
        InvestmentPlanPreferences fine =
                new InvestmentPlanPreferences(null, List.of("AAPL", "MSFT"), List.of("GME"), null, true);

        assertThatCode(() -> service.execute(createWith(fine))).doesNotThrowAnyException();
        assertThatCode(() -> service.execute(updateWith(fine))).doesNotThrowAnyException();
        verify(planRepository, org.mockito.Mockito.times(2)).save(any(InvestmentPlan.class));
    }
}
