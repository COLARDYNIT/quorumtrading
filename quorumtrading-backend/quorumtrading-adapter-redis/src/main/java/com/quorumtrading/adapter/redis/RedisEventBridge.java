package com.quorumtrading.adapter.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.pubsub.PubSubCommands;
import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Redis Pub/Sub bridge for CDI events.
 * Enables cluster-wide event broadcasting for WebSocket notifications.
 *
 * <p><b>How it works:</b></p>
 * <ol>
 *   <li>Observes application-layer CDI events (same as MessageBroadcaster)</li>
 *   <li>Wraps events in an envelope with the source instance ID</li>
 *   <li>Serializes to JSON and publishes to Redis channels</li>
 *   <li>Subscribes to Redis channels on startup</li>
 *   <li>Ignores messages from this instance (same source ID)</li>
 *   <li>Deserializes and re-fires as local CDI events for remote messages</li>
 * </ol>
 *
 * <p><b>Loop prevention:</b> Two-layer protection:</p>
 * <ul>
 *   <li>Envelope source instance ID: subscriber ignores messages from itself</li>
 *   <li>Recently re-fired event dedup: events re-fired from Redis are tracked;
 *       the observer skips publishing events that match the dedup set</li>
 * </ul>
 *
 * <p><b>Channel naming:</b> {@code quorumtrading:events:{eventType}}</p>
 */
@ApplicationScoped
@Startup
public class RedisEventBridge {

    private static final Logger LOG = Logger.getLogger(RedisEventBridge.class);
    private static final String CHANNEL_PREFIX = "quorumtrading:events:";

    /**
     * Unique instance ID for loop prevention.
     */
    private final String instanceId = resolveInstanceId();

    private final ObjectMapper objectMapper;
    private final Map<String, PubSubCommands.RedisSubscriber> subscribers = new ConcurrentHashMap<>();

    /**
     * Deduplication set: hashes of events recently re-fired from Redis.
     * When the CDI observer picks up these events, it skips re-publishing them.
     * Entries are removed after being matched once (single-use dedup tokens).
     */
    private final Set<Integer> recentlyRefiredHashes = Collections.newSetFromMap(new ConcurrentHashMap<>());

    @Inject
    RedisDataSource redisDataSource;

    @Inject
    Event<com.quorumtrading.application.event.LLMDecisionCreatedEvent> llmDecisionEvent;

    @Inject
    Event<com.quorumtrading.application.event.TradeExecutionEvent> tradeExecutionEvent;

    @Inject
    Event<com.quorumtrading.application.event.PortfolioChangeEvent> portfolioChangeEvent;

    @Inject
    Event<com.quorumtrading.application.event.ConsensusStartedEvent> consensusStartedEvent;

    @Inject
    Event<com.quorumtrading.application.event.SimulationCompletedEvent> simulationCompletedEvent;

    @Inject
    Event<com.quorumtrading.application.event.IndicatorUpdateEvent> indicatorUpdateEvent;

    public RedisEventBridge() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.objectMapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
    }

    @PostConstruct
    void init() {
        LOG.infof("Initializing Redis event bridge (instance: %s)", instanceId);
        subscribeToChannel("LLMDecisionCreated", com.quorumtrading.application.event.LLMDecisionCreatedEvent.class, llmDecisionEvent);
        subscribeToChannel("TradeExecution", com.quorumtrading.application.event.TradeExecutionEvent.class, tradeExecutionEvent);
        subscribeToChannel("PortfolioChange", com.quorumtrading.application.event.PortfolioChangeEvent.class, portfolioChangeEvent);
        subscribeToChannel("ConsensusStarted", com.quorumtrading.application.event.ConsensusStartedEvent.class, consensusStartedEvent);
        subscribeToChannel("SimulationCompleted", com.quorumtrading.application.event.SimulationCompletedEvent.class, simulationCompletedEvent);
        subscribeToChannel("IndicatorUpdate", com.quorumtrading.application.event.IndicatorUpdateEvent.class, indicatorUpdateEvent);
        LOG.infof("Redis event bridge subscribed to %d channels", subscribers.size());
    }

    @PreDestroy
    void cleanup() {
        LOG.info("Shutting down Redis event bridge");
        subscribers.values().forEach(PubSubCommands.RedisSubscriber::unsubscribe);
        subscribers.clear();
    }

    // ========== Event Observers (Local → Redis) ==========

    void onLLMDecisionCreated(@ObservesAsync com.quorumtrading.application.event.LLMDecisionCreatedEvent event) {
        publishToRedis("LLMDecisionCreated", event);
    }

    void onTradeExecution(@ObservesAsync com.quorumtrading.application.event.TradeExecutionEvent event) {
        publishToRedis("TradeExecution", event);
    }

    void onPortfolioChange(@ObservesAsync com.quorumtrading.application.event.PortfolioChangeEvent event) {
        publishToRedis("PortfolioChange", event);
    }

    void onConsensusStarted(@ObservesAsync com.quorumtrading.application.event.ConsensusStartedEvent event) {
        publishToRedis("ConsensusStarted", event);
    }

    void onSimulationCompleted(@ObservesAsync com.quorumtrading.application.event.SimulationCompletedEvent event) {
        publishToRedis("SimulationCompleted", event);
    }

    void onIndicatorUpdate(@ObservesAsync com.quorumtrading.application.event.IndicatorUpdateEvent event) {
        publishToRedis("IndicatorUpdate", event);
    }

    // ========== Redis Publishing ==========

    private <T> void publishToRedis(String eventType, T event) {
        try {
            String payload = objectMapper.writeValueAsString(event);

            // Check dedup: if this event was re-fired from Redis, skip publishing
            int hash = payload.hashCode();
            if (recentlyRefiredHashes.remove(hash)) {
                LOG.debugf("Skipped re-publishing %s event (received from Redis)", eventType);
                return;
            }

            RedisEventEnvelope envelope = new RedisEventEnvelope(instanceId, eventType, payload);
            String json = objectMapper.writeValueAsString(envelope);
            String channel = CHANNEL_PREFIX + eventType;

            PubSubCommands<String> pub = redisDataSource.pubsub(String.class);
            pub.publish(channel, json);

            LOG.debugf("Published %s event to Redis channel %s", eventType, channel);
        } catch (JsonProcessingException e) {
            LOG.warnf(e, "Failed to serialize %s event for Redis publishing", eventType);
        } catch (Exception e) {
            LOG.warnf(e, "Failed to publish %s event to Redis", eventType);
        }
    }

    // ========== Redis Subscription ==========

    private <T> void subscribeToChannel(String eventType, Class<T> eventClass, Event<T> cdiEvent) {
        String channel = CHANNEL_PREFIX + eventType;

        try {
            PubSubCommands<String> pub = redisDataSource.pubsub(String.class);
            PubSubCommands.RedisSubscriber subscriber = pub.subscribe(channel, new Consumer<String>() {
                @Override
                public void accept(String json) {
                    try {
                        RedisEventEnvelope envelope = objectMapper.readValue(json, RedisEventEnvelope.class);

                        // Ignore events from this instance
                        if (instanceId.equals(envelope.sourceInstanceId())) {
                            return;
                        }

                        T event = objectMapper.readValue(envelope.payload(), eventClass);

                        // Register dedup token before re-firing
                        recentlyRefiredHashes.add(envelope.payload().hashCode());

                        cdiEvent.fireAsync(event);
                        LOG.debugf("Re-fired %s event from instance %s", eventType, envelope.sourceInstanceId());
                    } catch (JsonProcessingException e) {
                        LOG.warnf(e, "Failed to deserialize %s event from Redis", eventType);
                    } catch (Exception e) {
                        LOG.warnf(e, "Failed to re-fire %s event from Redis", eventType);
                    }
                }
            });

            subscribers.put(channel, subscriber);
            LOG.debugf("Subscribed to Redis channel: %s", channel);
        } catch (Exception e) {
            LOG.errorf(e, "Failed to subscribe to Redis channel: %s", channel);
        }
    }

    // ========== Helpers ==========

    private static String resolveInstanceId() {
        String ecsTaskId = System.getenv("ECS_TASK_ID");
        if (ecsTaskId != null && !ecsTaskId.isBlank()) {
            return ecsTaskId;
        }
        try {
            String hostname = java.net.InetAddress.getLocalHost().getHostName();
            long pid = ProcessHandle.current().pid();
            return hostname + "-" + pid;
        } catch (Exception e) {
            return UUID.randomUUID().toString();
        }
    }

    /**
     * Envelope wrapping an event with source instance metadata.
     */
    record RedisEventEnvelope(String sourceInstanceId, String eventType, String payload) {}
}
