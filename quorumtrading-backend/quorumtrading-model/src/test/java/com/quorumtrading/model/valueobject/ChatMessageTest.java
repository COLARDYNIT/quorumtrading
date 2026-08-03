package com.quorumtrading.model.valueobject;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

class ChatMessageTest {

    @Test
    void user_shouldCreateUserMessage() {
        var msg = ChatMessage.user("Hello");

        assertThat(msg.role()).isEqualTo("user");
        assertThat(msg.content()).isEqualTo("Hello");
        assertThat(msg.toolCalls()).isNull();
        assertThat(msg.toolCallId()).isNull();
    }

    @Test
    void assistant_shouldCreateAssistantMessage() {
        var msg = ChatMessage.assistant("I can help with that.");

        assertThat(msg.role()).isEqualTo("assistant");
        assertThat(msg.content()).isEqualTo("I can help with that.");
        assertThat(msg.hasToolCalls()).isFalse();
    }

    @Test
    void system_shouldCreateSystemMessage() {
        var msg = ChatMessage.system("You are a helpful trading assistant.");

        assertThat(msg.role()).isEqualTo("system");
        assertThat(msg.content()).isEqualTo("You are a helpful trading assistant.");
    }

    @Test
    void assistantWithToolCalls_shouldCreateMessageWithToolCalls() {
        var toolCalls = List.of(new ToolCall("call_1", "listStrategies", "{}"));
        var msg = ChatMessage.assistantWithToolCalls(toolCalls);

        assertThat(msg.role()).isEqualTo("assistant");
        assertThat(msg.content()).isNull();
        assertThat(msg.hasToolCalls()).isTrue();
        assertThat(msg.toolCalls()).hasSize(1);
    }

    @Test
    void toolResult_shouldCreateToolResultMessage() {
        var msg = ChatMessage.toolResult("call_1", "{\"strategies\": []}");

        assertThat(msg.role()).isEqualTo("tool");
        assertThat(msg.content()).isEqualTo("{\"strategies\": []}");
        assertThat(msg.toolCallId()).isEqualTo("call_1");
    }

    @Test
    void shouldRejectNullRole() {
        assertThatThrownBy(() -> new ChatMessage(null, "content", null, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("role");
    }

    @Test
    void shouldRejectBlankRole() {
        assertThatThrownBy(() -> new ChatMessage("  ", "content", null, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("role");
    }
}
