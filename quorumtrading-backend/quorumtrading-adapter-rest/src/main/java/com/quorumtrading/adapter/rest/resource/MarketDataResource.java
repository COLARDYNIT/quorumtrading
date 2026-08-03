package com.quorumtrading.adapter.rest.resource;

import com.quorumtrading.adapter.rest.dto.response.MarketEventResponse;
import com.quorumtrading.adapter.rest.dto.response.MarketEventsResponse;
import com.quorumtrading.adapter.rest.exception.BadRequestException;
import com.quorumtrading.adapter.rest.exception.ProblemDetails;
import com.quorumtrading.adapter.rest.exception.UnprocessableEntityException;
import com.quorumtrading.adapter.rest.mapper.DtoMapper;
import com.quorumtrading.application.usecase.DataCollectionResult;
import com.quorumtrading.model.enums.Granularity;
import com.quorumtrading.model.marketdata.MarketEvent;
import com.quorumtrading.application.usecase.QueryMarketEventsUseCase;
import com.quorumtrading.application.usecase.TriggerDataCollectionUseCase;
import com.quorumtrading.application.usecase.command.TriggerDataCollectionCommand;
import com.quorumtrading.application.usecase.query.QueryMarketEventsQuery;
import com.quorumtrading.model.valueobject.UserId;
import com.quorumtrading.adapter.rest.security.CurrentUserProvider;
import io.quarkus.security.Authenticated;
import jakarta.annotation.security.PermitAll;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@ApplicationScoped
@Path("/api/v1/market-data")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Market Data", description = "Historical market events")
@PermitAll
public class MarketDataResource {

    @Inject
    QueryMarketEventsUseCase queryMarketEventsUseCase;

    @Inject
    TriggerDataCollectionUseCase triggerDataCollectionUseCase;

    @Inject
    Instance<com.quorumtrading.application.usecase.BackFillTickerPriceDataUseCase> backFillTickerPriceDataUseCase;

    @Inject
    Instance<com.quorumtrading.application.port.out.FinancialDataPort> financialDataPort;

    @Inject
    DtoMapper dtoMapper;

    @Inject
    CurrentUserProvider currentUser;

    @GET
    @Path("/events")
    @Operation(
        summary = "Query market events",
        description = "Retrieves historical market events (price updates, news, Reddit posts) within a specified time range with optional filters"
    )
    @APIResponse(
        responseCode = "200",
        description = "Market events retrieved successfully",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = MarketEventsResponse.class)
        )
    )
    @APIResponse(
        responseCode = "400",
        description = "Bad request - missing required parameters or invalid format",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = ProblemDetails.class)
        )
    )
    @APIResponse(
        responseCode = "422",
        description = "Unprocessable entity - time range exceeds 365 days or 'from' is after 'to'",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = ProblemDetails.class)
        )
    )
    @APIResponse(
        responseCode = "500",
        description = "Internal server error",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = ProblemDetails.class)
        )
    )
    public MarketEventsResponse queryMarketEvents(
            @Parameter(
                description = "Start timestamp in ISO-8601 format",
                required = true,
                example = "2023-01-01T00:00:00Z"
            )
            @QueryParam("from") String fromStr,
            @Parameter(
                description = "End timestamp in ISO-8601 format",
                required = true,
                example = "2023-12-31T23:59:59Z"
            )
            @QueryParam("to") String toStr,
            @Parameter(
                description = "Filter by trading symbol (e.g., BTC, ETH)",
                required = false,
                example = "BTC"
            )
            @QueryParam("symbol") String symbol,
            @Parameter(
                description = "Filter by data source",
                required = false,
                example = "alpaca"
            )
            @QueryParam("source") String source,
            @Parameter(
                description = "Filter by event type (PRICE, NEWS, SOCIAL)",
                required = false,
                example = "PRICE"
            )
            @QueryParam("eventType") String eventTypeStr,
            @Parameter(
                description = "Maximum number of events to return (1-1000)",
                required = false,
                example = "100"
            )
            @QueryParam("limit") Integer limit) {

        // Validate required parameters
        if (fromStr == null || fromStr.isBlank()) {
            throw new BadRequestException("Query parameter 'from' is required");
        }
        if (toStr == null || toStr.isBlank()) {
            throw new BadRequestException("Query parameter 'to' is required");
        }

        // Parse timestamps
        Instant from;
        Instant to;
        try {
            from = Instant.parse(fromStr);
            to = Instant.parse(toStr);
        } catch (Exception e) {
            throw new BadRequestException("Invalid timestamp format. Use ISO-8601 format (e.g., 2023-01-01T00:00:00Z)");
        }

        // Validate time range order
        if (!from.isBefore(to)) {
            throw new UnprocessableEntityException("Query parameter 'from' must be before 'to'");
        }

        // Validate time range is not too large (max 365 days)
        long daysBetween = ChronoUnit.DAYS.between(from, to);
        if (daysBetween > 365) {
            throw new BadRequestException("Time range cannot exceed 365 days");
        }

        // Validate limit bounds
        if (limit != null) {
            if (limit < 1 || limit > 1000) {
                throw new BadRequestException("Query parameter 'limit' must be between 1 and 1000");
            }
        }

        // Validate eventType if provided
        if (eventTypeStr != null && !eventTypeStr.isBlank()) {
            try {
                // Just validate it's a valid enum value
                // The actual eventType string will be passed to the use case
            } catch (IllegalArgumentException e) {
                throw new BadRequestException("Invalid eventType. Must be one of: PRICE, NEWS, SOCIAL");
            }
        }

        // Create query and execute use case
        QueryMarketEventsQuery query = new QueryMarketEventsQuery(
                from, to, symbol, source, eventTypeStr, limit);
        List<MarketEvent> events = queryMarketEventsUseCase.execute(query);

        // Map to response DTOs
        List<MarketEventResponse> eventResponses = dtoMapper.toMarketEventResponseList(events);

        // Return wrapper response
        return new MarketEventsResponse(eventResponses, eventResponses.size(), from, to, limit);
    }

    @POST
    @Authenticated
    @Path("/collect")
    @Operation(
        summary = "Trigger market data collection",
        description = "Manually triggers data collection from all enabled market data providers. " +
                     "Useful for testing and on-demand data ingestion. Optionally supports historical data collection " +
                     "for providers that implement date range fetching (e.g., Reddit)."
    )
    @APIResponse(
        responseCode = "200",
        description = "Data collection completed successfully",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = Map.class)
        )
    )
    @APIResponse(
        responseCode = "400",
        description = "Bad request - invalid date format or invalid date range",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = ProblemDetails.class)
        )
    )
    @APIResponse(
        responseCode = "500",
        description = "Internal server error",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = ProblemDetails.class)
        )
    )
    public Map<String, Object> triggerDataCollection(
            @Parameter(
                description = "Optional category filter (e.g., 'crypto', 'forex')",
                required = false,
                example = "crypto"
            )
            @QueryParam("category") String category,
            @Parameter(
                description = "Optional start timestamp for historical data collection (ISO-8601 format)",
                required = false,
                example = "2024-01-01T00:00:00Z"
            )
            @QueryParam("from") String fromStr,
            @Parameter(
                description = "Optional end timestamp for historical data collection (ISO-8601 format)",
                required = false,
                example = "2024-01-31T23:59:59Z"
            )
            @QueryParam("to") String toStr) {

        // Authentication check
        UserId userId = currentUser.getCurrentUserIdOptional()
            .orElseThrow(() -> new NotAuthorizedException("Authentication required", "Bearer"));

        // Parse optional date range parameters
        Instant from = null;
        Instant to = null;

        if (fromStr != null && !fromStr.isBlank()) {
            try {
                from = Instant.parse(fromStr);
            } catch (Exception e) {
                throw new BadRequestException("Invalid 'from' timestamp format. Use ISO-8601 format (e.g., 2024-01-01T00:00:00Z)");
            }
        }

        if (toStr != null && !toStr.isBlank()) {
            try {
                to = Instant.parse(toStr);
            } catch (Exception e) {
                throw new BadRequestException("Invalid 'to' timestamp format. Use ISO-8601 format (e.g., 2024-01-31T23:59:59Z)");
            }
        }

        // Validate date range if both dates provided
        if (from != null && to != null && !from.isBefore(to)) {
            throw new BadRequestException("Query parameter 'from' must be before 'to'");
        }

        // Both dates must be provided together
        if ((from != null && to == null) || (from == null && to != null)) {
            throw new BadRequestException("Both 'from' and 'to' parameters must be provided together for date range collection");
        }

        // Create command and execute use case
        TriggerDataCollectionCommand command = new TriggerDataCollectionCommand(category, from, to);
        DataCollectionResult result = triggerDataCollectionUseCase.execute(command);

        String status = result.errors().isEmpty() ? "SUCCESS" : "PARTIAL_SUCCESS";
        String message = result.errors().isEmpty()
            ? "Market data collection completed"
            : "Collection completed with errors: " + String.join(", ", result.errors());

        return Map.of(
            "status", status,
            "message", message,
            "provider", result.providerName(),
            "eventsCollected", result.eventsCollected(),
            "timestamp", java.time.Instant.now().toString()
        );
    }

    @POST
    @Authenticated
    @Path("/backfill")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(
        summary = "Backfill historical price data for a ticker",
        description = "Fetches historical OHLC (candlestick) data from the configured market-data providers and inserts it into the database. " +
                     "Crypto tickers are served keyless by the Kraken/Coinbase adapters (no API key required); stock tickers use a configured provider such as Alpaca or Tiingo (requires a key). " +
                     "Supports configurable granularity (1Min, 5Min, 15Min, 1Hour, 1Day, etc.); defaults to 1Day if not specified. " +
                     "Automatically skips duplicate records via the content_hash constraint."
    )
    @APIResponse(
        responseCode = "200",
        description = "Backfill completed successfully",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = Map.class)
        )
    )
    @APIResponse(
        responseCode = "400",
        description = "Bad request - missing required parameters or invalid format",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = ProblemDetails.class)
        )
    )
    @APIResponse(
        responseCode = "500",
        description = "Internal server error",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = ProblemDetails.class)
        )
    )
    public Map<String, Object> backFillTickerPriceData(
            @Parameter(
                description = "Backfill request containing ticker, asset type, and date range",
                required = true
            )
            Map<String, String> request) {

        // Authentication check
        UserId userId = currentUser.getCurrentUserIdOptional()
            .orElseThrow(() -> new NotAuthorizedException("Authentication required", "Bearer"));

        // Validate required fields
        String ticker = request.get("ticker");
        String assetType = request.get("assetType");
        String startDateStr = request.get("startDate");
        String endDateStr = request.get("endDate");
        String granularityStr = request.get("granularity");  // Optional, defaults to ONE_DAY

        if (ticker == null || ticker.isBlank()) {
            throw new BadRequestException("Field 'ticker' is required");
        }
        if (assetType == null || assetType.isBlank()) {
            throw new BadRequestException("Field 'assetType' is required (CRYPTO or STOCK)");
        }
        if (startDateStr == null || startDateStr.isBlank()) {
            throw new BadRequestException("Field 'startDate' is required (ISO-8601 format)");
        }
        if (endDateStr == null || endDateStr.isBlank()) {
            throw new BadRequestException("Field 'endDate' is required (ISO-8601 format)");
        }

        // Parse timestamps
        Instant startDate;
        Instant endDate;
        try {
            startDate = Instant.parse(startDateStr);
            endDate = Instant.parse(endDateStr);
        } catch (Exception e) {
            throw new BadRequestException("Invalid timestamp format. Use ISO-8601 format (e.g., 2025-11-01T00:00:00Z)");
        }

        // Parse granularity (optional, defaults to ONE_DAY via Granularity.fromString())
        Granularity granularity;
        try {
            granularity = Granularity.fromString(granularityStr);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid granularity: " + e.getMessage());
        }

        // Check if backfill use case is available (disabled if work-in-progress)
        if (backFillTickerPriceDataUseCase.isUnsatisfied()) {
            throw new UnprocessableEntityException("Backfill functionality is currently unavailable (work-in-progress feature)");
        }

        // Create command and execute use case
        com.quorumtrading.application.usecase.command.BackFillTickerPriceDataCommand command =
            new com.quorumtrading.application.usecase.command.BackFillTickerPriceDataCommand(
                ticker, assetType, startDate, endDate, granularity
            );

        com.quorumtrading.application.usecase.BackFillResult result = backFillTickerPriceDataUseCase.get().execute(command);

        // Build response
        if (result.success()) {
            return Map.of(
                "status", "SUCCESS",
                "message", String.format("Backfill completed for %s", ticker),
                "ticker", result.ticker(),
                "assetType", result.assetType(),
                "recordsInserted", result.recordsInserted(),
                "recordsSkipped", result.recordsSkipped(),
                "recordsFailed", result.recordsFailed(),
                "totalProcessed", result.totalProcessed(),
                "timestamp", Instant.now().toString()
            );
        } else {
            return Map.of(
                "status", "FAILED",
                "message", result.errorMessage(),
                "ticker", result.ticker(),
                "assetType", result.assetType(),
                "timestamp", Instant.now().toString()
            );
        }
    }

    @GET
    @Path("/financial-metrics")
    @Operation(summary = "Get financial metrics for a stock",
        description = "Returns valuation ratios, growth, profitability, earnings, and analyst recommendations. Only works for individual stocks, not ETFs or crypto.")
    @APIResponse(responseCode = "200", description = "Financial metrics")
    @APIResponse(responseCode = "404", description = "No financial data available")
    @APIResponse(responseCode = "503", description = "Financial data provider not available")
    public Response getFinancialMetrics(
            @QueryParam("symbol") @Parameter(description = "Stock ticker symbol (e.g., AAPL)") String symbol) {

        if (symbol == null || symbol.isBlank()) {
            throw new BadRequestException("symbol query parameter is required");
        }

        var port = financialDataPort.stream().findFirst().orElse(null);
        if (port == null) {
            // A missing provider is service unavailability, not a missing resource
            return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                .entity(new ProblemDetails(
                    "https://api.quorumtrading.com/problems/service-unavailable",
                    "Service Unavailable",
                    503,
                    "Financial data provider not available",
                    "/api/v1/market-data/financial-metrics"))
                .build();
        }

        var tickerSymbol = new com.quorumtrading.model.valueobject.Symbol(
            symbol.toUpperCase(), com.quorumtrading.model.enums.InvestmentType.STOCK);

        return port.getFinancialMetrics(tickerSymbol)
            .map(fm -> Response.ok(toFinancialMetricsResponse(fm)).build())
            .orElse(Response.status(Response.Status.NOT_FOUND)
                .entity(ProblemDetails.notFound(
                    "No financial data available for " + symbol,
                    "/api/v1/market-data/financial-metrics"))
                .build());
    }

    private Map<String, Object> toFinancialMetricsResponse(com.quorumtrading.model.marketdata.FinancialMetrics fm) {
        var response = new java.util.LinkedHashMap<String, Object>();

        // Company
        if (fm.companyName() != null) response.put("companyName", fm.companyName());
        if (fm.industry() != null) response.put("industry", fm.industry());
        if (fm.marketCapMillions() != null) response.put("marketCapMillions", fm.marketCapMillions());

        // Valuation
        var valuation = new java.util.LinkedHashMap<String, Object>();
        if (fm.peRatioTTM() != null) valuation.put("peRatioTTM", fm.peRatioTTM());
        if (fm.forwardPE() != null) valuation.put("forwardPE", fm.forwardPE());
        if (fm.priceToBook() != null) valuation.put("priceToBook", fm.priceToBook());
        if (fm.priceToSales() != null) valuation.put("priceToSales", fm.priceToSales());
        if (fm.evToEbitda() != null) valuation.put("evToEbitda", fm.evToEbitda());
        if (fm.pegRatio() != null) valuation.put("pegRatio", fm.pegRatio());
        if (!valuation.isEmpty()) response.put("valuation", valuation);

        // Growth
        var growth = new java.util.LinkedHashMap<String, Object>();
        if (fm.epsGrowthYoYPercent() != null) growth.put("epsGrowthYoY", fm.epsGrowthYoYPercent());
        if (fm.epsGrowth5YPercent() != null) growth.put("epsGrowth5Y", fm.epsGrowth5YPercent());
        if (fm.revenueGrowthYoYPercent() != null) growth.put("revenueGrowthYoY", fm.revenueGrowthYoYPercent());
        if (fm.revenueGrowth5YPercent() != null) growth.put("revenueGrowth5Y", fm.revenueGrowth5YPercent());
        if (!growth.isEmpty()) response.put("growth", growth);

        // Profitability
        var profitability = new java.util.LinkedHashMap<String, Object>();
        if (fm.grossMarginPercent() != null) profitability.put("grossMargin", fm.grossMarginPercent());
        if (fm.operatingMarginPercent() != null) profitability.put("operatingMargin", fm.operatingMarginPercent());
        if (fm.netProfitMarginPercent() != null) profitability.put("netProfitMargin", fm.netProfitMarginPercent());
        if (fm.returnOnEquityPercent() != null) profitability.put("returnOnEquity", fm.returnOnEquityPercent());
        if (fm.returnOnAssetsPercent() != null) profitability.put("returnOnAssets", fm.returnOnAssetsPercent());
        if (!profitability.isEmpty()) response.put("profitability", profitability);

        // Financial Health
        var health = new java.util.LinkedHashMap<String, Object>();
        if (fm.debtToEquity() != null) health.put("debtToEquity", fm.debtToEquity());
        if (fm.currentRatio() != null) health.put("currentRatio", fm.currentRatio());
        if (fm.quickRatio() != null) health.put("quickRatio", fm.quickRatio());
        if (!health.isEmpty()) response.put("financialHealth", health);

        // Per Share
        var perShare = new java.util.LinkedHashMap<String, Object>();
        if (fm.epsTTM() != null) perShare.put("epsTTM", fm.epsTTM());
        if (fm.bookValuePerShare() != null) perShare.put("bookValue", fm.bookValuePerShare());
        if (fm.dividendYieldPercent() != null) perShare.put("dividendYield", fm.dividendYieldPercent());
        if (fm.beta() != null) perShare.put("beta", fm.beta());
        if (!perShare.isEmpty()) response.put("perShare", perShare);

        // Earnings
        if (fm.hasEarningsData()) {
            var earnings = fm.recentEarnings().stream().map(er -> {
                var e = new java.util.LinkedHashMap<String, Object>();
                e.put("period", er.period());
                e.put("quarter", er.quarter());
                e.put("year", er.year());
                if (er.actualEps() != null) e.put("actual", er.actualEps());
                if (er.estimateEps() != null) e.put("estimate", er.estimateEps());
                if (er.surprisePercent() != null) e.put("surprisePercent", er.surprisePercent());
                e.put("beat", er.beat());
                return e;
            }).toList();
            response.put("earnings", earnings);
        }

        // Analyst
        if (fm.hasAnalystData()) {
            var analyst = new java.util.LinkedHashMap<String, Object>();
            var ac = fm.analystConsensus();
            analyst.put("strongBuy", ac.strongBuy());
            analyst.put("buy", ac.buy());
            analyst.put("hold", ac.hold());
            analyst.put("sell", ac.sell());
            analyst.put("strongSell", ac.strongSell());
            analyst.put("total", ac.totalAnalysts());
            response.put("analystConsensus", analyst);
        }

        return response;
    }

}
