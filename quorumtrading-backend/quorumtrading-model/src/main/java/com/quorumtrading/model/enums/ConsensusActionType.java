package com.quorumtrading.model.enums;

import java.util.Locale;
import java.util.Optional;

/**
 * The resolved action of a consensus decision.
 *
 * <p>Unlike {@link RecommendationType} (what an agent recommends), this enum is a
 * <b>total</b> classification of the free-form {@code finalAction} text that enters the
 * domain from LLM output: every string maps to <em>something</em>, with {@link #UNKNOWN}
 * as the sink for unparseable or unrecognized input. {@code UNKNOWN} is never tradeable
 * and must never leak into agent-recommendation semantics.</p>
 *
 * <p>Constant names are byte-identical to the historical action filter strings
 * ({@code "BUY"}, {@code "SELL"}, ...) so {@link #name()} slots directly into the
 * existing {@code final_action->>'action'} repository predicates.</p>
 */
public enum ConsensusActionType {
    BUY, SELL, HOLD, SKIP,
    /** Raw text could not be resolved to a known action. Never tradeable. */
    UNKNOWN;

    /**
     * Map to the agent-recommendation enum. Empty for {@link #UNKNOWN} — callers on the
     * trade-execution path must treat empty as a failed action, never as a default.
     */
    public Optional<RecommendationType> toRecommendationType() {
        return this == UNKNOWN ? Optional.empty() : Optional.of(RecommendationType.valueOf(name()));
    }

    /** True for the two actions that move money. */
    public boolean isTradeSignal() {
        return this == BUY || this == SELL;
    }

    /**
     * Prompt-facing past-tense label used in the recent-trade-history block fed to LLMs:
     * BUY→BOUGHT, SELL→SOLD, HOLD→HOLD, SKIP→SKIP, UNKNOWN→TRADED.
     */
    public String pastTenseLabel() {
        return switch (this) {
            case BUY -> "BOUGHT";
            case SELL -> "SOLD";
            case HOLD -> "HOLD";
            case SKIP -> "SKIP";
            case UNKNOWN -> "TRADED";
        };
    }

    /**
     * Resolve a raw action token case-insensitively; {@link #UNKNOWN} when the token is
     * null or not a known action. Total — never throws.
     */
    public static ConsensusActionType fromToken(String token) {
        if (token == null) {
            return UNKNOWN;
        }
        return switch (token.trim().toUpperCase(Locale.ROOT)) {
            case "BUY" -> BUY;
            case "SELL" -> SELL;
            case "HOLD" -> HOLD;
            case "SKIP" -> SKIP;
            default -> UNKNOWN;
        };
    }
}
