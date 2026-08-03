package com.quorumtrading.adapter.mcp.tool;

import com.quorumtrading.adapter.mcp.auth.McpUserContext;
import com.quorumtrading.adapter.mcp.dto.McpPaginatedResponse;
import com.quorumtrading.adapter.mcp.dto.McpPortfolioSnapshotDto;
import com.quorumtrading.adapter.mcp.mapper.McpDtoMapper;
import com.quorumtrading.application.usecase.GetSimulationUseCase;
import com.quorumtrading.application.usecase.QueryPortfolioSnapshotsUseCase;
import com.quorumtrading.application.usecase.query.GetSimulationQuery;
import com.quorumtrading.application.usecase.query.QueryPortfolioSnapshotsQuery;
import com.quorumtrading.model.entity.PortfolioSnapshot;
import com.quorumtrading.model.entity.Simulation;
import com.quorumtrading.model.valueobject.SimulationId;
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
 * MCP tools for portfolio snapshot querying.
 * Exposes portfolio history with positions and values.
 */
@Singleton
@ActivateRequestContext
public class PortfolioTools extends McpToolBase {

    @Inject
    QueryPortfolioSnapshotsUseCase queryPortfolioSnapshotsUseCase;

    @Inject
    GetSimulationUseCase getSimulationUseCase;

    @Inject
    McpUserContext mcpUserContext;

    @Inject
    McpDtoMapper mapper;

    @Tool(name = "query_portfolio_snapshots", description = "Query portfolio snapshots for a simulation over time. "
        + "Returns portfolio value, cash available, and individual position details at each snapshot timestamp. "
        + "Useful for analyzing portfolio evolution and performance charting. "
        + "Defaults to one snapshot per day ('daily'), because snapshots are written every few hours regardless "
        + "of how often the strategy is evaluated — at full resolution a default-sized page covers only a couple "
        + "of weeks of a longer run and hides the days the strategy actually traded. "
        + "Pass resolution='all' for every snapshot.")
    public ToolResponse queryPortfolioSnapshots(
        @ToolArg(description = "The simulation UUID") String simulationId,
        @ToolArg(description = "Start of time range in ISO-8601 format") Optional<String> from,
        @ToolArg(description = "End of time range in ISO-8601 format") Optional<String> to,
        @ToolArg(description = "Maximum number of results (default 20, max 200)") Optional<Integer> limit,
        @ToolArg(description = "'daily' (default) returns the last snapshot of each day; 'all' returns every snapshot",
                 required = false) Optional<String> resolution
    ) {
        return execute(() -> {
            int effectiveLimit = parseLimit(limit, 20, 200);
            int fetchLimit = effectiveLimit + 1;

            String effectiveResolution = resolution
                .filter(r -> !r.isBlank())
                .map(String::toLowerCase)
                .orElse("daily");
            if (!effectiveResolution.equals("daily") && !effectiveResolution.equals("all")) {
                throw new IllegalArgumentException(
                    "resolution must be 'daily' or 'all', got: " + effectiveResolution);
            }

            // Authorize before reading, not after: this used the overload that leaves
            // requestingUserId null, so the snapshots were fetched with the ownership check
            // skipped and only validated afterwards by the get_simulation call below.
            QueryPortfolioSnapshotsQuery query = new QueryPortfolioSnapshotsQuery(
                UUID.fromString(simulationId),
                from.map(this::parseDate).orElse(null),
                to.map(this::parseDate).orElse(null),
                fetchLimit,
                false,
                effectiveResolution,
                mcpUserContext.getUserId()
            );

            Page<PortfolioSnapshot> page =
                paginate(queryPortfolioSnapshotsUseCase.execute(query), effectiveLimit);

            List<McpPortfolioSnapshotDto> dtos = page.items().stream()
                .map(mapper::toPortfolioSnapshotDto)
                .toList();

            // Fetch simulation for initialCash context
            Simulation sim = getSimulationUseCase.execute(
                new GetSimulationQuery(new SimulationId(UUID.fromString(simulationId)), mcpUserContext.getUserId()));

            var responseMap = new java.util.LinkedHashMap<String, Object>();
            responseMap.put("simulationId", simulationId);
            responseMap.put("initialCash", sim.getInitialCash() != null ? sim.getInitialCash().amount().toPlainString() : null);
            responseMap.put("currency", sim.getInitialCash() != null ? sim.getInitialCash().currency().name() : null);
            responseMap.put("resolution", effectiveResolution);
            responseMap.put("items", dtos);
            responseMap.put("count", dtos.size());
            responseMap.put("hasMore", page.hasMore());
            return toJson(responseMap);
        });
    }

}
