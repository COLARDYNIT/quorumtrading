package com.quorumtrading.application.service;

import com.quorumtrading.application.port.out.HistoricalPricePort;
import com.quorumtrading.application.usecase.BackFillTickerPriceDataUseCase;
import com.quorumtrading.application.usecase.command.BackFillTickerPriceDataCommand;
import com.quorumtrading.model.valueobject.BuyAndHoldDataPoint;
import com.quorumtrading.model.valueobject.OverlayResult;
import com.quorumtrading.model.valueobject.PriceBar;
import com.quorumtrading.model.valueobject.Symbol;
import org.jboss.logging.Logger;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Computes a buy-and-hold equity curve for a single symbol over a date range.
 *
 * <p>Reusable by both benchmark overlays and simulation overlays.
 * Auto-backfills missing price data when a gap exceeding {@value GAP_THRESHOLD_DAYS} days
 * is detected at the start of the range.</p>
 */
public class BuyAndHoldOverlayCalculator {

    private static final Logger LOG = Logger.getLogger(BuyAndHoldOverlayCalculator.class);
    static final long GAP_THRESHOLD_DAYS = 5;

    private final HistoricalPricePort historicalPricePort;
    private final BackFillTickerPriceDataUseCase backFillTickerPriceDataUseCase;

    public BuyAndHoldOverlayCalculator(
            HistoricalPricePort historicalPricePort,
            BackFillTickerPriceDataUseCase backFillTickerPriceDataUseCase) {
        this.historicalPricePort = historicalPricePort;
        this.backFillTickerPriceDataUseCase = backFillTickerPriceDataUseCase;
    }

    /**
     * Computes the buy-and-hold overlay for a single symbol.
     *
     * @param symbol     the reference asset
     * @param startDate  overlay start (inclusive)
     * @param endDate    overlay end (inclusive)
     * @param capital    initial capital in USD
     * @param resolution time bucket for price bars (e.g. "1d", "1h")
     * @return overlay result with equity curve or NO_DATA status
     */
    public OverlayResult computeOverlay(Symbol symbol, Instant startDate, Instant endDate,
                                         BigDecimal capital, String resolution) {
        String assetType = symbol.type().name();

        List<PriceBar> bars = historicalPricePort.getHistoricalPrices(symbol, startDate, endDate, resolution);

        boolean needsBackfill = bars.isEmpty()
            || ChronoUnit.DAYS.between(startDate, bars.get(0).timestamp()) > GAP_THRESHOLD_DAYS;

        if (needsBackfill) {
            Instant backfillEnd = bars.isEmpty() ? endDate : bars.get(0).timestamp();
            LOG.infof("Price data gap for %s (first bar: %s, requested start: %s), attempting backfill from %s to %s",
                symbol.ticker(),
                bars.isEmpty() ? "none" : bars.get(0).timestamp(),
                startDate, startDate, backfillEnd);
            try {
                backFillTickerPriceDataUseCase.execute(new BackFillTickerPriceDataCommand(
                    symbol.ticker(), assetType, startDate, backfillEnd, null));
                bars = historicalPricePort.getHistoricalPrices(symbol, startDate, endDate, resolution);
            } catch (Exception e) {
                LOG.warnf("Backfill failed for %s: %s", symbol.ticker(), e.getMessage());
            }
        }

        if (bars.isEmpty()) {
            return OverlayResult.noData(symbol.ticker(), assetType,
                "No price data available for " + symbol.ticker() + " in the given date range");
        }

        BigDecimal firstClose = bars.get(0).close();
        BigDecimal shares = capital.divide(firstClose, 8, RoundingMode.HALF_UP);

        List<BuyAndHoldDataPoint> dataPoints = new ArrayList<>(bars.size());
        for (PriceBar bar : bars) {
            BigDecimal value = shares.multiply(bar.close()).setScale(2, RoundingMode.HALF_UP);
            dataPoints.add(new BuyAndHoldDataPoint(bar.timestamp(), value));
        }

        BigDecimal lastValue = dataPoints.get(dataPoints.size() - 1).portfolioValue();
        BigDecimal returnPercent = lastValue.subtract(capital)
            .divide(capital, 4, RoundingMode.HALF_UP)
            .multiply(new BigDecimal("100"));

        LOG.debugf("Computed overlay for %s: %d data points, return=%.2f%%",
            symbol.ticker(), dataPoints.size(), returnPercent);

        return OverlayResult.ready(symbol.ticker(), assetType, dataPoints, returnPercent);
    }
}
