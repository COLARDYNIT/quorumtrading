package com.quorumtrading.adapter.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ManagedContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.websocket.*;
import jakarta.websocket.server.ServerEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket endpoint for real-time streaming of simulation progress, agent decisions,
 * market events, and portfolio changes.
 *
 * Endpoint: ws://localhost:8080/ws/stream
 *
 * Supported channels:
 * - simulation.progress: Simulation status updates
 * - agent.decisions: LLM agent decisions
 * - market.events: Market data events
 * - portfolio.changes: Portfolio snapshot changes
 */
@ApplicationScoped
@ServerEndpoint("/ws/stream")
public class StreamEndpoint {

    private static final Logger log = LoggerFactory.getLogger(StreamEndpoint.class);

    private static final Set<String> VALID_CHANNELS = Set.of(
        "simulation.progress",
        "agent.decisions",
        "market.events",
        "portfolio.changes"
    );

    @Inject
    ChannelManager channelManager;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    com.quorumtrading.application.usecase.ReachConsensusUseCase reachConsensusUseCase;

    @Inject
    com.quorumtrading.application.port.out.PortfolioRepository portfolioRepository;

    @Inject
    com.quorumtrading.application.service.BrokerRouter brokerRouter;

    @Inject
    org.eclipse.microprofile.context.ManagedExecutor managedExecutor;

    // Track active sessions for broadcasting
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    @OnOpen
    public void onOpen(Session session) {
        log.info("WebSocket connection opened: sessionId={}", session.getId());
        sessions.put(session.getId(), session);
    }

    @OnClose
    public void onClose(Session session) {
        log.info("WebSocket connection closed: sessionId={}", session.getId());
        sessions.remove(session.getId());
        channelManager.removeSession(session.getId());
    }

    @OnError
    public void onError(Session session, Throwable throwable) {
        log.error("WebSocket error: sessionId={}, error={}", session.getId(), throwable.getMessage(), throwable);
    }

    @OnMessage
    public void onMessage(String message, Session session) {
        log.debug("Received message from sessionId={}: {}", session.getId(), message);

        try {
            JsonNode node = objectMapper.readTree(message);

            // Validate required fields
            if (!node.has("action")) {
                sendError(session, "Missing required field", "Field 'action' is required");
                return;
            }

            String action = node.get("action").asText();

            // Handle action - frontend sends simplified format with just { action, simulationId/researchId }
            switch (action) {
                case "subscribe" -> handleSubscribe(session, node);
                case "unsubscribe" -> handleUnsubscribe(session, node);
                case "subscribeResearch" -> handleSubscribeResearch(session, node);
                case "unsubscribeResearch" -> handleUnsubscribeResearch(session, node);
                case "reachConsensus" -> handleReachConsensus(session, node);
                default -> sendError(session, "Invalid action", "Action must be 'subscribe', 'unsubscribe', 'subscribeResearch', 'unsubscribeResearch', or 'reachConsensus'");
            }

        } catch (IOException e) {
            log.error("Failed to parse message from sessionId={}: {}", session.getId(), e.getMessage());
            sendError(session, "Invalid JSON", "Failed to parse message: " + e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error processing message from sessionId={}: {}", session.getId(), e.getMessage(), e);
            sendError(session, "Internal error", "An unexpected error occurred");
        }
    }

    private void handleSubscribe(Session session, JsonNode node) {
        // Frontend sends: { action: 'subscribe', simulationId: 'uuid' }
        if (!node.has("simulationId")) {
            sendError(session, "Missing required field", "Field 'simulationId' is required for subscribe");
            return;
        }

        String simulationId = node.get("simulationId").asText();

        // Validate simulationId
        if (!isValidUUID(simulationId)) {
            sendError(session, "Invalid simulationId", "simulationId must be a valid UUID");
            return;
        }

        // Create filter with simulationId
        Map<String, Object> filters = new HashMap<>();
        filters.put("simulationId", simulationId);

        // Subscribe to a generic "simulation" channel with simulationId filter
        channelManager.subscribe(session.getId(), "simulation", filters);
        log.info("Session subscribed to simulation: sessionId={}, simulationId={}",
                session.getId(), simulationId);
    }

    private void handleUnsubscribe(Session session, JsonNode node) {
        // Frontend sends: { action: 'unsubscribe', simulationId: 'uuid' }
        if (!node.has("simulationId")) {
            // If no simulationId provided, unsubscribe from all
            channelManager.removeSession(session.getId());
            log.info("Session unsubscribed from all: sessionId={}", session.getId());
            return;
        }

        // For targeted unsubscribe, just remove from simulation channel
        channelManager.unsubscribe(session.getId(), "simulation");
        log.info("Session unsubscribed from simulation: sessionId={}", session.getId());
    }

    /**
     * Handle subscribeResearch action.
     * Subscribes the session to receive progress updates for a specific research execution.
     *
     * Expected message format:
     * {
     *   "action": "subscribeResearch",
     *   "researchId": "uuid"
     * }
     */
    private void handleSubscribeResearch(Session session, JsonNode node) {
        // Frontend sends: { action: 'subscribeResearch', researchId: 'uuid' }
        if (!node.has("researchId")) {
            sendError(session, "Missing required field", "Field 'researchId' is required for subscribeResearch");
            return;
        }

        String researchId = node.get("researchId").asText();

        // Validate researchId
        if (!isValidUUID(researchId)) {
            sendError(session, "Invalid researchId", "researchId must be a valid UUID");
            return;
        }

        // Subscribe to research updates (ChannelManagerImpl logs this operation)
        channelManager.subscribeToResearch(session.getId(), researchId);
    }

    /**
     * Handle unsubscribeResearch action.
     * Unsubscribes the session from receiving progress updates for a specific research execution.
     *
     * Expected message format:
     * {
     *   "action": "unsubscribeResearch",
     *   "researchId": "uuid"
     * }
     */
    private void handleUnsubscribeResearch(Session session, JsonNode node) {
        // Frontend sends: { action: 'unsubscribeResearch', researchId: 'uuid' }
        if (!node.has("researchId")) {
            sendError(session, "Missing required field", "Field 'researchId' is required for unsubscribeResearch");
            return;
        }

        String researchId = node.get("researchId").asText();

        // Validate researchId
        if (!isValidUUID(researchId)) {
            sendError(session, "Invalid researchId", "researchId must be a valid UUID");
            return;
        }

        // Unsubscribe from research updates (ChannelManagerImpl logs this operation)
        channelManager.unsubscribeFromResearch(session.getId(), researchId);
    }

    private void sendError(Session session, String error, String detail) {
        try {
            ObjectNode errorNode = objectMapper.createObjectNode();
            errorNode.put("error", error);
            errorNode.put("detail", detail);

            String errorMessage = objectMapper.writeValueAsString(errorNode);
            // Use async remote to avoid "Cannot use basic remote from IO thread" error
            session.getAsyncRemote().sendText(errorMessage);

            log.warn("Sent error to sessionId={}: error={}, detail={}", session.getId(), error, detail);
        } catch (Exception e) {
            log.error("Failed to send error message to sessionId={}: {}", session.getId(), e.getMessage());
        }
    }

    private boolean isValidUUID(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Get all active WebSocket session IDs.
     * Used for global broadcasts (e.g., backfill progress) that should reach all connected clients.
     *
     * @return Set of all active session IDs
     */
    public Set<String> getAllSessionIds() {
        return new HashSet<>(sessions.keySet());
    }

    /**
     * Broadcasts a message to multiple WebSocket sessions.
     * Handles session errors gracefully - one bad session doesn't affect others.
     *
     * @param sessionIds Set of session IDs to broadcast to
     * @param message The message to send (will be serialized to JSON)
     */
    public void broadcast(Set<String> sessionIds, Object message) {
        if (sessionIds == null || sessionIds.isEmpty()) {
            log.debug("No sessions to broadcast to");
            return;
        }

        try {
            String json = objectMapper.writeValueAsString(message);
            String messageType = message.getClass().getSimpleName();
            log.info("Broadcasting {} to {} sessions", messageType, sessionIds.size());

            for (String sessionId : sessionIds) {
                try {
                    Session session = sessions.get(sessionId);
                    if (session != null && session.isOpen()) {
                        // Use async remote to avoid threading issues
                        session.getAsyncRemote().sendText(json);
                        log.debug("Broadcast sent to sessionId={}", sessionId);
                    } else {
                        log.warn("Session not found or closed: sessionId={}", sessionId);
                    }
                } catch (Exception e) {
                    // Log error but continue broadcasting to other sessions
                    log.error("Failed to broadcast to sessionId={}: {}", sessionId, e.getMessage());
                }
            }
        } catch (IOException e) {
            log.error("Failed to serialize broadcast message: {}", e.getMessage(), e);
        }
    }

    /**
     * Handle reachConsensus action.
     * Triggers the consensus-reaching process for a simulation.
     *
     * Expected message format:
     * {
     *   "action": "reachConsensus",
     *   "simulationId": "uuid"
     * }
     */
    private void handleReachConsensus(Session session, JsonNode node) {
        if (!node.has("simulationId")) {
            sendError(session, "Missing required field", "Field 'simulationId' is required for reachConsensus");
            return;
        }

        String simulationIdStr = node.get("simulationId").asText();

        // Validate simulationId
        if (!isValidUUID(simulationIdStr)) {
            sendError(session, "Invalid simulationId", "simulationId must be a valid UUID");
            return;
        }

        try {
            UUID simulationId = UUID.fromString(simulationIdStr);
            com.quorumtrading.model.valueobject.SimulationId simId = new com.quorumtrading.model.valueobject.SimulationId(simulationId);

            log.info("Triggering consensus for simulation: sessionId={}, simulationId={}",
                    session.getId(), simulationIdStr);

            // Execute ALL blocking operations (data fetching + consensus) asynchronously on worker executor
            // Must manually activate CDI request context since WebSocket thread doesn't have one
            java.util.concurrent.CompletableFuture.runAsync(() -> {
                // Activate request context for database access
                ManagedContext requestContext = Arc.container().requestContext();
                boolean wasActive = requestContext.isActive();
                if (!wasActive) {
                    requestContext.activate();
                }

                try {
                    // Get latest portfolio snapshot to initialize broker
                    java.util.List<com.quorumtrading.model.entity.PortfolioSnapshot> snapshots =
                        portfolioRepository.findBySimulationId(simId, null, null, 1);

                    if (snapshots.isEmpty()) {
                        log.error("No portfolio snapshot found for simulation: {}", simulationIdStr);
                        return;
                    }

                    com.quorumtrading.model.entity.PortfolioSnapshot latestSnapshot = snapshots.get(0);

                    // Initialize broker's internal portfolio state with cash from latest snapshot
                    brokerRouter.initializePortfolio(simId, latestSnapshot.getCashAvailable());
                    log.info("Initialized broker portfolio for simulation {} with cash: {}",
                        simulationIdStr, latestSnapshot.getCashAvailable());

                    // Build command with minimal design - service layer will fetch all other data
                    java.time.Instant now = java.time.Instant.now();
                    com.quorumtrading.application.usecase.command.ReachConsensusCommand command =
                        new com.quorumtrading.application.usecase.command.ReachConsensusCommand(
                            simId,
                            now
                        );

                    // Execute consensus process
                    reachConsensusUseCase.execute(command);
                    log.info("Consensus process completed for simulation: {}", simulationIdStr);
                } catch (Exception e) {
                    log.error("Failed to execute consensus for simulation {}: {}", simulationIdStr, e.getMessage(), e);

                    // Send error message to client
                    try {
                        com.quorumtrading.adapter.websocket.dto.ErrorMessage errorMsg =
                            new com.quorumtrading.adapter.websocket.dto.ErrorMessage(
                                java.time.Instant.now().toString(),
                                simulationIdStr,
                                "Failed to reach consensus: " + e.getMessage(),
                                "CONSENSUS_FAILED"
                            );

                        Set<String> sessionIds = channelManager.getSessionsBySimulationId(simulationIdStr);
                        if (!sessionIds.isEmpty()) {
                            broadcast(sessionIds, errorMsg);
                        }
                    } catch (Exception broadcastError) {
                        log.error("Failed to broadcast error message: {}", broadcastError.getMessage());
                    }
                } finally {
                    // Clean up request context if we activated it
                    if (!wasActive && requestContext.isActive()) {
                        requestContext.terminate();
                    }
                }
            }, managedExecutor);

            // Send acknowledgment to client
            ObjectNode ack = objectMapper.createObjectNode();
            ack.put("status", "consensus_triggered");
            ack.put("simulationId", simulationIdStr);
            ack.put("message", "Consensus process started. Updates will be streamed via WebSocket.");

            session.getAsyncRemote().sendText(objectMapper.writeValueAsString(ack));
            log.info("Consensus acknowledgment sent to sessionId={}", session.getId());

        } catch (IllegalArgumentException e) {
            log.error("Invalid consensus request from sessionId={}: {}", session.getId(), e.getMessage());
            sendError(session, "Invalid request", e.getMessage());
        } catch (Exception e) {
            log.error("Failed to trigger consensus from sessionId={}: {}", session.getId(), e.getMessage(), e);
            sendError(session, "Internal error", "Failed to trigger consensus: " + e.getMessage());
        }
    }
}
