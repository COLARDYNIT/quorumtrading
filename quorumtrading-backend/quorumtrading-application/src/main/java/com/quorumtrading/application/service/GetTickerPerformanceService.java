package com.quorumtrading.application.service;

import com.quorumtrading.application.port.out.DividendInfoPort;
import com.quorumtrading.application.port.out.TickerPerformancePort;
import com.quorumtrading.application.port.out.TickerPerformancePort.PerformanceAggregate;
import com.quorumtrading.application.usecase.GetTickerPerformanceUseCase;
import com.quorumtrading.application.usecase.query.GetTickerPerformanceQuery;
import com.quorumtrading.application.usecase.result.TickerPerformanceResult;
import com.quorumtrading.model.valueobject.DividendInfo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Lightweight ticker performance lookup using a single SQL aggregate query.
 * No simulation or benchmark creation required.
 */
public class GetTickerPerformanceService implements GetTickerPerformanceUseCase {

    private static final Logger LOG = Logger.getLogger(GetTickerPerformanceService.class.getName());

    private final TickerPerformancePort tickerPerformancePort;
    private final DividendInfoPort dividendInfoPort;

    public GetTickerPerformanceService(TickerPerformancePort tickerPerformancePort, DividendInfoPort dividendInfoPort) {
        this.tickerPerformancePort = tickerPerformancePort;
        this.dividendInfoPort = dividendInfoPort;
    }

    /** Backwards-compatible constructor. */
    public GetTickerPerformanceService(TickerPerformancePort tickerPerformancePort) {
        this(tickerPerformancePort, null);
    }

    @Override
    public TickerPerformanceResult execute(GetTickerPerformanceQuery query) {
        String ticker = query.ticker().toUpperCase();

        TickerPerformanceResult result = tickerPerformancePort.getPerformanceAggregate(ticker, query.startDate(), query.endDate())
            .map(agg -> {
                BigDecimal changeAbsolute = agg.endPrice().subtract(agg.startPrice());
                BigDecimal changePercent = agg.startPrice().compareTo(BigDecimal.ZERO) != 0
                    ? changeAbsolute.multiply(new BigDecimal("100")).divide(agg.startPrice(), 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

                return TickerPerformanceResult.withData(
                    ticker,
                    agg.startDate(), agg.endDate(),
                    agg.startPrice(), agg.endPrice(),
                    agg.periodHigh(), agg.periodLow(),
                    changePercent, changeAbsolute,
                    (int) agg.dataPoints());
            })
            .orElseGet(() -> TickerPerformanceResult.noData(ticker,
                "No price data available for " + ticker + " in the requested range. "
                + "Use backfill_price_data to load historical data first."));

        return enrichWithDividendInfo(result);
    }

    private TickerPerformanceResult enrichWithDividendInfo(TickerPerformanceResult result) {
        if (dividendInfoPort == null || !result.dataAvailable()) {
            return result;
        }
        try {
            Map<String, DividendInfo> infoMap = dividendInfoPort.getDividendInfo(Set.of(result.ticker()), Set.of());
            DividendInfo info = infoMap.get(result.ticker());
            if (info != null && info.paysDividendsKnown()) {
                String note = String.format(
                    "Returns shown are price-only. %s paid ~$%s/share in dividends over the trailing 12 months (%d payments/year). Actual total return would be higher.",
                    result.ticker(),
                    info.annualYieldEstimate() != null ? info.annualYieldEstimate().toPlainString() : "unknown",
                    info.dividendCountLastYear());
                return result.withDividendInfo(info, note);
            }
        } catch (Exception e) {
            LOG.log(Level.FINE, "Failed to enrich ticker performance with dividend info: {0}", e.getMessage());
        }
        return result;
    }
}
