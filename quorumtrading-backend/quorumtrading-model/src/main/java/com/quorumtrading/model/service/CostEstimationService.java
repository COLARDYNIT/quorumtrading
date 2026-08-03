package com.quorumtrading.model.service;

import com.quorumtrading.model.valueobject.CostEstimate;
import com.quorumtrading.model.valueobject.LLMPricing;
import com.quorumtrading.model.valueobject.LLMUsage;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Collection;

/**
 * Pure domain service for estimating backtest/benchmark costs.
 * <p>
 * Formula (empirically validated against real usage data):
 * <pre>
 * tradingDays      = calendarDays × 5/7 (stock) or calendarDays (crypto)
 * consensusRounds  = tradingDays × evalsPerDay
 * agentsPerRound   = enabledAgents + 1 (orchestrator), or enabledAgents under MAJORITY,
 *                    which tallies votes in code and makes no orchestrator LLM call
 * totalLLMCalls    = consensusRounds × tickerCount × agentsPerRound
 * costPerCall      = avgPromptTokens × promptCostPerToken + avgCompletionTokens × completionCostPerToken
 * billedCost       = totalLLMCalls × costPerCall × MARKUP_RATE
 * </pre>
 * <p>
 * Token averages from production data (21.5M tokens / 8K requests):
 * ~2,540 input + ~160 output per call.
 *
 * <p><b>DDD Classification:</b> Domain service — pure business logic, no CDI annotations.</p>
 */
public class CostEstimationService {

    /**
     * Estimate the cost of a backtest or live simulation.
     *
     * @param calendarDays          Number of calendar days in the period
     * @param isCrypto              true for crypto (24/7), false for stock (weekdays only)
     * @param evalsPerDay           Number of consensus evaluations per trading day
     * @param tickerCount           Number of tickers evaluated each round
     * @param agentCount            Number of enabled agents (excluding orchestrator)
     * @param usesOrchestratorLlm   Whether the consensus method makes an orchestrator LLM call.
     *                              MAJORITY tallies the agents' votes in code and makes none, so
     *                              counting one overstates every MAJORITY estimate by
     *                              1/(agents+1) — 25% with the default three agents.
     * @param pricing               Raw LLM pricing (before markup)
     * @param avgPromptTokens       Average prompt tokens per LLM call
     * @param avgCompletionTokens   Average completion tokens per LLM call
     * @param modelId               Model identifier
     * @param modelName             Human-readable model name
     * @return CostEstimate with all computed fields
     */
    /**
     * Estimate from an actual date range, counting weekdays exactly.
     *
     * <p>Prefer this wherever the dates are known. The day-count overload can only approximate,
     * because the number of trading days in a span depends on which weekday it starts on.</p>
     */
    public CostEstimate estimate(
        LocalDate from,
        LocalDate to,
        boolean isCrypto,
        int evalsPerDay,
        int tickerCount,
        int agentCount,
        boolean usesOrchestratorLlm,
        LLMPricing pricing,
        int avgPromptTokens,
        int avgCompletionTokens,
        String modelId,
        String modelName
    ) {
        long days = isCrypto
            ? ChronoUnit.DAYS.between(from, to) + 1
            : countWeekdays(from, to);
        // Hand the exact figure to the shared path as a crypto-style "every day counts" span, so
        // it is not reduced a second time by the approximation above.
        return estimate(days, true, evalsPerDay, tickerCount, agentCount, usesOrchestratorLlm,
            pricing, avgPromptTokens, avgCompletionTokens, modelId, modelName);
    }

    /** Weekdays in an inclusive range. Holidays are not modelled; this is an estimate. */
    public static long countWeekdays(LocalDate from, LocalDate to) {
        return countWeekdays(from, to, null);
    }

    /**
     * Weekdays in an inclusive range, restricted to {@code tradingDays} when given.
     *
     * <p>A strategy whose {@code backtest_schedule} narrows {@code trading_days} evaluates only
     * on those days — counting every weekday quotes it at up to five times its real cost, and
     * narrowing the schedule is the documented way to keep AI spend down. Weekend entries in
     * {@code tradingDays} are ignored for stock schedules, which is what the caller means.</p>
     *
     * @param tradingDays days the schedule actually evaluates on; null/empty means all weekdays
     */
    public static long countWeekdays(LocalDate from, LocalDate to, Collection<DayOfWeek> tradingDays) {
        if (from == null || to == null || to.isBefore(from)) {
            return 0;
        }
        long count = 0;
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            DayOfWeek dow = d.getDayOfWeek();
            if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) {
                continue;
            }
            if (tradingDays == null || tradingDays.isEmpty() || tradingDays.contains(dow)) {
                count++;
            }
        }
        return count;
    }

    public CostEstimate estimate(
        long calendarDays,
        boolean isCrypto,
        int evalsPerDay,
        int tickerCount,
        int agentCount,
        boolean usesOrchestratorLlm,
        LLMPricing pricing,
        int avgPromptTokens,
        int avgCompletionTokens,
        String modelId,
        String modelName
    ) {
        // Trading days: crypto = every day, stock = weekdays.
        //
        // Without a start date the weekday count is genuinely unknowable — five calendar days is
        // three, four or five trading days depending on where the week falls — so this path can
        // only approximate. Two things it must not do, and previously did:
        //
        //   * truncate. (int)(calendarDays * 5 / 7) is integer division, so it rounds DOWN every
        //     time, understating short windows worst. Four days became two.
        //   * reach zero. A positive window with tradingDays == 0 fell into the guard below and
        //     returned 0 LLM calls and $0.00 — quoting a run as free while it went on to bill a
        //     full set of agents. Invisible on a local engine, real money on a metered one.
        //
        // Callers that know the actual dates should use the estimate(...) overload that takes
        // them and counts weekdays exactly.
        int tradingDays;
        if (isCrypto) {
            tradingDays = (int) calendarDays;
        } else if (calendarDays <= 0) {
            tradingDays = 0;
        } else {
            tradingDays = Math.max(1, Math.round(calendarDays * 5.0f / 7.0f));
        }

        int agentsPerRound = agentCount + (usesOrchestratorLlm ? 1 : 0);

        if (tradingDays <= 0 || tickerCount <= 0 || evalsPerDay <= 0) {
            return CostEstimate.of(
                tradingDays, 0, tickerCount, agentsPerRound,
                0, BigDecimal.ZERO, BigDecimal.ZERO, modelId, modelName
            );
        }

        long totalConsensusRounds = (long) tradingDays * evalsPerDay;
        long totalLLMCalls = totalConsensusRounds * tickerCount * agentsPerRound;

        // Cost per call using raw pricing
        BigDecimal rawCostPerCall = pricing.estimateCost(avgPromptTokens, avgCompletionTokens);

        // Apply markup
        BigDecimal billedCostPerCall = rawCostPerCall.multiply(PlatformMarkup.rate());
        BigDecimal estimatedCost = billedCostPerCall
            .multiply(BigDecimal.valueOf(totalLLMCalls))
            .setScale(4, RoundingMode.HALF_UP);

        return CostEstimate.of(
            tradingDays,
            totalConsensusRounds,
            tickerCount,
            agentsPerRound,
            totalLLMCalls,
            billedCostPerCall.setScale(8, RoundingMode.HALF_UP),
            estimatedCost,
            modelId,
            modelName
        );
    }
}
