package com.quorumtrading.adapter.mcp.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.List;

/**
 * Generic paginated response wrapper for MCP tool output.
 *
 * @param items the items in the current page
 * @param count number of items returned
 * @param hasMore whether more items exist beyond the limit
 */
@RegisterForReflection
public record McpPaginatedResponse<T>(
    List<T> items,
    int count,
    boolean hasMore
) {}
