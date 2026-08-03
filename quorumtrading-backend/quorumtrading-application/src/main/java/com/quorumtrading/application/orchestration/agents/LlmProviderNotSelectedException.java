package com.quorumtrading.application.orchestration.agents;

/**
 * Thrown when an LLM call is attempted but no LLM provider has been selected yet
 * (self-host edition, {@code quorumtrading.llm.selection.enabled=true}).
 *
 * <p>Extends {@link LLMQueryException} so existing failure handling degrades gracefully, while
 * edges (REST, MCP) can recognise the subtype and surface the actionable message — which should
 * always tell the user <em>how</em> to choose (setup URL / MCP tool) and what is available.</p>
 */
public class LlmProviderNotSelectedException extends LLMQueryException {

    public LlmProviderNotSelectedException(String message) {
        super(message);
    }
}
