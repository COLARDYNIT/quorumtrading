package com.quorumtrading.application.chat;

import com.quorumtrading.application.port.out.ChatSessionPort;
import com.quorumtrading.model.valueobject.UserId;

import java.util.UUID;

/**
 * Retrieves an existing chat session for reconnection after page navigation.
 */
public class GetChatSessionUseCase {

    private final ChatSessionPort chatSessionPort;

    public GetChatSessionUseCase(ChatSessionPort chatSessionPort) {
        this.chatSessionPort = chatSessionPort;
    }

    public ChatSession execute(UUID sessionId, UserId userId) {
        return chatSessionPort.findById(sessionId, userId)
            // NoSuchElementException, not IllegalArgumentException: the REST layer maps the
            // former to 404 and the latter to 400, so a session that does not exist (or belongs
            // to someone else) was reported as a malformed request. The caller's request was
            // fine; the thing they asked for is not there.
            .orElseThrow(() -> new java.util.NoSuchElementException("Chat session not found: " + sessionId));
    }
}
