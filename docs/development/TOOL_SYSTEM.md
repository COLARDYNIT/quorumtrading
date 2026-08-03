# Tool System: MCP & Chat

QuorumTrading exposes tools through two parallel systems that share the same underlying use cases. When adding a new use case that should be accessible as a tool, **both systems must be updated**.

## Architecture Overview

```
                   MCP Client (Claude Code, etc.)
                          │
              Streamable HTTP transport
              (quarkus-mcp-server-http)
                          │
              ┌───────────┴───────────┐
              │  adapter-mcp          │
              │  @Tool annotations    │
              │  snake_case names     │
              └───────────┬───────────┘
                          │
                    Use Cases (application layer)
                          │
              ┌───────────┴───────────┐
              │  ChatToolRegistry     │  ← tool definitions for LLM
              │  ChatToolExecutor     │  ← dispatch switch
              │  snake_case names     │
              └───────────┬───────────┘
                          │
                   Chat LLM (web UI)
```

## Two Systems, Same Use Cases

| Aspect | MCP Tools | Chat Tools |
|--------|-----------|------------|
| **Layer** | `adapter-mcp` | `application/chat` |
| **Name convention** | snake_case | snake_case |
| **Registration** | `@Tool(name = "...")` annotation on method | `ChatToolRegistry.allTools()` static list |
| **Dispatch** | Quarkiverse MCP auto-discovery | `ChatToolExecutor.dispatch()` switch |
| **Auth** | API token via `McpUserContext` — every tool calls `getUserId()` as its first step, including read-only global-data tools; the sole deliberate exception is `GuideTools` (static public documentation). MCP protocol handshake (`initialize`/`ping`) is pre-auth per spec. | Session-based via `UserId` |
| **Response** | `ToolResponse` (MCP envelope) | JSON string (truncated to 4000 chars) |
| **Consumer** | External MCP clients | Internal chat LLM (web assistant) |

## Files to Update When Adding a New Tool

### 1. MCP Adapter (`quorumtrading-adapter-mcp`)

**Add tool method** in the appropriate `*Tools.java` class under `adapter-mcp/src/main/java/.../tool/`:

```java
@Tool(name = "my_new_tool", description = "Does something useful.")
public ToolResponse myNewTool(
    @ToolArg(description = "The ID") String someId
) {
    return execute(() -> {
        // call use case, map to JSON
    });
}
```

Tool classes live under `adapter-mcp/src/main/java/com/quorumtrading/adapter/mcp/tool/` — one class per domain area (e.g. `StrategyTools`, `SimulationTools`, `BenchmarkTools`, `InvestmentPlanTools`, `NotificationTools`). List the directory for the current set; add a new class only if no existing domain fits.

### 2. Chat Tool Registry (`quorumtrading-application`)

**Add tool definition** in `ChatToolRegistry.allTools()`:

```java
tools.add(new ToolDefinition("my_new_tool",
    "Does something useful.",
    Map.of("someId", new ToolParameter("string", "The ID")),
    List.of("someId")));
```

File: `quorumtrading-application/src/main/java/.../chat/ChatToolRegistry.java`

### 3. Chat Tool Executor (`quorumtrading-application`)

**Add dispatch case** in `ChatToolExecutor.dispatch()`:

```java
case "my_new_tool" -> {
    // parse args, call use case, yield JSON
}
```

File: `quorumtrading-application/src/main/java/.../chat/ChatToolExecutor.java`

### 4. Follow-Up Suggestions — no action needed

Follow-up suggestion chips are generated at runtime by an LLM (`FollowUpSuggestionService` in `quorumtrading-application/src/main/java/.../chat/`), so there is no per-tool suggestion mapping to maintain.

### 5. Tests

- **MCP test:** Add test in `adapter-mcp/src/test/` for the new tool method
- **ChatToolExecutorTest:** Add test for the new dispatch case

## Naming Convention

All tool names use **snake_case** in both systems. This was standardized in Jan 2026 (commit `d12dc2b7` for MCP, subsequent commit for chat tools).

Examples: `list_strategies`, `get_simulation`, `backfill_price_data`, `get_consensus_with_agents`

## Tool Inventory

The tool inventory changes too often to list here. The sources of truth are:

- **MCP tools:** the `@Tool` annotations in the `*Tools.java` classes under `adapter-mcp/.../tool/`
- **Chat tools:** `ChatToolRegistry.allTools()` in `quorumtrading-application/.../chat/`

The two sets overlap heavily, but neither is a subset of the other. A few tools exist only in chat (e.g. `compare_simulations`, `list_public_strategies`), while whole areas are MCP-only and not exposed to the chat LLM — investment plans, notifications, model preferences / LLM configuration, and broker credential tools among them. Destructive operations (deletes, `force_end_simulation`, `liquidate_all_positions`) are available in both systems. Check the sources of truth above rather than assuming a tool exists in both.
