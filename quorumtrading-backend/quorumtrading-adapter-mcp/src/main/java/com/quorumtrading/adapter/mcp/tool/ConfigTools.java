package com.quorumtrading.adapter.mcp.tool;

import com.quorumtrading.adapter.mcp.auth.McpUserContext;
import com.quorumtrading.adapter.mcp.dto.McpAvailableModelDto;
import com.quorumtrading.adapter.mcp.dto.McpModelPreferenceDto;
import com.quorumtrading.application.port.out.SystemModelDefaultsPort;
import com.quorumtrading.application.usecase.GetAvailableModelsUseCase;
import com.quorumtrading.application.usecase.UpdateModelPreferencesUseCase;
import com.quorumtrading.model.valueobject.CategorizedModel;
import com.quorumtrading.model.enums.AgentRole;
import com.quorumtrading.model.valueobject.LLMModelInfo;
import com.quorumtrading.model.valueobject.LLMPricing;
import com.quorumtrading.model.valueobject.LLMUsage;
import com.quorumtrading.model.service.PlatformMarkup;
import com.quorumtrading.model.valueobject.ModelId;
import com.quorumtrading.model.valueobject.ModelPreferenceKey;
import com.quorumtrading.model.valueobject.ModelPreferences;
import com.quorumtrading.model.valueobject.TradingContext;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolResponse;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * MCP tools for model configuration: preferences and available models.
 */
@Singleton
@ActivateRequestContext
public class ConfigTools extends McpToolBase {

    private static final Map<TradingContext, String> CONTEXT_DESCRIPTIONS = Map.of(
        TradingContext.LIVE_TRADING, "Real money, highest quality required",
        TradingContext.PAPER_TRADING, "Simulated trading, balanced quality/cost",
        TradingContext.BACKTESTING, "Historical analysis, high volume, cost-sensitive",
        TradingContext.RESEARCH, "Market research, balanced quality/cost",
        TradingContext.CHAT, "AI agent chat, general-purpose assistant"
    );

    @Inject
    UpdateModelPreferencesUseCase updateModelPreferencesUseCase;

    @Inject
    GetAvailableModelsUseCase getAvailableModelsUseCase;

    @Inject
    SystemModelDefaultsPort systemModelDefaultsPort;

    @Inject
    com.quorumtrading.application.usecase.GetEffectiveModelsUseCase getEffectiveModelsUseCase;

    @Inject
    McpUserContext mcpUserContext;

    @Tool(name = "get_model_preferences", description = "Get the current user's model preferences for each trading context. "
        + "Shows the selected model, whether it's a user override or system default, "
        + "and the system default for each context (RESEARCH, BACKTESTING, PAPER_TRADING, LIVE_TRADING, CHAT).")
    public ToolResponse getModelPreferences() {
        return execute(() -> {
            // The EFFECTIVE model, not a re-derivation from config plus the DB. This tool used to
            // compute its own answer, which meant it could report a model the active engine would
            // never run — and disagree with both the settings page and llm_decisions.
            Map<TradingContext, String> systemDefaults = systemModelDefaultsPort.getAllDefaults();

            List<McpModelPreferenceDto> result = new ArrayList<>();
            for (var effective : getEffectiveModelsUseCase.execute(mcpUserContext.getUserId())) {
                TradingContext context = effective.context();
                result.add(new McpModelPreferenceDto(
                    context.name(),
                    CONTEXT_DESCRIPTIONS.getOrDefault(context, context.name()),
                    effective.effective(),
                    effective.source(),
                    systemDefaults.get(context)
                ));
            }

            logger.info("MCP: Retrieved model preferences for user");
            return toJson(result);
        });
    }

    @Tool(name = "update_model_preferences", description = "Update the current user's model preferences. "
        + "Only provided contexts are updated; omitted contexts remain unchanged. "
        + "Pass an empty string to clear a preference and revert to system default. "
        + "Use agentOverrides for per-agent model overrides (JSON object with keys like 'BACKTESTING_TECHNICAL').")
    public ToolResponse updateModelPreferences(
        @ToolArg(description = "Model ID for LIVE_TRADING (e.g. 'anthropic/claude-sonnet-4.6'), or empty string to clear")
            Optional<String> liveTradingModel,
        @ToolArg(description = "Model ID for PAPER_TRADING, or empty string to clear")
            Optional<String> paperTradingModel,
        @ToolArg(description = "Model ID for BACKTESTING, or empty string to clear")
            Optional<String> backtestingModel,
        @ToolArg(description = "Model ID for RESEARCH, or empty string to clear")
            Optional<String> researchModel,
        @ToolArg(description = "Model ID for CHAT (AI agent), or empty string to clear")
            Optional<String> chatModel,
        @ToolArg(description = "Per-agent model overrides as JSON, e.g. '{\"BACKTESTING_TECHNICAL\": \"google/gemini-2.5-flash\", \"BACKTESTING_SENTIMENT\": \"anthropic/claude-sonnet-4.6\"}'. "
            + "Keys are CONTEXT_ROLE (e.g. BACKTESTING_TECHNICAL, LIVE_TRADING_ORCHESTRATOR). Pass empty string as value to clear.")
            Optional<String> agentOverrides
    ) {
        return execute(() -> {
            Map<TradingContext, String> updates = new EnumMap<>(TradingContext.class);
            liveTradingModel.ifPresent(v -> updates.put(TradingContext.LIVE_TRADING, v));
            paperTradingModel.ifPresent(v -> updates.put(TradingContext.PAPER_TRADING, v));
            backtestingModel.ifPresent(v -> updates.put(TradingContext.BACKTESTING, v));
            researchModel.ifPresent(v -> updates.put(TradingContext.RESEARCH, v));
            chatModel.ifPresent(v -> updates.put(TradingContext.CHAT, v));

            // Parse agent overrides JSON if provided
            Map<String, String> agentOverridesMap = new java.util.HashMap<>();
            if (agentOverrides.isPresent() && !agentOverrides.get().isBlank()) {
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, String> parsed = new com.fasterxml.jackson.databind.ObjectMapper()
                        .readValue(agentOverrides.get(), Map.class);
                    agentOverridesMap.putAll(parsed);
                } catch (Exception e) {
                    throw new IllegalArgumentException("Invalid agentOverrides JSON: " + e.getMessage());
                }
            }

            if (updates.isEmpty() && agentOverridesMap.isEmpty()) {
                throw new IllegalArgumentException("No preferences provided. Specify at least one context or agent override to update.");
            }

            updateModelPreferencesUseCase.execute(mcpUserContext.getUserId(), updates, agentOverridesMap);
            logger.info("MCP: Updated model preferences for {} context(s) and {} agent override(s)",
                updates.size(), agentOverridesMap.size());

            // Return updated preferences
            return getModelPreferencesJson();
        });
    }

    @Tool(name = "get_available_models", description = "List available LLM models with pricing information. "
        + "Optionally filter by trading context to get only models that meet that context's requirements. "
        + "Use 'provider' to filter by provider name (e.g. 'anthropic', 'openai', 'google'). "
        + "Use 'limit' to control how many results are returned (default 50, max 200).")
    public ToolResponse getAvailableModels(
        @ToolArg(description = "Optional trading context filter: RESEARCH, BACKTESTING, PAPER_TRADING, LIVE_TRADING, or CHAT. "
            + "If omitted, returns all available models.") Optional<String> context,
        @ToolArg(description = "Maximum number of models to return (default 50, max 200)") Optional<Integer> limit,
        @ToolArg(description = "Filter by provider name (e.g. 'anthropic', 'openai', 'google', 'meta-llama'). Case-insensitive.") Optional<String> provider
    ) {
        return execute(() -> {
            mcpUserContext.getUserId(); // auth gate: global data, but a valid API token is still required
            int effectiveLimit = parseLimit(limit, 50, 200);
            TradingContext tradingContext = parseContext(context.orElse(null));
            String providerFilter = provider.map(String::toLowerCase).orElse(null);

            List<McpAvailableModelDto> result;
            if (tradingContext != null) {
                List<CategorizedModel> categorized = getAvailableModelsUseCase.executeWithTiers(tradingContext);
                result = categorized.stream()
                    .map(this::toModelDto)
                    .toList();
            } else {
                List<LLMModelInfo> models = getAvailableModelsUseCase.execute();
                result = models.stream()
                    .map(this::toModelDtoNoTier)
                    .toList();
            }

            // Apply provider filter if specified
            if (providerFilter != null) {
                String pf = providerFilter;
                result = result.stream()
                    .filter(m -> m.provider() != null && m.provider().toLowerCase().contains(pf))
                    .toList();
            }

            // Apply limit. Unlike the paginated list tools this one does not over-fetch, so
            // hasMore is not reported -- only the truncation is shared.
            result = paginate(result, effectiveLimit).items();

            logger.info("MCP: Retrieved {} available models (context={}, provider={}, limit={})", result.size(),
                tradingContext != null ? tradingContext.name() : "ALL", providerFilter, effectiveLimit);
            var responseMap = new java.util.LinkedHashMap<String, Object>();
            responseMap.put("items", result);
            responseMap.put("count", result.size());
            return toJson(responseMap);
        });
    }

    private String getModelPreferencesJson() {
        ModelPreferences userPrefs = updateModelPreferencesUseCase.getPreferences(mcpUserContext.getUserId());
        Map<TradingContext, String> systemDefaults = systemModelDefaultsPort.getAllDefaults();

        List<McpModelPreferenceDto> result = new ArrayList<>();
        for (TradingContext context : TradingContext.values()) {
            Optional<ModelId> userOverride = userPrefs.getFor(context);
            String systemDefault = systemDefaults.get(context);

            String selectedModel;
            String source;
            if (userOverride.isPresent()) {
                selectedModel = userOverride.get().value();
                source = "user_override";
            } else {
                selectedModel = systemDefault;
                source = "system_default";
            }

            result.add(new McpModelPreferenceDto(
                context.name(),
                CONTEXT_DESCRIPTIONS.getOrDefault(context, context.name()),
                selectedModel,
                source,
                systemDefault
            ));
        }

        // Include agent overrides if any
        Map<String, Map<String, ModelId>> grouped = userPrefs.getAllAgentOverridesGrouped();
        var responseMap = new java.util.LinkedHashMap<String, Object>();
        responseMap.put("contexts", result);
        if (!grouped.isEmpty()) {
            Map<String, Map<String, String>> agentOverridesStr = new java.util.LinkedHashMap<>();
            for (var entry : grouped.entrySet()) {
                Map<String, String> agentMap = new java.util.LinkedHashMap<>();
                for (var agentEntry : entry.getValue().entrySet()) {
                    agentMap.put(agentEntry.getKey(), agentEntry.getValue().value());
                }
                agentOverridesStr.put(entry.getKey(), agentMap);
            }
            responseMap.put("agentOverrides", agentOverridesStr);
        }
        return toJson(responseMap);
    }

    private McpAvailableModelDto toModelDto(CategorizedModel categorized) {
        LLMModelInfo model = categorized.model();
        LLMPricing billedPricing = model.pricing().withMarkup(PlatformMarkup.rate());
        return new McpAvailableModelDto(
            model.id(),
            model.name(),
            model.provider(),
            model.contextLength(),
            billedPricing.promptCostPerMillion().toPlainString(),
            billedPricing.completionCostPerMillion().toPlainString(),
            model.supportsWebSearch(),
            model.isFree(),
            model.parameterSizeB(),
            categorized.evaluation().tier().name(),
            categorized.evaluation().reason()
        );
    }

    private McpAvailableModelDto toModelDtoNoTier(LLMModelInfo model) {
        LLMPricing billedPricing = model.pricing().withMarkup(PlatformMarkup.rate());
        return new McpAvailableModelDto(
            model.id(),
            model.name(),
            model.provider(),
            model.contextLength(),
            billedPricing.promptCostPerMillion().toPlainString(),
            billedPricing.completionCostPerMillion().toPlainString(),
            model.supportsWebSearch(),
            model.isFree(),
            model.parameterSizeB(),
            null,
            null
        );
    }

    private TradingContext parseContext(String context) {
        if (context == null || context.isBlank()) {
            return null;
        }
        return parseEnum(TradingContext.class, context, "context");
    }
}
