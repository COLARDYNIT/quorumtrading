package com.quorumtrading.adapter.websocket.mapper;

import com.quorumtrading.adapter.websocket.dto.*;
import com.quorumtrading.adapter.websocket.event.AgentDecisionEvent;
import com.quorumtrading.adapter.websocket.event.MarketEventReceived;
import com.quorumtrading.adapter.websocket.event.PortfolioChangeEvent;
import com.quorumtrading.adapter.websocket.event.SimulationProgressEvent;
import com.quorumtrading.adapter.websocket.event.TradeExecutionEvent;
import com.quorumtrading.application.event.IndicatorUpdateEvent;
import com.quorumtrading.model.valueobject.IndicatorContext;
import com.quorumtrading.model.valueobject.Symbol;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * MapStruct mapper for converting CDI events to WebSocket DTOs.
 * Aligns backend events with frontend message type expectations.
 */
@ApplicationScoped
@Mapper(componentModel = "cdi")
public interface WebSocketMessageMapper {

    @Mapping(target = "timestamp", source = "timestamp", qualifiedByName = "instantToString")
    @Mapping(target = "simulationId", source = "simulationId", qualifiedByName = "uuidToString")
    @Mapping(target = "currentTimestamp", source = "timestamp", qualifiedByName = "instantToString")
    @Mapping(target = "eventsProcessed", constant = "0") // Will need to be set manually if available
    SimulationProgressMessage toSimulationProgressMessage(SimulationProgressEvent event);

    @Mapping(target = "timestamp", source = "timestamp", qualifiedByName = "instantToString")
    @Mapping(target = "simulationId", source = "simulationId", qualifiedByName = "uuidToString")
    @Mapping(target = "agentType", source = "agentId")
    @Mapping(target = "reasoning", source = "reasoning")
    @Mapping(target = "suggestedAction", source = "suggestedAction")
    @Mapping(target = "confidence", source = "confidence")
    @Mapping(target = "indicatorSnapshot", source = "indicatorSnapshot")
    @Mapping(target = "uiPayload", source = "uiPayload")
    LLMDecisionMessage toLLMDecisionMessage(AgentDecisionEvent event);

    @Mapping(target = "timestamp", source = "timestamp", qualifiedByName = "instantToString")
    @Mapping(target = "simulationId", constant = "") // Not available in MarketEventReceived
    @Mapping(target = "eventId", constant = "0L") // Not available in MarketEventReceived
    MarketEventMessage toMarketEventMessage(MarketEventReceived event);

    @Mapping(target = "timestamp", source = "timestamp", qualifiedByName = "instantToString")
    @Mapping(target = "simulationId", source = "simulationId", qualifiedByName = "uuidToString")
    @Mapping(target = "cashBalance", source = "cashAvailable", qualifiedByName = "bigDecimalToDouble")
    @Mapping(target = "totalValue", source = "totalValueUsd", qualifiedByName = "bigDecimalToDouble")
    @Mapping(target = "positions", source = "positions", qualifiedByName = "parsePositionsJson")
    PortfolioUpdateMessage toPortfolioUpdateMessage(PortfolioChangeEvent event);

    @Mapping(target = "timestamp", source = "timestamp", qualifiedByName = "instantToString")
    @Mapping(target = "simulationId", source = "simulationId", qualifiedByName = "uuidToString")
    @Mapping(target = "quantity", source = "quantity", qualifiedByName = "bigDecimalToDouble")
    @Mapping(target = "executionPrice", source = "executionPrice", qualifiedByName = "bigDecimalToDouble")
    TradeExecutionMessage toTradeExecutionMessage(TradeExecutionEvent event);

    @Named("instantToString")
    default String instantToString(Instant instant) {
        return instant != null ? instant.toString() : null;
    }

    @Named("uuidToString")
    default String uuidToString(UUID uuid) {
        return uuid != null ? uuid.toString() : null;
    }

    @Named("bigDecimalToDouble")
    default double bigDecimalToDouble(BigDecimal value) {
        return value != null ? value.doubleValue() : 0.0;
    }

    @Named("parsePositionsJson")
    default Map<String, Object> parsePositionsJson(String positionsJson) {
        if (positionsJson == null || positionsJson.isEmpty()) {
            return Collections.emptyMap();
        }
        try {
            ObjectMapper mapper = new ObjectMapper();
            // Parse as array first
            List<Map<String, Object>> positionsList = mapper.readValue(
                positionsJson,
                new TypeReference<List<Map<String, Object>>>() {}
            );

            // Convert to Map keyed by symbol
            return positionsList.stream()
                .filter(p -> p.containsKey("symbol"))
                .collect(Collectors.toMap(
                    p -> (String) p.get("symbol"),
                    p -> p,
                    (existing, replacement) -> replacement
                ));
        } catch (Exception e) {
            // Gracefully degrade - return empty map to prevent WebSocket message failures
            return Collections.emptyMap();
        }
    }

    /**
     * Convert IndicatorUpdateEvent to IndicatorUpdateMessage.
     * Uses the domain object's toStorageMap() to format indicators for JSON.
     */
    default IndicatorUpdateMessage toIndicatorUpdateMessage(IndicatorUpdateEvent event) {
        // Build indicator context for single ticker to use toStorageMap()
        Map<Symbol, com.quorumtrading.model.valueobject.IndicatorSnapshot> singleTicker = Map.of(
            event.getTicker(), event.getIndicators()
        );
        IndicatorContext context = new IndicatorContext(singleTicker);
        Map<String, Object> storageMap = context.toStorageMap();

        // Extract the single ticker's indicators from the storage map
        @SuppressWarnings("unchecked")
        Map<String, Object> tickerData = (Map<String, Object>) storageMap.get(event.getTicker().ticker());
        @SuppressWarnings("unchecked")
        Map<String, Object> indicators = tickerData != null
            ? (Map<String, Object>) tickerData.get("indicators")
            : Collections.emptyMap();

        // Add derived trend signal based on SMA crossover
        Map<String, Object> enrichedIndicators = new LinkedHashMap<>(indicators);
        enrichedIndicators.put("trend", calculateTrendSignal(indicators));

        return new IndicatorUpdateMessage(
            event.getTimestamp().toString(),
            event.getSimulationId().value().toString(),
            event.getTicker().ticker(),
            event.getTicker().type().name(),
            event.getCurrentPrice().doubleValue(),
            event.getPriceChange().setScale(2, RoundingMode.HALF_UP).doubleValue(),
            event.getPriceChangePercent().setScale(2, RoundingMode.HALF_UP).doubleValue(),
            enrichedIndicators
        );
    }

    /**
     * Calculate trend signal based on SMA crossover.
     * - UPTREND: SMA-50 > SMA-200 (golden cross territory)
     * - DOWNTREND: SMA-50 < SMA-200 (death cross territory)
     * - SIDEWAYS: No clear trend or insufficient data
     */
    @SuppressWarnings("unchecked")
    private String calculateTrendSignal(Map<String, Object> indicators) {
        if (indicators == null) {
            return "SIDEWAYS";
        }

        Map<String, Object> sma50Data = (Map<String, Object>) indicators.get("SMA_50");
        Map<String, Object> sma200Data = (Map<String, Object>) indicators.get("SMA_200");

        if (sma50Data == null || sma200Data == null) {
            return "SIDEWAYS";
        }

        Object sma50Obj = sma50Data.get("value");
        Object sma200Obj = sma200Data.get("value");

        if (sma50Obj == null || sma200Obj == null) {
            return "SIDEWAYS";
        }

        BigDecimal sma50 = sma50Obj instanceof BigDecimal ? (BigDecimal) sma50Obj : new BigDecimal(sma50Obj.toString());
        BigDecimal sma200 = sma200Obj instanceof BigDecimal ? (BigDecimal) sma200Obj : new BigDecimal(sma200Obj.toString());

        int comparison = sma50.compareTo(sma200);
        if (comparison > 0) {
            return "UPTREND";
        } else if (comparison < 0) {
            return "DOWNTREND";
        }
        return "SIDEWAYS";
    }
}
