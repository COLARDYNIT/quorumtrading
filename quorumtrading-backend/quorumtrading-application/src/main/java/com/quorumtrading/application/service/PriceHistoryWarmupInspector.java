package com.quorumtrading.application.service;

import com.quorumtrading.application.port.out.MarketEventRepository;
import com.quorumtrading.model.enums.EventType;
import com.quorumtrading.model.orchestration.StrategyConfig;
import com.quorumtrading.model.valueobject.Symbol;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Reports which technical indicators a backtest will be unable to compute,
 * before it is paid for.
 *
 * <p><b>Why this exists.</b> An indicator needs history <em>before</em> the
 * period it is evaluated over: a 50-day moving average on the first day of a
 * backtest requires fifty trading days that precede that day. Nothing checked
 * whether that history was there. A user who backfilled a month and then
 * backtested it got a run that looked entirely successful — {@code COMPLETED},
 * progress 100, no failure reason — in which every rule built on SMA-50, MACD
 * or Bollinger Bands silently never evaluated, because those indicators were
 * null on every single day.</p>
 *
 * <p>The observed case: eighteen bars backfilled, a strategy requiring SMA-50,
 * MACD and Bollinger accepted without comment, fourteen consensus rounds and
 * twenty-eight agent calls spent, and a final result of exactly 0.00% that is
 * indistinguishable from "the strategy correctly chose not to trade". The truth
 * existed only inside the agents' own reasoning text. A user then spends hours
 * tuning thresholds against a run that could never have fired.</p>
 *
 * <p>This is deliberately a <b>warning, not a rejection</b>. Short backtests
 * over a slice of recent data are a legitimate and common way to try the
 * product, and refusing them would break the first thing a new user does. The
 * failure mode being fixed is silence, not permissiveness.</p>
 */
public class PriceHistoryWarmupInspector {

    /**
     * Warm-up requirement of every indicator the platform computes, in trading
     * days of history needed before the evaluation date.
     *
     * <p>These mirror {@code IndicatorEnrichmentService}: the platform computes
     * this fixed set for every ticker regardless of what the strategy text asks
     * for, so the shortfall can be reported without parsing the strategy body —
     * which would be guesswork, since the body is prose.</p>
     */
    private static final List<Indicator> INDICATORS = List.of(
        new Indicator("RSI-14", 15),
        new Indicator("Bollinger Bands (20)", 20),
        new Indicator("MACD (12/26/9)", 35),
        new Indicator("SMA-50 / EMA-50", 55),
        new Indicator("SMA-200 / EMA-200", 220)
    );

    private final MarketEventRepository marketEventRepository;

    public PriceHistoryWarmupInspector(MarketEventRepository marketEventRepository) {
        this.marketEventRepository = marketEventRepository;
    }

    /** One indicator and the bars of prior history it needs. */
    private record Indicator(String name, int barsRequired) {
    }

    /**
     * What a single ticker's stored history supports as of a start date.
     *
     * @param symbol        the instrument inspected
     * @param barsAvailable stored bars strictly before {@code startDate}
     * @param uncomputable  names of indicators that cannot be computed at all
     */
    public record TickerWarmup(Symbol symbol, long barsAvailable, List<String> uncomputable) {

        public boolean hasShortfall() {
            return !uncomputable.isEmpty();
        }
    }

    /**
     * Inspects every ticker a strategy trades.
     *
     * @param config    the parsed strategy configuration
     * @param startDate the backtest's first evaluation date
     * @return one entry per ticker that is missing at least one indicator; empty
     *     when everything the platform computes is fully supported
     */
    public List<TickerWarmup> inspect(StrategyConfig config, Instant startDate) {
        Set<Symbol> symbols = new LinkedHashSet<>();
        if (config.getStockTickers() != null) {
            symbols.addAll(config.getStockTickers());
        }
        if (config.getCryptoTickers() != null) {
            symbols.addAll(config.getCryptoTickers());
        }

        List<TickerWarmup> shortfalls = new ArrayList<>();
        for (Symbol symbol : symbols) {
            long bars = countBarsBefore(symbol, startDate);
            List<String> uncomputable = INDICATORS.stream()
                .filter(indicator -> bars < indicator.barsRequired())
                .map(Indicator::name)
                .toList();
            if (!uncomputable.isEmpty()) {
                shortfalls.add(new TickerWarmup(symbol, bars, uncomputable));
            }
        }
        return shortfalls;
    }

    /**
     * Renders a shortfall as a sentence to append to the creation confirmation.
     *
     * <p>Names the actual counts. "Insufficient data" tells a user nothing they
     * can act on; "18 bars stored before the start date, so SMA-50 needs 55"
     * tells them exactly how much more to backfill.</p>
     *
     * @return the warning, or an empty string when there is nothing to report
     */
    public String describe(List<TickerWarmup> shortfalls) {
        if (shortfalls == null || shortfalls.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(". WARNING: not enough price history before the start"
            + " date for every indicator, so rules built on the missing ones can never fire");
        for (TickerWarmup warmup : shortfalls) {
            sb.append(String.format(" — %s has %d bar(s) stored before the start date, leaving %s"
                    + " uncomputable",
                warmup.symbol().canonical(), warmup.barsAvailable(),
                String.join(", ", warmup.uncomputable())));
        }
        sb.append(". Back-fill further back than your start date (the longest indicator needs 220"
            + " prior bars) and re-check with get_price_coverage");
        return sb.toString();
    }

    /**
     * Counts stored bars strictly before the start date.
     *
     * <p>Bars on or after the start date are the ones being traded, not warm-up,
     * so counting them would report history the indicators cannot use.</p>
     */
    private long countBarsBefore(Symbol symbol, Instant startDate) {
        try {
            return marketEventRepository.countEventsInRange(
                symbol, EventType.PRICE, Instant.EPOCH, startDate.minusMillis(1));
        } catch (Exception e) {
            // A warning that cannot be produced must never block creation.
            return Long.MAX_VALUE;
        }
    }
}
