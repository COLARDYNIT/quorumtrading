package com.quorumtrading.adapter.redis;

import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.value.SetArgs;
import io.quarkus.redis.datasource.value.ValueCommands;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link RedisOAuthStateStore}.
 *
 * <p>Redis is mocked with an in-memory map so the single-use ({@code GETDEL}) and TTL
 * ({@code SET … EX}) semantics can be asserted without a live Redis, mirroring the existing
 * {@code RedisFreeModelRateLimiterTest} style.</p>
 */
@ExtendWith(MockitoExtension.class)
class RedisOAuthStateStoreTest {

    @Mock
    RedisDataSource redisDataSource;

    @Mock
    ValueCommands<String, String> valueCommands;

    @InjectMocks
    RedisOAuthStateStore store;

    private static final String STATE = "opaque-state-abc123";
    private static final String VALUE = "{\"userId\":\"u-1\",\"environment\":\"SIM\"}";
    private static final Duration TTL = Duration.ofMinutes(10);

    /** Backing map that emulates Redis string storage for SET/GETDEL. */
    private final Map<String, String> backing = new HashMap<>();

    @BeforeEach
    void setUp() {
        lenient().when(redisDataSource.value(String.class)).thenReturn(valueCommands);

        // SET key value [EX ttl] -> store in the backing map.
        lenient().doAnswer(inv -> {
            backing.put(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(valueCommands).set(anyString(), anyString(), any(SetArgs.class));

        // GETDEL key -> atomic fetch-and-remove.
        lenient().when(valueCommands.getdel(anyString()))
            .thenAnswer(inv -> backing.remove(inv.getArgument(0)));
    }

    @Test
    void putThenTakeOnce_returnsValueExactlyOnce() {
        store.put(STATE, VALUE, TTL);

        Optional<String> first = store.takeOnce(STATE);

        assertThat(first).contains(VALUE);
    }

    @Test
    void takeOnce_secondCall_returnsEmpty() {
        store.put(STATE, VALUE, TTL);

        Optional<String> first = store.takeOnce(STATE);
        Optional<String> second = store.takeOnce(STATE);

        assertThat(first).contains(VALUE);
        assertThat(second).isEmpty();
    }

    @Test
    void takeOnce_unknownState_returnsEmpty() {
        assertThat(store.takeOnce("never-stored")).isEmpty();
    }

    @Test
    void takeOnce_isAtomicGetDel_notPlainGet() {
        store.put(STATE, VALUE, TTL);

        store.takeOnce(STATE);

        // Single-use must be enforced by an atomic get-and-delete, never a plain GET.
        verify(valueCommands).getdel("quorumtrading:oauth:state:" + STATE);
    }

    @Test
    void put_encodesTtlIntoSetCommand() {
        store.put(STATE, VALUE, TTL);

        ArgumentCaptor<SetArgs> argsCaptor = ArgumentCaptor.forClass(SetArgs.class);
        verify(valueCommands).set(eq("quorumtrading:oauth:state:" + STATE), eq(VALUE), argsCaptor.capture());

        // SetArgs renders to the Redis command args (e.g. ["EX", "600"]); assert the TTL is carried
        // as the EX expiry equal to the requested seconds (rendered as a String).
        var rendered = argsCaptor.getValue().toArgs();
        assertThat(rendered).contains("EX");
        assertThat(rendered).contains(String.valueOf(TTL.toSeconds()));
    }

    @Test
    void put_usesPrefixedKey() {
        store.put(STATE, VALUE, TTL);

        verify(valueCommands).set(eq("quorumtrading:oauth:state:" + STATE), eq(VALUE), any(SetArgs.class));
    }

    @Test
    void put_blankState_throws() {
        assertThatThrownBy(() -> store.put("  ", VALUE, TTL))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void put_nullValue_throws() {
        assertThatThrownBy(() -> store.put(STATE, null, TTL))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void put_nonPositiveTtl_throws() {
        assertThatThrownBy(() -> store.put(STATE, VALUE, Duration.ZERO))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.put(STATE, VALUE, Duration.ofSeconds(-1)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void takeOnce_blankState_throws() {
        assertThatThrownBy(() -> store.takeOnce(""))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
