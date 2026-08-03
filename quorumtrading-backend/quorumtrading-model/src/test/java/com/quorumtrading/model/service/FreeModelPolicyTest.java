package com.quorumtrading.model.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("FreeModelPolicy Domain Service Tests")
class FreeModelPolicyTest {

    @Test
    @DisplayName("isFreeForUser() should return true for configured free models")
    void isFreeForUserShouldReturnTrueForConfiguredModels() {
        FreeModelPolicy policy = new FreeModelPolicy(
            List.of("qwen/qwen3-coder:free", "meta-llama/llama-3.3-8b-instruct:free"),
            50
        );

        assertTrue(policy.isFreeForUser("qwen/qwen3-coder:free"));
        assertTrue(policy.isFreeForUser("meta-llama/llama-3.3-8b-instruct:free"));
    }

    @Test
    @DisplayName("isFreeForUser() should return false for non-free models")
    void isFreeForUserShouldReturnFalseForNonFreeModels() {
        FreeModelPolicy policy = new FreeModelPolicy(
            List.of("qwen/qwen3-coder:free"),
            50
        );

        assertFalse(policy.isFreeForUser("anthropic/claude-sonnet-4"));
        assertFalse(policy.isFreeForUser("openai/gpt-4o"));
    }

    @Test
    @DisplayName("isFreeForUser() should return false when no free models configured")
    void isFreeForUserShouldReturnFalseWhenEmpty() {
        FreeModelPolicy policy = new FreeModelPolicy(List.of(), 50);

        assertFalse(policy.isFreeForUser("qwen/qwen3-coder:free"));
    }

    @Test
    @DisplayName("getDailyLimit() should return configured limit")
    void getDailyLimitShouldReturnConfiguredValue() {
        FreeModelPolicy policy = new FreeModelPolicy(List.of(), 75);

        assertEquals(75, policy.getDailyLimit());
    }

    @Test
    @DisplayName("getFreeModelIds() should return unmodifiable set")
    void getFreeModelIdsShouldReturnUnmodifiableSet() {
        FreeModelPolicy policy = new FreeModelPolicy(
            List.of("model-a", "model-b"),
            50
        );

        assertEquals(2, policy.getFreeModelIds().size());
        assertThrows(UnsupportedOperationException.class,
            () -> policy.getFreeModelIds().add("model-c"));
    }
}
