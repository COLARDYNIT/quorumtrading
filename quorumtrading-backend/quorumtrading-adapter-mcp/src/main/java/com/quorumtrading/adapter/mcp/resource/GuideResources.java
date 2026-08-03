package com.quorumtrading.adapter.mcp.resource;

import io.quarkiverse.mcp.server.ResourceResponse;
import io.quarkiverse.mcp.server.ResourceTemplate;
import io.quarkiverse.mcp.server.TextResourceContents;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MCP resource templates providing educational guides about the QuorumTrading platform,
 * trading concepts, and broker setup. Content is shared with {@link GuideContent}.
 * <p>
 * Resources are an alternative access path for MCP clients that support the resources protocol.
 * Most LLM clients prefer the {@code get_guide} tool instead.
 */
@Singleton
public class GuideResources {

    private static final Logger logger = LoggerFactory.getLogger(GuideResources.class);

    @ResourceTemplate(
        uriTemplate = "quorumtrading://guides/platform-overview",
        name = "Platform Overview",
        description = "How QuorumTrading works: strategies, simulations, AI agents, consensus methods, market research, and portfolio management."
    )
    public ResourceResponse getPlatformOverview() {
        logger.debug("MCP resource: fetching platform overview guide");
        return new ResourceResponse(new TextResourceContents(
            "quorumtrading://guides/platform-overview",
            GuideContent.GUIDES.get("platform-overview"),
            "text/markdown"
        ));
    }

    @ResourceTemplate(
        uriTemplate = "quorumtrading://guides/trading-concepts",
        name = "Trading Concepts",
        description = "Quantitative analysis fundamentals: technical indicators, market hours, order types, backtesting pitfalls, and risk management."
    )
    public ResourceResponse getTradingConcepts() {
        logger.debug("MCP resource: fetching trading concepts guide");
        return new ResourceResponse(new TextResourceContents(
            "quorumtrading://guides/trading-concepts",
            GuideContent.GUIDES.get("trading-concepts"),
            "text/markdown"
        ));
    }

    @ResourceTemplate(
        uriTemplate = "quorumtrading://guides/alpaca-setup",
        name = "Alpaca Broker Setup",
        description = "Step-by-step guide for setting up an Alpaca broker account with QuorumTrading."
    )
    public ResourceResponse getAlpacaSetup() {
        logger.debug("MCP resource: fetching Alpaca setup guide");
        return new ResourceResponse(new TextResourceContents(
            "quorumtrading://guides/alpaca-setup",
            GuideContent.GUIDES.get("alpaca-setup"),
            "text/markdown"
        ));
    }

    @ResourceTemplate(
        uriTemplate = "quorumtrading://guides/strategy-format",
        name = "Strategy Format Reference",
        description = "Complete reference for strategy YAML frontmatter fields, trading modes, and markdown body structure. Read this before calling create_strategy."
    )
    public ResourceResponse getStrategyFormat() {
        logger.debug("MCP resource: fetching strategy format guide");
        return new ResourceResponse(new TextResourceContents(
            "quorumtrading://guides/strategy-format",
            GuideContent.GUIDES.get("strategy-format"),
            "text/markdown"
        ));
    }

    @ResourceTemplate(
        uriTemplate = "quorumtrading://guides/market-research-instructions",
        name = "Market Research Instructions Guide",
        description = "How to write effective market research instructions with examples and best practices. Read this before calling create_market_research."
    )
    public ResourceResponse getMarketResearchInstructions() {
        logger.debug("MCP resource: fetching market research instructions guide");
        return new ResourceResponse(new TextResourceContents(
            "quorumtrading://guides/market-research-instructions",
            GuideContent.GUIDES.get("market-research"),
            "text/markdown"
        ));
    }

    @ResourceTemplate(
        uriTemplate = "quorumtrading://guides/strategy-cheatsheet",
        name = "Strategy Cheatsheet",
        description = "Quick reference for strategy frontmatter fields, schedule syntax, consensus methods, and agent guidance."
    )
    public ResourceResponse getStrategyCheatsheet() {
        logger.debug("MCP resource: fetching strategy cheatsheet guide");
        return new ResourceResponse(new TextResourceContents(
            "quorumtrading://guides/strategy-cheatsheet",
            GuideContent.GUIDES.get("strategy-cheatsheet"),
            "text/markdown"
        ));
    }
}
