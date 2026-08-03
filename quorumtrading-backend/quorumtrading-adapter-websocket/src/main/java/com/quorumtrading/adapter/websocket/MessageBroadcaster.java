package com.quorumtrading.adapter.websocket;

import com.quorumtrading.adapter.websocket.dto.*;
import com.quorumtrading.adapter.websocket.event.*;
import com.quorumtrading.adapter.websocket.mapper.WebSocketMessageMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Message broadcaster that observes CDI events from domain services
 * and broadcasts them to subscribed WebSocket clients.
 *
 * Acts as a bridge between domain events and WebSocket infrastructure:
 * Domain Service → fires CDI event → MessageBroadcaster observes →
 *   → converts to DTO via mapper →
 *     → queries ChannelManager for subscribers →
 *       → broadcasts to WebSocket sessions
 */
@ApplicationScoped
public class MessageBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(MessageBroadcaster.class);

    @Inject
    ChannelManager channelManager;

    @Inject
    StreamEndpoint streamEndpoint;

    @Inject
    WebSocketMessageMapper mapper;

    /**
     * Observes simulation progress events and broadcasts to subscribers.
     */
    public void onSimulationProgress(@Observes SimulationProgressEvent event) {
        log.debug("Received SimulationProgressEvent: simulationId={}, status={}",
            event.getSimulationId(), event.getStatus());

        try {
            // Build event data map for filter matching
            Map<String, Object> eventData = new HashMap<>();
            eventData.put("simulationId", event.getSimulationId().toString());

            // Get subscribed sessions (no channel-based filtering, just by simulationId)
            Set<String> sessionIds = channelManager.getSessionsBySimulationId(event.getSimulationId().toString());

            log.info("Broadcasting simulation progress to {} subscribers: simulationId={}, status={}",
                sessionIds.size(), event.getSimulationId(), event.getStatus());

            if (sessionIds.isEmpty()) {
                log.debug("No subscribers for simulation progress event");
                return;
            }

            // Convert to DTO using mapper
            SimulationProgressMessage message = mapper.toSimulationProgressMessage(event);

            // Broadcast to each session
            streamEndpoint.broadcast(sessionIds, message);

        } catch (Exception e) {
            log.error("Failed to broadcast simulation progress event: {}", e.getMessage(), e);
        }
    }

    /**
     * Observes agent decision events and broadcasts to subscribers.
     */
    public void onAgentDecision(@Observes AgentDecisionEvent event) {
        log.debug("Received AgentDecisionEvent: simulationId={}, agentId={}",
            event.getSimulationId(), event.getAgentId());

        try {
            // Get subscribed sessions
            Set<String> sessionIds = channelManager.getSessionsBySimulationId(event.getSimulationId().toString());

            log.info("Broadcasting agent decision to {} subscribers: simulationId={}, agentId={}",
                sessionIds.size(), event.getSimulationId(), event.getAgentId());

            if (sessionIds.isEmpty()) {
                log.debug("No subscribers for agent decision event");
                return;
            }

            // Convert to DTO using mapper
            LLMDecisionMessage message = mapper.toLLMDecisionMessage(event);

            // Broadcast to each session
            streamEndpoint.broadcast(sessionIds, message);

        } catch (Exception e) {
            log.error("Failed to broadcast agent decision event: {}", e.getMessage(), e);
        }
    }

    /**
     * Observes market events and broadcasts to subscribers.
     * Note: Market events are not simulation-specific, so we broadcast to all subscribers.
     */
    public void onMarketEvent(@Observes MarketEventReceived event) {
        log.debug("Received MarketEventReceived: symbol={}, eventType={}",
            event.getSymbol(), event.getEventType());

        try {
            // Get all active sessions (market events are global)
            Set<String> sessionIds = channelManager.getAllActiveSessions();

            log.info("Broadcasting market event to {} subscribers: symbol={}, eventType={}",
                sessionIds.size(), event.getSymbol(), event.getEventType());

            if (sessionIds.isEmpty()) {
                log.debug("No subscribers for market event");
                return;
            }

            // Convert to DTO using mapper
            MarketEventMessage message = mapper.toMarketEventMessage(event);

            // Broadcast to each session
            streamEndpoint.broadcast(sessionIds, message);

        } catch (Exception e) {
            log.error("Failed to broadcast market event: {}", e.getMessage(), e);
        }
    }

    /**
     * Observes portfolio change events and broadcasts to subscribers.
     */
    public void onPortfolioChange(@Observes PortfolioChangeEvent event) {
        log.debug("Received PortfolioChangeEvent: simulationId={}",
            event.getSimulationId());

        try {
            // Get subscribed sessions
            Set<String> sessionIds = channelManager.getSessionsBySimulationId(event.getSimulationId().toString());

            log.info("Broadcasting portfolio change to {} subscribers: simulationId={}",
                sessionIds.size(), event.getSimulationId());

            if (sessionIds.isEmpty()) {
                log.debug("No subscribers for portfolio change event");
                return;
            }

            // Convert to DTO using mapper
            PortfolioUpdateMessage message = mapper.toPortfolioUpdateMessage(event);

            // Broadcast to each session
            streamEndpoint.broadcast(sessionIds, message);

        } catch (Exception e) {
            log.error("Failed to broadcast portfolio change event: {}", e.getMessage(), e);
        }
    }

    /**
     * Observes LLM decision created events (from application layer) and converts to
     * agent decision events for WebSocket broadcasting.
     */
    public void onLLMDecisionCreated(@Observes com.quorumtrading.application.event.LLMDecisionCreatedEvent event) {
        log.debug("Received LLMDecisionCreatedEvent: simulationId={}, agentRole={}",
            event.getSimulationId(), event.getAgentRole());

        try {
            // Convert application layer event to adapter layer event with full decision data
            AgentDecisionEvent adapterEvent = new AgentDecisionEvent(
                event.getSimulationId().value(),
                event.getDecisionId(),
                event.getAgentRole(),
                event.getRecommendation(),
                event.getReasoning(),
                event.getSuggestedAction(),
                event.getConfidence(),
                event.getTimestamp(),
                event.getIndicatorSnapshot(),
                event.getUiPayload()
            );

            // Reuse existing agent decision handler
            onAgentDecision(adapterEvent);

        } catch (Exception e) {
            log.error("Failed to process LLM decision created event: {}", e.getMessage(), e);
        }
    }

    /**
     * Observes application layer trade execution events and converts to adapter events for WebSocket broadcasting.
     */
    public void onApplicationTradeExecution(@Observes com.quorumtrading.application.event.TradeExecutionEvent event) {
        log.debug("Received application TradeExecutionEvent: simulationId={}, action={}, success={}",
            event.getSimulationId(), event.getAction(), event.isSuccess());

        try {
            // Convert application event to adapter event
            TradeExecutionEvent adapterEvent = new TradeExecutionEvent(
                event.getSimulationId().value(),
                event.getConsensusDecisionId(),
                event.getAction(),
                event.getSymbol(),
                event.getQuantity(),
                event.getExecutionPrice(),
                event.isSuccess(),
                event.getMessage(),
                event.getPortfolioSnapshotId(),
                event.getTimestamp()
            );

            // Get subscribed sessions
            Set<String> sessionIds = channelManager.getSessionsBySimulationId(event.getSimulationId().value().toString());

            log.info("Broadcasting trade execution to {} subscribers: simulationId={}, action={}, success={}",
                sessionIds.size(), event.getSimulationId(), event.getAction(), event.isSuccess());

            if (sessionIds.isEmpty()) {
                log.debug("No subscribers for trade execution event");
                return;
            }

            // Convert to DTO using mapper
            TradeExecutionMessage message = mapper.toTradeExecutionMessage(adapterEvent);

            // Broadcast to each session
            streamEndpoint.broadcast(sessionIds, message);

        } catch (Exception e) {
            log.error("Failed to broadcast trade execution event: {}", e.getMessage(), e);
        }
    }

    /**
     * Observes application layer portfolio change events and converts to adapter events for WebSocket broadcasting.
     */
    public void onApplicationPortfolioChange(@Observes com.quorumtrading.application.event.PortfolioChangeEvent event) {
        log.debug("Received application PortfolioChangeEvent: simulationId={}",
            event.simulationId());

        try {
            // Serialize positions Map to JSON for WebSocket transmission
            String positionsJson = serializePositionsForWebSocket(event.positions());

            // Convert application event to adapter event
            PortfolioChangeEvent adapterEvent = new PortfolioChangeEvent(
                event.simulationId().value(),
                event.totalValueUsd(),
                event.cashAvailable(),
                event.timestamp(),
                positionsJson
            );

            // Reuse existing portfolio change handler
            onPortfolioChange(adapterEvent);

        } catch (Exception e) {
            log.error("Failed to process application portfolio change event: {}", e.getMessage(), e);
        }
    }

    /**
     * Serialize positions Map to JSON string for WebSocket transmission.
     * Format: [{"symbol":"BTC","type":"CRYPTO","quantity":0.5,"averagePrice":50000.00,"currentPrice":51000.00}]
     *
     * This serialization happens in the WebSocket adapter layer because:
     * - JSON is a WebSocket/transport concern, not a domain concern
     * - Domain model uses Map<Symbol, Position>, which is the proper type
     * - Adapter layer translates between domain types and wire formats
     */
    private String serializePositionsForWebSocket(java.util.Map<com.quorumtrading.model.valueobject.Symbol, com.quorumtrading.model.entity.Position> positions) {
        if (positions == null || positions.isEmpty()) {
            return "[]";
        }

        StringBuilder json = new StringBuilder("[");
        boolean first = true;
        for (java.util.Map.Entry<com.quorumtrading.model.valueobject.Symbol, com.quorumtrading.model.entity.Position> entry : positions.entrySet()) {
            if (!first) {
                json.append(",");
            }
            first = false;
            com.quorumtrading.model.entity.Position pos = entry.getValue();
            json.append("{")
                    .append("\"symbol\":\"").append(pos.symbol().ticker()).append("\",")
                    .append("\"type\":\"").append(pos.symbol().type()).append("\",")
                    .append("\"quantity\":").append(pos.quantity()).append(",")
                    .append("\"averagePrice\":").append(pos.averagePrice().amount());
            // Include currentPrice if available (may be null for positions without price updates)
            if (pos.currentPrice() != null) {
                json.append(",\"currentPrice\":").append(pos.currentPrice().amount());
            }
            json.append("}");
        }
        json.append("]");
        return json.toString();
    }

    /**
     * Observes consensus started events and broadcasts to subscribers.
     */
    void onConsensusStarted(@Observes com.quorumtrading.application.event.ConsensusStartedEvent event) {
        log.debug("Broadcasting CONSENSUS_STARTED for simulation: {}", event.getSimulationId());

        try {
            // Get subscribed sessions
            Set<String> sessionIds = channelManager.getSessionsBySimulationId(event.getSimulationId().value().toString());

            log.info("Broadcasting consensus started to {} subscribers: simulationId={}, consensusRequestId={}",
                sessionIds.size(), event.getSimulationId(), event.getConsensusRequestId());

            if (sessionIds.isEmpty()) {
                log.debug("No subscribers for consensus started event");
                return;
            }

            // Build message map
            var message = Map.of(
                "type", "CONSENSUS_STARTED",
                "timestamp", java.time.Instant.now().toString(),
                "simulationId", event.getSimulationId().value().toString(),
                "consensusRequestId", event.getConsensusRequestId(),
                "decisionTimestamp", event.getDecisionTimestamp().toString()
            );

            // Broadcast to each session
            streamEndpoint.broadcast(sessionIds, message);

        } catch (Exception e) {
            log.error("Failed to broadcast consensus started event: {}", e.getMessage(), e);
        }
    }

    /**
     * Observes consensus skipped events and broadcasts to subscribers.
     * This clears the "in progress" state on the frontend when consensus is skipped
     * (e.g., no market data available, simulation paused).
     */
    void onConsensusSkipped(@Observes com.quorumtrading.application.event.ConsensusSkippedEvent event) {
        log.info("Broadcasting CONSENSUS_SKIPPED for simulation: {} reason: {}",
            event.simulationId(), event.reason());

        try {
            Set<String> sessionIds = channelManager.getSessionsBySimulationId(event.simulationId().value().toString());

            if (sessionIds.isEmpty()) {
                log.debug("No subscribers for consensus skipped event");
                return;
            }

            var message = Map.of(
                "type", "CONSENSUS_SKIPPED",
                "timestamp", java.time.Instant.now().toString(),
                "simulationId", event.simulationId().value().toString(),
                "consensusRequestId", event.consensusRequestId(),
                "decisionTimestamp", event.decisionTimestamp().toString(),
                "reason", event.reason()
            );

            streamEndpoint.broadcast(sessionIds, message);

        } catch (Exception e) {
            log.error("Failed to broadcast consensus skipped event: {}", e.getMessage(), e);
        }
    }

    /**
     * Observes simulation completed events (async) and broadcasts to subscribers.
     * Uses @ObservesAsync since events are published via fireAsync().
     */
    void onSimulationCompleted(@ObservesAsync com.quorumtrading.application.event.SimulationCompletedEvent event) {
        log.info("Broadcasting SIMULATION_COMPLETED for simulation: {}", event.simulationId());

        try {
            // Get subscribed sessions
            Set<String> sessionIds = channelManager.getSessionsBySimulationId(event.simulationId().value().toString());

            log.info("Broadcasting simulation completed to {} subscribers: simulationId={}",
                sessionIds.size(), event.simulationId());

            if (sessionIds.isEmpty()) {
                log.debug("No subscribers for simulation completed event");
                return;
            }

            // Build message map
            var message = Map.of(
                "type", "SIMULATION_COMPLETED",
                "timestamp", java.time.Instant.now().toString(),
                "simulationId", event.simulationId().value().toString(),
                "strategyId", event.strategyId().value().toString(),
                "completedAt", event.completedAt() != null ? event.completedAt().toString() : java.time.Instant.now().toString()
            );

            // Broadcast to each session
            streamEndpoint.broadcast(sessionIds, message);

        } catch (Exception e) {
            log.error("Failed to broadcast simulation completed event: {}", e.getMessage(), e);
        }
    }

    /**
     * Observes indicator update events (from application layer) and broadcasts
     * real-time technical indicator updates to subscribed clients.
     *
     * <p>This enables the frontend to display live indicator values
     * (RSI, MACD, SMA, Bollinger Bands) as prices update.</p>
     */
    public void onIndicatorUpdate(@Observes com.quorumtrading.application.event.IndicatorUpdateEvent event) {
        log.debug("Received IndicatorUpdateEvent: simulationId={}, ticker={}",
            event.getSimulationId(), event.getTicker().ticker());

        try {
            // Get subscribed sessions
            Set<String> sessionIds = channelManager.getSessionsBySimulationId(event.getSimulationId().value().toString());

            if (sessionIds.isEmpty()) {
                log.debug("No subscribers for indicator update event");
                return;
            }

            log.info("Broadcasting indicator update to {} subscribers: simulationId={}, ticker={}",
                sessionIds.size(), event.getSimulationId(), event.getTicker().ticker());

            // Convert to DTO using mapper
            IndicatorUpdateMessage message = mapper.toIndicatorUpdateMessage(event);

            // Broadcast to each session
            streamEndpoint.broadcast(sessionIds, message);

        } catch (Exception e) {
            log.error("Failed to broadcast indicator update event: {}", e.getMessage(), e);
        }
    }
}
