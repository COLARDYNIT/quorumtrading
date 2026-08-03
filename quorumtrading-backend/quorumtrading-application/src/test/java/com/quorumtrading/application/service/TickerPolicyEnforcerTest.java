package com.quorumtrading.application.service;

import com.quorumtrading.model.entity.Strategy;
import com.quorumtrading.model.valueobject.InvestmentPlanPreferences;
import com.quorumtrading.model.valueobject.StrategyId;
import com.quorumtrading.model.valueobject.UserId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TickerPolicyEnforcerTest {

    private static final UserId OWNER = new UserId(UUID.randomUUID());

    private static final String STRATEGY_AAPL_MSFT = """
        ---
        version: v1.0
        consensus_method: MAJORITY
        consensus_threshold: 0.66
        stock_tickers:
          - AAPL
          - MSFT
        live_schedule:
          timezone: "America/New_York"
          trading_days: [MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY]
          evaluation_times: ["10:00"]
          market_open: "09:30"
          market_close: "16:00"
        ---
        # Test
        Buy AAPL/MSFT.
        """;

    private static final String STRATEGY_TSLA_ONLY = """
        ---
        version: v1.0
        consensus_method: MAJORITY
        consensus_threshold: 0.66
        stock_tickers:
          - TSLA
        live_schedule:
          timezone: "America/New_York"
          trading_days: [MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY]
          evaluation_times: ["10:00"]
          market_open: "09:30"
          market_close: "16:00"
        ---
        # Test
        Buy TSLA.
        """;

    private static final String STRATEGY_UNPARSEABLE = "this is not yaml frontmatter";

    @Test
    void empty_policy_allows_everything() {
        Strategy s = new Strategy(StrategyId.generate(), "S", STRATEGY_AAPL_MSFT, OWNER);

        TickerPolicyEnforcer.Decision decision =
                TickerPolicyEnforcer.evaluate(s, InvestmentPlanPreferences.EMPTY);

        assertThat(decision.allowed()).isTrue();
    }

    @Test
    void empty_policy_passes_unparseable_strategy_without_invoking_parser() {
        Strategy s = new Strategy(StrategyId.generate(), "S", STRATEGY_UNPARSEABLE, OWNER);

        TickerPolicyEnforcer.Decision decision =
                TickerPolicyEnforcer.evaluate(s, InvestmentPlanPreferences.EMPTY);

        assertThat(decision.allowed()).isTrue();
    }

    @Test
    void blocked_ticker_rejects_strategy_that_trades_it() {
        Strategy s = new Strategy(StrategyId.generate(), "S", STRATEGY_AAPL_MSFT, OWNER);
        InvestmentPlanPreferences prefs = new InvestmentPlanPreferences(
                null, List.of(), List.of("MSFT"), null, true);

        TickerPolicyEnforcer.Decision decision = TickerPolicyEnforcer.evaluate(s, prefs);

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reason()).contains("MSFT");
    }

    @Test
    void allow_list_rejects_strategy_with_any_out_of_list_ticker() {
        Strategy s = new Strategy(StrategyId.generate(), "S", STRATEGY_AAPL_MSFT, OWNER);
        // Only AAPL is allowed — MSFT is not in the list, so the strategy is rejected (strict).
        InvestmentPlanPreferences prefs = new InvestmentPlanPreferences(
                null, List.of("AAPL"), List.of(), null, true);

        TickerPolicyEnforcer.Decision decision = TickerPolicyEnforcer.evaluate(s, prefs);

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reason()).contains("MSFT");
    }

    @Test
    void allow_list_accepts_strategy_whose_tickers_are_all_in_list() {
        Strategy s = new Strategy(StrategyId.generate(), "S", STRATEGY_AAPL_MSFT, OWNER);
        InvestmentPlanPreferences prefs = new InvestmentPlanPreferences(
                null, List.of("AAPL", "MSFT", "GOOG"), List.of(), null, true);

        TickerPolicyEnforcer.Decision decision = TickerPolicyEnforcer.evaluate(s, prefs);

        assertThat(decision.allowed()).isTrue();
    }

    @Test
    void block_list_accepts_strategy_with_no_blocked_tickers() {
        Strategy s = new Strategy(StrategyId.generate(), "S", STRATEGY_AAPL_MSFT, OWNER);
        InvestmentPlanPreferences prefs = new InvestmentPlanPreferences(
                null, List.of(), List.of("TSLA"), null, true);

        TickerPolicyEnforcer.Decision decision = TickerPolicyEnforcer.evaluate(s, prefs);

        assertThat(decision.allowed()).isTrue();
    }

    @Test
    void single_ticker_strategy_against_block_list() {
        Strategy s = new Strategy(StrategyId.generate(), "S", STRATEGY_TSLA_ONLY, OWNER);
        InvestmentPlanPreferences prefs = new InvestmentPlanPreferences(
                null, List.of(), List.of("TSLA"), null, true);

        TickerPolicyEnforcer.Decision decision = TickerPolicyEnforcer.evaluate(s, prefs);

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reason()).contains("TSLA");
    }

    @Test
    void unparseable_strategy_under_active_policy_is_rejected() {
        Strategy s = new Strategy(StrategyId.generate(), "S", STRATEGY_UNPARSEABLE, OWNER);
        InvestmentPlanPreferences prefs = new InvestmentPlanPreferences(
                null, List.of("AAPL"), List.of(), null, true);

        TickerPolicyEnforcer.Decision decision = TickerPolicyEnforcer.evaluate(s, prefs);

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reason()).containsIgnoringCase("unparseable");
    }
}
