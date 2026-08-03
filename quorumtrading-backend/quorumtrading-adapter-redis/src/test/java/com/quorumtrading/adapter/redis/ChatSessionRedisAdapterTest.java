package com.quorumtrading.adapter.redis;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.quorumtrading.application.chat.ChatSession;
import com.quorumtrading.model.valueobject.ChatMessage;
import com.quorumtrading.model.valueobject.UserId;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.keys.KeyCommands;
import io.quarkus.redis.datasource.value.ValueCommands;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;

/**
 * The save/read round-trip, which had no test and did not work.
 *
 * <p>Sessions were written to Redis correctly and could never be read back. Jackson serialised
 * {@code UserId} as {@code {"value":"…","system":false}} — {@code system} being a derived accessor,
 * not state — while the canonical constructor accepts only {@code value}. With
 * {@code quarkus.jackson.fail-on-unknown-properties=true} the read threw, {@code findById} turned
 * the exception into an empty {@code Optional}, and the API answered a tidy 404. Nothing surfaced
 * as an error; a chat session simply could not be reopened, ever.</p>
 *
 * <p>Redis is mocked with an in-memory map, mirroring {@code RedisOAuthStateStoreTest}. The
 * {@code ObjectMapper} is a real one on Jackson's own defaults, because the defect lives entirely
 * in the mapping — mock that and the test asserts nothing.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Chat session Redis adapter")
class ChatSessionRedisAdapterTest {

    private static final String KEY_PREFIX = "quorumtrading:chat:";

    @Mock
    RedisDataSource redisDataSource;

    @Mock
    ValueCommands<String, String> valueCommands;

    @Mock
    KeyCommands<String> keyCommands;

    private final Map<String, String> store = new HashMap<>();

    private ChatSessionRedisAdapter adapter;

    @BeforeEach
    void setUp() {
        store.clear();

        lenient().when(redisDataSource.value(String.class)).thenReturn(valueCommands);
        lenient().when(redisDataSource.key()).thenReturn(keyCommands);
        lenient().doAnswer(inv -> store.put(inv.getArgument(0), inv.getArgument(1)))
            .when(valueCommands).set(anyString(), anyString());
        lenient().when(valueCommands.get(anyString())).thenAnswer(inv -> store.get(inv.getArgument(0)));
        lenient().when(keyCommands.expire(anyString(), anyLong())).thenReturn(true);

        adapter = new ChatSessionRedisAdapter();
        adapter.redisDataSource = redisDataSource;
        // Jackson's defaults on purpose: fail-on-unknown-properties is on by default, and the
        // application turns it on explicitly too, so this is what production reads with.
        adapter.objectMapper = JsonMapper.builder().findAndAddModules().build();
        adapter.sessionTtl = Duration.ofDays(7);
        adapter.historyMinMessages = 3;
        adapter.initSessionReader();
    }

    private String keyFor(UserId userId, UUID sessionId) {
        return KEY_PREFIX + userId.value() + ":" + sessionId;
    }

    @Test
    @DisplayName("a saved session can be read back")
    void savedSessionCanBeReadBack() {
        UserId userId = UserId.generate();
        UUID sessionId = UUID.randomUUID();
        ChatSession session = new ChatSession(sessionId, userId, "some/model", "system prompt");
        session.addMessage(ChatMessage.assistant("Welcome."));

        adapter.save(session);
        Optional<ChatSession> found = adapter.findById(sessionId, userId);

        assertThat(found).isPresent();
        assertThat(found.get().getSessionId()).isEqualTo(sessionId);
        assertThat(found.get().getUserId()).isEqualTo(userId);
        assertThat(found.get().getModel()).isEqualTo("some/model");
        assertThat(found.get().getMessages()).hasSize(1);
    }

    @Test
    @DisplayName("a session carrying a field this version does not know still reads")
    void unknownFieldsDoNotMakeASessionUnreadable() {
        // Sessions live for seven days, so a deploy always finds sessions in Redis that the
        // previous version wrote. A field added since must not make them unopenable — which is the
        // general form of the UserId.system defect.
        UserId userId = UserId.generate();
        UUID sessionId = UUID.randomUUID();
        adapter.save(new ChatSession(sessionId, userId, "some/model", "system prompt"));

        String key = keyFor(userId, sessionId);
        store.put(key, store.get(key).replaceFirst("\\{", "{\"fieldFromTheFuture\":\"whatever\","));

        assertThat(adapter.findById(sessionId, userId)).isPresent();
    }

    @Test
    @DisplayName("a missing session is empty, not an error")
    void missingSessionIsEmpty() {
        assertThat(adapter.findById(UUID.randomUUID(), UserId.generate())).isEmpty();
    }

    @Test
    @DisplayName("genuinely corrupt JSON is empty, not an exception")
    void corruptSessionIsEmpty() {
        UserId userId = UserId.generate();
        UUID sessionId = UUID.randomUUID();
        store.put(keyFor(userId, sessionId), "{not json");

        assertThat(adapter.findById(sessionId, userId)).isEmpty();
    }

    @Test
    @DisplayName("a session is not readable by another user")
    void sessionIsScopedToItsOwner() {
        UserId owner = UserId.generate();
        UUID sessionId = UUID.randomUUID();
        adapter.save(new ChatSession(sessionId, owner, "some/model", "system prompt"));

        assertThat(adapter.findById(sessionId, UserId.generate())).isEmpty();
    }

    @Test
    @DisplayName("the storage key is scoped per user and session")
    void keyLayoutIsPerUserAndSession() {
        UserId userId = UserId.generate();
        UUID sessionId = UUID.randomUUID();
        adapter.save(new ChatSession(sessionId, userId, "some/model", "system prompt"));

        assertThat(store).containsOnlyKeys(keyFor(userId, sessionId));
    }
}
