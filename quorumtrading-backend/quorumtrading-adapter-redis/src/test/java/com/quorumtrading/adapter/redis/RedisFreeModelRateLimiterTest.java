package com.quorumtrading.adapter.redis;

import com.quorumtrading.model.valueobject.UserId;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.keys.KeyCommands;
import io.quarkus.redis.datasource.value.ValueCommands;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisFreeModelRateLimiterTest {

    @Mock
    RedisDataSource redisDataSource;

    @Mock
    ValueCommands<String, String> valueCommands;

    @Mock
    KeyCommands<String> keyCommands;

    @InjectMocks
    RedisFreeModelRateLimiter rateLimiter;

    private static final UserId USER_ID = new UserId(UUID.randomUUID());
    private static final int DAILY_LIMIT = 10;

    @BeforeEach
    void setUp() {
        // ValueCommands is used by both isAllowed and recordUsage
        when(redisDataSource.value(String.class)).thenReturn(valueCommands);
    }

    @Test
    void isAllowed_redisDown_returnsFalse() {
        when(redisDataSource.value(String.class)).thenThrow(new RuntimeException("Redis connection refused"));

        boolean result = rateLimiter.isAllowed(USER_ID, DAILY_LIMIT);

        assertThat(result).isFalse();
    }

    @Test
    void isAllowed_underLimit_returnsTrue() {
        when(valueCommands.get(anyString())).thenReturn("5");

        boolean result = rateLimiter.isAllowed(USER_ID, DAILY_LIMIT);

        assertThat(result).isTrue();
    }

    @Test
    void isAllowed_noExistingKey_returnsTrue() {
        when(valueCommands.get(anyString())).thenReturn(null);

        boolean result = rateLimiter.isAllowed(USER_ID, DAILY_LIMIT);

        assertThat(result).isTrue();
    }

    @Test
    void isAllowed_atLimit_returnsFalse() {
        when(valueCommands.get(anyString())).thenReturn("10");

        boolean result = rateLimiter.isAllowed(USER_ID, DAILY_LIMIT);

        assertThat(result).isFalse();
    }

    @Test
    void isAllowed_overLimit_returnsFalse() {
        when(valueCommands.get(anyString())).thenReturn("15");

        boolean result = rateLimiter.isAllowed(USER_ID, DAILY_LIMIT);

        assertThat(result).isFalse();
    }

    @Test
    void recordUsage_incrementsCounter() {
        when(valueCommands.incr(anyString())).thenReturn(5L);

        rateLimiter.recordUsage(USER_ID);

        verify(valueCommands).incr(anyString());
    }

    @Test
    void recordUsage_setsTtlOnFirstCallOfDay() {
        when(valueCommands.incr(anyString())).thenReturn(1L);
        when(redisDataSource.key()).thenReturn(keyCommands);

        rateLimiter.recordUsage(USER_ID);

        verify(keyCommands).expire(anyString(), eq(25 * 3600L));
    }

    @Test
    void recordUsage_doesNotSetTtlOnSubsequentCalls() {
        when(valueCommands.incr(anyString())).thenReturn(2L);

        rateLimiter.recordUsage(USER_ID);

        // key() should never be called when count > 1
        verify(redisDataSource, org.mockito.Mockito.never()).key();
    }

    @Test
    void recordUsage_swallowsRedisErrors() {
        when(redisDataSource.value(String.class)).thenThrow(new RuntimeException("Redis connection refused"));

        // Should not throw
        rateLimiter.recordUsage(USER_ID);
    }
}
