package com.quorumtrading.adapter.websocket.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * WebSocket message for real-time technical indicator updates.
 * Sent when a ticker's price is updated and indicators are recalculated.
 *
 * <p>Matches frontend type: IndicatorUpdateMessage</p>
 *
 * <p><b>Message Structure:</b></p>
 * <pre>
 * {
 *   "type": "INDICATOR_UPDATE",
 *   "timestamp": "2026-01-15T14:00:00Z",
 *   "simulationId": "uuid",
 *   "ticker": "AAPL",
 *   "currentPrice": 185.50,
 *   "priceChange": 2.35,
 *   "priceChangePercent": 1.28,
 *   "indicators": {
 *     "rsi": { "value": 62.5, "signal": "NEUTRAL" },
 *     "macd": { "line": 1.25, "signal": 0.89, "histogram": 0.36, "trend": "BULLISH" },
 *     "sma50": 182.30,
 *     "sma200": 175.80,
 *     "bollinger": { "upper": 192.10, "middle": 185.00, "lower": 177.90 },
 *     "trend": "UPTREND"
 *   }
 * }
 * </pre>
 */
public class IndicatorUpdateMessage implements BaseStreamMessage {

    @JsonProperty("type")
    private final String type = "INDICATOR_UPDATE";

    @JsonProperty("timestamp")
    private String timestamp;

    @JsonProperty("simulationId")
    private String simulationId;

    @JsonProperty("ticker")
    private String ticker;

    @JsonProperty("tickerType")
    private String tickerType;

    @JsonProperty("currentPrice")
    private double currentPrice;

    @JsonProperty("priceChange")
    private double priceChange;

    @JsonProperty("priceChangePercent")
    private double priceChangePercent;

    @JsonProperty("indicators")
    private Map<String, Object> indicators;

    public IndicatorUpdateMessage() {
    }

    public IndicatorUpdateMessage(
            String timestamp,
            String simulationId,
            String ticker,
            String tickerType,
            double currentPrice,
            double priceChange,
            double priceChangePercent,
            Map<String, Object> indicators) {
        this.timestamp = timestamp;
        this.simulationId = simulationId;
        this.ticker = ticker;
        this.tickerType = tickerType;
        this.currentPrice = currentPrice;
        this.priceChange = priceChange;
        this.priceChangePercent = priceChangePercent;
        this.indicators = indicators;
    }

    @Override
    public String getType() {
        return type;
    }

    @Override
    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public String getSimulationId() {
        return simulationId;
    }

    public void setSimulationId(String simulationId) {
        this.simulationId = simulationId;
    }

    public String getTicker() {
        return ticker;
    }

    public void setTicker(String ticker) {
        this.ticker = ticker;
    }

    public String getTickerType() {
        return tickerType;
    }

    public void setTickerType(String tickerType) {
        this.tickerType = tickerType;
    }

    public double getCurrentPrice() {
        return currentPrice;
    }

    public void setCurrentPrice(double currentPrice) {
        this.currentPrice = currentPrice;
    }

    public double getPriceChange() {
        return priceChange;
    }

    public void setPriceChange(double priceChange) {
        this.priceChange = priceChange;
    }

    public double getPriceChangePercent() {
        return priceChangePercent;
    }

    public void setPriceChangePercent(double priceChangePercent) {
        this.priceChangePercent = priceChangePercent;
    }

    public Map<String, Object> getIndicators() {
        return indicators;
    }

    public void setIndicators(Map<String, Object> indicators) {
        this.indicators = indicators;
    }
}
