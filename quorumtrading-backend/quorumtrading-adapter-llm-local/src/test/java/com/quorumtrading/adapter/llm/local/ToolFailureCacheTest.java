package com.quorumtrading.adapter.llm.local;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ToolFailureCacheTest {

    private ToolFailureCache cache;

    @BeforeEach
    void setup() {
        cache = new ToolFailureCache(60, 1440);
        cache.clearCache();
    }

    @Test
    void testIsToolBlacklisted_notBlacklistedInitially() {
        assertFalse(cache.isToolBlacklisted("qwen"));
    }

    @Test
    void testRecordFailure_firstFailure() {
        cache.recordFailure("qwen");

        assertTrue(cache.isToolBlacklisted("qwen"));

        Map<String, FailureRecord> status = cache.getFailureStatus();
        assertEquals(1, status.size());
        assertEquals(1, status.get("qwen").failureCount());
        assertEquals(60, status.get("qwen").currentBackoffMinutes());
    }

    @Test
    void testRecordFailure_exponentialBackoff() {
        cache.recordFailure("qwen");
        cache.recordFailure("qwen");
        cache.recordFailure("qwen");

        FailureRecord record = cache.getFailureStatus().get("qwen");
        assertEquals(3, record.failureCount());
        assertEquals(240, record.currentBackoffMinutes()); // 60 * 2^2
    }

    @Test
    void testRecordFailure_maxBackoffCap() {
        // Record 10 failures to exceed max
        for (int i = 0; i < 10; i++) {
            cache.recordFailure("qwen");
        }

        FailureRecord record = cache.getFailureStatus().get("qwen");
        assertEquals(1440, record.currentBackoffMinutes()); // Capped at max
    }

    @Test
    void testClearCache() {
        cache.recordFailure("qwen");
        cache.recordFailure("claude");

        cache.clearCache();

        assertFalse(cache.isToolBlacklisted("qwen"));
        assertFalse(cache.isToolBlacklisted("claude"));
        assertTrue(cache.getFailureStatus().isEmpty());
    }
}
