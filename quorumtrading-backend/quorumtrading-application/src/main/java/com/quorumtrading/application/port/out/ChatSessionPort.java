package com.quorumtrading.application.port.out;

import com.quorumtrading.application.chat.ChatSession;
import com.quorumtrading.application.chat.ChatSessionSummary;
import com.quorumtrading.model.valueobject.UserId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Output port for chat session persistence.
 * Implemented by a Redis adapter with TTL-based expiry.
 */
public interface ChatSessionPort {

    void save(ChatSession session);

    Optional<ChatSession> findById(UUID sessionId, UserId userId);

    /**
     * List all sessions for a user, ordered by last activity (most recent first).
     */
    List<ChatSessionSummary> findAllByUserId(UserId userId);

    void delete(UUID sessionId);

    /**
     * Delete a session by ID and user (avoids KEYS pattern scan).
     */
    void delete(UUID sessionId, UserId userId);
}
