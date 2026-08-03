package com.quorumtrading.application.event;

import com.quorumtrading.model.valueobject.IndicatorSnapshot;
import com.quorumtrading.model.valueobject.SimulationId;
import com.quorumtrading.model.valueobject.Symbol;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Domain event fired when technical indicators are updated for a ticker.
 * This event is observed by adapters (e.g., WebSocket adapter) to broadcast real-time
 * indicator updates to subscribed clients.
 *
 * <p><b>Triggered by:</b> Price updates for tickers tracked by active simulations</p>
 * <p><b>Observed by:</b> MessageBroadcaster in WebSocket adapter</p>
 *
 * @see com.quorumtrading.adapter.websocket.MessageBroadcaster
 */
public class IndicatorUpdateEvent {

    private final SimulationId simulationId;
    private final Symbol ticker;
    private final BigDecimal currentPrice;
    private final BigDecimal previousPrice;
    private final IndicatorSnapshot indicators;
    private final Instant timestamp;

    public IndicatorUpdateEvent(
            SimulationId simulationId,
            Symbol ticker,
            BigDecimal currentPrice,
            BigDecimal previousPrice,
            IndicatorSnapshot indicators,
            Instant timestamp) {
        this.simulationId = Objects.requireNonNull(simulationId, "simulationId is required");
        this.ticker = Objects.requireNonNull(ticker, "ticker is required");
        this.currentPrice = Objects.requireNonNull(currentPrice, "currentPrice is required");
        this.previousPrice = previousPrice; // Can be null for first update
        this.indicators = Objects.requireNonNull(indicators, "indicators is required");
        this.timestamp = Objects.requireNonNull(timestamp, "timestamp is required");
    }

    public SimulationId getSimulationId() {
        return simulationId;
    }

    public Symbol getTicker() {
        return ticker;
    }

    public BigDecimal getCurrentPrice() {
        return currentPrice;
    }

    public BigDecimal getPreviousPrice() {
        return previousPrice;
    }

    /**
     * Calculate price change from previous price.
     * @return Price difference, or BigDecimal.ZERO if no previous price
     */
    public BigDecimal getPriceChange() {
        if (previousPrice == null || previousPrice.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return currentPrice.subtract(previousPrice);
    }

    /**
     * Calculate price change percentage from previous price.
     * @return Percentage change (e.g., 2.5 for 2.5%), or BigDecimal.ZERO if no previous price
     */
    public BigDecimal getPriceChangePercent() {
        if (previousPrice == null || previousPrice.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return currentPrice.subtract(previousPrice)
                .divide(previousPrice, 6, java.math.RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"));
    }

    public IndicatorSnapshot getIndicators() {
        return indicators;
    }

    public Instant getTimestamp() {
        return timestamp;
    }
}
