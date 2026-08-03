package com.quorumtrading.adapter.mcp.tool;

import com.quorumtrading.adapter.mcp.auth.McpUserContext;
import com.quorumtrading.adapter.mcp.dto.McpCreatedResponse;
import com.quorumtrading.adapter.mcp.dto.McpPaginatedResponse;
import com.quorumtrading.adapter.mcp.dto.McpStrategyDto;
import com.quorumtrading.adapter.mcp.mapper.McpDtoMapper;
import com.quorumtrading.application.port.out.SimulationRepository;
import com.quorumtrading.application.usecase.*;
import com.quorumtrading.application.usecase.command.CreateStrategyCommand;
import com.quorumtrading.application.usecase.command.DeleteStrategyCommand;
import com.quorumtrading.application.usecase.command.UpdateStrategyCommand;
import com.quorumtrading.application.usecase.query.CalculateStrategyPerformanceQuery;
import com.quorumtrading.application.usecase.query.FindTickerQuery;
import com.quorumtrading.application.usecase.query.GetDividendInfoQuery;
import com.quorumtrading.application.usecase.query.GetStrategyQuery;
import com.quorumtrading.application.usecase.query.ListStrategiesQuery;
import com.quorumtrading.model.valueobject.BrokerAccountId;
import com.quorumtrading.model.valueobject.DividendInfo;
import com.quorumtrading.model.valueobject.TickerSearchResult;
import com.quorumtrading.model.entity.Strategy;
import com.quorumtrading.model.valueobject.StrategyId;
import com.quorumtrading.model.valueobject.StrategyPerformance;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolResponse;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * MCP tools for strategy management.
 * Exposes list, get, performance, and create operations.
 */
@Singleton
@ActivateRequestContext
public class StrategyTools extends McpToolBase {

    @Inject
    ListStrategiesUseCase listStrategiesUseCase;

    @Inject
    GetStrategyUseCase getStrategyUseCase;

    @Inject
    CalculateStrategyPerformanceUseCase calculateStrategyPerformanceUseCase;

    @Inject
    CreateStrategyUseCase createStrategyUseCase;

    @Inject
    DeleteStrategyUseCase deleteStrategyUseCase;

    @Inject
    UpdateStrategyUseCase updateStrategyUseCase;

    @Inject
    FindTickerUseCase findTickerUseCase;

    @Inject
    GetDividendInfoUseCase getDividendInfoUseCase;

    @Inject
    McpUserContext mcpUserContext;

    @Inject
    SimulationRepository simulationRepository;

    @Inject
    McpDtoMapper mapper;

    @Inject
    com.quorumtrading.adapter.mcp.mapper.UiLinks uiLinks;

    @Tool(name = "list_strategies", description = "List trading strategies with trading mode, "
        + "tickers, and simulation count. Does not include full markdown content — use get_strategy "
        + "for that. On a library with many strategies, filter with nameContains rather than raising "
        + "limit: a large page can exceed the MCP result size, and there is no cursor to page past "
        + "hasMore.")
    public ToolResponse listStrategies(
        @ToolArg(description = "Maximum number of results (default 20, max 100)") Optional<Integer> limit,
        @ToolArg(description = "Case-insensitive substring of the strategy name, e.g. 'BTC' or a "
            + "prefix you named your own work with. Omit to list everything.") Optional<String> nameContains
    ) {
        return execute(() -> {
            int effectiveLimit = parseLimit(limit, 20, 100);
            int fetchLimit = effectiveLimit + 1;

            // The use case has always supported a case-insensitive name filter; only the MCP tool
            // never exposed it. Without it, an agent on a library of 100+ strategies could not find
            // its own: limit=100 exceeded the MCP result size outright, and every smaller page came
            // back hasMore:true with no cursor to continue from.
            Optional<String> nameFilter = nameContains.filter(s -> !s.isBlank()).map(String::trim);
            ListStrategiesQuery query = new ListStrategiesQuery(nameFilter, Optional.of(fetchLimit), mcpUserContext.getUserId());
            Page<Strategy> page = paginate(listStrategiesUseCase.execute(query), effectiveLimit);

            List<McpStrategyDto> dtos = page.items().stream()
                .map(s -> {
                    int simCount = simulationRepository.findByStrategyId(s.getId()).size();
                    return mapper.toStrategyDto(s, false, simCount);
                })
                .toList();

            McpPaginatedResponse<McpStrategyDto> response = new McpPaginatedResponse<>(dtos, dtos.size(), page.hasMore());
            return toJson(response);
        });
    }

    @Tool(name = "get_strategy", description = "Get a specific strategy by ID, including full markdown content with trading rules and agent configuration.")
    public ToolResponse getStrategy(
        @ToolArg(description = "The strategy UUID") String strategyId
    ) {
        return execute(() -> {
            StrategyId id = new StrategyId(UUID.fromString(strategyId));
            Strategy strategy = getStrategyUseCase.execute(new GetStrategyQuery(id, mcpUserContext.getUserId()));
            return toJson(mapper.toStrategyDto(strategy));
        });
    }

    @Tool(name = "get_strategy_performance", description = "Get performance metrics for a strategy across all its simulations. Returns average return, win rate, Sharpe ratio, and recent simulation return percentages (newest first).")
    public ToolResponse getStrategyPerformance(
        @ToolArg(description = "The strategy UUID") String strategyId
    ) {
        return execute(() -> {
            StrategyId id = new StrategyId(UUID.fromString(strategyId));
            String name = getStrategyUseCase.execute(new GetStrategyQuery(id, mcpUserContext.getUserId())).getName();
            StrategyPerformance perf = calculateStrategyPerformanceUseCase.execute(
                new CalculateStrategyPerformanceQuery(id, mcpUserContext.getUserId())
            );
            return toJson(mapper.toStrategyPerformanceDto(id, name, perf));
        });
    }

    @Tool(name = "create_strategy", description = "Create a new trading strategy with markdown content. The content must include YAML frontmatter (---...---) defining consensus method, live_schedule, backtest_schedule, and agent configuration. Call get_guide with topic 'strategy-format' first for the full field reference and examples. SIZING RULE worth knowing before you write one: a BUY whose consensus carries no explicit quantity fills straight to the tightest cap — position_limits × portfolio value if the ticker has an entry, otherwise all cash above reserve_cash_percentage. Markdown prose like 'enter in tranches' does not size orders; for partial entries the strategy must make the agents state a numeric quantity, or the first BUY consumes the entire limit.")
    public ToolResponse createStrategy(
        @ToolArg(description = "Display name for the strategy") String name,
        @ToolArg(description = "Full markdown content with YAML frontmatter defining trading rules. Call get_guide('strategy-format') for all fields and examples.") String markdownContent,
        @ToolArg(description = "Optional human-readable description for the strategy library (max 500 chars)") Optional<String> description
    ) {
        return execute(() -> {
            CreateStrategyCommand command = new CreateStrategyCommand(name, description.orElse(null), markdownContent, mcpUserContext.getUserId());
            StrategyId id = createStrategyUseCase.execute(command);
            logger.info("MCP: Created strategy {}", id.value());

            String message = String.format("Strategy '%s' created successfully", name);

            // Append dividend note if any tickers pay dividends
            String dividendNote = buildDividendNoteForStrategy(id);
            if (dividendNote != null) {
                message = message + ". " + dividendNote;
            }

            return toJson(new McpCreatedResponse(id.value().toString(), message,
                uiLinks.strategy(id.value().toString())));
        });
    }

    private String buildDividendNoteForStrategy(StrategyId strategyId) {
        try {
            Strategy strategy = getStrategyUseCase.execute(new GetStrategyQuery(strategyId, mcpUserContext.getUserId()));
            List<String> symbols = mapper.extractSymbols(strategy);
            if (symbols.isEmpty()) {
                return null;
            }
            Map<String, DividendInfo> dividendMap = getDividendInfoUseCase.execute(new GetDividendInfoQuery(symbols));
            List<String> dividendTickers = new ArrayList<>();
            for (Map.Entry<String, DividendInfo> entry : dividendMap.entrySet()) {
                DividendInfo info = entry.getValue();
                if (info.paysDividendsKnown()) {
                    String yield = info.annualYieldEstimate() != null
                        ? String.format("~$%s/share", info.annualYieldEstimate().toPlainString())
                        : "yield unknown";
                    dividendTickers.add(String.format("%s (%s, %d payments/year)", info.ticker(), yield, info.dividendCountLastYear()));
                }
            }
            if (dividendTickers.isEmpty()) {
                return null;
            }
            return String.format("Dividend note: %s pay dividends. Backtest results will reflect price returns only — dividend income is not tracked",
                String.join(", ", dividendTickers));
        } catch (Exception e) {
            logger.debug("Failed to build dividend note for strategy: {}", e.getMessage());
            return null;
        }
    }

    @Tool(name = "delete_strategy", description = "Delete a strategy and all associated simulations, decisions, and snapshots. "
        + "This cascade is permanent and cannot be undone. Rejected while any simulation on the strategy is still running "
        + "(status SIMULATING or LIVE and not paused) — pause it, force-end it, or let it finish first. Paused and finished "
        + "simulations are deleted along with the strategy.")
    public ToolResponse deleteStrategy(
        @ToolArg(description = "The strategy UUID to delete") String strategyId
    ) {
        return execute(() -> {
            StrategyId id = new StrategyId(UUID.fromString(strategyId));
            deleteStrategyUseCase.execute(new DeleteStrategyCommand(id, mcpUserContext.getUserId()));
            logger.info("MCP: Deleted strategy {}", id.value());
            return toJson(new McpCreatedResponse(strategyId, "Strategy and all associated data deleted"));
        });
    }

    @Tool(name = "update_strategy", description = "Update an existing trading strategy's name and/or markdown content. "
        + "At least one of 'name' or 'markdownContent' must be provided. "
        + "If the strategy has simulations, tickers cannot be changed (only position limits). "
        + "Content must include YAML frontmatter (---...---). Call get_guide('strategy-format') for field reference.")
    public ToolResponse updateStrategy(
        @ToolArg(description = "The strategy UUID to update") String strategyId,
        @ToolArg(description = "New display name for the strategy (optional, omit to keep current name)") Optional<String> name,
        @ToolArg(description = "New human-readable description (optional, omit to keep current)") Optional<String> description,
        @ToolArg(description = "New full markdown content with YAML frontmatter (optional, omit to keep current content)") Optional<String> markdownContent
    ) {
        return execute(() -> {
            StrategyId id = new StrategyId(UUID.fromString(strategyId));
            UpdateStrategyCommand command = new UpdateStrategyCommand(
                id,
                name.orElse(null),
                description.orElse(null),
                markdownContent.orElse(null),
                mcpUserContext.getUserId()
            );
            updateStrategyUseCase.execute(command);

            Strategy updated = getStrategyUseCase.execute(new GetStrategyQuery(id, mcpUserContext.getUserId()));
            logger.info("MCP: Updated strategy {} (name={}, contentChanged={})",
                id.value(), name.isPresent(), markdownContent.isPresent());
            return toJson(mapper.toStrategyDto(updated));
        });
    }

    @Tool(name = "find_ticker", description = """
        Resolve a company or asset name to a tradable ticker, verified against the broker.

        Branch on `resolution`, not on `tradableOnBroker`:
        - RESOLVED — identified and tradable. `ticker` is safe to use in a strategy.
        - TRADABLE_ELSEWHERE — the company was identified but this broker cannot reach its venue \
        (e.g. a European listing on a US-only broker). A statement about the broker, not the company.
        - AMBIGUOUS — a candidate symbol was found but the broker's record for it names a DIFFERENT \
        company, so it was withheld. `ticker` is null on purpose; check `alternatives` for what was \
        rejected and why. Do not work around this by passing the rejected symbol on.
        - NOT_LISTED — genuinely not publicly traded, confirmed by an engine that could check.
        - UNVERIFIED — could not be determined. NOT the same as "does not exist": an engine without \
        web access cannot see a listing made after its training cutoff. If the user believes the \
        company is listed, pass the ticker straight to backfill_price_data or get_price_coverage, \
        which read live market data and settle it.

        `listingType` says WHAT you would be buying. DEPOSITARY_RECEIPT means the tradable symbol is \
        an ADR/GDR — a local-currency proxy for a foreign home listing, so returns carry an FX \
        component. `alternatives` lists other venues, including ones whose ticker format this \
        platform cannot express (Tokyo's numeric codes). `confidence` reports how far identity was \
        corroborated; `warnings` carries caveats to surface before acting.

        Pass brokerAccountId to validate against a specific broker account, or omit for the default.
        """)
    public ToolResponse findTicker(
        @ToolArg(description = "Company name to search for (e.g., 'Apple', 'Tesla', 'Nothing Phone Company')") String companyName,
        @ToolArg(description = "Optional broker account UUID to validate tradability against. Omit to use the default broker.") Optional<String> brokerAccountId
    ) {
        return execute(() -> {
            BrokerAccountId accountId = brokerAccountId
                .filter(id -> !id.isBlank())
                .map(id -> new BrokerAccountId(UUID.fromString(id)))
                .orElse(null);
            TickerSearchResult result = findTickerUseCase.execute(
                new FindTickerQuery(companyName, mcpUserContext.getUserId(), accountId));
            logger.info("MCP: Found ticker for '{}' -> {}", companyName, result.ticker());
            return toJson(result);
        });
    }
}
