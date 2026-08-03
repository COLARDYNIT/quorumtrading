package com.quorumtrading.application.usecase.llm;

import com.quorumtrading.model.enums.LlmProvider;

import java.util.Objects;

/**
 * Input-port for choosing the deployment's LLM provider at runtime (self-host edition).
 *
 * <p>Validates that the chosen provider can actually serve calls (CLI present / key usable) before
 * persisting; for OpenRouter an API key may be submitted inline and is stored encrypted. The
 * selection takes effect on the next LLM call — no restart. Implementation is a POJO wired in the
 * bootstrap {@code ApplicationConfig}.</p>
 */
public interface SelectLlmProviderUseCase {

    /**
     * Selects {@code provider} as the deployment's LLM engine.
     *
     * @param command the selection (provider + optional OpenRouter key)
     * @return the post-selection status snapshot
     * @throws IllegalArgumentException when the provider cannot serve calls right now
     */
    LlmProviderStatus select(Command command);

    /**
     * The selection request.
     *
     * @param provider         the provider to activate (non-null)
     * @param openRouterApiKey an OpenRouter API key to store (encrypted) before validation;
     *                         ignored for {@link LlmProvider#LOCAL}; may be {@code null} when a
     *                         key is already configured via environment or a previous submission
     */
    record Command(LlmProvider provider, String openRouterApiKey) {
        public Command {
            Objects.requireNonNull(provider, "provider cannot be null");
        }
    }
}
