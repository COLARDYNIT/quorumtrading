package com.quorumtrading.application.usecase;

import com.quorumtrading.application.port.out.ActiveLlmEnginePort;
import com.quorumtrading.model.enums.LlmProvider;

import java.util.List;

/**
 * What "fallback" actually means on the engine that is selected.
 *
 * <p>The settings page rendered one engine's model chain as if it were universal — mistral,
 * llama-4-scout, cohere, deepseek — on a deployment running Ollama, where not one of them could be
 * served. Engine-blind, and describing hosted behaviour to a self-hoster.</p>
 *
 * <p>Fallback is not one concept. On OpenRouter it is a cross-<em>model</em> chain; on the CLI
 * engine it is a cross-<em>tool</em> chain, and only tools that are actually installed; on a local
 * HTTP server there is no chain at all, only a substitution rule. Saying so plainly beats showing a
 * list that happens to be true in one deployment shape.</p>
 *
 * <p>Plain POJO wired in {@code ApplicationConfig}, per the CDI convention.</p>
 */
public class DescribeFallbackBehaviourUseCase {

    private final ActiveLlmEnginePort activeLlmEnginePort;
    private final List<String> openRouterModelChain;
    private final InstalledToolsSupplier installedTools;

    /** Which local CLI tools are installed right now — a chain entry is not evidence of one. */
    @FunctionalInterface
    public interface InstalledToolsSupplier {
        List<String> installedTools();
    }

    public DescribeFallbackBehaviourUseCase(ActiveLlmEnginePort activeLlmEnginePort,
                                            List<String> openRouterModelChain,
                                            InstalledToolsSupplier installedTools) {
        this.activeLlmEnginePort = activeLlmEnginePort;
        this.openRouterModelChain = openRouterModelChain == null ? List.of() : List.copyOf(openRouterModelChain);
        this.installedTools = installedTools == null ? List::of : installedTools;
    }

    public FallbackBehaviour execute() {
        LlmProvider engine = activeEngine();
        if (engine == null) {
            return new FallbackBehaviour(null, Kind.NONE, List.of(),
                    "No LLM engine is selected yet, so there is nothing to fall back to.",
                    false);
        }

        return switch (engine) {
            case OPENROUTER -> new FallbackBehaviour(engine, Kind.MODEL_CHAIN, openRouterModelChain,
                    "If the chosen model fails, OpenRouter is retried with each of these models in turn.",
                    false);
            case LOCAL -> new FallbackBehaviour(engine, Kind.TOOL_CHAIN, installedTools.installedTools(),
                    "If a CLI tool is missing or fails, the next INSTALLED tool is tried. This is a "
                    + "tool chain, not a model chain — the model you chose is used on whichever tool runs.",
                    false);
            case LOCAL_HTTP -> new FallbackBehaviour(engine, Kind.SERVER_DEFAULT, List.of(),
                    "There is no fallback chain. If the server does not advertise the chosen model, "
                    + "a system default falls back to the server's first advertised model, while an "
                    + "explicit pin or preference fails rather than being substituted.",
                    false);
        };
    }

    private LlmProvider activeEngine() {
        try {
            return activeLlmEnginePort.activeEngine().orElse(null);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** The shape of the fallback on this engine. */
    public enum Kind {
        /** Retry the same engine with other models. */
        MODEL_CHAIN,
        /** Retry the same model on another local tool. */
        TOOL_CHAIN,
        /** No chain; the server's own default substitutes for system defaults only. */
        SERVER_DEFAULT,
        /** Nothing to describe. */
        NONE
    }

    /**
     * @param engine             the selected engine, null when none is
     * @param kind               what kind of fallback this engine has
     * @param entries            the chain, in order; empty when the engine has none
     * @param explanation        one sentence a user can act on
     * @param crossEngineFailover whether the platform would fall through to a DIFFERENT engine.
     *                            Always false with runtime selection on: the router returns exactly
     *                            one provider by design, and a page implying otherwise is describing
     *                            hosted behaviour to a self-hoster.
     */
    public record FallbackBehaviour(
            LlmProvider engine,
            Kind kind,
            List<String> entries,
            String explanation,
            boolean crossEngineFailover) {

        public FallbackBehaviour {
            entries = entries == null ? List.of() : List.copyOf(entries);
        }
    }
}
