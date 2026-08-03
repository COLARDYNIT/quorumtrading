package com.quorumtrading.application.usecase;

import com.quorumtrading.application.port.out.HistoricalPricePort;
import com.quorumtrading.model.orchestration.StrategyConfig;
import com.quorumtrading.model.valueobject.Symbol;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Cross-checks a backtest window against stored price data.
 *
 * <p>A cost estimate prices the <em>schedule</em>, not the data. A confidently-quoted run over a
 * window with no bars evaluates nothing there and completes at 0.00% having decided nothing, so
 * the quote needs to say when the data is not there to back it.</p>
 *
 * <p>Extracted from {@code EstimateBacktestCostUseCase}: the coverage probe pulls in a cluster of
 * types (symbols, dates, coverage windows) that the estimator itself has no other use for, and
 * carrying them there pushed the class past the coupling limit once wall-clock projection landed
 * alongside it. Advisory by contract — every caller treats a failure here as "no warning".</p>
 */
class BacktestDataCoverageProbe {

    /** A window is called partial when more than this fraction of expected days is missing. */
    private static final int PARTIAL_COVERAGE_DIVISOR = 5;

    private final HistoricalPricePort historicalPricePort;

    BacktestDataCoverageProbe(HistoricalPricePort historicalPricePort) {
        this.historicalPricePort = historicalPricePort;
    }

    /**
     * @return a human-readable warning, or null when stored data covers the window well enough
     */
    String warningFor(StrategyConfig config, Instant from, Instant to) {
        LocalDate fromDay = LocalDate.ofInstant(from, ZoneOffset.UTC);
        LocalDate toDay = LocalDate.ofInstant(to, ZoneOffset.UTC);
        List<String> uncovered = new ArrayList<>();
        List<String> partial = new ArrayList<>();
        for (Symbol symbol : configuredTickers(config)) {
            classifyCoverage(symbol, fromDay, toDay, uncovered, partial);
        }
        if (uncovered.isEmpty() && partial.isEmpty()) {
            return null;
        }
        return buildCoverageMessage(uncovered, partial);
    }

    private Set<Symbol> configuredTickers(StrategyConfig config) {
        Set<Symbol> tickers = new LinkedHashSet<>();
        if (config.getCryptoTickers() != null) {
            tickers.addAll(config.getCryptoTickers());
        }
        if (config.getStockTickers() != null) {
            tickers.addAll(config.getStockTickers());
        }
        return tickers;
    }

    private void classifyCoverage(Symbol symbol, LocalDate from, LocalDate to,
                                  List<String> uncovered, List<String> partial) {
        var coverage = historicalPricePort.getCoverage(symbol, from, to);
        if (coverage == null || coverage.window() == null || coverage.window().expectedDays() == 0) {
            return;
        }
        var window = coverage.window();
        if (window.coveredDays() == 0) {
            uncovered.add(symbol.ticker());
        } else if (window.missingDays() * PARTIAL_COVERAGE_DIVISOR > window.expectedDays()) {
            partial.add(String.format("%s (%d of %d expected days stored)",
                symbol.ticker(), window.coveredDays(), window.expectedDays()));
        }
    }

    private static String buildCoverageMessage(List<String> uncovered, List<String> partial) {
        StringBuilder warning = new StringBuilder("Stored price data does not cover this window");
        if (!uncovered.isEmpty()) {
            warning.append(" — no bars at all for ").append(String.join(", ", uncovered));
        }
        if (!partial.isEmpty()) {
            warning.append(uncovered.isEmpty() ? " — only partial data for " : "; only partial data for ")
                .append(String.join(", ", partial));
        }
        return warning.append(". This estimate prices the schedule, not the data: the run would evaluate "
            + "nothing on the missing days. Run backfill_price_data first, or measure the window "
            + "with get_price_coverage.").toString();
    }
}
