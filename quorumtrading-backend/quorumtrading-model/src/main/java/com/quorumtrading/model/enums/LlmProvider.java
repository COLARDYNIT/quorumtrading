package com.quorumtrading.model.enums;

/**
 * The user-selectable LLM engine powering AI consensus.
 *
 * <p>In the self-host (local) edition the user must explicitly choose one at runtime — see
 * {@code quorumtrading.llm.selection.enabled}. In the hosted SaaS the platform routes via the
 * static provider-order config and this enum is unused at runtime.</p>
 */
public enum LlmProvider {

    /**
     * Local CLI tooling on the host machine (the {@code claude} CLI and friends).
     * No API key, no per-call cost — requires a supported CLI on the PATH.
     */
    LOCAL,

    /**
     * A local OpenAI-compatible HTTP model server the user runs themselves — Ollama, LM Studio,
     * llama.cpp, vLLM, LocalAI, etc. No API key, no per-call cost; reachable over HTTP (so, unlike
     * {@link #LOCAL}, it works from inside the Docker container via {@code host.docker.internal}).
     */
    LOCAL_HTTP,

    /** Hosted models through the user's own OpenRouter API key. Usage billed to their account. */
    OPENROUTER
}
