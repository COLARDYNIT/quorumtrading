package com.quorumtrading.adapter.rest.mapper;

import com.quorumtrading.adapter.rest.dto.response.ConsensusDecisionResponse;
import com.quorumtrading.adapter.rest.dto.response.ConsensusWithAgentsResponseDTO;
import com.quorumtrading.adapter.rest.dto.response.LLMDecisionResponse;
import com.quorumtrading.adapter.rest.dto.response.MarketEventResponse;
import com.quorumtrading.adapter.rest.dto.response.PortfolioSnapshotResponse;
import com.quorumtrading.application.usecase.dto.ConsensusWithAgentsResponse;
import com.quorumtrading.model.entity.ConsensusDecision;
import com.quorumtrading.model.entity.LLMDecision;
import com.quorumtrading.model.marketdata.MarketEvent;
import com.quorumtrading.model.entity.PortfolioSnapshot;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * MapStruct mapper for converting application layer domain objects to REST response DTOs.
 * Handles type conversions: UUID → String, Instant → String (ISO-8601), BigDecimal → double.
 */
@ApplicationScoped
@Mapper(componentModel = "cdi")
public interface DtoMapper {

    // ========== MarketEvent Mappings ==========

    @Mapping(target = "id", source = "id", qualifiedByName = "longToString")
    @Mapping(target = "timestamp", source = "timestamp", qualifiedByName = "instantToString")
    @Mapping(target = "eventType", source = "eventType", qualifiedByName = "eventTypeToString")
    @Mapping(target = "source", source = "source")
    @Mapping(target = "symbol", source = "symbol")
    @Mapping(target = "rawData", source = "rawData")
    @Mapping(target = "ingestedAt", source = "ingestedAt", qualifiedByName = "instantToString")
    MarketEventResponse toMarketEventResponse(MarketEvent marketEvent);

    List<MarketEventResponse> toMarketEventResponseList(List<MarketEvent> marketEvents);

    // ========== LLMDecision Mappings ==========

    @Mapping(target = "id", source = "id", qualifiedByName = "longToString")
    @Mapping(target = "simulationId", source = "simulationId", qualifiedByName = "simulationIdToString")
    @Mapping(target = "agentType", source = "role")
    @Mapping(target = "decisionTimestamp", source = "decisionTimestamp", qualifiedByName = "instantToString")
    @Mapping(target = "reasoning", source = "reasoning")
    @Mapping(target = "suggestedAction", source = "recommendedAction")
    @Mapping(target = "confidence", source = "confidenceScore", qualifiedByName = "percentageToDouble")
    @Mapping(target = "ticker", source = "ticker")
    @Mapping(target = "modelUsed", source = "LLMUsage.model")
    @Mapping(target = "promptTokens", source = "LLMUsage.promptTokens")
    @Mapping(target = "completionTokens", source = "LLMUsage.completionTokens")
    @Mapping(target = "totalTokens", source = "LLMUsage.totalTokens")
    @Mapping(target = "rawCostUsd", source = "LLMUsage.rawCost")
    @Mapping(target = "executionTimeMs", source = "executionTimeMs")
    @Mapping(target = "indicatorSnapshot", source = "indicatorSnapshot")
    @Mapping(target = "uiPayload", source = "uiPayload")
    LLMDecisionResponse toLLMDecisionResponse(LLMDecision llmDecision);

    List<LLMDecisionResponse> toLLMDecisionResponseList(List<LLMDecision> llmDecisions);

    // ========== ConsensusDecision Mappings ==========

    @Mapping(target = "id", source = "id", qualifiedByName = "longToString")
    @Mapping(target = "simulationId", source = "simulationId", qualifiedByName = "simulationIdToString")
    @Mapping(target = "decisionTimestamp", source = "decisionTimestamp", qualifiedByName = "instantToString")
    @Mapping(target = "consensusMethod", source = "consensusMethod", qualifiedByName = "enumToString")
    @Mapping(target = "finalAction", source = "finalAction")
    @Mapping(target = "executed", source = "executed")
    @Mapping(target = "executionOutcome", source = "executionOutcome", qualifiedByName = "enumToString")
    @Mapping(target = "notes", source = "notes")
    @Mapping(target = "symbol", source = "symbol", qualifiedByName = "symbolToString")
    @Mapping(target = "investmentType", source = "investmentType", qualifiedByName = "enumToString")
    @Mapping(target = "billedCostUsd", source = "billedCost", qualifiedByName = "moneyToBigDecimal")
    @Mapping(target = "executionPrice", source = "executionPrice", qualifiedByName = "moneyToBigDecimal")
    @Mapping(target = "quantity", source = "quantity")
    ConsensusDecisionResponse toConsensusDecisionResponse(ConsensusDecision consensusDecision);

    List<ConsensusDecisionResponse> toConsensusDecisionResponseList(List<ConsensusDecision> consensusDecisions);

    // ========== PortfolioSnapshot Mappings ==========

    @Mapping(target = "id", source = "id", qualifiedByName = "longToString")
    @Mapping(target = "simulationId", source = "simulationId", qualifiedByName = "simulationIdToString")
    @Mapping(target = "timestamp", source = "timestamp", qualifiedByName = "instantToString")
    @Mapping(target = "cashBalance", source = "cashAvailable", qualifiedByName = "moneyToDouble")
    @Mapping(target = "positions", source = "positions", qualifiedByName = "positionsMapToResponseMap")
    @Mapping(target = "totalValue", source = "totalValue", qualifiedByName = "moneyToDouble")
    PortfolioSnapshotResponse toPortfolioSnapshotResponse(PortfolioSnapshot portfolioSnapshot);

    List<PortfolioSnapshotResponse> toPortfolioSnapshotResponseList(List<PortfolioSnapshot> portfolioSnapshots);

    // ========== ConsensusWithAgents Mappings ==========

    /**
     * Maps ConsensusWithAgentsResponse (use case response) to ConsensusWithAgentsResponseDTO (REST API DTO).
     * Uses existing mappers for the nested consensus decision and agent decisions.
     */
    default ConsensusWithAgentsResponseDTO toConsensusWithAgentsResponseDTO(ConsensusWithAgentsResponse response) {
        if (response == null) {
            return null;
        }
        ConsensusDecisionResponse consensusDto = toConsensusDecisionResponse(response.getConsensusDecision());
        List<LLMDecisionResponse> agentDtos = toLLMDecisionResponseList(response.getAgentDecisions());
        return new ConsensusWithAgentsResponseDTO(consensusDto, agentDtos);
    }

    List<ConsensusWithAgentsResponseDTO> toConsensusWithAgentsResponseDTOList(List<ConsensusWithAgentsResponse> responses);

    // ========== MarketDataContext Mappings ==========

    /**
     * Maps MarketDataContext domain objects to MarketDataContextResponseDTO.
     * Converts the domain aggregate structure to the REST API response format.
     */
    default com.quorumtrading.adapter.rest.dto.response.MarketDataContextResponseDTO toMarketDataContextResponseDTO(
            Map<String, com.quorumtrading.model.marketdata.MarketDataContext> contextByTicker,
            com.quorumtrading.model.valueobject.SimulationId simulationId,
            Instant analysisTimestamp) {

        if (contextByTicker == null || contextByTicker.isEmpty()) {
            return new com.quorumtrading.adapter.rest.dto.response.MarketDataContextResponseDTO(
                    simulationId != null ? simulationId.value().toString() : null,
                    analysisTimestamp,
                    Map.of()
            );
        }

        Map<String, com.quorumtrading.adapter.rest.dto.response.MarketDataContextResponseDTO.TickerMarketDataDTO> tickerMap = new java.util.HashMap<>();

        for (Map.Entry<String, com.quorumtrading.model.marketdata.MarketDataContext> entry : contextByTicker.entrySet()) {
            String ticker = entry.getKey();
            com.quorumtrading.model.marketdata.MarketDataContext context = entry.getValue();
            com.quorumtrading.model.valueobject.Symbol symbol = new com.quorumtrading.model.valueobject.Symbol(ticker, com.quorumtrading.model.enums.InvestmentType.STOCK);

            // Build timeframe map
            Map<String, com.quorumtrading.adapter.rest.dto.response.MarketDataContextResponseDTO.TimeframeDataDTO> timeframeMap = new java.util.HashMap<>();

            for (com.quorumtrading.model.marketdata.Timeframe timeframe : com.quorumtrading.model.marketdata.Timeframe.values()) {
                List<com.quorumtrading.model.marketdata.MarketEvent> events = context.getEventsForSymbol(symbol, timeframe);

                if (!events.isEmpty()) {
                    // Calculate start and end times from events
                    Instant startTime = events.stream()
                            .map(com.quorumtrading.model.marketdata.MarketEvent::timestamp)
                            .min(Instant::compareTo)
                            .orElse(analysisTimestamp);
                    Instant endTime = events.stream()
                            .map(com.quorumtrading.model.marketdata.MarketEvent::timestamp)
                            .max(Instant::compareTo)
                            .orElse(analysisTimestamp);

                    // Convert MarketEvent domain objects to DTOs (filter rawData to only frontend-used fields)
                    List<com.quorumtrading.adapter.rest.dto.response.MarketDataContextResponseDTO.MarketEventDTO> eventDTOs = events.stream()
                            .map(event -> new com.quorumtrading.adapter.rest.dto.response.MarketDataContextResponseDTO.MarketEventDTO(
                                    event.timestamp(),
                                    event.eventType().toString(),
                                    event.source(),
                                    event.symbol(),
                                    filterRawData(event.rawData())
                            ))
                            .toList();

                    timeframeMap.put(
                            timeframe.name(),
                            new com.quorumtrading.adapter.rest.dto.response.MarketDataContextResponseDTO.TimeframeDataDTO(
                                    timeframe.name(),
                                    eventDTOs,
                                    startTime,
                                    endTime
                            )
                    );
                }
            }

            int totalEvents = context.getEventCount(symbol);

            tickerMap.put(ticker, new com.quorumtrading.adapter.rest.dto.response.MarketDataContextResponseDTO.TickerMarketDataDTO(
                    ticker,
                    timeframeMap,
                    totalEvents
            ));
        }

        return new com.quorumtrading.adapter.rest.dto.response.MarketDataContextResponseDTO(
                simulationId != null ? simulationId.value().toString() : null,
                analysisTimestamp,
                tickerMap
        );
    }

    // ========== Custom Type Conversions ==========

    @Named("longToString")
    default String longToString(Long value) {
        return value != null ? value.toString() : null;
    }

    @Named("simulationIdToString")
    default String simulationIdToString(com.quorumtrading.model.valueobject.SimulationId simulationId) {
        return simulationId != null ? simulationId.value().toString() : null;
    }

    @Named("symbolToString")
    default String symbolToString(com.quorumtrading.model.valueobject.Symbol symbol) {
        return symbol != null ? symbol.ticker() : null;
    }

    @Named("instantToString")
    default String instantToString(Instant instant) {
        return instant != null ? instant.toString() : null;
    }

    @Named("moneyToDouble")
    default double moneyToDouble(com.quorumtrading.model.valueobject.Money money) {
        return money != null ? money.amount().doubleValue() : 0.0;
    }

    @Named("moneyToBigDecimal")
    default BigDecimal moneyToBigDecimal(com.quorumtrading.model.valueobject.Money money) {
        return money != null ? money.amount() : null;
    }

    @Named("percentageToDouble")
    default double percentageToDouble(com.quorumtrading.model.valueobject.Percentage percentage) {
        return percentage != null ? percentage.value().doubleValue() : 0.0;
    }

    @Named("enumToString")
    default String enumToString(Object enumValue) {
        return enumValue != null ? enumValue.toString() : null;
    }

    @Named("eventTypeToString")
    default String eventTypeToString(Object type) {
        return type != null ? type.toString() : null;
    }

    /**
     * Convert domain positions Map to REST API response map.
     * Maps Symbol → Position to String (ticker) → PositionResponse.
     * Includes currentPrice if available (null for old snapshots or when market data unavailable).
     */
    @Named("positionsMapToResponseMap")
    default Map<String, com.quorumtrading.adapter.rest.dto.response.PositionResponse> positionsMapToResponseMap(
            Map<com.quorumtrading.model.valueobject.Symbol, com.quorumtrading.model.entity.Position> positions) {
        if (positions == null || positions.isEmpty()) {
            return Map.of();
        }

        return positions.entrySet().stream()
                .collect(Collectors.toMap(
                        entry -> entry.getKey().ticker(),  // Key: symbol ticker string
                        entry -> {
                            com.quorumtrading.model.entity.Position pos = entry.getValue();
                            // Extract currentPrice, handling null case for backward compatibility
                            Double currentPrice = pos.currentPrice() != null
                                ? pos.currentPrice().amount().doubleValue()
                                : null;
                            return new com.quorumtrading.adapter.rest.dto.response.PositionResponse(
                                    pos.symbol().ticker(),
                                    pos.symbol().type().toString(),
                                    pos.quantity().doubleValue(),
                                    pos.averagePrice().amount().doubleValue(),
                                    currentPrice  // Market price at snapshot time (nullable)
                            );
                        }
                ));
    }

    /**
     * Filter rawData map to only include fields the frontend actually uses.
     * PRICE events: price, currency
     * NEWS/SOCIAL events: title, headline
     */
    default Map<String, Object> filterRawData(Map<String, Object> rawData) {
        if (rawData == null || rawData.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> filtered = new java.util.HashMap<>();
        for (String key : java.util.List.of("price", "currency", "title", "headline")) {
            if (rawData.containsKey(key)) {
                filtered.put(key, rawData.get(key));
            }
        }
        return filtered;
    }

}
