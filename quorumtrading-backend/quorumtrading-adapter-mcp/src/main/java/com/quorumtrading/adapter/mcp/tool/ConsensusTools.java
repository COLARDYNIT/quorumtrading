package com.quorumtrading.adapter.mcp.tool;

import com.quorumtrading.adapter.mcp.dto.McpConsensusDecisionDto;
import com.quorumtrading.adapter.mcp.dto.McpConsensusWithAgentsDto;
import com.quorumtrading.adapter.mcp.dto.McpPaginatedResponse;
import com.quorumtrading.adapter.mcp.mapper.McpDtoMapper;
import com.quorumtrading.application.usecase.GetConsensusWithAgentsUseCase;
import com.quorumtrading.application.usecase.QueryConsensusDecisionsUseCase;
import com.quorumtrading.application.usecase.command.GetConsensusWithAgentsCommand;
import com.quorumtrading.application.usecase.dto.ConsensusWithAgentsResponse;
import com.quorumtrading.application.usecase.query.QueryConsensusDecisionsQuery;
import com.quorumtrading.model.entity.ConsensusDecision;
import com.quorumtrading.model.enums.RecommendationType;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolResponse;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * MCP tools for consensus decision querying.
 * Exposes consensus with agent breakdown and decision history.
 */
@Singleton
@ActivateRequestContext
public class ConsensusTools extends McpToolBase {

    @Inject
    GetConsensusWithAgentsUseCase getConsensusWithAgentsUseCase;

    @Inject
    QueryConsensusDecisionsUseCase queryConsensusDecisionsUseCase;

    @Inject
    McpDtoMapper mapper;

    @Inject
    com.quorumtrading.adapter.mcp.auth.McpUserContext mcpUserContext;

    @Tool(name = "get_consensus_with_agents", description = "Get consensus decisions with agent breakdown for a simulation. Shows the orchestrator's final decision alongside each agent's recommendation. "
        + "finalAction and recommendedAction are structured objects (action/symbol/quantity/confidence), not JSON strings — no parsing needed; raw is present only when the LLM output could not be parsed. "
        + "By default returns compact results with no reasoning text anywhere. Use includeReasoning=true to add the orchestrator's reasoning and each agent's reasoning, each returned exactly once. "
        + "Use onlyParseFailures=true to return only consensus rounds where an agent's output could not be PARSED. "
        + "Note this covers parse errors only — an agent that errored, timed out, or was never invoked leaves no parse failure, "
        + "so an empty result here does not mean the agents were healthy. Read the consensus notes for those: "
        + "they name each non-voting agent and why.")
    public ToolResponse getConsensusWithAgents(
        @ToolArg(description = "The simulation UUID") String simulationId,
        @ToolArg(description = "Filter by ticker symbol (e.g., BTC, AAPL). Leave empty for all.") Optional<String> ticker,
        @ToolArg(description = "Filter by actions: BUY, SELL, HOLD, SKIP. Comma-separated.") Optional<String> actions,
        @ToolArg(description = "Maximum number of results (default 5, max 50)") Optional<Integer> limit,
        @ToolArg(description = "Include reasoning text (default false). When false, no reasoning is returned on the consensus or on any agent. When true, each is returned once — the orchestrator's on finalAction.reasoning, each agent's on its own reasoning field.") Optional<Boolean> includeReasoning,
        @ToolArg(description = "When true, returns only consensus rounds that had LLM parse failures (default false). Mutually exclusive with normal results.") Optional<Boolean> onlyParseFailures
    ) {
        return execute(() -> {
            int effectiveLimit = parseLimit(limit, 5, 50);
            boolean withReasoning = includeReasoning.orElse(false);
            boolean failuresOnly = onlyParseFailures.orElse(false);

            List<String> actionList = parseActions(actions);

            int fetchLimit = effectiveLimit + 1;

            GetConsensusWithAgentsCommand fetchCommand = new GetConsensusWithAgentsCommand(
                UUID.fromString(simulationId),
                ticker.orElse(null),
                actionList,
                fetchLimit,
                null,
                null,
                mcpUserContext.getUserId(),
                failuresOnly
            );

            Page<ConsensusWithAgentsResponse> page =
                paginate(getConsensusWithAgentsUseCase.execute(fetchCommand), effectiveLimit);

            List<McpConsensusWithAgentsDto> dtos = page.items().stream()
                .map(r -> mapper.toConsensusWithAgentsDto(r, withReasoning))
                .toList();

            return toJson(new McpPaginatedResponse<>(dtos, dtos.size(), page.hasMore()));
        });
    }

    @Tool(name = "query_consensus_decisions", description = "Query consensus decision history for a simulation with optional time range and action filters. Returns flat decision records without agent breakdown. "
        + "finalAction is a structured object (action/symbol/quantity/confidence), not a JSON string. Use get_consensus_with_agents for full agent detail.")
    public ToolResponse queryConsensusDecisions(
        @ToolArg(description = "The simulation UUID") String simulationId,
        @ToolArg(description = "Start of time range in ISO-8601 format") Optional<String> from,
        @ToolArg(description = "End of time range in ISO-8601 format") Optional<String> to,
        @ToolArg(description = "Filter by actions: BUY, SELL, HOLD, SKIP. Comma-separated.") Optional<String> actions,
        @ToolArg(description = "Maximum number of results (default 20, max 200)") Optional<Integer> limit,
        @ToolArg(description = "Include the orchestrator's reasoning text on finalAction.reasoning (default false).") Optional<Boolean> includeReasoning
    ) {
        return execute(() -> {
            int effectiveLimit = parseLimit(limit, 20, 200);
            boolean withReasoning = includeReasoning.orElse(false);
            int fetchLimit = effectiveLimit + 1;

            List<String> actionList = parseActions(actions);

            QueryConsensusDecisionsQuery query = new QueryConsensusDecisionsQuery(
                UUID.fromString(simulationId),
                from.map(this::parseDate).orElse(null),
                to.map(this::parseDate).orElse(null),
                null,
                null,
                actionList,
                fetchLimit,
                mcpUserContext.getUserId()
            );

            Page<ConsensusDecision> page =
                paginate(queryConsensusDecisionsUseCase.execute(query), effectiveLimit);

            List<McpConsensusDecisionDto> dtos = page.items().stream()
                .map(cd -> mapper.toConsensusDecisionDto(cd, withReasoning))
                .toList();

            McpPaginatedResponse<McpConsensusDecisionDto> response =
                new McpPaginatedResponse<>(dtos, dtos.size(), page.hasMore());
            return toJson(response);
        });
    }

    private List<String> parseActions(Optional<String> actions) {
        return actions
            .filter(a -> !a.isBlank())
            .map(a -> {
                List<String> values = List.of(a.split(","));
                // Validate each action value
                for (String action : values) {
                    parseEnum(RecommendationType.class, action.trim(), "actions");
                }
                return values;
            })
            .orElse(null);
    }
}
