package com.quorumtrading.application.usecase.llm;

/**
 * Input-port for reading the deployment's LLM provider selection state.
 *
 * <p>Drives the setup page, the JSON status endpoint, the {@code get_llm_status} MCP tool, and the
 * boot-banner nudge. Implementation is a POJO wired in the bootstrap {@code ApplicationConfig}.</p>
 */
public interface GetLlmProviderStatusUseCase {

    /** The current selection plus per-provider availability. */
    LlmProviderStatus get();
}
