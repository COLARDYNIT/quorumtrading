package com.quorumtrading.adapter.mcp.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quorumtrading.adapter.mcp.auth.McpUserContext;
import com.quorumtrading.application.service.IndicatorEnrichmentService;
import com.quorumtrading.application.usecase.BackFillResult;
import com.quorumtrading.application.usecase.BackFillTickerPriceDataUseCase;
import com.quorumtrading.application.usecase.QueryMarketEventsUseCase;
import com.quorumtrading.application.usecase.command.BackFillTickerPriceDataCommand;
import com.quorumtrading.application.usecase.query.QueryMarketEventsQuery;
import com.quorumtrading.model.enums.EventType;
import com.quorumtrading.model.enums.InvestmentType;
import com.quorumtrading.model.marketdata.MarketEvent;
import com.quorumtrading.application.port.out.HistoricalPricePort;
import com.quorumtrading.model.valueobject.*;
import io.quarkiverse.mcp.server.ToolResponse;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@QuarkusTest
class MarketDataToolsTest {

    @Inject
    MarketDataTools marketDataTools;

    @InjectMock
    QueryMarketEventsUseCase queryMarketEventsUseCase;

    @InjectMock
    BackFillTickerPriceDataUseCase backFillTickerPriceDataUseCase;

    @InjectMock
    IndicatorEnrichmentService indicatorEnrichmentService;

    @InjectMock
    HistoricalPricePort historicalPricePort;

    @InjectMock
    McpUserContext mcpUserContext;

    @InjectMock
    com.quorumtrading.application.usecase.GetTickerPerformanceUseCase getTickerPerformanceUseCase;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void queryMarketEvents_withoutValidToken_returnsAuthError() {
        when(mcpUserContext.getUserId()).thenThrow(new SecurityException("Missing or invalid Authorization header"));

        ToolResponse response = marketDataTools.queryMarketEvents(
            "2025-01-01T00:00:00Z", "2025-01-02T00:00:00Z",
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());

        assertThat(response.isError()).isTrue();
        assertThat(response.firstContent().asText().text()).contains("Authentication required");
        verifyNoInteractions(queryMarketEventsUseCase);
    }



    @Test
    void queryMarketEvents_returnsPaginatedResults() throws Exception {
        MarketEvent event = new MarketEvent(
            Instant.parse("2025-03-15T10:00:00Z"),
            EventType.PRICE,
            "alpaca",
            "AAPL",
            Map.of("close", 175.50, "volume", 1000000)
        );
        when(queryMarketEventsUseCase.execute(any(QueryMarketEventsQuery.class)))
            .thenReturn(List.of(event));

        ToolResponse response = marketDataTools.queryMarketEvents(
            "2025-01-01T00:00:00Z",
            "2025-06-01T00:00:00Z",
            Optional.of("AAPL"),
            Optional.of("PRICE"),
            Optional.of(10),
            Optional.empty()
        );

        assertThat(response.isError()).isFalse();
        String result = response.firstContent().asText().text();
        JsonNode json = objectMapper.readTree(result);
        assertThat(json.get("count").asInt()).isEqualTo(1);
        assertThat(json.get("hasMore").asBoolean()).isFalse();
        assertThat(json.get("items").get(0).get("symbol").asText()).isEqualTo("AAPL");
        assertThat(json.get("items").get(0).get("eventType").asText()).isEqualTo("PRICE");
        assertThat(json.get("items").get(0).get("closePrice").asText()).isEqualTo("175.5");
    }

    @Test
    void getIndicators_returnsSnapshot() throws Exception {
        Symbol symbol = new Symbol("AAPL", InvestmentType.STOCK);
        Instant now = Instant.now();

        PriceBar priceBar = new PriceBar(now, null, null, null, new BigDecimal("185.42"), null);
        when(historicalPricePort.getHistoricalPrices(any(), eq(7))).thenReturn(List.of(priceBar));

        IndicatorSnapshot snapshot = new IndicatorSnapshot(
            symbol,
            now,
            Map.of(
                IndicatorType.RSI, new IndicatorValue.RSIValue(new BigDecimal("62.3")),
                IndicatorType.MOVING_AVERAGE, new IndicatorValue.MovingAverageValue(
                    new BigDecimal("185.42"), new BigDecimal("178.91")
                ),
                IndicatorType.MACD, new IndicatorValue.MACDValue(
                    new BigDecimal("2.15"), new BigDecimal("1.89"), new BigDecimal("0.26")
                ),
                IndicatorType.BOLLINGER_BANDS, new IndicatorValue.BollingerValue(
                    new BigDecimal("192.10"), new BigDecimal("185.00"), new BigDecimal("177.90")
                )
            ),
            Map.of()
        );

        IndicatorContext context = new IndicatorContext(Map.of(symbol, snapshot));
        when(indicatorEnrichmentService.calculateIndicators(any())).thenReturn(context);

        ToolResponse response = marketDataTools.getIndicators("AAPL", "STOCK");

        assertThat(response.isError()).isFalse();
        String result = response.firstContent().asText().text();
        JsonNode json = objectMapper.readTree(result);
        assertThat(json.get("ticker").asText()).isEqualTo("AAPL");
        assertThat(json.get("rsi").get("rsi14").asText()).isEqualTo("62.3");
        assertThat(json.get("movingAverage").get("sma50").asText()).isEqualTo("185.42");
        assertThat(json.get("macd").get("macdLine").asText()).isEqualTo("2.15");
        assertThat(json.get("bollingerBands").get("upper").asText()).isEqualTo("192.10");
        assertThat(json.get("latestClosePrice")).isNotNull();
    }

    @Test
    void getIndicators_autoBackfillSuccess() throws Exception {
        Symbol symbol = new Symbol("AAPL", InvestmentType.STOCK);
        when(historicalPricePort.getHistoricalPrices(any(), eq(7))).thenReturn(List.of());

        // First call returns empty indicators (no data)
        IndicatorContext emptyContext = new IndicatorContext(Map.of());
        // Second call (after backfill) returns real indicators
        IndicatorSnapshot richSnapshot = new IndicatorSnapshot(
            symbol, Instant.now(),
            Map.of(IndicatorType.RSI, new IndicatorValue.RSIValue(new BigDecimal("55.0"))),
            Map.of()
        );
        IndicatorContext richContext = new IndicatorContext(Map.of(symbol, richSnapshot));

        when(indicatorEnrichmentService.calculateIndicators(any()))
            .thenReturn(emptyContext)
            .thenReturn(richContext);

        BackFillResult backFillResult = BackFillResult.success("AAPL", "STOCK", 200, 0, 0);
        when(backFillTickerPriceDataUseCase.execute(any(BackFillTickerPriceDataCommand.class)))
            .thenReturn(backFillResult);

        ToolResponse response = marketDataTools.getIndicators("AAPL", "STOCK");

        assertThat(response.isError()).isFalse();
        String result = response.firstContent().asText().text();
        JsonNode json = objectMapper.readTree(result);
        assertThat(json.get("ticker").asText()).isEqualTo("AAPL");
        assertThat(json.get("rsi").get("rsi14").asText()).isEqualTo("55.0");
        assertThat(json.get("autoBackfillNote").asText()).contains("Auto-backfill completed");
        assertThat(json.get("autoBackfillNote").asText()).contains("200");
        verify(backFillTickerPriceDataUseCase).execute(any(BackFillTickerPriceDataCommand.class));
    }

    @Test
    void getIndicators_autoBackfillNoRecords() throws Exception {
        Symbol symbol = new Symbol("XYZ", InvestmentType.STOCK);
        when(historicalPricePort.getHistoricalPrices(any(), eq(7))).thenReturn(List.of());

        IndicatorContext emptyContext = new IndicatorContext(Map.of());
        when(indicatorEnrichmentService.calculateIndicators(any())).thenReturn(emptyContext);

        BackFillResult backFillResult = BackFillResult.success("XYZ", "STOCK", 0, 0, 0);
        when(backFillTickerPriceDataUseCase.execute(any(BackFillTickerPriceDataCommand.class)))
            .thenReturn(backFillResult);

        ToolResponse response = marketDataTools.getIndicators("XYZ", "STOCK");

        assertThat(response.isError()).isFalse();
        String result = response.firstContent().asText().text();
        JsonNode json = objectMapper.readTree(result);
        assertThat(json.get("ticker").asText()).isEqualTo("XYZ");
        assertThat(json.get("autoBackfillNote").asText()).contains("no data was available from the provider");
    }

    @Test
    void getIndicators_autoBackfillFailure() throws Exception {
        Symbol symbol = new Symbol("BAD", InvestmentType.STOCK);
        when(historicalPricePort.getHistoricalPrices(any(), eq(7))).thenReturn(List.of());

        IndicatorContext emptyContext = new IndicatorContext(Map.of());
        when(indicatorEnrichmentService.calculateIndicators(any())).thenReturn(emptyContext);

        when(backFillTickerPriceDataUseCase.execute(any(BackFillTickerPriceDataCommand.class)))
            .thenThrow(new RuntimeException("API rate limit exceeded"));

        ToolResponse response = marketDataTools.getIndicators("BAD", "STOCK");

        assertThat(response.isError()).isFalse();
        String result = response.firstContent().asText().text();
        JsonNode json = objectMapper.readTree(result);
        assertThat(json.get("ticker").asText()).isEqualTo("BAD");
        assertThat(json.get("autoBackfillNote").asText()).contains("Auto-backfill failed");
        assertThat(json.get("autoBackfillNote").asText()).contains("API rate limit exceeded");
    }

    @Test
    void getIndicators_noAutoBackfillWhenDataExists() throws Exception {
        Symbol symbol = new Symbol("AAPL", InvestmentType.STOCK);
        when(historicalPricePort.getHistoricalPrices(any(), eq(7))).thenReturn(List.of());
        IndicatorSnapshot snapshot = new IndicatorSnapshot(
            symbol, Instant.now(),
            Map.of(IndicatorType.RSI, new IndicatorValue.RSIValue(new BigDecimal("62.3"))),
            Map.of()
        );
        IndicatorContext context = new IndicatorContext(Map.of(symbol, snapshot));
        when(indicatorEnrichmentService.calculateIndicators(any())).thenReturn(context);

        ToolResponse response = marketDataTools.getIndicators("AAPL", "STOCK");

        assertThat(response.isError()).isFalse();
        String result = response.firstContent().asText().text();
        JsonNode json = objectMapper.readTree(result);
        assertThat(json.get("rsi").get("rsi14").asText()).isEqualTo("62.3");
        assertThat(json.get("autoBackfillNote").isNull()).isTrue();
        verify(backFillTickerPriceDataUseCase, never()).execute(any(BackFillTickerPriceDataCommand.class));
    }

    @Test
    void backfillPriceData_returnsResult() throws Exception {
        BackFillResult backFillResult = BackFillResult.success("AAPL", "STOCK", 250, 5, 0);
        when(backFillTickerPriceDataUseCase.execute(any(BackFillTickerPriceDataCommand.class)))
            .thenReturn(backFillResult);

        ToolResponse response = marketDataTools.backfillPriceData(
            "AAPL", "STOCK",
            "2024-01-01T00:00:00Z", "2025-01-01T00:00:00Z",
            Optional.empty()
        );

        assertThat(response.isError()).isFalse();
        String result = response.firstContent().asText().text();
        JsonNode json = objectMapper.readTree(result);
        assertThat(json.get("ticker").asText()).isEqualTo("AAPL");
        assertThat(json.get("success").asBoolean()).isTrue();
        assertThat(json.get("recordsInserted").asInt()).isEqualTo(250);
        assertThat(json.get("recordsSkipped").asInt()).isEqualTo(5);
        assertThat(json.get("startDate").asText()).isEqualTo("2024-01-01T00:00:00Z");
        assertThat(json.get("endDate").asText()).isEqualTo("2025-01-01T00:00:00Z");
        assertThat(json.get("granularity").asText()).isEqualTo("1Day");
    }

    @Test
    void backfillPriceData_returnsFailure() throws Exception {
        BackFillResult backFillResult = BackFillResult.failure("BTC", "CRYPTO", "API rate limit exceeded");
        when(backFillTickerPriceDataUseCase.execute(any(BackFillTickerPriceDataCommand.class)))
            .thenReturn(backFillResult);

        ToolResponse response = marketDataTools.backfillPriceData(
            "BTC", "CRYPTO",
            "2024-01-01T00:00:00Z", "2025-01-01T00:00:00Z",
            Optional.of("1Day")
        );

        assertThat(response.isError()).isFalse();
        String result = response.firstContent().asText().text();
        JsonNode json = objectMapper.readTree(result);
        assertThat(json.get("success").asBoolean()).isFalse();
        assertThat(json.get("errorMessage").asText()).isEqualTo("API rate limit exceeded");
    }

    @Test
    void queryMarketEvents_newsEventIncludesHeadline() throws Exception {
        MarketEvent event = new MarketEvent(
            Instant.parse("2025-03-15T10:00:00Z"),
            EventType.NEWS,
            "finnhub",
            "AAPL",
            Map.of("headline", "Apple announces record Q1 earnings", "category", "company")
        );
        when(queryMarketEventsUseCase.execute(any(QueryMarketEventsQuery.class)))
            .thenReturn(List.of(event));

        // excludeRawData=true — headline should still be extracted
        ToolResponse response = marketDataTools.queryMarketEvents(
            "2025-01-01T00:00:00Z",
            "2025-06-01T00:00:00Z",
            Optional.of("AAPL"),
            Optional.of("NEWS"),
            Optional.of(10),
            Optional.of(true)
        );

        assertThat(response.isError()).isFalse();
        String result = response.firstContent().asText().text();
        JsonNode json = objectMapper.readTree(result);
        JsonNode item = json.get("items").get(0);
        assertThat(item.get("eventType").asText()).isEqualTo("NEWS");
        assertThat(item.get("headline").asText()).isEqualTo("Apple announces record Q1 earnings");
        assertThat(item.has("data")).isTrue();
        assertThat(item.get("data").isNull()).isTrue(); // raw data excluded
        assertThat(item.get("closePrice").isNull()).isTrue(); // not a PRICE event
    }

    @Test
    void queryMarketEvents_returnsErrorForInvalidDate() {
        ToolResponse response = marketDataTools.queryMarketEvents(
            "bad-date",
            "2025-06-01T00:00:00Z",
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty()
        );

        assertThat(response.isError()).isTrue();
        String errorText = response.firstContent().asText().text();
        assertThat(errorText).contains("Invalid date format");
    }

    /**
     * The enum is validated before the use case runs. While the check lived inside the
     * "no data available" backfill branch, an invalid investmentType was rejected only when the
     * range happened to be missing from storage and accepted silently otherwise — the same input
     * passing or failing on unrelated cache state.
     *
     * <p>Data availability is stubbed TRUE on purpose: with no data the old code reached the
     * backfill branch and errored too, so this test would not have discriminated.
     */
    @Test
    void getTickerPerformance_rejectsInvalidInvestmentType_evenWhenDataIsAvailable() {
        when(mcpUserContext.getUserId()).thenReturn(new com.quorumtrading.model.valueobject.UserId(java.util.UUID.randomUUID()));
        when(getTickerPerformanceUseCase.execute(any())).thenReturn(
            new com.quorumtrading.application.usecase.result.TickerPerformanceResult(
                "AAPL", true, java.time.Instant.parse("2025-01-01T00:00:00Z"),
                java.time.Instant.parse("2025-01-10T00:00:00Z"),
                new java.math.BigDecimal("100"), new java.math.BigDecimal("110"),
                new java.math.BigDecimal("112"), new java.math.BigDecimal("99"),
                new java.math.BigDecimal("10"), new java.math.BigDecimal("10"), 10, null, null, null));

        ToolResponse response = marketDataTools.getTickerPerformance(
            "AAPL", "2025-01-01", "2025-01-10", Optional.of("EQUITY"));

        assertThat(response.isError()).isTrue();
        assertThat(response.firstContent().asText().text()).contains("Invalid investmentType");
    }

    // --- get_price_coverage: one validator, and a measurable window ---

    private void authenticated() {
        when(mcpUserContext.getUserId()).thenReturn(
            new com.quorumtrading.model.valueobject.UserId(java.util.UUID.randomUUID()));
    }

    /**
     * The divergence this cluster of fixes exists to end: coverage rejected {@code ASML:XAMS}
     * outright while {@code backfill_price_data} accepted it, called three live providers and
     * blamed coverage. Same string, two answers.
     */
    @Test
    void getPriceCoverage_acceptsVenueQualifiedSymbolsLikeBackfillDoes() {
        authenticated();
        when(historicalPricePort.getCoverage(any(com.quorumtrading.model.valueobject.Symbol.class)))
            .thenReturn(com.quorumtrading.model.valueobject.PriceCoverage.empty("ASML:XAMS"));

        ToolResponse response = marketDataTools.getPriceCoverage(
            "ASML:XAMS", Optional.empty(), Optional.empty());

        assertThat(response.isError()).isFalse();
    }

    /**
     * {@code BTC-USD} passed the old check because Symbol's grammar permits a hyphen for US share
     * classes, so coverage told the user to run a backfill that can never succeed.
     */
    @Test
    void getPriceCoverage_refusesAPairFormInsteadOfSendingTheUserToBackfillIt() {
        authenticated();

        ToolResponse response = marketDataTools.getPriceCoverage(
            "BTC-USD", Optional.empty(), Optional.empty());

        assertThat(response.isError()).isTrue();
        String text = response.firstContent().asText().text();
        assertThat(text).contains("trading pair").contains("BTC");
        assertThat(text)
            .as("recommending the backfill is the defect, not the fix")
            .doesNotContain("run backfill_price_data first");
    }

    @Test
    void getPriceCoverage_windowReportsExpectedVsCoveredDays() throws Exception {
        authenticated();
        var coverage = new com.quorumtrading.model.valueobject.PriceCoverage(
            "BTC", 2, 2, java.time.Instant.parse("2026-01-01T00:00:00Z"),
            java.time.Instant.parse("2026-01-05T00:00:00Z"), List.of("kraken"))
            .withWindow(com.quorumtrading.model.valueobject.PriceCoverage.buildWindow(
                java.time.LocalDate.of(2026, 1, 1), java.time.LocalDate.of(2026, 1, 5),
                InvestmentType.CRYPTO,
                java.util.Set.of(java.time.LocalDate.of(2026, 1, 1), java.time.LocalDate.of(2026, 1, 5))));
        when(historicalPricePort.getCoverage(any(com.quorumtrading.model.valueobject.Symbol.class),
            any(java.time.LocalDate.class), any(java.time.LocalDate.class))).thenReturn(coverage);

        ToolResponse response = marketDataTools.getPriceCoverage(
            "BTC", Optional.of("2026-01-01"), Optional.of("2026-01-05"));

        assertThat(response.isError()).isFalse();
        JsonNode json = objectMapper.readTree(response.firstContent().asText().text());
        assertThat(json.get("window").get("expectedDays").asLong()).isEqualTo(5);
        assertThat(json.get("window").get("coveredDays").asLong()).isEqualTo(2);
        assertThat(json.get("window").get("gaps")).hasSize(1);
    }

    @Test
    void getPriceCoverage_halfAWindowIsRejectedRatherThanSilentlyIgnored() {
        authenticated();

        ToolResponse response = marketDataTools.getPriceCoverage(
            "BTC", Optional.of("2026-01-01"), Optional.empty());

        assertThat(response.isError()).isTrue();
        assertThat(response.firstContent().asText().text()).contains("must be given together");
    }

    @Test
    void getPriceCoverage_rejectsABackwardsWindow() {
        authenticated();

        ToolResponse response = marketDataTools.getPriceCoverage(
            "BTC", Optional.of("2026-01-05"), Optional.of("2026-01-01"));

        assertThat(response.isError()).isTrue();
        assertThat(response.firstContent().asText().text()).contains("must not be before");
    }

    @Test
    void getPriceCoverage_rejectsAnUnparseableDate() {
        authenticated();

        ToolResponse response = marketDataTools.getPriceCoverage(
            "BTC", Optional.of("last tuesday"), Optional.of("2026-01-05"));

        assertThat(response.isError()).isTrue();
        assertThat(response.firstContent().asText().text()).contains("ISO-8601");
    }

    /**
     * The tool ran the backfill that produces the best error in the product and replaced it with a
     * fixed sentence blaming "the provider".
     */
    @Test
    void getIndicators_carriesTheBackfillsOwnDiagnosisRatherThanAGenericOne() throws Exception {
        when(historicalPricePort.getHistoricalPrices(any(), eq(7))).thenReturn(List.of());
        when(indicatorEnrichmentService.calculateIndicators(any()))
            .thenReturn(new IndicatorContext(Map.of()));
        when(backFillTickerPriceDataUseCase.execute(any(BackFillTickerPriceDataCommand.class)))
            .thenReturn(BackFillResult.failure("FAKECOIN", "CRYPTO",
                "No price bars returned for FAKECOIN. Tried 2 configured provider(s): kraken, coinbase"));

        ToolResponse response = marketDataTools.getIndicators("FAKECOIN", "CRYPTO");

        JsonNode json = objectMapper.readTree(response.firstContent().asText().text());
        assertThat(json.get("autoBackfillNote").asText())
            .contains("kraken, coinbase")
            .contains("Tried 2 configured provider(s)");
    }

    @Test
    void getIndicators_saysWhenEverythingReturnedWasAlreadyStored() throws Exception {
        when(historicalPricePort.getHistoricalPrices(any(), eq(7))).thenReturn(List.of());
        when(indicatorEnrichmentService.calculateIndicators(any()))
            .thenReturn(new IndicatorContext(Map.of()));
        when(backFillTickerPriceDataUseCase.execute(any(BackFillTickerPriceDataCommand.class)))
            .thenReturn(BackFillResult.success("AAPL", "STOCK", 0, 42, 0));

        ToolResponse response = marketDataTools.getIndicators("AAPL", "STOCK");

        JsonNode json = objectMapper.readTree(response.firstContent().asText().text());
        assertThat(json.get("autoBackfillNote").asText())
            .as("'no data available' and 'we already had it all' are different findings")
            .contains("already stored");
    }

    @Test
    void getIndicators_refusesAPairFormBeforeCallingAnything() {
        ToolResponse response = marketDataTools.getIndicators("BTC-USD", "CRYPTO");

        assertThat(response.isError()).isTrue();
        assertThat(response.firstContent().asText().text()).contains("trading pair");
    }
}
