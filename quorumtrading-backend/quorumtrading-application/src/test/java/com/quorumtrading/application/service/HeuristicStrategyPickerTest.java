package com.quorumtrading.application.service;

import com.quorumtrading.application.port.out.StrategyPickerPort;
import com.quorumtrading.application.port.out.StrategyRepository;
import com.quorumtrading.model.entity.InvestmentPlan;
import com.quorumtrading.model.entity.Strategy;
import com.quorumtrading.model.enums.BudgetMode;
import com.quorumtrading.model.enums.PickerPolicy;
import com.quorumtrading.model.valueobject.BrokerAccountId;
import com.quorumtrading.model.valueobject.InvestmentPlanId;
import com.quorumtrading.model.valueobject.InvestmentPlanPreferences;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.StrategyId;
import com.quorumtrading.model.valueobject.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * The explanation the picker gives when nothing is eligible.
 *
 * <p>It built one rejection line per active strategy and joined them all, with no cap. That string
 * becomes the allocation's {@code pickerReasoning} <em>and</em> its {@code failureReason}, and is
 * then dropped verbatim into a notification body — so a user with a large strategy pool got a
 * multi-kilobyte wall of text where a sentence was needed. The size scales with the pool, which
 * means the problem gets worse for exactly the users who have been on the platform longest.</p>
 *
 * <p>Note the shape of the fix: the count is the useful part, a few examples are the actionable
 * part, and the rest is noise. Truncation must say that it truncated — a silently cut list reads
 * as a complete one.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("HeuristicStrategyPicker — the no-eligible-strategy explanation")
class HeuristicStrategyPickerTest {

    /** Comfortably readable in a notification body and a timeline row. */
    private static final int REASONABLE_LENGTH = 600;

    @Mock private StrategyRepository strategyRepository;

    private HeuristicStrategyPicker picker;
    private UserId userId;
    private InvestmentPlan plan;

    @BeforeEach
    void setUp() {
        picker = new HeuristicStrategyPicker(strategyRepository);
        userId = UserId.generate();
        plan = InvestmentPlan.create(
                InvestmentPlanId.generate(), userId, BrokerAccountId.generate(),
                BudgetMode.FIXED_AMOUNT, Money.usd(new BigDecimal("500.00")),
                1, PickerPolicy.AI_PICK,
                // A blocked ticker every strategy trades, so all of them are rejected.
                new InvestmentPlanPreferences(null, List.of(), List.of("AAPL"), null, true),
                Instant.parse("2026-05-15T00:00:00Z"));
    }

    /** Strategies that all trade the blocked ticker, so every one is filtered out. */
    private void poolOf(int count) {
        List<Strategy> strategies = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            strategies.add(new Strategy(
                    StrategyId.generate(),
                    "Momentum strategy number " + i,
                    """
                    ---
                    stock_tickers:
                      - AAPL
                    ---

                    # Body
                    """,
                    userId));
        }
        when(strategyRepository.findByOwnerId(any(UserId.class))).thenReturn(strategies);
    }

    @Test
    @DisplayName("a large pool does not produce a multi-kilobyte reason")
    void largePoolReasonIsCapped() {
        poolOf(76);

        StrategyPickerPort.Outcome outcome = picker.pick(plan);

        assertThat(outcome.isChoice()).isFalse();
        assertThat(outcome.reasoning())
                .as("this string lands in a notification body and a timeline row; it grew with "
                    + "the user's strategy pool and reached kilobytes")
                .hasSizeLessThan(REASONABLE_LENGTH);
    }

    @Test
    @DisplayName("a truncated list says how many it left out")
    void truncationIsDisclosed() {
        poolOf(76);

        String reasoning = picker.pick(plan).reasoning();

        assertThat(reasoning)
                .as("the total is the number the user needs; a silently cut list reads complete")
                .contains("76");
        assertThat(reasoning)
                .as("say that there are more, or the examples look like the whole story")
                .containsAnyOf("more", "…", "...");
    }

    @Test
    @DisplayName("a small pool still names every strategy that was filtered")
    void smallPoolNamesEveryRejection() {
        poolOf(3);

        String reasoning = picker.pick(plan).reasoning();

        assertThat(reasoning)
                .contains("Momentum strategy number 0")
                .contains("Momentum strategy number 1")
                .contains("Momentum strategy number 2");
    }

    @Test
    @DisplayName("an empty pool tells the user to create a strategy")
    void emptyPoolAsksForAStrategy() {
        when(strategyRepository.findByOwnerId(any(UserId.class))).thenReturn(List.of());

        StrategyPickerPort.Outcome outcome = picker.pick(plan);

        assertThat(outcome.isChoice()).isFalse();
        assertThat(outcome.reasoning()).contains("create one");
    }
}
