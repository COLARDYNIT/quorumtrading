package com.quorumtrading.application.usecase.command;

import com.quorumtrading.model.valueobject.UserId;

/**
 * Command for creating a new strategy.
 *
 * @param name        name of the strategy
 * @param description optional human-readable description (max 500 chars)
 * @param content     markdown content with strategy configuration
 * @param ownerId     ID of the user who will own this strategy
 */
public record CreateStrategyCommand(
    String name,
    String description,
    String content,
    UserId ownerId
) {
    /** Backward-compatible constructor without description. */
    public CreateStrategyCommand(String name, String content, UserId ownerId) {
        this(name, null, content, ownerId);
    }
}

