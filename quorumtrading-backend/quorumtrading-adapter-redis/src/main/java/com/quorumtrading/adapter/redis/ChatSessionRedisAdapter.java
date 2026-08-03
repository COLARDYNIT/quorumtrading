package com.quorumtrading.adapter.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.quorumtrading.application.chat.ChatSession;
import com.quorumtrading.application.chat.ChatSessionSummary;
import com.quorumtrading.application.port.out.ChatSessionPort;
import com.quorumtrading.model.valueobject.ChatMessage;
import com.quorumtrading.model.valueobject.LLMUsage;
import com.quorumtrading.model.valueobject.ToolCall;
import com.quorumtrading.model.valueobject.UserId;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.keys.KeyCommands;
import io.quarkus.redis.datasource.sortedset.SortedSetCommands;
import io.quarkus.redis.datasource.value.ValueCommands;
import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.util.*;

/**
 * Redis-backed implementation of ChatSessionPort.
 * Sessions are stored as JSON with configurable TTL.
 * A sorted set index per user tracks session IDs ordered by last activity.
 */
@ApplicationScoped
@RegisterForReflection(targets = {
    ChatSession.class,
    ChatMessage.class,
    ToolCall.class,
    LLMUsage.class,
    UserId.class,
})
public class ChatSessionRedisAdapter implements ChatSessionPort {

    private static final Logger LOG = Logger.getLogger(ChatSessionRedisAdapter.class);
    private static final String KEY_PREFIX = "quorumtrading:chat:";
    private static final String INDEX_PREFIX = "quorumtrading:chat:index:";

    @Inject
    RedisDataSource redisDataSource;

    @Inject
    ObjectMapper objectMapper;

    /**
     * Reader for sessions coming back out of Redis. Deliberately more permissive than the
     * application-wide mapper, which runs with {@code fail-on-unknown-properties=true} so that a
     * malformed API request is rejected rather than silently half-applied.
     *
     * <p>That strictness is right for input from the outside world and wrong for our own cache.
     * The domain value objects written here carry no Jackson annotations — by design, see the DDD
     * rules in CLAUDE.md — so Jackson serialises their derived accessors alongside their state and
     * then cannot map them back. {@code UserId} is the live example: it gained an
     * {@code isSystem()} accessor, every session written since serialised as
     * {@code {"value":"…","system":false}}, and every read of one threw
     * "Unrecognized field \"system\"". {@link #findById} caught it and returned empty, so the API
     * answered a clean 404 and nothing looked broken — a chat session simply could never be
     * reopened. A native smoke test against the packaged artifact is what finally surfaced it.
     *
     * <p>Being lenient here also makes the seven-day TTL survivable across a deploy: sessions
     * written by the previous version stay readable when a field is added.
     */
    private ObjectReader sessionReader;

    @PostConstruct
    void initSessionReader() {
        sessionReader = objectMapper
            .readerFor(ChatSession.class)
            .without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    @ConfigProperty(name = "quorumtrading.chat.session-ttl", defaultValue = "P7D")
    Duration sessionTtl;

    @ConfigProperty(name = "quorumtrading.chat.history-min-messages", defaultValue = "3")
    int historyMinMessages;

    @Override
    public void save(ChatSession session) {
        try {
            String key = buildKey(session.getUserId(), session.getSessionId());
            String json = objectMapper.writeValueAsString(session);

            ValueCommands<String, String> commands = redisDataSource.value(String.class);
            commands.set(key, json);
            redisDataSource.key().expire(key, sessionTtl.toSeconds());

            // Only add to the history index once the user has sent enough messages.
            // This prevents empty "New conversation" entries from cluttering the sidebar
            // when the AI sends a greeting but the user never engages.
            if (session.getUserMessageCount() >= historyMinMessages) {
                String indexKey = buildIndexKey(session.getUserId());
                SortedSetCommands<String, String> zset = redisDataSource.sortedSet(String.class);
                zset.zadd(indexKey, session.getLastActivityAt().toEpochMilli(), session.getSessionId().toString());
                redisDataSource.key().expire(indexKey, sessionTtl.toSeconds());
            }

            LOG.debugf("Saved chat session: key=%s, userMessages=%d, indexed=%b, ttl=%s",
                key, session.getUserMessageCount(), session.getUserMessageCount() >= historyMinMessages, sessionTtl);
        } catch (JsonProcessingException e) {
            LOG.errorf("Failed to serialize chat session: sessionId=%s, error=%s",
                session.getSessionId(), e.getMessage());
            throw new RuntimeException("Failed to save chat session", e);
        } catch (Exception e) {
            LOG.errorf("Failed to save chat session to Redis: sessionId=%s, error=%s",
                session.getSessionId(), e.getMessage());
            throw new RuntimeException("Failed to save chat session to Redis", e);
        }
    }

    @Override
    public Optional<ChatSession> findById(UUID sessionId, UserId userId) {
        try {
            String key = buildKey(userId, sessionId);
            ValueCommands<String, String> commands = redisDataSource.value(String.class);
            String json = commands.get(key);

            if (json == null) {
                return Optional.empty();
            }

            ChatSession session = sessionReader.readValue(json);
            return Optional.of(session);
        } catch (JsonProcessingException e) {
            LOG.errorf("Failed to deserialize chat session: sessionId=%s, error=%s",
                sessionId, e.getMessage());
            return Optional.empty();
        } catch (Exception e) {
            LOG.errorf("Failed to read chat session from Redis: sessionId=%s, error=%s",
                sessionId, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public List<ChatSessionSummary> findAllByUserId(UserId userId) {
        try {
            String indexKey = buildIndexKey(userId);
            SortedSetCommands<String, String> zset = redisDataSource.sortedSet(String.class);

            // Get all session IDs ordered by score descending (most recent first)
            List<String> sessionIds = zset.zrange(indexKey, 0, -1);
            Collections.reverse(sessionIds); // zrange returns ascending, we want descending

            List<ChatSessionSummary> summaries = new ArrayList<>();
            ValueCommands<String, String> valueCommands = redisDataSource.value(String.class);
            List<String> staleIds = new ArrayList<>();

            for (String sessionIdStr : sessionIds) {
                String key = buildKey(userId, UUID.fromString(sessionIdStr));
                String json = valueCommands.get(key);

                if (json == null) {
                    // Session expired but index entry remains — mark for cleanup
                    staleIds.add(sessionIdStr);
                    continue;
                }

                try {
                    ChatSession session = sessionReader.readValue(json);
                    summaries.add(new ChatSessionSummary(
                        session.getSessionId(),
                        session.getTitle(),
                        session.getModel(),
                        session.getCreatedAt(),
                        session.getLastActivityAt()
                    ));
                } catch (JsonProcessingException e) {
                    LOG.warnf("Failed to deserialize session for listing: sessionId=%s", sessionIdStr);
                    staleIds.add(sessionIdStr);
                }
            }

            // Clean up stale index entries
            if (!staleIds.isEmpty()) {
                for (String staleId : staleIds) {
                    zset.zrem(indexKey, staleId);
                }
                LOG.debugf("Cleaned %d stale index entries for user %s", staleIds.size(), userId.value());
            }

            return summaries;
        } catch (Exception e) {
            LOG.errorf("Failed to list chat sessions from Redis: userId=%s, error=%s",
                userId.value(), e.getMessage());
            return List.of();
        }
    }

    @Override
    public void delete(UUID sessionId) {
        // Without userId we use a pattern match to find the key
        try {
            KeyCommands<String> keyCommands = redisDataSource.key();
            var keys = keyCommands.keys(KEY_PREFIX + "*:" + sessionId);
            for (String key : keys) {
                keyCommands.unlink(key);
                LOG.debugf("Deleted chat session: key=%s", key);
            }
        } catch (Exception e) {
            LOG.warnf("Failed to delete chat session: sessionId=%s, error=%s",
                sessionId, e.getMessage());
        }
    }

    @Override
    public void delete(UUID sessionId, UserId userId) {
        try {
            String key = buildKey(userId, sessionId);
            redisDataSource.key().unlink(key);

            // Remove from sorted set index
            String indexKey = buildIndexKey(userId);
            SortedSetCommands<String, String> zset = redisDataSource.sortedSet(String.class);
            zset.zrem(indexKey, sessionId.toString());

            LOG.debugf("Deleted chat session: key=%s", key);
        } catch (Exception e) {
            LOG.warnf("Failed to delete chat session: sessionId=%s, userId=%s, error=%s",
                sessionId, userId.value(), e.getMessage());
        }
    }

    private String buildKey(UserId userId, UUID sessionId) {
        return KEY_PREFIX + userId.value() + ":" + sessionId;
    }

    private String buildIndexKey(UserId userId) {
        return INDEX_PREFIX + userId.value();
    }
}
