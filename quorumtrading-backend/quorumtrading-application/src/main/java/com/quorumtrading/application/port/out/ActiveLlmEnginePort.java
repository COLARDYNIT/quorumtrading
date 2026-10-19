package com.quorumtrading.application.port.out;

import com.quorumtrading.model.enums.LlmProvider;

import java.util.Optional;

/**
 * Which LLM engine will actually serve calls right now.
 *
 * <p>Model defaults are meaningless without this. A single flat default set hands
 * {@code llama3.2} to OpenRouter and {@code anthropic/claude-sonnet-5.5} to an Ollama server that
 * has never heard of it — so "the system default" is only well-defined once scoped to an engine.
 * Every consumer of {@link SystemModelDefaultsPort}'s engine-scoped lookups gets the engine from
 * here.</p>
 *
 * <p>Two deployment shapes answer this differently:</p>
 * <ul>
 *   <li><b>Runtime selection on</b> (self-host): the user's persisted choice, and nothing else —
 *       exactly the one provider the router will call.</li>
 *   <li><b>Runtime selection off</b> (hosted): derived from the static provider order, preferring
 *       a provider that is currently available.</li>
 * </ul>
 *
 * <p>Empty means the question genuinely has no answer yet — no selection has been made and no
 * configured provider is deployed. Callers must treat that as "unknown", never as a default.</p>
 */
public interface ActiveLlmEnginePort {

    /** The engine that would serve an LLM call issued right now, or empty when undetermined. */
    Optional<LlmProvider> activeEngine();
}
