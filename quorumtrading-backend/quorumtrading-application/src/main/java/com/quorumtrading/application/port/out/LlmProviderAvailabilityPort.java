package com.quorumtrading.application.port.out;

import com.quorumtrading.model.enums.LlmProvider;

/**
 * Output port answering "can this LLM provider actually serve calls on this deployment right now?"
 *
 * <p>For {@link LlmProvider#LOCAL} that means a supported CLI tool is installed on the host; for
 * {@link LlmProvider#OPENROUTER} that a usable API key is configured (env or stored). Implemented
 * by the LLM router adapter, which already discovers the concrete provider beans.</p>
 */
public interface LlmProviderAvailabilityPort {

    /** Whether {@code provider} is deployed, enabled, and able to serve a call right now. */
    boolean isAvailable(LlmProvider provider);
}
