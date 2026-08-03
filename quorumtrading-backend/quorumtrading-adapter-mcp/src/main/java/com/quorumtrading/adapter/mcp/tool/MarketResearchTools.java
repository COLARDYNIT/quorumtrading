package com.quorumtrading.adapter.mcp.tool;

import com.quorumtrading.adapter.mcp.auth.McpUserContext;
import com.quorumtrading.adapter.mcp.dto.McpCreatedResponse;
import com.quorumtrading.adapter.mcp.dto.McpMarketResearchDetailsDto;
import com.quorumtrading.adapter.mcp.dto.McpMarketResearchDto;
import com.quorumtrading.adapter.mcp.dto.McpPaginatedResponse;
import com.quorumtrading.adapter.mcp.mapper.McpDtoMapper;
import com.quorumtrading.application.port.out.MarketResearchRepository;
import com.quorumtrading.application.port.out.ResearchExecutionRepository;
import com.quorumtrading.application.usecase.*;
import com.quorumtrading.application.usecase.command.CreateMarketResearchCommand;
import com.quorumtrading.application.usecase.dto.MarketResearchDetails;
import com.quorumtrading.application.usecase.dto.MarketResearchSummary;
import com.quorumtrading.model.entity.MarketResearch;
import com.quorumtrading.model.valueobject.BacktestConfiguration;
import com.quorumtrading.model.valueobject.ResearchInterval;
import com.quorumtrading.model.valueobject.Symbol;
import com.quorumtrading.model.valueobject.SymbolParser;
import com.quorumtrading.model.enums.Feature;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolResponse;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * MCP tools for market research management.
 * Exposes list, get, create, execute, pause, and resume operations.
 */
@Singleton
@ActivateRequestContext
public class MarketResearchTools extends McpToolBase {

    @Inject
    ListMarketResearchUseCase listMarketResearchUseCase;

    @Inject
    GetMarketResearchDetailsUseCase getMarketResearchDetailsUseCase;

    @Inject
    CreateMarketResearchUseCase createMarketResearchUseCase;

    @Inject
    ExecuteMarketResearchUseCase executeMarketResearchUseCase;

    @Inject
    PauseMarketResearchUseCase pauseMarketResearchUseCase;

    @Inject
    ResumeMarketResearchUseCase resumeMarketResearchUseCase;

    @Inject
    DeleteMarketResearchUseCase deleteMarketResearchUseCase;

    @Inject
    ResearchExecutionRepository researchExecutionRepository;

    @Inject
    MarketResearchRepository marketResearchRepository;

    @Inject
    McpUserContext mcpUserContext;

    @Inject
    McpDtoMapper mapper;

    @Inject
    com.quorumtrading.adapter.mcp.mapper.UiLinks uiLinks;

    @Tool(name = "list_market_research", description = "List all market research configurations with their status, target assets, interval, and strategy count. Returns all research entries regardless of status.")
    public ToolResponse listMarketResearch() {
        if (!isFeatureEnabled(Feature.MARKET_RESEARCH)) {
            return featureDisabled(Feature.MARKET_RESEARCH);
        }
        return execute(() -> {
            List<MarketResearchSummary> summaries = listMarketResearchUseCase.execute(mcpUserContext.getUserId());
            List<McpMarketResearchDto> dtos = summaries.stream()
                .map(summary -> {
                    UUID researchId = summary.research().getId();
                    int totalExecs = researchExecutionRepository.findByMarketResearchId(researchId).size();
                    String lastOutcome = researchExecutionRepository.findLatestByMarketResearchId(researchId)
                        .map(McpDtoMapper::resolveOutcomeDisplay)
                        .orElse(null);
                    return mapper.toMarketResearchDto(summary.research(), summary.strategyCount(), totalExecs, lastOutcome);
                })
                .toList();
            McpPaginatedResponse<McpMarketResearchDto> response = new McpPaginatedResponse<>(dtos, dtos.size(), false);
            return toJson(response);
        });
    }

    @Tool(name = "get_market_research_details", description = "Get detailed information about a specific market research including execution counts and created strategies. "
        + "By default returns a compact summary (counts + latest execution summary). "
        + "Use includeExecutions=true to get the full execution list, and includeAgentDecisions=true to include agent reasoning.")
    public ToolResponse getMarketResearchDetails(
        @ToolArg(description = "UUID of the market research") String marketResearchId,
        @ToolArg(description = "Include full execution list (default false). When false, only counts and latest execution summary are returned.") Optional<Boolean> includeExecutions,
        @ToolArg(description = "Include agent decisions with reasoning in executions (default false). Only applies when includeExecutions=true.") Optional<Boolean> includeAgentDecisions,
        @ToolArg(description = "Maximum number of executions to return (default 5, max 20). Only applies when includeExecutions=true.") Optional<Integer> executionLimit
    ) {
        if (!isFeatureEnabled(Feature.MARKET_RESEARCH)) {
            return featureDisabled(Feature.MARKET_RESEARCH);
        }
        return execute(() -> {
            UUID id = UUID.fromString(marketResearchId);
            MarketResearchDetails details = getMarketResearchDetailsUseCase.execute(id, mcpUserContext.getUserId());

            boolean withExecutions = includeExecutions.orElse(false);
            boolean withAgentDecisions = includeAgentDecisions.orElse(false);
            int execLimit = parseLimit(executionLimit, 5, 20, "executionLimit");

            McpMarketResearchDetailsDto dto = mapper.toMarketResearchDetailsDto(
                details, withExecutions, withAgentDecisions, execLimit);
            return toJson(dto);
        });
    }

    @Tool(name = "create_market_research", description = "Create a new market research configuration. The research will be created in ACTIVE status and can be executed immediately. Returns the new research ID. Call get_guide with topic 'market-research' first for instruction writing best practices and examples.")
    public ToolResponse createMarketResearch(
        @ToolArg(description = "Name for the market research") String name,
        @ToolArg(description = "Markdown instructions for the LLM research agents. Describe specific patterns, criteria, and signals to look for. Call get_guide('market-research') for examples.") String markdownInstructions,
        @ToolArg(description = "Comma-separated target assets with types, e.g. 'BTC:CRYPTO,AAPL:STOCK'. Type defaults to STOCK if omitted.") Optional<String> targetAssets,
        @ToolArg(description = "Interval amount (e.g., 7 for weekly)") int intervalAmount,
        @ToolArg(description = "Interval unit: DAYS, WEEKS, or MONTHS") String intervalUnit,
        @ToolArg(description = "Enable automatic backtesting of created strategies (default false)") Optional<Boolean> enableAutoBacktest,
        @ToolArg(description = "Backtest length in days (e.g., 90 for 3 months)") Optional<Integer> backtestLengthDays,
        @ToolArg(description = "Backtest period start in ISO-8601 format") Optional<String> backtestPeriodStart,
        @ToolArg(description = "Backtest period end in ISO-8601 format") Optional<String> backtestPeriodEnd
    ) {
        if (!isFeatureEnabled(Feature.MARKET_RESEARCH)) {
            return featureDisabled(Feature.MARKET_RESEARCH);
        }
        return execute(() -> {
            Set<Symbol> symbols = parseTargetAssets(targetAssets.orElse(null));
            ResearchInterval.Unit unit = parseEnum(ResearchInterval.Unit.class, intervalUnit, "intervalUnit");
            ResearchInterval interval = new ResearchInterval(intervalAmount, unit);
            boolean autoBacktest = enableAutoBacktest.orElse(false);

            BacktestConfiguration btConfig = null;
            if (autoBacktest && backtestLengthDays.isPresent()) {
                btConfig = new BacktestConfiguration(
                    Duration.ofDays(backtestLengthDays.get()),
                    backtestPeriodStart.map(this::parseDate).orElse(null),
                    backtestPeriodEnd.map(this::parseDate).orElse(null)
                );
            }

            CreateMarketResearchCommand command = new CreateMarketResearchCommand(
                name,
                markdownInstructions,
                symbols,
                interval,
                autoBacktest,
                btConfig,
                mcpUserContext.getUserId()
            );

            MarketResearch created = createMarketResearchUseCase.execute(command);
            logger.info("MCP: Created market research {} (id={})", name, created.getId());
            String targetAssetsStr = symbols.isEmpty() ? "none" : symbols.stream()
                .map(s -> s.ticker() + ":" + s.type().name())
                .collect(Collectors.joining(", "));
            String message = String.format("Market research '%s' created — targets: [%s], interval: %d %s, autoBacktest: %s",
                name, targetAssetsStr, intervalAmount, intervalUnit, autoBacktest);
            return toJson(new McpCreatedResponse(created.getId().toString(), message,
                uiLinks.marketResearch(created.getId().toString())));
        });
    }

    @Tool(name = "execute_market_research", description = "Trigger an immediate execution of a market research. The research must be in ACTIVE status. " +
        "Execution runs asynchronously in the background — you will NOT be notified when it completes. " +
        "Tell the user to ask you to check results later using get_market_research_details.")
    public ToolResponse executeMarketResearch(
        @ToolArg(description = "UUID of the market research to execute") String marketResearchId
    ) {
        if (!isFeatureEnabled(Feature.MARKET_RESEARCH)) {
            return featureDisabled(Feature.MARKET_RESEARCH);
        }
        return execute(() -> {
            UUID id = UUID.fromString(marketResearchId);
            MarketResearch research = marketResearchRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Market research not found: " + marketResearchId));
            UUID executionId = executeMarketResearchUseCase.execute(id, mcpUserContext.getUserId());
            logger.info("MCP: Triggered execution of market research {} (executionId={})", marketResearchId, executionId);
            // The id is the execution's, but executions have no page of their own — the
            // research page is where this run shows up.
            return toJson(new McpCreatedResponse(executionId.toString(),
                "Market research '" + research.getName() + "' execution started",
                uiLinks.marketResearch(marketResearchId)));
        });
    }

    @Tool(name = "pause_market_research", description = "Pause an active market research. Paused research will not execute on schedule until resumed.")
    public ToolResponse pauseMarketResearch(
        @ToolArg(description = "UUID of the market research to pause") String marketResearchId
    ) {
        if (!isFeatureEnabled(Feature.MARKET_RESEARCH)) {
            return featureDisabled(Feature.MARKET_RESEARCH);
        }
        return execute(() -> {
            UUID id = UUID.fromString(marketResearchId);
            MarketResearch research = marketResearchRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Market research not found: " + marketResearchId));
            pauseMarketResearchUseCase.execute(id, mcpUserContext.getUserId());
            logger.info("MCP: Paused market research {}", marketResearchId);
            return toJson(new McpCreatedResponse(marketResearchId, "Market research '" + research.getName() + "' paused",
                uiLinks.marketResearch(marketResearchId)));
        });
    }

    @Tool(name = "resume_market_research", description = "Resume a paused market research. The research will return to ACTIVE status and resume scheduled executions.")
    public ToolResponse resumeMarketResearch(
        @ToolArg(description = "UUID of the market research to resume") String marketResearchId
    ) {
        if (!isFeatureEnabled(Feature.MARKET_RESEARCH)) {
            return featureDisabled(Feature.MARKET_RESEARCH);
        }
        return execute(() -> {
            UUID id = UUID.fromString(marketResearchId);
            MarketResearch research = marketResearchRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Market research not found: " + marketResearchId));
            resumeMarketResearchUseCase.execute(id, mcpUserContext.getUserId());
            logger.info("MCP: Resumed market research {}", marketResearchId);
            return toJson(new McpCreatedResponse(marketResearchId, "Market research '" + research.getName() + "' resumed",
                uiLinks.marketResearch(marketResearchId)));
        });
    }

    @Tool(name = "delete_market_research", description = "Delete a market research configuration and all its execution history and agent decisions. Strategies created by the research are kept but unlinked. This action is permanent.")
    public ToolResponse deleteMarketResearch(
        @ToolArg(description = "UUID of the market research to delete") String marketResearchId
    ) {
        if (!isFeatureEnabled(Feature.MARKET_RESEARCH)) {
            return featureDisabled(Feature.MARKET_RESEARCH);
        }
        return execute(() -> {
            UUID id = UUID.fromString(marketResearchId);
            deleteMarketResearchUseCase.execute(id, mcpUserContext.getUserId());
            logger.info("MCP: Deleted market research {}", marketResearchId);
            return toJson(new McpCreatedResponse(marketResearchId, "Market research and all associated data deleted"));
        });
    }

    private Set<Symbol> parseTargetAssets(String targetAssets) {
        if (targetAssets == null || targetAssets.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(targetAssets.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            // Shared "TICKER[:TYPE]" convention (also used by the REST endpoint):
            // the colon delimits the optional asset TYPE ("AAPL:STOCK"), NOT a MIC,
            // and a bare ticker defaults to STOCK. The ticker root may still be a
            // dotted EU venue alias ("RHM.DE"); SymbolParser enriches that while
            // leaving a bare US ticker byte-identical (US/USD default).
            .map(SymbolParser::parseWithTypeSuffix)
            .collect(Collectors.toSet());
    }
}
