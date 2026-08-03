package com.quorumtrading.application.port.out;

import com.quorumtrading.model.enums.LlmProvider;

import java.time.Instant;
import java.util.Optional;

/**
 * Output port for the deployment-wide LLM provider preference (self-host edition).
 *
 * <p>Backs the runtime "choose your LLM engine" flow: the selection (and, for OpenRouter, an
 * optionally user-submitted API key, encrypted at rest) is persisted so it survives restarts and
 * can be changed at any time without one. A single preference per deployment — the local edition
 * is single-user by design.</p>
 *
 * <p>Implementations must make writes immediately visible to subsequent reads (the router and the
 * OpenRouter adapter consult this port at call time).</p>
 */
public interface LlmProviderPreferencePort {

    /** The current selection, or empty when no provider has been chosen yet. */
    Optional<LlmProviderPreference> current();

    /** Persists {@code provider} as the active selection, stamping the configuration time. */
    void select(LlmProvider provider);

    /**
     * Stores the user-submitted OpenRouter API key, encrypted at rest. Saving a key does not by
     * itself select OpenRouter — call {@link #select(LlmProvider)} for that.
     */
    void saveOpenRouterApiKey(String apiKey);

    /** The decrypted user-submitted OpenRouter API key, or empty when none was ever stored. */
    Optional<String> openRouterApiKey();

    /** Removes the selection and any stored key. */
    void clear();

    /**
     * The persisted selection.
     *
     * @param provider     the chosen LLM engine
     * @param configuredAt when the choice was (last) made
     */
    record LlmProviderPreference(LlmProvider provider, Instant configuredAt) {}
}
