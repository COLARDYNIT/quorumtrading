package com.quorumtrading.application.service;

import com.quorumtrading.application.port.out.BrokerAccountRepository;
import com.quorumtrading.application.port.out.InvestmentAllocationRepository;
import com.quorumtrading.application.port.out.InvestmentPlanRepository;
import com.quorumtrading.application.usecase.command.UpdateInvestmentPlanCommand;
import com.quorumtrading.model.entity.InvestmentPlan;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@code update_investment_plan} and {@code PUT /investment-plans/{id}} both advertise partial
 * updates. They rebuilt the whole preferences object from whichever arguments arrived, so naming
 * one preference erased the rest.
 *
 * <p>Observed: a plan restricted to {@code allowedTickers:["BTC"]} with {@code riskTolerance}
 * conservative and {@code maxStrategiesActive} 1 had all three silently reset to null/empty by an
 * edit that only meant to change the amount and add a block-list entry. That widens the picker from
 * one ticker to every strategy the user owns — it changes what the plan buys, with money.</p>
 *
 * <p>The merge belongs in the use case, not in an adapter: REST and MCP both had the defect, so
 * fixing either alone would leave the other spending wrongly.</p>
 */
class InvestmentPlanPreferenceMergeTest {

    private static final UserId OWNER = new UserId(UUID.randomUUID());
    private static final InvestmentPlanPreferences STORED = new InvestmentPlanPreferences(
            "conservative", List.of("BTC"), List.of(), 1, true);

    private InvestmentPlanService service;
    private InvestmentPlanId planId;

    @BeforeEach
    void setUp() {
        InvestmentPlanRepository planRepository = mock(InvestmentPlanRepository.class);
        service = new InvestmentPlanService(
                planRepository,
                mock(InvestmentAllocationRepository.class),
                mock(BrokerAccountRepository.class));

        // A real aggregate: preference merging must survive the entity's own validation, and a
        // mocked plan would accept anything and prove nothing.
        InvestmentPlan existing = InvestmentPlan.create(
                InvestmentPlanId.generate(), OWNER, new BrokerAccountId(UUID.randomUUID()),
                BudgetMode.FIXED_AMOUNT, Money.usd(new BigDecimal("250.00")), 10,
                PickerPolicy.AI_PICK, STORED, Instant.parse("2026-07-01T00:00:00Z"));
        planId = existing.getId();
        when(planRepository.findById(planId)).thenReturn(Optional.of(existing));
        when(planRepository.save(any(InvestmentPlan.class))).thenAnswer(i -> i.getArgument(0));
    }

    private InvestmentPlan update(Optional<Money> amount, InvestmentPlanPreferencesPatch patch) {
        return service.execute(new UpdateInvestmentPlanCommand(
                planId, OWNER, Optional.empty(), Optional.empty(), amount,
                Optional.empty(), Optional.of(patch), false));
    }

    @Test
    @DisplayName("adding a blocked ticker keeps the allow-list, risk tolerance and cap")
    void blockListEditPreservesAllowList() {
        InvestmentPlan updated = update(
                Optional.of(Money.usd(new BigDecimal("300.00"))),
                new InvestmentPlanPreferencesPatch(
                        Optional.empty(), Optional.empty(), Optional.of(List.of("DOGE")),
                        Optional.empty(), Optional.empty()));

        assertThat(updated.getPreferences().allowedTickers()).containsExactly("BTC");
        assertThat(updated.getPreferences().blockedTickers()).containsExactly("DOGE");
        assertThat(updated.getPreferences().riskTolerance()).isEqualTo("conservative");
        assertThat(updated.getPreferences().maxStrategiesActive()).isEqualTo(1);
        assertThat(updated.getAmount().amount()).isEqualByComparingTo("300.00");
    }

    @Test
    @DisplayName("editing only the allow-list keeps a previously stored block-list")
    void allowListEditPreservesBlockList() {
        update(Optional.empty(), new InvestmentPlanPreferencesPatch(
                Optional.empty(), Optional.empty(), Optional.of(List.of("DOGE")),
                Optional.empty(), Optional.empty()));

        InvestmentPlan updated = update(Optional.empty(), new InvestmentPlanPreferencesPatch(
                Optional.empty(), Optional.of(List.of("BTC", "ETH")), Optional.empty(),
                Optional.empty(), Optional.empty()));

        assertThat(updated.getPreferences().allowedTickers()).containsExactly("BTC", "ETH");
        assertThat(updated.getPreferences().blockedTickers()).containsExactly("DOGE");
    }

    @Test
    @DisplayName("an update that names no preference leaves all of them untouched")
    void amountOnlyEditTouchesNoPreference() {
        InvestmentPlan updated = service.execute(new UpdateInvestmentPlanCommand(
                planId, OWNER, Optional.empty(), Optional.empty(),
                Optional.of(Money.usd(new BigDecimal("275.00"))),
                Optional.empty(), Optional.empty(), false));

        assertThat(updated.getPreferences()).isEqualTo(STORED);
    }

    @Test
    @DisplayName("an explicitly empty allow-list still clears it")
    void emptyAllowListStillClears() {
        InvestmentPlan updated = update(Optional.empty(), new InvestmentPlanPreferencesPatch(
                Optional.empty(), Optional.of(List.of()), Optional.empty(),
                Optional.empty(), Optional.empty()));

        assertThat(updated.getPreferences().allowedTickers()).isEmpty();
        assertThat(updated.getPreferences().riskTolerance()).isEqualTo("conservative");
    }

    @Test
    @DisplayName("the merged result, not the patch, is what the entity validates")
    void mergedResultIsValidated() {
        // The stored allow-list is BTC. Blocking BTC via a patch that mentions no allow-list makes
        // the *merged* policy unfundable — which only the entity can see, and only if it is handed
        // the merge rather than the patch.
        assertThat(STORED.allowedTickers()).containsExactly("BTC");

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        update(Optional.empty(), new InvestmentPlanPreferencesPatch(
                                Optional.empty(), Optional.empty(), Optional.of(List.of("BTC")),
                                Optional.empty(), Optional.empty())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("BTC");
    }
}
