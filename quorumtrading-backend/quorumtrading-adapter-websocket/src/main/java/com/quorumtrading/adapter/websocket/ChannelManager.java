package com.quorumtrading.adapter.websocket;

import java.util.Map;
import java.util.Set;

/**
 * Manages WebSocket channel subscriptions and message broadcasting.
 * This interface is implemented with in-memory subscription tracking for MVP.
 */
public interface ChannelManager {

    /**
     * Subscribe a session to a specific channel with optional filters.
     *
     * @param sessionId The WebSocket session identifier
     * @param channel The channel name to subscribe to
     * @param filters Optional filters for the subscription (e.g., simulationId)
     */
    void subscribe(String sessionId, String channel, Map<String, Object> filters);

    /**
     * Unsubscribe a session from a specific channel.
     *
     * @param sessionId The WebSocket session identifier
     * @param channel The channel name to unsubscribe from
     */
    void unsubscribe(String sessionId, String channel);

    /**
     * Remove all subscriptions for a session (called when connection closes).
     *
     * @param sessionId The WebSocket session identifier
     */
    void removeSession(String sessionId);

    /**
     * Get all subscriptions for a session.
     *
     * @param sessionId The WebSocket session identifier
     * @return Set of subscriptions for the session, empty set if none
     */
    Set<? extends Subscription> getSubscriptions(String sessionId);

    /**
     * Get all session IDs subscribed to a channel that match the event data filters.
     *
     * @param channel The channel name
     * @param eventData The event data to match against subscription filters
     * @return Set of session IDs that should receive the event, empty set if none
     */
    Set<String> getSessionsForChannel(String channel, Map<String, Object> eventData);

    /**
     * Get all session IDs subscribed to a specific simulation ID.
     *
     * @param simulationId The simulation ID to match
     * @return Set of session IDs subscribed to the simulation, empty set if none
     */
    Set<String> getSessionsBySimulationId(String simulationId);

    /**
     * Get all active session IDs.
     *
     * @return Set of all active session IDs, empty set if none
     */
    Set<String> getAllActiveSessions();

    /**
     * Subscribe a session to a specific research ID for progress updates.
     *
     * @param sessionId  The WebSocket session identifier
     * @param researchId The research ID to subscribe to
     */
    void subscribeToResearch(String sessionId, String researchId);

    /**
     * Unsubscribe a session from a specific research ID.
     *
     * @param sessionId  The WebSocket session identifier
     * @param researchId The research ID to unsubscribe from
     */
    void unsubscribeFromResearch(String sessionId, String researchId);

    /**
     * Get all session IDs subscribed to a specific research ID.
     *
     * @param researchId The research ID to match
     * @return Set of session IDs subscribed to the research, empty set if none
     */
    Set<String> getSessionsByResearchId(String researchId);

    /**
     * Represents a channel subscription with optional filters.
     */
    interface Subscription {
        String getChannel();
        Map<String, Object> getFilters();
    }
}
