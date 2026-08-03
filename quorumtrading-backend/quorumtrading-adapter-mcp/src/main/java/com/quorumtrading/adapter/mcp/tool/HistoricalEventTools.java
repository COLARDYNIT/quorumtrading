package com.quorumtrading.adapter.mcp.tool;

import com.quorumtrading.adapter.mcp.auth.McpUserContext;
import com.quorumtrading.application.usecase.GetHistoricalEventUseCase;
import com.quorumtrading.application.usecase.ListHistoricalEventsUseCase;
import com.quorumtrading.application.usecase.query.GetHistoricalEventQuery;
import com.quorumtrading.application.usecase.query.ListHistoricalEventsQuery;
import com.quorumtrading.model.entity.HistoricalEvent;
import com.quorumtrading.model.enums.AssetClass;
import com.quorumtrading.model.enums.EventCategory;
import com.quorumtrading.model.enums.MarketSentiment;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolResponse;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Singleton
@ActivateRequestContext
public class HistoricalEventTools extends McpToolBase {

    private final ListHistoricalEventsUseCase listHistoricalEventsUseCase;
    private final GetHistoricalEventUseCase getHistoricalEventUseCase;

    @Inject
    McpUserContext userContext;

    public HistoricalEventTools(
            ListHistoricalEventsUseCase listHistoricalEventsUseCase,
            GetHistoricalEventUseCase getHistoricalEventUseCase) {
        this.listHistoricalEventsUseCase = listHistoricalEventsUseCase;
        this.getHistoricalEventUseCase = getHistoricalEventUseCase;
    }

    @Tool(description = "List historical market events for backtesting. "
            + "Returns events like COVID Crash, FTX Collapse, AI Boom, etc. "
            + "Each event includes date range, suggested assets, and keywords. "
            + "Use these to quickly set up backtests over significant market periods.")
    public ToolResponse list_historical_events(
            @ToolArg(required = false,
                    description = "Optional filter by category: CRISIS, BULL_RUN, VOLATILITY, CATALYST, BEAR_MARKET")
            Optional<String> category,
            @ToolArg(required = false, description = "Optional filter by asset class: STOCK, CRYPTO, BOTH")
            Optional<String> assetClass,
            @ToolArg(required = false, description = "Optional filter by market sentiment: BULLISH, BEARISH, MIXED")
            Optional<String> sentiment) {
        return execute(() -> {
            userContext.getUserId(); // auth gate: global data, but a valid API token is still required
            // These were declared as bare String, which the MCP framework publishes as REQUIRED and
            // enforces before the method runs — so "list the events" was impossible without naming
            // all three filters, and the blank-handling below was unreachable. The whole point of
            // the tool is the unfiltered call.
            Optional<EventCategory> cat = category.filter(s -> !s.isBlank())
                    .map(s -> parseEnum(EventCategory.class, s, "category"));
            Optional<AssetClass> ac = assetClass.filter(s -> !s.isBlank())
                    .map(s -> parseEnum(AssetClass.class, s, "assetClass"));
            Optional<MarketSentiment> sent = sentiment.filter(s -> !s.isBlank())
                    .map(s -> parseEnum(MarketSentiment.class, s, "sentiment"));

            List<HistoricalEvent> events = listHistoricalEventsUseCase.execute(
                    new ListHistoricalEventsQuery(cat, ac, sent));

            return toJson(events.stream().map(this::toDto).toList());
        });
    }

    @Tool(description = "Get a historical event by slug or keyword. "
            + "Tries exact slug match first (e.g., 'covid-crash'), "
            + "then falls back to keyword search (e.g., 'pandemic', 'ftx', 'meme stocks').")
    public ToolResponse get_historical_event(
            @ToolArg(description = "Event slug (e.g., 'covid-crash') or keyword (e.g., 'pandemic')") String slugOrKeyword) {
        return execute(() -> {
            userContext.getUserId(); // auth gate: global data, but a valid API token is still required
            if (slugOrKeyword == null || slugOrKeyword.isBlank()) {
                throw new IllegalArgumentException("slugOrKeyword is required");
            }

            List<HistoricalEvent> events = getHistoricalEventUseCase.execute(
                    new GetHistoricalEventQuery(slugOrKeyword));

            if (events.isEmpty()) {
                return toJson(Map.of("results", List.of(), "count", 0,
                        "message", "No historical event found matching '" + slugOrKeyword + "'. Use list_historical_events to see all available events."));
            }

            return toJson(events.stream().map(this::toDto).toList());
        });
    }

    private Map<String, Object> toDto(HistoricalEvent event) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("slug", event.slug());
        map.put("name", event.name());
        map.put("startDate", event.startDate().toString());
        map.put("endDate", event.endDate().toString());
        map.put("category", event.category().name());
        map.put("assetClass", event.assetClass().name());
        map.put("sentiment", event.sentiment().name());
        map.put("description", event.description());
        map.put("impactSummary", event.impactSummary());
        map.put("suggestedAssets", event.suggestedAssets());
        map.put("keywords", event.keywords());
        return map;
    }
}
