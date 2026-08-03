package com.quorumtrading.adapter.llm.openrouter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the rule that decides whether a local server's reported prompt-token count is a real
 * measurement or a placeholder it never computed.
 *
 * <p>Observed in production data: {@code prompt_tokens = 2} recorded against a 4,543-character
 * prompt, while {@code completion_tokens = 369} was genuine. Storing that 2 makes a simulation
 * look almost free and reads as a measurement, which is worse than storing nothing.</p>
 */
class LocalHttpPromptTokenPlausibilityTest {

    @Test
    @DisplayName("rejects the counts local servers actually reported")
    void rejectsObservedPlaceholderCounts() {
        // Real rows from consensus_decisions: single-digit tokens for multi-thousand char prompts.
        assertThat(LocalHttpLLMAdapter.isPlausiblePromptTokenCount(2, 4543)).isFalse();
        assertThat(LocalHttpLLMAdapter.isPlausiblePromptTokenCount(4, 6076)).isFalse();
        assertThat(LocalHttpLLMAdapter.isPlausiblePromptTokenCount(10, 5119)).isFalse();
        assertThat(LocalHttpLLMAdapter.isPlausiblePromptTokenCount(30, 10924)).isFalse();
    }

    @Test
    @DisplayName("accepts a genuine count at roughly one token per four characters")
    void acceptsGenuineCounts() {
        assertThat(LocalHttpLLMAdapter.isPlausiblePromptTokenCount(1136, 4543)).isTrue();
        assertThat(LocalHttpLLMAdapter.isPlausiblePromptTokenCount(2731, 10924)).isTrue();
    }

    @Test
    @DisplayName("accepts unusual tokenizers well below the typical ratio")
    void acceptsUnusualButRealTokenizers() {
        // The bound is deliberately loose so a real count from a coarse tokenizer survives:
        // one token per 10 characters is still accepted.
        assertThat(LocalHttpLLMAdapter.isPlausiblePromptTokenCount(454, 4543)).isTrue();
        // Exactly at the 1-per-20 boundary.
        assertThat(LocalHttpLLMAdapter.isPlausiblePromptTokenCount(227, 4543)).isTrue();
    }

    @Test
    @DisplayName("accepts anything when there is no prompt to compare against")
    void acceptsWhenPromptLengthUnknown() {
        assertThat(LocalHttpLLMAdapter.isPlausiblePromptTokenCount(2, 0)).isTrue();
        assertThat(LocalHttpLLMAdapter.isPlausiblePromptTokenCount(2, -1)).isTrue();
    }

    @Test
    @DisplayName("accepts small counts for genuinely short prompts")
    void acceptsSmallCountsForShortPrompts() {
        // A 40-character prompt really is ~10 tokens — the rule must not flag it.
        assertThat(LocalHttpLLMAdapter.isPlausiblePromptTokenCount(10, 40)).isTrue();
    }
}
