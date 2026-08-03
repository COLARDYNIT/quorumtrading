package com.quorumtrading.application.usecase.command;

import com.quorumtrading.model.valueobject.BacktestConfiguration;
import com.quorumtrading.model.valueobject.ResearchInterval;
import com.quorumtrading.model.valueobject.Symbol;
import com.quorumtrading.model.valueobject.UserId;

import java.util.Objects;
import java.util.Set;

/**
 * Command to create a new market research configuration.
 */
public record CreateMarketResearchCommand(
    String name,
    String markdownInstructions,
    Set<Symbol> targetAssets,
    ResearchInterval interval,
    boolean enableAutoBacktest,
    BacktestConfiguration backtestConfiguration,
    UserId ownerId
) {
    public CreateMarketResearchCommand {
        Objects.requireNonNull(name, "Name cannot be null");
        if (name.isBlank()) {
            throw new IllegalArgumentException("Name cannot be blank");
        }
        Objects.requireNonNull(markdownInstructions, "Markdown instructions cannot be null");
        if (markdownInstructions.isBlank()) {
            throw new IllegalArgumentException("Markdown instructions cannot be blank");
        }
        // Target assets are optional - LLM can discover relevant assets from instructions
        // Default to empty set if null
        targetAssets = targetAssets != null ? targetAssets : Set.of();
        Objects.requireNonNull(interval, "Interval cannot be null");
        if (enableAutoBacktest && backtestConfiguration == null) {
            throw new IllegalArgumentException("Backtest configuration required when auto-backtest is enabled");
        }
        Objects.requireNonNull(ownerId, "OwnerId cannot be null");
    }
}
