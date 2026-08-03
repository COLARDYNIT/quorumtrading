package com.quorumtrading.adapter.rest.dto.request;

/**
 * Request body for selecting the LLM provider.
 *
 * @param provider {@code LOCAL} or {@code OPENROUTER}
 * @param apiKey   optional OpenRouter API key to store (encrypted) with the selection
 */
public record LlmProviderSelectRequest(String provider, String apiKey) {}
