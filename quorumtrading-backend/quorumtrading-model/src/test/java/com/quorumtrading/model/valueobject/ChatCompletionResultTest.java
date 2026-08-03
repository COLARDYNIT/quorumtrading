package com.quorumtrading.model.valueobject;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class ChatCompletionResultTest {

    @Test
    void hasToolCalls_shouldReturnTrueWhenToolCallsPresent() {
        var toolCalls = List.of(new ToolCall("call_1", "listStrategies", "{}"));
        var result = ChatCompletionResult.withToolCalls(toolCalls, LLMUsage.ZERO, "test/model");

        assertThat(result.hasToolCalls()).isTrue();
    }

    @Test
    void hasToolCalls_shouldReturnFalseWhenNull() {
        var result = ChatCompletionResult.textOnly("Hello", LLMUsage.ZERO, "test/model");

        assertThat(result.hasToolCalls()).isFalse();
    }

    @Test
    void hasToolCalls_shouldReturnFalseWhenEmpty() {
        var result = new ChatCompletionResult("Hello", List.of(), LLMUsage.ZERO, "test/model");

        assertThat(result.hasToolCalls()).isFalse();
    }

    @Test
    void textOnly_shouldCreateResultWithContentOnly() {
        var usage = LLMUsage.of("test/model", 10, 20, 0.001);
        var result = ChatCompletionResult.textOnly("Hello world", usage, "test/model");

        assertThat(result.content()).isEqualTo("Hello world");
        assertThat(result.toolCalls()).isNull();
        assertThat(result.model()).isEqualTo("test/model");
        assertThat(result.usage().totalTokens()).isEqualTo(30);
    }

    @Test
    void withToolCalls_shouldCreateResultWithToolCallsOnly() {
        var toolCalls = List.of(
            new ToolCall("call_1", "listStrategies", "{}"),
            new ToolCall("call_2", "getSimulation", "{\"simulationId\":\"abc\"}")
        );
        var result = ChatCompletionResult.withToolCalls(toolCalls, LLMUsage.ZERO, "test/model");

        assertThat(result.content()).isNull();
        assertThat(result.toolCalls()).hasSize(2);
        assertThat(result.toolCalls().get(0).functionName()).isEqualTo("listStrategies");
        assertThat(result.toolCalls().get(1).functionName()).isEqualTo("getSimulation");
    }

    @Test
    void usageShouldDefaultToZeroWhenNull() {
        var result = new ChatCompletionResult("Hello", null, null, "test/model");

        assertThat(result.usage()).isNotNull();
        assertThat(result.usage().totalTokens()).isZero();
    }
}
