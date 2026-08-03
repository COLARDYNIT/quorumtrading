package com.quorumtrading.application.chat;

import com.quorumtrading.application.port.out.ChatSessionPort;
import com.quorumtrading.model.valueobject.UserId;

import java.util.List;

/**
 * Lists all chat sessions for a user, ordered by most recent activity.
 */
public class ListChatSessionsUseCase {

    private final ChatSessionPort chatSessionPort;

    public ListChatSessionsUseCase(ChatSessionPort chatSessionPort) {
        this.chatSessionPort = chatSessionPort;
    }

    public List<ChatSessionSummary> execute(UserId userId) {
        return chatSessionPort.findAllByUserId(userId);
    }
}
