package com.quorumtrading.adapter.rest.dto.response;

import com.quorumtrading.model.valueobject.ModelId;
import com.quorumtrading.model.valueobject.ModelPreferences;
import com.quorumtrading.model.valueobject.TradingContext;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Response DTO for user model preferences.
 */
@Schema(description = "User's preferred LLM models for each trading context")
public record ModelPreferencesResponse(
    @Schema(description = "Preferred model for market research (requires websearch)", example = "perplexity/llama-3.1-sonar-huge-128k-online")
    String research,

    @Schema(description = "Preferred model for backtesting", example = "openai/gpt-4o-mini")
    String backtesting,

    @Schema(description = "Preferred model for paper trading", example = "anthropic/claude-3.5-sonnet")
    String paperTrading,

    @Schema(description = "Preferred model for live trading", example = "anthropic/claude-3.5-sonnet")
    String liveTrading,

    @Schema(description = "Preferred model for AI agent chat", example = "google/gemini-2.5-flash")
    String chat,

    @Schema(description = "Per-agent model overrides by context, e.g. {\"BACKTESTING\": {\"TECHNICAL\": \"model-id\"}}")
    Map<String, Map<String, String>> agentOverrides
) {
    /**
     * Create response from domain model.
     */
    public static ModelPreferencesResponse fromDomain(ModelPreferences preferences) {
        // Extract per-agent overrides grouped by context
        Map<String, Map<String, String>> overrides = new LinkedHashMap<>();
        Map<String, Map<String, ModelId>> grouped = preferences.getAllAgentOverridesGrouped();
        for (var contextEntry : grouped.entrySet()) {
            Map<String, String> agentMap = new LinkedHashMap<>();
            for (var agentEntry : contextEntry.getValue().entrySet()) {
                agentMap.put(agentEntry.getKey(), agentEntry.getValue().value());
            }
            overrides.put(contextEntry.getKey(), agentMap);
        }

        return new ModelPreferencesResponse(
            preferences.getFor(TradingContext.RESEARCH).map(ModelId::value).orElse(null),
            preferences.getFor(TradingContext.BACKTESTING).map(ModelId::value).orElse(null),
            preferences.getFor(TradingContext.PAPER_TRADING).map(ModelId::value).orElse(null),
            preferences.getFor(TradingContext.LIVE_TRADING).map(ModelId::value).orElse(null),
            preferences.getFor(TradingContext.CHAT).map(ModelId::value).orElse(null),
            overrides.isEmpty() ? null : overrides
        );
    }
}
