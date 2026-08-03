package com.quorumtrading.adapter.rest.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * DTO for market data context response.
 * Contains the market events grouped by ticker that were (or would be) available
 * to AI agents during consensus evaluation.
 */
public class MarketDataContextResponseDTO {

    @JsonProperty("simulationId")
    private String simulationId;

    @JsonProperty("analysisTimestamp")
    private Instant analysisTimestamp;

    @JsonProperty("tickers")
    private Map<String, TickerMarketDataDTO> tickers;

    public MarketDataContextResponseDTO() {
    }

    public MarketDataContextResponseDTO(
            String simulationId,
            Instant analysisTimestamp,
            Map<String, TickerMarketDataDTO> tickers) {
        this.simulationId = simulationId;
        this.analysisTimestamp = analysisTimestamp;
        this.tickers = tickers;
    }

    public String getSimulationId() {
        return simulationId;
    }

    public void setSimulationId(String simulationId) {
        this.simulationId = simulationId;
    }

    public Instant getAnalysisTimestamp() {
        return analysisTimestamp;
    }

    public void setAnalysisTimestamp(Instant analysisTimestamp) {
        this.analysisTimestamp = analysisTimestamp;
    }

    public Map<String, TickerMarketDataDTO> getTickers() {
        return tickers;
    }

    public void setTickers(Map<String, TickerMarketDataDTO> tickers) {
        this.tickers = tickers;
    }

    /**
     * Market data for a specific ticker, organized by timeframe.
     */
    public static class TickerMarketDataDTO {

        @JsonProperty("symbol")
        private String symbol;

        @JsonProperty("timeframes")
        private Map<String, TimeframeDataDTO> timeframes;

        @JsonProperty("totalEvents")
        private int totalEvents;

        public TickerMarketDataDTO() {
        }

        public TickerMarketDataDTO(
                String symbol,
                Map<String, TimeframeDataDTO> timeframes,
                int totalEvents) {
            this.symbol = symbol;
            this.timeframes = timeframes;
            this.totalEvents = totalEvents;
        }

        public String getSymbol() {
            return symbol;
        }

        public void setSymbol(String symbol) {
            this.symbol = symbol;
        }

        public Map<String, TimeframeDataDTO> getTimeframes() {
            return timeframes;
        }

        public void setTimeframes(Map<String, TimeframeDataDTO> timeframes) {
            this.timeframes = timeframes;
        }

        public int getTotalEvents() {
            return totalEvents;
        }

        public void setTotalEvents(int totalEvents) {
            this.totalEvents = totalEvents;
        }
    }

    /**
     * Market events for a specific timeframe.
     */
    public static class TimeframeDataDTO {

        @JsonProperty("timeframe")
        private String timeframe;

        @JsonProperty("events")
        private List<MarketEventDTO> events;

        @JsonProperty("startTime")
        private Instant startTime;

        @JsonProperty("endTime")
        private Instant endTime;

        public TimeframeDataDTO() {
        }

        public TimeframeDataDTO(
                String timeframe,
                List<MarketEventDTO> events,
                Instant startTime,
                Instant endTime) {
            this.timeframe = timeframe;
            this.events = events;
            this.startTime = startTime;
            this.endTime = endTime;
        }

        public String getTimeframe() {
            return timeframe;
        }

        public void setTimeframe(String timeframe) {
            this.timeframe = timeframe;
        }

        public List<MarketEventDTO> getEvents() {
            return events;
        }

        public void setEvents(List<MarketEventDTO> events) {
            this.events = events;
        }

        public Instant getStartTime() {
            return startTime;
        }

        public void setStartTime(Instant startTime) {
            this.startTime = startTime;
        }

        public Instant getEndTime() {
            return endTime;
        }

        public void setEndTime(Instant endTime) {
            this.endTime = endTime;
        }
    }

    /**
     * Individual market event.
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public static class MarketEventDTO {

        @JsonProperty("timestamp")
        private Instant timestamp;

        @JsonProperty("eventType")
        private String eventType;

        @JsonProperty("source")
        private String source;

        @JsonProperty("symbol")
        private String symbol;

        @JsonProperty("rawData")
        private Map<String, Object> rawData;

        public MarketEventDTO() {
        }

        public MarketEventDTO(
                Instant timestamp,
                String eventType,
                String source,
                String symbol,
                Map<String, Object> rawData) {
            this.timestamp = timestamp;
            this.eventType = eventType;
            this.source = source;
            this.symbol = symbol;
            this.rawData = rawData;
        }

        public Instant getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(Instant timestamp) {
            this.timestamp = timestamp;
        }

        public String getEventType() {
            return eventType;
        }

        public void setEventType(String eventType) {
            this.eventType = eventType;
        }

        public String getSource() {
            return source;
        }

        public void setSource(String source) {
            this.source = source;
        }

        public String getSymbol() {
            return symbol;
        }

        public void setSymbol(String symbol) {
            this.symbol = symbol;
        }

        public Map<String, Object> getRawData() {
            return rawData;
        }

        public void setRawData(Map<String, Object> rawData) {
            this.rawData = rawData;
        }
    }
}
