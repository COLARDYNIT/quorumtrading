package com.quorumtrading.application.chat;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.quorumtrading.model.valueobject.ChatMessage;
import com.quorumtrading.model.valueobject.LLMUsage;
import com.quorumtrading.model.valueobject.UserId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Represents a chat conversation session with message history.
 * Sessions are stored in Redis with a configurable TTL.
 */
public class ChatSession {

    private final UUID sessionId;
    private final UserId userId;
    private String model;
    private final List<ChatMessage> messages;
    private String systemPrompt;
    private LLMUsage cumulativeUsage;
    private final Instant createdAt;
    private Instant lastActivityAt;
    private String title;

    public ChatSession(UUID sessionId, UserId userId, String model, String systemPrompt) {
        this.sessionId = sessionId;
        this.userId = userId;
        this.model = model;
        this.messages = new ArrayList<>();
        this.systemPrompt = systemPrompt;
        this.cumulativeUsage = LLMUsage.ZERO;
        this.createdAt = Instant.now();
        this.lastActivityAt = Instant.now();
    }

    /**
     * Full constructor for deserialization.
     */
    @JsonCreator
    public ChatSession(@JsonProperty("sessionId") UUID sessionId,
                        @JsonProperty("userId") UserId userId,
                        @JsonProperty("model") String model,
                        @JsonProperty("messages") List<ChatMessage> messages,
                        @JsonProperty("systemPrompt") String systemPrompt,
                        @JsonProperty("cumulativeUsage") LLMUsage cumulativeUsage,
                        @JsonProperty("createdAt") Instant createdAt,
                        @JsonProperty("lastActivityAt") Instant lastActivityAt,
                        @JsonProperty("title") String title) {
        this.sessionId = sessionId;
        this.userId = userId;
        this.model = model;
        this.messages = new ArrayList<>(messages);
        this.systemPrompt = systemPrompt;
        this.cumulativeUsage = cumulativeUsage;
        this.createdAt = createdAt;
        this.lastActivityAt = lastActivityAt;
        this.title = title;
    }

    public void addMessage(ChatMessage message) {
        messages.add(message);
        touch();
    }

    /**
     * Get the full message list including the system prompt as the first message.
     * Used when sending to the LLM.
     */
    @JsonIgnore
    public List<ChatMessage> getMessagesForLLM() {
        List<ChatMessage> llmMessages = new ArrayList<>();
        llmMessages.add(ChatMessage.system(systemPrompt));
        llmMessages.addAll(messages);
        return Collections.unmodifiableList(llmMessages);
    }

    /**
     * Get the most recent messages from the conversation.
     * Useful for providing context to follow-up suggestion generation.
     *
     * @param count Maximum number of messages to return
     * @return List of up to {@code count} most recent messages
     */
    @JsonIgnore
    public List<ChatMessage> getRecentMessages(int count) {
        int size = messages.size();
        int start = Math.max(0, size - count);
        return new ArrayList<>(messages.subList(start, size));
    }

    /**
     * Count user messages in this session.
     * Used to determine if the session has enough engagement to appear in history.
     */
    @JsonIgnore
    public long getUserMessageCount() {
        return messages.stream().filter(m -> "user".equals(m.role())).count();
    }

    public void updateModel(String model) {
        this.model = model;
        touch();
    }

    public void recordUsage(LLMUsage usage) {
        this.cumulativeUsage = this.cumulativeUsage.add(usage);
    }

    public void touch() {
        this.lastActivityAt = Instant.now();
    }

    // Getters

    public UUID getSessionId() { return sessionId; }
    public UserId getUserId() { return userId; }
    public String getModel() { return model; }
    public List<ChatMessage> getMessages() { return Collections.unmodifiableList(messages); }
    public String getSystemPrompt() { return systemPrompt; }
    public LLMUsage getCumulativeUsage() { return cumulativeUsage; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastActivityAt() { return lastActivityAt; }
    public String getTitle() { return title; }

    public void setTitle(String title) {
        this.title = title;
    }
}
