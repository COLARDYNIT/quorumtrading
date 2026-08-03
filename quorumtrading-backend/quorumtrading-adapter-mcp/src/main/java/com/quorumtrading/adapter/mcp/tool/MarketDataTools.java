package com.quorumtrading.adapter.mcp.tool;

import com.quorumtrading.adapter.mcp.auth.McpUserContext;
import com.quorumtrading.adapter.mcp.dto.*;
import com.quorumtrading.adapter.mcp.mapper.McpDtoMapper;
import com.quorumtrading.application.port.out.HistoricalPricePort;
import com.quorumtrading.application.service.IndicatorEnrichmentService;
import com.quorumtrading.application.usecase.BackFillResult;
import com.quorumtrading.application.usecase.BackFillTickerPriceDataUseCase;
import com.quorumtrading.application.usecase.GetDividendInfoUseCase;
import com.quorumtrading.application.usecase.GetTickerPerformanceUseCase;
import com.quorumtrading.application.usecase.QueryMarketEventsUseCase;
import com.quorumtrading.application.usecase.query.GetDividendInfoQuery;
import com.quorumtrading.application.usecase.command.BackFillTickerPriceDataCommand;
import com.quorumtrading.application.usecase.query.GetTickerPerformanceQuery;
import com.quorumtrading.application.usecase.query.QueryMarketEventsQuery;
import com.quorumtrading.application.usecase.result.TickerPerformanceResult;
import com.quorumtrading.model.enums.Granularity;
import com.quorumtrading.model.enums.InvestmentType;
import com.quorumtrading.model.marketdata.MarketEvent;
import com.quorumtrading.model.valueobject.DividendInfo;
import com.quorumtrading.model.valueobject.IndicatorContext;
import com.quorumtrading.model.valueobject.IndicatorSnapshot;
import com.quorumtrading.model.valueobject.Symbol;
import com.quorumtrading.model.valueobject.SymbolParser;
import com.quorumtrading.model.valueobject.TickerForm;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolResponse;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * MCP tools for market data queries, technical indicator calculation, and historical data backfill.
 */
@Singleton
@ActivateRequestContext
public class MarketDataTools extends McpToolBase {

    /** Hard cap: never auto-backfill more than 1 year of data. */
    static final int MAX_AUTO_BACKFILL_DAYS = 365;

    /**
     * Calendar days derived from the indicator requirement
     * (trading days * 7/5 + 20-day buffer for holidays), capped at 1 year.
     */
    static final int AUTO_BACKFILL_CALENDAR_DAYS = Math.min(
        (int) Math.ceil(IndicatorEnrichmentService.REQUIRED_TRADING_DAYS * 7.0 / 5) + 20,
        MAX_AUTO_BACKFILL_DAYS
    );

    @Inject
    QueryMarketEventsUseCase queryMarketEventsUseCase;

    @Inject
    BackFillTickerPriceDataUseCase backFillTickerPriceDataUseCase;

    @Inject
    IndicatorEnrichmentService indicatorEnrichmentService;

    @Inject
    GetTickerPerformanceUseCase getTickerPerformanceUseCase;

    @Inject
    GetDividendInfoUseCase getDividendInfoUseCase;

    @Inject
    HistoricalPricePort historicalPricePort;

    @Inject
    McpDtoMapper mapper;

    @Inject
    McpUserContext userContext;

    @Tool(name = "query_market_events", description = "Query market events (price updates, news, social) within a time range. Supports filtering by symbol and event type. "
        + "PRICE events always include closePrice regardless of excludeRawData setting. Use excludeRawData=false for full OHLCV data.")
    public ToolResponse queryMarketEvents(
        @ToolArg(description = "Start of time range in ISO-8601 format (e.g., 2025-01-01T00:00:00Z)") String from,
        @ToolArg(description = "End of time range in ISO-8601 format (e.g., 2025-06-01T00:00:00Z)") String to,
        @ToolArg(description = "Filter by trading symbol (e.g., AAPL, BTC)") Optional<String> symbol,
        @ToolArg(description = "Filter by event type: PRICE, NEWS, or SOCIAL") Optional<String> eventType,
        @ToolArg(description = "Maximum number of results (default 20, max 200)") Optional<Integer> limit,
        @ToolArg(description = "Exclude raw event data JSON to reduce response size (default true)") Optional<Boolean> excludeRawData
    ) {
        return execute(() -> {
            userContext.getUserId(); // auth gate: global data, but a valid API token is still required
            int effectiveLimit = parseLimit(limit, 20, 200);
            int fetchLimit = effectiveLimit + 1;
            boolean stripData = excludeRawData.orElse(true);

            QueryMarketEventsQuery query = new QueryMarketEventsQuery(
                parseDate(from),
                parseDate(to),
                symbol.orElse(null),
                null,
                eventType.orElse(null),
                fetchLimit
            );

            Page<MarketEvent> page = paginate(queryMarketEventsUseCase.execute(query), effectiveLimit);

            List<McpMarketEventDto> dtos = page.items().stream()
                .map(e -> mapper.toMarketEventDto(e, stripData))
                .toList();

            McpPaginatedResponse<McpMarketEventDto> response =
                new McpPaginatedResponse<>(dtos, dtos.size(), page.hasMore());
            return toJson(response);
        });
    }

    @Tool(name = "get_indicators", description = "Calculate technical indicators (RSI-14, SMA-50/200, MACD, Bollinger Bands) for a ticker with latest close price. Automatically backfills price data if missing. Returns current indicator values with warnings if insufficient data.")
    public ToolResponse getIndicators(
        @ToolArg(description = "Trading symbol ticker (e.g., AAPL, BTC)") String ticker,
        @ToolArg(description = "Asset type: STOCK or CRYPTO") String investmentType
    ) {
        return execute(() -> {
            userContext.getUserId(); // auth gate: global data, but a valid API token is still required
            InvestmentType type = parseEnum(InvestmentType.class, investmentType, "investmentType");
            // Same door as backfill_price_data and get_price_coverage — see TickerForm.
            Symbol sym = TickerForm.resolve(ticker, type);

            // Fetch latest price for context (independent of indicator calculation)
            String latestClosePrice = null;
            String latestPriceDate = null;
            try {
                var priceBars = historicalPricePort.getHistoricalPrices(sym, 7);
                if (priceBars != null && !priceBars.isEmpty()) {
                    var lastBar = priceBars.get(priceBars.size() - 1);
                    latestClosePrice = lastBar.close().toPlainString();
                    latestPriceDate = lastBar.timestamp().toString();
                }
            } catch (Exception e) {
                // Graceful degradation: indicators still useful without price
            }

            IndicatorContext context = indicatorEnrichmentService.calculateIndicators(List.of(sym));
            IndicatorSnapshot snapshot = context.tickerIndicators().get(sym);

            if (snapshot == null) {
                snapshot = IndicatorSnapshot.empty(sym, "No data available for " + ticker);
            }

            if (snapshot.values().isEmpty()) {
                String autoBackfillNote = attemptAutoBackfill(sym);
                if (autoBackfillNote.contains("inserted")) {
                    IndicatorContext retryContext = indicatorEnrichmentService.calculateIndicators(List.of(sym));
                    IndicatorSnapshot retrySnapshot = retryContext.tickerIndicators().get(sym);
                    if (retrySnapshot != null && !retrySnapshot.values().isEmpty()) {
                        return toJson(mapper.toIndicatorSnapshotDto(retrySnapshot, autoBackfillNote, latestClosePrice, latestPriceDate));
                    }
                    autoBackfillNote = "Auto-backfill completed but no indicator data could be calculated from the available price data";
                }
                return toJson(mapper.toIndicatorSnapshotDto(snapshot, autoBackfillNote, latestClosePrice, latestPriceDate));
            }

            DividendInfo dividendInfo = lookupDividendInfo(ticker.toUpperCase());
            McpIndicatorSnapshotDto dto = mapper.toIndicatorSnapshotDto(snapshot, null, latestClosePrice, latestPriceDate, dividendInfo);
            return toJson(dto);
        });
    }

    private DividendInfo lookupDividendInfo(String ticker) {
        try {
            var result = getDividendInfoUseCase.execute(new GetDividendInfoQuery(List.of(ticker)));
            return result.get(ticker);
        } catch (Exception e) {
            logger.debug("Failed to fetch dividend info for {}: {}", ticker, e.getMessage());
            return null;
        }
    }

    private String attemptAutoBackfill(Symbol sym) {
        try {
            Instant end = Instant.now();
            Instant start = end.minus(Duration.ofDays(AUTO_BACKFILL_CALENDAR_DAYS));
            BackFillTickerPriceDataCommand command = new BackFillTickerPriceDataCommand(
                sym.ticker(),
                sym.type().name(),
                start,
                end,
                Granularity.ONE_DAY
            );
            BackFillResult result = backFillTickerPriceDataUseCase.execute(command);
            logger.info("MCP: Auto-backfill for {} - inserted={}, skipped={}, failed={}",
                sym.ticker(), result.recordsInserted(), result.recordsSkipped(), result.recordsFailed());
            return describeBackfill(result, sym);
        } catch (Exception e) {
            logger.warn("MCP: Auto-backfill failed for {}: {}", sym.ticker(), e.getMessage());
            return "Auto-backfill failed for " + sym.ticker() + ": " + e.getMessage();
        }
    }

    /**
     * The note this tool shows for a backfill that landed nothing.
     *
     * <p>It used to be a fixed sentence — "Auto-backfill completed but no data was available from
     * the provider for BTC-USD" — written after the backfill had already produced a precise,
     * form-specific, provider-by-provider explanation, which was then thrown away. This is the
     * tool a newcomer is most likely to call first, and {@code explain_indicators} routes them
     * through it, so the best diagnostic in the product was being discarded at the point it was
     * most needed. Carry it instead of restating a guess about whose fault it was.</p>
     */
    private static String describeBackfill(BackFillResult result, Symbol sym) {
        if (result.recordsInserted() > 0) {
            return "Auto-backfill completed: inserted " + result.recordsInserted()
                + " daily bars for " + sym.ticker();
        }
        if (result.errorMessage() != null && !result.errorMessage().isBlank()) {
            return "Auto-backfill returned no data for " + sym.ticker() + ". " + result.errorMessage();
        }
        if (result.recordsSkipped() > 0) {
            return "Auto-backfill added nothing new for " + sym.ticker() + ": all "
                + result.recordsSkipped() + " bars the provider returned were already stored.";
        }
        return "Auto-backfill completed but no data was available from the provider for " + sym.ticker();
    }

    @Tool(name = "get_price_coverage", description = "Show what price data is stored locally for a ticker: bar count, distinct days covered, first/last bar timestamps, and which providers supplied the bars. "
        + "Pass startDate and endDate to measure a specific backtest window — the response then reports expected vs covered days and lists the gaps, which first/last bar alone cannot tell you. "
        + "Use after backfill_price_data to verify what actually landed, or before a backtest to confirm the period is covered.")
    public ToolResponse getPriceCoverage(
        @ToolArg(description = "Trading symbol ticker (e.g., AAPL, BTC)") String ticker,
        @ToolArg(description = "Optional window start, ISO-8601 date (e.g. 2024-01-01). Requires endDate.", required = false) Optional<String> startDate,
        @ToolArg(description = "Optional window end, ISO-8601 date (e.g. 2024-12-31), inclusive. Requires startDate.", required = false) Optional<String> endDate
    ) {
        return execute(() -> {
            userContext.getUserId(); // auth gate: global data, but a valid API token is still required
            // Same door as backfill_price_data. This used to build `new Symbol(ticker, STOCK)`
            // directly, which rejected ASML:XAMS that backfill accepted, and accepted BTC-USD
            // that no backfill can ever satisfy — then told the user to go and backfill it.
            Symbol sym = TickerForm.resolve(ticker);

            boolean hasStart = startDate.filter(s -> !s.isBlank()).isPresent();
            boolean hasEnd = endDate.filter(s -> !s.isBlank()).isPresent();
            if (hasStart != hasEnd) {
                throw new IllegalArgumentException(
                    "startDate and endDate must be given together — a window with one end is not a window.");
            }
            if (!hasStart) {
                return toJson(mapper.toPriceCoverageDto(historicalPricePort.getCoverage(sym)));
            }
            LocalDate from = parseLocalDate(startDate.get(), "startDate");
            LocalDate to = parseLocalDate(endDate.get(), "endDate");
            if (to.isBefore(from)) {
                throw new IllegalArgumentException("endDate must not be before startDate.");
            }
            return toJson(mapper.toPriceCoverageDto(historicalPricePort.getCoverage(sym, from, to)));
        });
    }

    private static LocalDate parseLocalDate(String raw, String field) {
        try {
            return LocalDate.parse(raw.trim().substring(0, Math.min(10, raw.trim().length())));
        } catch (RuntimeException e) {
            throw new IllegalArgumentException(
                field + " must be an ISO-8601 date such as 2024-01-01 (got '" + raw + "').", e);
        }
    }

    @Tool(name = "get_ticker_performance", description = "Get historical price performance for a ticker over a date range. "
        + "Returns start/end price, period high/low, and percentage change. "
        + "Automatically backfills price data from the provider if missing. "
        + "Much faster than creating a benchmark — use this for simple 'what did X do between date A and B?' questions.")
    public ToolResponse getTickerPerformance(
        @ToolArg(description = "Trading symbol ticker (e.g., QQQ, AAPL, BTC)") String ticker,
        @ToolArg(description = "Start date in ISO-8601 format (e.g., 2024-01-01T00:00:00Z) or plain date (e.g., 2024-01-01)") String startDate,
        @ToolArg(description = "End date in ISO-8601 format (e.g., 2024-12-31T23:59:59Z) or plain date (e.g., 2024-12-31). A plain date includes the whole of that day.") String endDate,
        @ToolArg(description = "Asset type: STOCK or CRYPTO. Only used to classify the ticker if "
            + "the range has to be backfilled; stored bars are found either way (optional, "
            + "defaults to STOCK)") Optional<String> investmentType
    ) {
        return execute(() -> {
            userContext.getUserId(); // auth gate: global data, but a valid API token is still required
            String upperTicker = ticker.toUpperCase();
            Instant start = parseDate(startDate);
            // Inclusive of the named end day — see parseEndDate.
            Instant end = parseEndDate(endDate);
            // Validated up front, not inside the backfill branch below. While it lived there, an
            // invalid investmentType was rejected only when the range happened to be missing from
            // storage and accepted silently otherwise — the same input passing or failing on
            // unrelated cache state. A typo must be reported deterministically.
            InvestmentType type = investmentType
                .map(t -> parseEnum(InvestmentType.class, t, "investmentType"))
                .orElse(InvestmentType.STOCK);
            GetTickerPerformanceQuery query = new GetTickerPerformanceQuery(upperTicker, start, end);
            TickerPerformanceResult result = getTickerPerformanceUseCase.execute(query);

            if (!result.dataAvailable()) {
                Symbol sym = SymbolParser.parse(upperTicker, type);
                String backfillNote = attemptAutoBackfillForRange(sym, start, end);
                if (backfillNote.contains("inserted")) {
                    TickerPerformanceResult retryResult = getTickerPerformanceUseCase.execute(query);
                    if (retryResult.dataAvailable()) {
                        return toJson(retryResult);
                    }
                }
            }
            return toJson(result);
        });
    }

    private String attemptAutoBackfillForRange(Symbol sym, Instant start, Instant end) {
        try {
            BackFillTickerPriceDataCommand command = new BackFillTickerPriceDataCommand(
                sym.ticker(), sym.type().name(), start, end, Granularity.ONE_DAY);
            BackFillResult result = backFillTickerPriceDataUseCase.execute(command);
            logger.info("MCP: Auto-backfill for {} ({} to {}) - inserted={}, skipped={}, failed={}",
                sym.ticker(), start, end, result.recordsInserted(), result.recordsSkipped(), result.recordsFailed());
            return describeBackfill(result, sym);
        } catch (Exception e) {
            logger.warn("MCP: Auto-backfill for {} ({} to {}) failed: {}", sym.ticker(), start, end, e.getMessage());
            return "Auto-backfill failed for " + sym.ticker() + ": " + e.getMessage();
        }
    }

    @Tool(name = "backfill_price_data", description = "Backfill historical price data for a ticker. Loads OHLCV bars from the market data provider into the database. Required before running backtests or calculating indicators for a new ticker.")
    public ToolResponse backfillPriceData(
        @ToolArg(description = "Trading symbol ticker (e.g., AAPL, BTC)") String ticker,
        @ToolArg(description = "Asset type: STOCK or CRYPTO") String assetType,
        @ToolArg(description = "Start date in ISO-8601 format (e.g., 2024-01-01T00:00:00Z) or plain date (e.g., 2024-01-01)") String startDate,
        @ToolArg(description = "End date in ISO-8601 format (e.g., 2025-01-01T00:00:00Z) or plain date (e.g., 2025-01-01)") String endDate,
        @ToolArg(description = "Bar granularity: 1Min, 5Min, 15Min, 30Min, 1Hour, 4Hour, 1Day, 1Week, 1Month (default: 1Day)") Optional<String> granularity
    ) {
        return execute(() -> {
            userContext.getUserId(); // auth gate: global data, but a valid API token is still required
            Granularity gran = Granularity.fromString(granularity.orElse("1Day"));
            BackFillTickerPriceDataCommand command = new BackFillTickerPriceDataCommand(
                ticker.toUpperCase(),
                assetType.toUpperCase(),
                parseDate(startDate),
                parseDate(endDate),
                gran
            );

            BackFillResult result = backFillTickerPriceDataUseCase.execute(command);
            logger.info("MCP: Backfill completed for {} - inserted={}, skipped={}, failed={}",
                ticker, result.recordsInserted(), result.recordsSkipped(), result.recordsFailed());
            return toJson(mapper.toBackFillResultDto(result, startDate, endDate, granularity.orElse("1Day")));
        });
    }

}
