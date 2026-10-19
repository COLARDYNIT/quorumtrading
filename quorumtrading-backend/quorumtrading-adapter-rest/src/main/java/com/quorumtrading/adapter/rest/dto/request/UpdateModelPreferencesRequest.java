package com.quorumtrading.adapter.rest.dto.request;

import com.quorumtrading.model.valueobject.TradingContext;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.HashMap;
import java.util.Map;

/**
 * Request DTO for updating model preferences.
 */
@Schema(description = "Request to update user's preferred LLM models")
public record UpdateModelPreferencesRequest(
    @Schema(description = "Preferred model for market research (must support websearch)", example = "perplexity/llama-3.1-sonar-huge-128k-online")
    String research,

    @Schema(description = "Preferred model for backtesting", example = "openai/gpt-4o-mini")
    String backtesting,

    @Schema(description = "Preferred model for paper trading", example = "anthropic/claude-sonnet-5.5")
    String paperTrading,

    @Schema(description = "Preferred model for live trading", example = "anthropic/claude-sonnet-5.5")
    String liveTrading,

    @Schema(description = "Preferred model for AI agent chat", example = "google/gemini-2.5-flash")
    String chat,

    @Schema(description = "Per-agent model overrides by context, e.g. {\"BACKTESTING\": {\"TECHNICAL\": \"model-id\"}}")
    Map<String, Map<String, String>> agentOverrides
) {
    /**
     * Convert to a map of TradingContext to model ID string.
     * Empty strings are treated as "clear preference".
     */
    public Map<TradingContext, String> toContextMap() {
        Map<TradingContext, String> map = new HashMap<>();

        if (research != null) {
            map.put(TradingContext.RESEARCH, research);
        }
        if (backtesting != null) {
            map.put(TradingContext.BACKTESTING, backtesting);
        }
        if (paperTrading != null) {
            map.put(TradingContext.PAPER_TRADING, paperTrading);
        }
        if (liveTrading != null) {
            map.put(TradingContext.LIVE_TRADING, liveTrading);
        }
        if (chat != null) {
            map.put(TradingContext.CHAT, chat);
        }

        return map;
    }

    /**
     * Convert agentOverrides to flat storage key map.
     * Input: { "BACKTESTING": { "TECHNICAL": "model-id" } }
     * Output: { "BACKTESTING_TECHNICAL": "model-id" }
     */
    public Map<String, String> toAgentOverridesMap() {
        if (agentOverrides == null || agentOverrides.isEmpty()) {
            return Map.of();
        }

        Map<String, String> flat = new HashMap<>();
        for (Map.Entry<String, Map<String, String>> contextEntry : agentOverrides.entrySet()) {
            String contextName = contextEntry.getKey();
            if (contextEntry.getValue() == null) continue;
            for (Map.Entry<String, String> agentEntry : contextEntry.getValue().entrySet()) {
                String storageKey = contextName + "_" + agentEntry.getKey();
                flat.put(storageKey, agentEntry.getValue());
            }
        }
        return flat;
    }
}
