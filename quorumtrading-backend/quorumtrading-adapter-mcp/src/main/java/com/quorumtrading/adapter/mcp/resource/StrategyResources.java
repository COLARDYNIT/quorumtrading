package com.quorumtrading.adapter.mcp.resource;

import com.quorumtrading.adapter.mcp.auth.McpUserContext;
import com.quorumtrading.application.usecase.GetStrategyUseCase;
import com.quorumtrading.application.usecase.query.GetStrategyQuery;
import com.quorumtrading.model.entity.Strategy;
import com.quorumtrading.model.valueobject.StrategyId;
import com.quorumtrading.model.valueobject.UserId;
import io.quarkiverse.mcp.server.ResourceResponse;
import io.quarkiverse.mcp.server.ResourceTemplate;
import io.quarkiverse.mcp.server.TextResourceContents;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * MCP resource templates for strategy content.
 * Allows LLM clients to read strategy markdown directly via resource URIs.
 */
@Singleton
public class StrategyResources {

    private static final Logger logger = LoggerFactory.getLogger(StrategyResources.class);

    @Inject
    GetStrategyUseCase getStrategyUseCase;

    @Inject
    McpUserContext mcpUserContext;

    @ResourceTemplate(uriTemplate = "quorumtrading://strategy/{strategyId}/content",
                      name = "Strategy Content",
                      description = "Full markdown content of a trading strategy including YAML frontmatter with trading rules, consensus method, and agent configuration.")
    public ResourceResponse getStrategyContent(String strategyId) {
        logger.debug("MCP resource: fetching strategy content for {}", strategyId);
        try {
            UserId userId = mcpUserContext.getUserId();
            StrategyId id = new StrategyId(UUID.fromString(strategyId));
            Strategy strategy = getStrategyUseCase.execute(new GetStrategyQuery(id, userId));

            return new ResourceResponse(
                new TextResourceContents(
                    "quorumtrading://strategy/" + strategyId + "/content",
                    strategy.getContent(),
                    "text/markdown"
                )
            );
        } catch (SecurityException e) {
            return new ResourceResponse(
                new TextResourceContents(
                    "quorumtrading://strategy/" + strategyId + "/content",
                    "Error: " + e.getMessage(),
                    "text/plain"
                )
            );
        }
    }
}
