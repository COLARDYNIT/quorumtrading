package com.quorumtrading.adapter.websocket;

import jakarta.enterprise.context.ApplicationScoped;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of ChannelManager for MVP.
 * Manages WebSocket channel subscriptions with thread-safe operations.
 *
 * This implementation uses ConcurrentHashMap for thread-safe subscription tracking
 * and supports filter-based message routing to subscribers.
 */
@ApplicationScoped
public class ChannelManagerImpl implements ChannelManager {

    private static final Logger log = LoggerFactory.getLogger(ChannelManagerImpl.class);

    // Thread-safe map: sessionId -> Set of subscriptions
    private final ConcurrentHashMap<String, Set<Subscription>> subscriptions = new ConcurrentHashMap<>();

    @Override
    public void subscribe(String sessionId, String channel, Map<String, Object> filters) {
        if (sessionId == null || channel == null) {
            log.warn("Attempted to subscribe with null sessionId or channel");
            return;
        }

        // Create immutable subscription
        Subscription subscription = new Subscription(channel,
            filters == null ? null : new HashMap<>(filters));

        // Add subscription to session's set (thread-safe)
        subscriptions.computeIfAbsent(sessionId, k -> ConcurrentHashMap.newKeySet())
                     .add(subscription);

        log.info("Session subscribed: sessionId={}, channel={}, filters={}",
                sessionId, channel, filters);
    }

    @Override
    public void unsubscribe(String sessionId, String channel) {
        if (sessionId == null || channel == null) {
            log.warn("Attempted to unsubscribe with null sessionId or channel");
            return;
        }

        Set<Subscription> sessionSubs = subscriptions.get(sessionId);
        if (sessionSubs == null) {
            log.debug("No subscriptions found for sessionId={}", sessionId);
            return;
        }

        // Remove all subscriptions for this channel (may be multiple with different filters)
        boolean removed = sessionSubs.removeIf(sub -> channel.equals(sub.getChannel()));

        // Clean up empty subscription sets
        if (sessionSubs.isEmpty()) {
            subscriptions.remove(sessionId);
        }

        if (removed) {
            log.info("Session unsubscribed: sessionId={}, channel={}", sessionId, channel);
        } else {
            log.debug("No subscription found to remove: sessionId={}, channel={}", sessionId, channel);
        }
    }

    @Override
    public void removeSession(String sessionId) {
        if (sessionId == null) {
            log.warn("Attempted to remove session with null sessionId");
            return;
        }

        Set<Subscription> removed = subscriptions.remove(sessionId);

        if (removed != null && !removed.isEmpty()) {
            log.info("Session removed: sessionId={}, subscriptions={}", sessionId, removed.size());
        } else {
            log.debug("No subscriptions found for removed sessionId={}", sessionId);
        }
    }

    @Override
    public Set<Subscription> getSubscriptions(String sessionId) {
        if (sessionId == null) {
            return Collections.emptySet();
        }

        Set<Subscription> sessionSubs = subscriptions.get(sessionId);
        if (sessionSubs == null) {
            return Collections.emptySet();
        }

        // Return unmodifiable copy to prevent external modification
        return Collections.unmodifiableSet(new HashSet<>(sessionSubs));
    }

    @Override
    public Set<String> getSessionsForChannel(String channel, Map<String, Object> eventData) {
        if (channel == null) {
            return Collections.emptySet();
        }

        Set<String> matchingSessions = ConcurrentHashMap.newKeySet();

        // Iterate through all sessions and their subscriptions
        subscriptions.forEach((sessionId, sessionSubs) -> {
            for (Subscription sub : sessionSubs) {
                // Check if subscription is for this channel
                if (channel.equals(sub.getChannel())) {
                    // Check if filters match
                    if (filtersMatch(sub.getFilters(), eventData)) {
                        matchingSessions.add(sessionId);
                        break; // No need to check other subscriptions for this session
                    }
                }
            }
        });

        return matchingSessions;
    }

    @Override
    public Set<String> getSessionsBySimulationId(String simulationId) {
        if (simulationId == null) {
            return Collections.emptySet();
        }

        Set<String> matchingSessions = ConcurrentHashMap.newKeySet();

        // Iterate through all sessions and their subscriptions
        subscriptions.forEach((sessionId, sessionSubs) -> {
            for (Subscription sub : sessionSubs) {
                // Check if subscription has simulationId filter matching this simulation
                if (sub.getFilters() != null &&
                    simulationId.equals(sub.getFilters().get("simulationId"))) {
                    matchingSessions.add(sessionId);
                    break; // No need to check other subscriptions for this session
                }
            }
        });

        return matchingSessions;
    }

    @Override
    public Set<String> getAllActiveSessions() {
        return new HashSet<>(subscriptions.keySet());
    }

    @Override
    public void subscribeToResearch(String sessionId, String researchId) {
        if (sessionId == null || researchId == null) {
            log.warn("Attempted to subscribe to research with null sessionId or researchId");
            return;
        }

        // Create filter with researchId
        Map<String, Object> filters = new HashMap<>();
        filters.put("researchId", researchId);

        // Create subscription for the "research" channel
        Subscription subscription = new Subscription("research", filters);

        // Add subscription to session's set (thread-safe)
        subscriptions.computeIfAbsent(sessionId, k -> ConcurrentHashMap.newKeySet())
                     .add(subscription);

        log.info("Session subscribed to research: sessionId={}, researchId={}",
                sessionId, researchId);
    }

    @Override
    public void unsubscribeFromResearch(String sessionId, String researchId) {
        if (sessionId == null || researchId == null) {
            log.warn("Attempted to unsubscribe from research with null sessionId or researchId");
            return;
        }

        Set<Subscription> sessionSubs = subscriptions.get(sessionId);
        if (sessionSubs == null) {
            log.debug("No subscriptions found for sessionId={}", sessionId);
            return;
        }

        // Remove subscription matching the research channel and researchId filter
        boolean removed = sessionSubs.removeIf(sub ->
            "research".equals(sub.getChannel()) &&
            sub.getFilters() != null &&
            researchId.equals(sub.getFilters().get("researchId")));

        // Clean up empty subscription sets
        if (sessionSubs.isEmpty()) {
            subscriptions.remove(sessionId);
        }

        if (removed) {
            log.info("Session unsubscribed from research: sessionId={}, researchId={}",
                    sessionId, researchId);
        } else {
            log.debug("No research subscription found to remove: sessionId={}, researchId={}",
                    sessionId, researchId);
        }
    }

    @Override
    public Set<String> getSessionsByResearchId(String researchId) {
        if (researchId == null) {
            return Collections.emptySet();
        }

        Set<String> matchingSessions = ConcurrentHashMap.newKeySet();

        // Iterate through all sessions and their subscriptions
        subscriptions.forEach((sessionId, sessionSubs) -> {
            for (Subscription sub : sessionSubs) {
                // Check if subscription is for the research channel and matches the researchId
                if ("research".equals(sub.getChannel()) &&
                    sub.getFilters() != null &&
                    researchId.equals(sub.getFilters().get("researchId"))) {
                    matchingSessions.add(sessionId);
                    break; // No need to check other subscriptions for this session
                }
            }
        });

        return matchingSessions;
    }

    /**
     * Check if subscription filters match the event data.
     *
     * Matching rules:
     * - If subscription has NO filters (null or empty) -> always match
     * - If subscription has filters -> ALL filter key-value pairs must match in eventData
     *
     * @param subscriptionFilters The filters defined in the subscription
     * @param eventData The event data to match against
     * @return true if filters match, false otherwise
     */
    private boolean filtersMatch(Map<String, Object> subscriptionFilters, Map<String, Object> eventData) {
        // No filters -> always match
        if (subscriptionFilters == null || subscriptionFilters.isEmpty()) {
            return true;
        }

        // Event data is null -> cannot match filters
        if (eventData == null) {
            return false;
        }

        // All subscription filters must match
        for (Map.Entry<String, Object> filterEntry : subscriptionFilters.entrySet()) {
            String filterKey = filterEntry.getKey();
            Object filterValue = filterEntry.getValue();

            // Event data must contain the filter key
            if (!eventData.containsKey(filterKey)) {
                return false;
            }

            // Event data value must match filter value
            Object eventValue = eventData.get(filterKey);
            if (!Objects.equals(filterValue, eventValue)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Represents a channel subscription with optional filters.
     * Immutable and thread-safe.
     */
    public static class Subscription implements ChannelManager.Subscription {
        private final String channel;
        private final Map<String, Object> filters;

        public Subscription(String channel, Map<String, Object> filters) {
            this.channel = channel;
            // Store immutable copy of filters
            this.filters = filters == null ? null : Collections.unmodifiableMap(new HashMap<>(filters));
        }

        @Override
        public String getChannel() {
            return channel;
        }

        @Override
        public Map<String, Object> getFilters() {
            return filters;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Subscription that = (Subscription) o;
            return Objects.equals(channel, that.channel) &&
                   Objects.equals(filters, that.filters);
        }

        @Override
        public int hashCode() {
            return Objects.hash(channel, filters);
        }

        @Override
        public String toString() {
            return "Subscription{" +
                   "channel='" + channel + '\'' +
                   ", filters=" + filters +
                   '}';
        }
    }
}
