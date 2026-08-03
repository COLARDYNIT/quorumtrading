package com.quorumtrading.adapter.mcp.filter;

import com.quorumtrading.application.usecase.EvaluateFeatureUseCase;
import com.quorumtrading.model.enums.Feature;
import com.quorumtrading.model.valueobject.FeatureFlagContext;
import io.quarkiverse.mcp.server.FilterContext;
import io.quarkiverse.mcp.server.PromptFilter;
import io.quarkiverse.mcp.server.PromptManager;
import io.quarkiverse.mcp.server.ToolFilter;
import io.quarkiverse.mcp.server.ToolManager;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Map;

/**
 * Keeps switched-off features out of the MCP catalogue.
 *
 * <p>Without this, a disabled feature's tools are still advertised by {@code tools/list} and only
 * refuse once called. An agent reads that list as a menu of what it can do: it will pick the tool,
 * spend a turn on it, and then have to explain the refusal to the user. Not offering it in the
 * first place is both cheaper and more honest.</p>
 *
 * <h2>The one thing this cannot do</h2>
 * <p>Listing happens per connection, not per request, and the MCP handshake carries no user, so the
 * catalogue is filtered against an <em>anonymous</em> context. A flag switched on for named
 * individuals only therefore stays hidden from the listing even for those individuals — but it
 * still works when invoked, because the tool guards
 * ({@code McpToolBase.isFeatureEnabled}) evaluate with the caller resolved from their API token.
 * Hidden-but-callable is the safe direction to be wrong in; advertised-but-refusing is not.</p>
 */
public class FeatureFlagMcpFilters {

    /** Tool name prefixes owned by each flagged feature. */
    private static final Map<String, Feature> TOOL_FEATURES = Map.of(
        "list_challenges", Feature.CHALLENGES,
        "get_challenge", Feature.CHALLENGES,
        "join_challenge", Feature.CHALLENGES,
        "list_market_research", Feature.MARKET_RESEARCH,
        "get_market_research_details", Feature.MARKET_RESEARCH,
        "create_market_research", Feature.MARKET_RESEARCH,
        "execute_market_research", Feature.MARKET_RESEARCH,
        "pause_market_research", Feature.MARKET_RESEARCH,
        "resume_market_research", Feature.MARKET_RESEARCH,
        "delete_market_research", Feature.MARKET_RESEARCH);

    private static final Map<String, Feature> PROMPT_FEATURES = Map.of(
        "analyze_market_research", Feature.MARKET_RESEARCH,
        "full_research_cycle", Feature.MARKET_RESEARCH);

    /** The tool names this filter governs — used by the contract test to catch drift. */
    public static Map<String, Feature> toolFeatures() {
        return TOOL_FEATURES;
    }

    public static Map<String, Feature> promptFeatures() {
        return PROMPT_FEATURES;
    }

    @Singleton
    public static class FeatureFlagToolFilter implements ToolFilter {

        @Inject
        EvaluateFeatureUseCase evaluateFeature;

        @Override
        @ActivateRequestContext
        public boolean test(ToolManager.ToolInfo tool, FilterContext context) {
            Feature feature = TOOL_FEATURES.get(tool.name());
            return feature == null
                || evaluateFeature.isEnabled(feature, FeatureFlagContext.anonymous());
        }
    }

    @Singleton
    public static class FeatureFlagPromptFilter implements PromptFilter {

        @Inject
        EvaluateFeatureUseCase evaluateFeature;

        @Override
        @ActivateRequestContext
        public boolean test(PromptManager.PromptInfo prompt, FilterContext context) {
            Feature feature = PROMPT_FEATURES.get(prompt.name());
            return feature == null
                || evaluateFeature.isEnabled(feature, FeatureFlagContext.anonymous());
        }
    }
}
