package com.quorumtrading.application.usecase.command;

import com.quorumtrading.model.valueobject.ResearchInterval;
import com.quorumtrading.model.valueobject.Symbol;
import com.quorumtrading.model.valueobject.UserId;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Command to update an existing market research configuration.
 * All fields except id are optional - only non-null fields will be updated.
 */
public record UpdateMarketResearchCommand(
    UUID id,
    String name,
    String markdownInstructions,
    Set<Symbol> targetAssets,
    ResearchInterval interval,
    UserId requestingUserId
) {
    public UpdateMarketResearchCommand {
        Objects.requireNonNull(id, "Market research ID cannot be null");

        // Validate optional fields if provided
        if (name != null && name.isBlank()) {
            throw new IllegalArgumentException("Name cannot be blank if provided");
        }
        if (markdownInstructions != null && markdownInstructions.isBlank()) {
            throw new IllegalArgumentException("Markdown instructions cannot be blank if provided");
        }
        if (targetAssets != null && targetAssets.isEmpty()) {
            throw new IllegalArgumentException("Target assets cannot be empty if provided");
        }
    }

    /**
     * Check if any update fields are provided.
     */
    public boolean hasUpdates() {
        return name != null || markdownInstructions != null || targetAssets != null || interval != null;
    }
}
