package com.quorumtrading.adapter.llm.local;

import com.quorumtrading.application.orchestration.agents.LLMConfig;
import com.quorumtrading.application.orchestration.agents.LLMQueryException;
import com.quorumtrading.application.orchestration.agents.LLMQueryResult;
import com.quorumtrading.application.port.out.LLMPort;
import com.quorumtrading.model.valueobject.LLMUsage;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.jboss.logging.Logger;

import java.util.List;

/**
 * Local LLM adapter that executes CLI tools (Codex, Claude, Gemini) for LLM queries.
 * <p>
 * This adapter provides per-agent tool selection with automatic fallback to a default tool (Claude)
 * when the requested tool is not installed.
 * </p>
 * <p>
 * Configuration:
 * <ul>
 *   <li>quorumtrading.llm.local.default-tool - Default tool to fallback to (default: claude)</li>
 *   <li>quorumtrading.agents.{agentName}.llm-tool - Tool to use for specific agent</li>
 * </ul>
 * </p>
 * <p>
 * Opt-in via {@code quorumtrading.llm.local.enabled} (default {@code false} — OpenRouter is the
 * default provider in the hosted SaaS). When enabled, the bean is resolvable through programmatic
 * lookup, so the LLM router's {@code Instance<LLMPort>} discovery picks it up; when disabled it is
 * invisible, exactly like the old unselected {@code @Alternative}. The self-host (local) edition
 * enables it so agentic users get the consensus engine through their own CLI tooling for free.
 * </p>
 *
 * @author QuorumTrading
 * @since 1.0.0
 */
@ApplicationScoped
@io.quarkus.arc.lookup.LookupIfProperty(name = "quorumtrading.llm.local.enabled", stringValue = "true", lookupIfMissing = false)
public class LocalLLMAdapter implements LLMPort {

    private static final Logger LOG = Logger.getLogger(LocalLLMAdapter.class);

    @Inject
    CodexExecutor codexExecutor;

    @Inject
    ClaudeExecutor claudeExecutor;

    @Inject
    GeminiExecutor geminiExecutor;

    @Inject
    QwenExecutor qwenExecutor;

    @Inject
    OpencodeExecutor opencodeExecutor;

    @Inject
    CodexResponseParser codexParser;

    @Inject
    ClaudeResponseParser claudeParser;

    @Inject
    QwenResponseParser qwenParser;

    /**
     * opencode gets its own parser rather than borrowing qwen's.
     *
     * <p>{@code --format json} emits a newline-delimited <em>event stream</em>, not the shape the
     * qwen parser handles. Treating it as text meant an {@code error} event could be handed on as
     * if it were an answer, and the token counts opencode really does report were thrown away.</p>
     */
    @Inject
    OpencodeResponseParser opencodeParser;

    @Inject
    GeminiResponseParser geminiParser;

    @Inject
    DummyExecutor dummyExecutor;

    @Inject
    DummyResponseParser dummyParser;

    @ConfigProperty(name = "quorumtrading.llm.local.default-tool", defaultValue = "qwen")
    String defaultTool;

    @Inject
    ToolFailureCache failureCache;

    @ConfigProperty(name = "quorumtrading.llm.local.tool.fallback")
    java.util.List<String> fallbackTools;

    @Override
    @CircuitBreaker(
        requestVolumeThreshold = 20,
        failureRatio = 0.5,
        delay = 60000,
        successThreshold = 2
    )
    public LLMQueryResult queryWithUsage(String prompt, LLMConfig config) {
        String requestedTool = determineToolForAgent(config.model());

        LOG.infof("LocalLLM query: requestedTool=%s, model=%s, promptLength=%d",
                  requestedTool, config.model(), prompt.length());

        // Build and filter fallback chain
        List<String> toolChain = buildFallbackChain(requestedTool);
        List<String> filteredChain = filterBlacklistedTools(toolChain);

        if (filteredChain.isEmpty()) {
            String errorMessage = String.format(
                "All tools blacklisted for agent %s. Original chain: %s",
                config.model(), toolChain
            );
            LOG.error(errorMessage);
            throw new LLMQueryException(errorMessage);
        }

        // Track failures for aggregated error message
        List<String> attemptedTools = new java.util.ArrayList<>();
        List<String> failureReasons = new java.util.ArrayList<>();

        // Try each tool in filtered chain (short-circuit on success)
        for (int i = 0; i < filteredChain.size(); i++) {
            String tool = filteredChain.get(i);
            attemptedTools.add(tool);

            LOG.infof("Attempting LLM query: tool=%s, agent=%s, attempt=%d/%d",
                     tool, config.model(), i + 1, filteredChain.size());

            CLIExecutor executor = getExecutor(tool);

            // Check if tool is installed
            if (!executor.isInstalled()) {
                String reason = tool + " not installed";
                failureReasons.add(reason);
                LOG.warnf("%s not installed, skipping to next tool", tool);
                continue;
            }

            try {
                // Execute CLI command WITH the resolved model. This used to be executor.execute(prompt)
                // against a hardcoded '--model haiku' / '--model gpt-5', which is why the six-tier
                // chain computed a model that reached nothing but log lines.
                String rawResponse = executor.execute(prompt, config.model(), config.effort());

                // Parse and normalize response
                ResponseParser parser = getParser(tool);
                String normalized = parser.parse(rawResponse);

                LOG.infof("LocalLLM query successful: tool=%s, responseLength=%d",
                         tool, normalized.length());

                // Record the MODEL, not the tool name. 'claude' is not a model, and recording it
                // meant llm_decisions could never agree with what get_model_preferences reported.
                // When nothing resolved there is genuinely no model to name, and the tool name is
                // the most specific true thing we know.
                String actualModel = recordedModel(config, tool);
                // Most local CLI tools do not report token usage, so ZERO here means "unknown",
                // NOT "no tokens were used" — the prompts that produced this response run to
                // thousands of characters. Anything reporting these figures to a user must
                // present them as unavailable rather than as a measured zero; see
                // get_simulation_cost, which suppresses prompt/total tokens on this path.
                //
                // opencode is the exception: its JSON event stream carries real counts, and where
                // a tool genuinely reports them there is no reason to record "unknown".
                LLMUsage usage = parser.parseUsage(rawResponse).orElse(LLMUsage.ZERO);
                if (usage != LLMUsage.ZERO) {
                    LOG.infof("LocalLLM usage reported by %s: promptTokens=%d, completionTokens=%d, "
                                    + "totalTokens=%d, agent=%s",
                            tool, usage.promptTokens(), usage.completionTokens(),
                            usage.totalTokens(), config.model());
                }
                return LLMQueryResult.of(normalized, usage, actualModel);

            } catch (CLIExecutionException e) {
                String reason = tool + " - " + e.getMessage();
                failureReasons.add(reason);

                LOG.warnf("Tool %s failed for agent %s: %s",
                         tool, config.model(), e.getMessage());

                // Record failure in cache
                failureCache.recordFailure(tool);

                // Continue to next tool (if available)
            }
        }

        // All tools exhausted
        List<String> blacklistedTools = new java.util.ArrayList<>(toolChain);
        blacklistedTools.removeAll(filteredChain);

        String errorMessage = String.format(
            "All LLM tools exhausted for agent %s. Attempted: %s. Blacklisted: %s. Failures: %s",
            config.model(), attemptedTools, blacklistedTools, failureReasons
        );

        LOG.error(errorMessage);
        throw new LLMQueryException(errorMessage);
    }

    @Override
    public String getProviderName() {
        return "local-llm";
    }

    @Override
    public boolean isAvailable() {
        // Report LOCAL as available ONLY if a CLI tool this deployment would actually invoke is
        // really installed. The tools that can run are the configured default plus the fallback
        // chain (e.g. `claude,qwen`), each probed with a real `--version` check.
        //
        // The DummyExecutor is an in-process test/e2e shim whose isInstalled() returns true
        // unconditionally, so it must NOT count here — otherwise LOCAL looks "available" on a
        // machine with no CLI (e.g. inside the Docker image) and every consensus call then throws
        // at run time. It counts only when a profile explicitly lists `dummy` in the tool chain
        // (the %e2e profile does exactly that), which this probe honours naturally.
        for (String tool : configuredTools()) {
            try {
                if (getExecutor(tool).isInstalled()) {
                    return true;
                }
            } catch (IllegalArgumentException unknownTool) {
                LOG.warnf("Configured local LLM tool '%s' is not recognised; skipping availability probe", tool);
            }
        }
        return false;
    }

    // ========== Model catalogue (LLMPort) ==========
    // The CLI tools were always model-selectable; this adapter simply never asked them. That gap —
    // not a tool limitation — is why preference validation no-op'd on this engine and any string
    // was accepted as a model.

    @Override
    public boolean supportsModelListing() {
        return !getAvailableModels().isEmpty();
    }

    /**
     * Models the <em>installed</em> tools accept.
     *
     * <p>Probed per tool with the same {@code isInstalled()} check availability uses, so an
     * uninstalled tool contributes nothing — a chain entry is not evidence the tool exists. Tools
     * whose flag surface was never verified advertise nothing rather than a guess.</p>
     */
    @Override
    public java.util.List<com.quorumtrading.model.valueobject.LLMModelInfo> getAvailableModels() {
        java.util.Set<String> ids = new java.util.LinkedHashSet<>();
        for (String tool : configuredTools()) {
            try {
                CLIExecutor executor = getExecutor(tool);
                if (executor.isInstalled()) {
                    ids.addAll(executor.supportedModels());
                }
            } catch (IllegalArgumentException unknownTool) {
                LOG.debugf("Configured local LLM tool '%s' is not recognised; contributing no models", tool);
            }
        }
        return ids.stream()
                .sorted()
                .map(id -> com.quorumtrading.model.valueobject.LLMModelInfo.of(
                        id, id, com.quorumtrading.model.valueobject.LLMPricing.FREE))
                .toList();
    }

    /**
     * A CLI engine genuinely has no catalogue when none of its installed tools advertises one —
     * that is "not supported", not "temporarily unreachable": nothing will change on a retry.
     */
    @Override
    public com.quorumtrading.model.valueobject.ModelListingCapability modelListingCapability() {
        return getAvailableModels().isEmpty()
                ? com.quorumtrading.model.valueobject.ModelListingCapability.NOT_SUPPORTED
                : com.quorumtrading.model.valueobject.ModelListingCapability.ENUMERABLE;
    }

    /** What to record as the model that answered: the model asked for, else the tool that ran. */
    private static String recordedModel(LLMConfig config, String tool) {
        String model = config.model();
        if (model == null || model.isBlank()) {
            return tool;
        }
        // Effort is part of what ran, so it belongs in the record — otherwise two runs at different
        // effort levels are indistinguishable after the fact.
        return config.effort() == null ? model : model + " (effort=" + config.effort().flagValue() + ")";
    }

    /**
     * The configured tools that are actually installed, in chain order.
     *
     * <p>Shown to users as the real fallback chain. Listing a configured-but-absent tool is the same
     * class of lie as showing a foreign engine's models: it describes a capability the deployment
     * does not have.</p>
     */
    public List<String> installedTools() {
        List<String> installed = new java.util.ArrayList<>();
        for (String tool : configuredTools()) {
            try {
                if (getExecutor(tool).isInstalled()) {
                    installed.add(tool);
                }
            } catch (IllegalArgumentException unknownTool) {
                LOG.debugf("Configured local LLM tool '%s' is not recognised; omitting from the chain", tool);
            }
        }
        return installed;
    }

    /** Distinct tools this deployment could invoke: the default tool followed by the fallback chain. */
    private List<String> configuredTools() {
        List<String> tools = new java.util.ArrayList<>();
        if (defaultTool != null && !defaultTool.isBlank()) {
            tools.add(defaultTool.trim());
        }
        if (fallbackTools != null) {
            for (String tool : fallbackTools) {
                if (tool != null && !tool.isBlank() && !tools.contains(tool.trim())) {
                    tools.add(tool.trim());
                }
            }
        }
        return tools;
    }

    /**
     * Determines which CLI tool to use for the given agent.
     * <p>
     * Checks configuration for agent-specific tool selection:
     * quorumtrading.agents.{agentName}.llm-tool
     * </p>
     * <p>
     * Falls back to default tool if no agent-specific configuration exists.
     * </p>
     *
     * @param agentName Name of the agent (from LLMConfig.model field)
     * @return Tool name (codex, claude, or gemini)
     */
    private String determineToolForAgent(String agentName) {
        return ConfigProvider.getConfig()
            .getOptionalValue("quorumtrading.agents." + agentName + ".llm-tool", String.class)
            .orElse(defaultTool);
    }

    /**
     * Gets the CLI executor for the specified tool name.
     *
     * @param toolName Tool name (codex, claude, gemini)
     * @return Corresponding executor
     * @throws IllegalArgumentException if tool name is unknown
     */
    private CLIExecutor getExecutor(String toolName) {
        return switch (toolName.toLowerCase()) {
            case "codex" -> codexExecutor;
            case "claude" -> claudeExecutor;
            case "gemini" -> geminiExecutor;
            case "qwen" -> qwenExecutor;
            case "opencode" -> opencodeExecutor;
            case "dummy" -> dummyExecutor;
            default -> throw new IllegalArgumentException("Unknown tool: " + toolName);
        };
    }

    /**
     * Gets the response parser for the specified tool name.
     *
     * @param toolName Tool name (codex, claude, gemini)
     * @return Corresponding parser
     * @throws IllegalArgumentException if tool name is unknown
     */
    private ResponseParser getParser(String toolName) {
        return switch (toolName.toLowerCase()) {
            case "codex" -> codexParser;
            case "claude" -> claudeParser;
            case "gemini" -> geminiParser;
            case "qwen" -> qwenParser;
            // opencode --format json emits the same shape the qwen parser already handles.
            case "opencode" -> opencodeParser;
            case "dummy" -> dummyParser;
            default -> throw new IllegalArgumentException("Unknown tool: " + toolName);
        };
    }

    /**
     * Builds the fallback chain by combining requested tool with configured fallbacks.
     * <p>
     * Deduplicates while preserving order: requested tool first, then configured fallbacks.
     * </p>
     *
     * @param requestedTool Tool requested by agent configuration
     * @return Ordered list of tools to try
     */
    private java.util.List<String> buildFallbackChain(String requestedTool) {
        java.util.List<String> chain = new java.util.ArrayList<>();
        chain.add(requestedTool);

        // Add fallbacks, skipping if same as requested
        for (String fallback : fallbackTools) {
            if (!fallback.equals(requestedTool)) {
                chain.add(fallback);
            }
        }

        LOG.debugf("Built fallback chain: %s", chain);
        return chain;
    }

    /**
     * Filters out blacklisted tools from the fallback chain.
     *
     * @param toolChain Original chain of tools
     * @return Filtered list with blacklisted tools removed
     */
    java.util.List<String> filterBlacklistedTools(java.util.List<String> toolChain) {
        java.util.List<String> filtered = toolChain.stream()
            .filter(tool -> !failureCache.isToolBlacklisted(tool))
            .toList();

        if (filtered.size() < toolChain.size()) {
            java.util.List<String> skipped = new java.util.ArrayList<>(toolChain);
            skipped.removeAll(filtered);
            LOG.debugf("Filtered blacklisted tools: %s", skipped);
        }

        return filtered;
    }
}
