package com.quorumtrading.application.exception;

import com.quorumtrading.model.valueobject.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("BudgetDepletedException Tests")
class BudgetDepletedExceptionTest {

    @Test
    @DisplayName("should contain user ID and descriptive message")
    void shouldContainUserIdAndMessage() {
        UserId userId = new UserId(UUID.randomUUID());

        BudgetDepletedException ex = new BudgetDepletedException(userId);

        assertEquals(userId, ex.getUserId());
        assertTrue(ex.getMessage().contains(userId.toString()));
        assertTrue(ex.getMessage().contains("budget depleted"));
    }

    @Test
    @DisplayName("should be a RuntimeException")
    void shouldBeRuntimeException() {
        UserId userId = new UserId(UUID.randomUUID());

        BudgetDepletedException ex = new BudgetDepletedException(userId);

        assertInstanceOf(RuntimeException.class, ex);
    }
}
