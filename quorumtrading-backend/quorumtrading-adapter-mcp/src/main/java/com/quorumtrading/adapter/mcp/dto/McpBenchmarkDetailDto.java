package com.quorumtrading.adapter.mcp.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.List;

/**
 * Composite response for getBenchmark with both benchmark details and ranked entries.
 */
@RegisterForReflection
public record McpBenchmarkDetailDto(
    /**
     * Duplicated from {@code benchmark.uiUrl} on purpose: every other tool reports the link
     * at the top level of its response, and a client scanning for it must not have to know
     * that this one composite nests it. Omitted on headless instances (frontend disabled).
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    String uiUrl,
    McpBenchmarkDto benchmark,
    List<McpBenchmarkEntryDto> entries
) {
    public McpBenchmarkDetailDto(McpBenchmarkDto benchmark, List<McpBenchmarkEntryDto> entries) {
        this(benchmark != null ? benchmark.uiUrl() : null, benchmark, entries);
    }
}
