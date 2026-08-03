package com.quorumtrading.adapter.mcp.tool;

import com.quorumtrading.adapter.mcp.resource.GuideContent;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolResponse;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Singleton;

import java.util.Optional;

/**
 * MCP tool for serving platform guides. A single {@code get_guide} tool with a topic parameter
 * replaces the need for the LLM to read MCP resources (which many clients don't support well).
 *
 * <p><b>Deliberately tokenless:</b> guides are static public documentation (no user data, no DB
 * access, no side effects), so this is the only tool class that does not gate on
 * {@code McpUserContext}. Every other tool requires a valid API token.</p>
 */
@Singleton
@ActivateRequestContext
public class GuideTools extends McpToolBase {

    @Tool(name = "get_guide",
        description = "Get a guide, tutorial, or how-to about the QuorumTrading platform. "
            + "Use this when the user asks for help, explanations, getting started, onboarding, "
            + "how something works, or what a concept means. "
            + "Available topics: "
            + "platform-overview (core concepts, agents, consensus, strategies, simulations, "
            + "benchmarks, and the 'Reading Your Results' question-to-tool table), "
            + "trading-concepts (RSI, MACD, SMA, Bollinger, order types, risk management, backtesting), "
            + "backtesting (the run lifecycle: creating a simulation starts it, state machine, why "
            + "LIVE is not about money, polling, what to check before trusting a result), "
            + "cost-and-models (pricing a run before you commit, why a free engine costs time not "
            + "money, estimate vs actual, which model runs your agents), "
            + "investment-plans (recurring DCA plans, budget modes, the picker, why automatic "
            + "execution is off by default, allocation rows, cleanup), "
            + "benchmarks-and-challenges (comparing against buy-and-hold; challenge limits on self-host), "
            + "alpaca-setup (broker account setup, paper trading, live trading), "
            + "market-data (price sources, which assets need an API key, ticker forms, warm-up history), "
            + "strategy-format (YAML frontmatter, trading modes, markdown body), "
            + "market-research (writing effective research instructions; needs the market-research feature flag), "
            + "strategy-cheatsheet (quick reference for frontmatter fields, schedules, agents), "
            + "feature-flags (why a feature is switched off on this instance and how to switch it on). "
            + "Omit topic to list all available guides.")
    public ToolResponse getGuide(
        @ToolArg(description = "Guide topic: platform-overview, trading-concepts, backtesting, "
            + "cost-and-models, investment-plans, benchmarks-and-challenges, alpaca-setup, "
            + "market-data, strategy-format, market-research, strategy-cheatsheet, or "
            + "feature-flags. "
            + "Omit to list available guides.")
        Optional<String> topic
    ) {
        return execute(() -> {
            if (topic.isEmpty() || topic.get().isBlank()) {
                return buildTopicList();
            }

            String slug = topic.get().strip().toLowerCase();
            String content = GuideContent.GUIDES.get(slug);

            if (content == null) {
                logger.warn("MCP: Unknown guide topic '{}', returning topic list", slug);
                return "Unknown topic: '" + slug + "'\n\n" + buildTopicList();
            }

            logger.info("MCP: Served guide '{}'", slug);
            return content;
        });
    }

    private String buildTopicList() {
        var sb = new StringBuilder("# Available Guides\n\n");
        sb.append("Call `get_guide` with one of these topics:\n\n");
        for (var entry : GuideContent.DESCRIPTIONS.entrySet()) {
            sb.append("- **").append(entry.getKey()).append("** — ").append(entry.getValue()).append('\n');
        }
        return sb.toString();
    }
}
