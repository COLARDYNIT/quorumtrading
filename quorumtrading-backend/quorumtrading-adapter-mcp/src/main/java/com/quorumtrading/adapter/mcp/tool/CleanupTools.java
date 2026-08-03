package com.quorumtrading.adapter.mcp.tool;

import com.quorumtrading.adapter.mcp.auth.McpUserContext;
import com.quorumtrading.application.usecase.PruneUnusedUseCase;
import com.quorumtrading.application.usecase.command.PruneUnusedCommand;
import com.quorumtrading.application.usecase.result.PruneUnusedResult;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolResponse;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * MCP tools for cleanup operations.
 * Exposes pruning of unused strategies and research.
 */
@Singleton
@ActivateRequestContext
public class CleanupTools extends McpToolBase {

    @Inject
    PruneUnusedUseCase pruneUnusedUseCase;

    @Inject
    McpUserContext mcpUserContext;

    @Tool(name = "prune_unused", description =
            "PREVIEW BY DEFAULT. Lists the unused strategies (0 simulations) and unused research "
                    + "(0 created strategies) that would be removed, and removes nothing. Pass "
                    + "confirm=true to actually delete them — deletion is irreversible. Note that "
                    + "\"unused\" means zero simulations, which every strategy is until its first "
                    + "backtest, so a confirmed run also deletes drafts you have written but not "
                    + "yet backtested. Read the preview before confirming.")
    public ToolResponse pruneUnused(
            @ToolArg(description = "Optional: include strategies with no simulations (default true)", required = false) Boolean removeStrategies,
            @ToolArg(description = "Optional: include research with no created strategies (default true)", required = false) Boolean removeResearch,
            @ToolArg(description = "Set true to actually delete. Omit or false to preview only.", required = false) Boolean confirm) {
        return execute(() -> {
            boolean strategies = removeStrategies == null || removeStrategies;
            boolean research = removeResearch == null || removeResearch;
            // Preview unless explicitly confirmed. This tool previously deleted on a no-argument
            // call, with both flags defaulting to true and no dry run — the most destructive
            // possible reading of the emptiest possible request. force_end_simulation already
            // requires its caller to state liquidatePositions; this follows that precedent.
            boolean dryRun = confirm == null || !confirm;

            PruneUnusedResult result = pruneUnusedUseCase.execute(
                new PruneUnusedCommand(mcpUserContext.getUserId(), strategies, research, dryRun));

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("dryRun", result.dryRun());
            body.put(result.dryRun() ? "wouldDeleteStrategies" : "deletedStrategies", result.deletedStrategies());
            body.put(result.dryRun() ? "wouldDeleteResearch" : "deletedResearch", result.deletedResearch());
            body.put("strategyNames", result.strategyNames());
            body.put("researchNames", result.researchNames());
            body.put("message", result.dryRun()
                    ? "Preview only — nothing was deleted. " + result.deletedStrategies()
                        + " unused strategies and " + result.deletedResearch() + " unused research"
                        + " would be removed. Check the names above, then call again with"
                        + " confirm=true if that is what you want."
                    : "Deleted " + result.deletedStrategies() + " unused strategies and "
                        + result.deletedResearch() + " unused research. This cannot be undone.");
            return toJson(body);
        });
    }
}
