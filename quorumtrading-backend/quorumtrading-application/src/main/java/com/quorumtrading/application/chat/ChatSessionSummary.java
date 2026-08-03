package com.quorumtrading.application.chat;

import java.time.Instant;
import java.util.UUID;

/**
 * Lightweight projection of a chat session for listing purposes.
 * Avoids loading full message history from Redis.
 */
public record ChatSessionSummary(
    UUID sessionId,
    String title,
    String model,
    Instant createdAt,
    Instant lastActivityAt
) {}
