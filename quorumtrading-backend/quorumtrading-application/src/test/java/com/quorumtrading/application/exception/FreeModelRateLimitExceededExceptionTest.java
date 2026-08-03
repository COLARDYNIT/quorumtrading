package com.quorumtrading.application.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("FreeModelRateLimitExceededException Tests")
class FreeModelRateLimitExceededExceptionTest {

    @Test
    @DisplayName("should contain daily limit, model ID, and descriptive message")
    void shouldContainLimitAndModelId() {
        FreeModelRateLimitExceededException ex =
            new FreeModelRateLimitExceededException("qwen/qwen3-coder:free", 50);

        assertEquals(50, ex.getDailyLimit());
        assertEquals("qwen/qwen3-coder:free", ex.getModelId());
        assertTrue(ex.getMessage().contains("50"));
        assertTrue(ex.getMessage().contains("qwen/qwen3-coder:free"));
    }

    @Test
    @DisplayName("should be a RuntimeException")
    void shouldBeRuntimeException() {
        FreeModelRateLimitExceededException ex =
            new FreeModelRateLimitExceededException("model-a", 25);

        assertInstanceOf(RuntimeException.class, ex);
    }
}
