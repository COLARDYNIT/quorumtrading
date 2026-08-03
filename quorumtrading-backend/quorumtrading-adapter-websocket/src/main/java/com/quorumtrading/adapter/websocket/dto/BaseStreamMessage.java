package com.quorumtrading.adapter.websocket.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Base interface for all WebSocket stream messages.
 * Uses Jackson polymorphic type handling to serialize with 'type' discriminator.
 */
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.EXISTING_PROPERTY,
    property = "type",
    visible = true
)
@JsonSubTypes({
    @JsonSubTypes.Type(value = SimulationStartedMessage.class, name = "SIMULATION_STARTED"),
    @JsonSubTypes.Type(value = SimulationProgressMessage.class, name = "SIMULATION_PROGRESS"),
    @JsonSubTypes.Type(value = SimulationCompletedMessage.class, name = "SIMULATION_COMPLETED"),
    @JsonSubTypes.Type(value = SimulationFailedMessage.class, name = "SIMULATION_FAILED"),
    @JsonSubTypes.Type(value = MarketEventMessage.class, name = "MARKET_EVENT"),
    @JsonSubTypes.Type(value = LLMDecisionMessage.class, name = "LLM_DECISION"),
    @JsonSubTypes.Type(value = ConsensusDecisionMessage.class, name = "CONSENSUS_DECISION"),
    @JsonSubTypes.Type(value = PortfolioUpdateMessage.class, name = "PORTFOLIO_UPDATE"),
    @JsonSubTypes.Type(value = TradeExecutionMessage.class, name = "TRADE_EXECUTION"),
    @JsonSubTypes.Type(value = IndicatorUpdateMessage.class, name = "INDICATOR_UPDATE"),
    @JsonSubTypes.Type(value = ErrorMessage.class, name = "ERROR")
})
public interface BaseStreamMessage {

    @JsonProperty("type")
    String getType();

    @JsonProperty("timestamp")
    String getTimestamp();

    @JsonProperty("simulationId")
    String getSimulationId();
}
