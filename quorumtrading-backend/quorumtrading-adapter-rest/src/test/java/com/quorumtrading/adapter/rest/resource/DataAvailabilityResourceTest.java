package com.quorumtrading.adapter.rest.resource;

import com.quorumtrading.adapter.rest.security.CurrentUserProvider;
import com.quorumtrading.application.usecase.AnalyzeDataAvailabilityUseCase;
import com.quorumtrading.application.usecase.QueryBackfillProgressUseCase;
import com.quorumtrading.application.usecase.RequestBackfillUseCase;
import com.quorumtrading.application.usecase.result.RequestBackfillResult;
import com.quorumtrading.model.enums.BackfillStatus;
import com.quorumtrading.model.enums.EventType;
import com.quorumtrading.model.enums.InvestmentType;
import com.quorumtrading.model.marketdata.BackfillProgress;
import com.quorumtrading.model.marketdata.DataAvailability;
import com.quorumtrading.model.marketdata.DataCoverageMetrics;
import com.quorumtrading.model.valueobject.Symbol;
import com.quorumtrading.model.valueobject.TimeRange;
import com.quorumtrading.model.valueobject.UserId;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.when;

@QuarkusTest
// The backfill endpoint is @Authenticated, which the security layer enforces against the
// real SecurityIdentity -- mocking CurrentUserProvider alone is not enough to get past it.
@TestSecurity(user = "test@quorumtrading.local")
class DataAvailabilityResourceTest {

    @InjectMock
    AnalyzeDataAvailabilityUseCase analyzeService;

    @InjectMock
    RequestBackfillUseCase backfillService;

    @InjectMock
    QueryBackfillProgressUseCase progressService;

    @InjectMock
    CurrentUserProvider currentUserProvider;

    @InjectMock
    com.quorumtrading.application.service.ClassifyGapsService classifyGapsService;

    @InjectMock
    com.quorumtrading.application.usecase.marketdata.GetMarketDataStatusUseCase marketDataStatusUseCase;

    private static final UserId TEST_USER_ID = UserId.generate();
    private static final Symbol BTC = new Symbol("BTC", InvestmentType.CRYPTO);
    private static final Symbol ETH = new Symbol("ETH", InvestmentType.CRYPTO);

    @BeforeEach
    void setUp() {
        when(currentUserProvider.getCurrentUserIdOptional()).thenReturn(Optional.of(TEST_USER_ID));
        // Pass raw gaps through as ACTIONABLE with a stub provider so the resource's
        // gap-enrichment step has data to map; the classification logic itself is
        // unit-tested in ClassifyGapsServiceTest.
        when(classifyGapsService.classify(any())).thenAnswer(inv -> {
            java.util.List<com.quorumtrading.model.marketdata.MarketDataGap> raw = inv.getArgument(0);
            return raw.stream()
                .map(g -> new com.quorumtrading.application.usecase.result.ClassifiedGap(
                    g, com.quorumtrading.model.enums.GapClassification.ACTIONABLE, "alpaca", 1))
                .toList();
        });
    }

    // --- Coverage endpoint tests ---

    @Test
    @DisplayName("GET /coverage - should return 100% coverage when all data present")
    void shouldReturn100PercentCoverage_WhenAllDataPresent() {
        DataCoverageMetrics day1 = new DataCoverageMetrics(
            new BigDecimal("100"),
            Map.of(EventType.PRICE, new BigDecimal("100"), EventType.NEWS, new BigDecimal("100")),
            Map.of(BTC, new BigDecimal("100"))
        );
        DataAvailability availability = DataAvailability.of(
            TimeRange.of(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 3)),
            Set.of(BTC),
            Set.of(EventType.PRICE, EventType.NEWS),
            Map.of(
                LocalDate.of(2024, 1, 1), day1,
                LocalDate.of(2024, 1, 2), day1,
                LocalDate.of(2024, 1, 3), day1
            )
        );
        when(analyzeService.execute(any())).thenReturn(availability);

        given()
            .queryParam("symbols", "BTC")
            .queryParam("dataTypes", "PRICE,NEWS")
            .queryParam("from", "2024-01-01")
            .queryParam("to", "2024-01-03")
        .when()
            .get("/api/v1/data-availability/coverage")
        .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("overallCoverage", equalTo(100.0f))
            .body("isSufficient", is(true))
            .body("gaps", hasSize(0))
            .body("dailyCoverage", hasSize(3));
    }

    @Test
    @DisplayName("GET /coverage - should return partial coverage when data is missing")
    void shouldReturnPartialCoverage_WhenDataIsMissing() {
        DataCoverageMetrics day = new DataCoverageMetrics(
            new BigDecimal("50"),
            Map.of(EventType.PRICE, new BigDecimal("100"), EventType.NEWS, new BigDecimal("0")),
            Map.of(BTC, new BigDecimal("50"))
        );
        DataAvailability availability = DataAvailability.of(
            TimeRange.of(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 2)),
            Set.of(BTC),
            Set.of(EventType.PRICE, EventType.NEWS),
            Map.of(LocalDate.of(2024, 1, 1), day, LocalDate.of(2024, 1, 2), day)
        );
        when(analyzeService.execute(any())).thenReturn(availability);

        given()
            .queryParam("symbols", "BTC")
            .queryParam("dataTypes", "PRICE,NEWS")
            .queryParam("from", "2024-01-01")
            .queryParam("to", "2024-01-02")
        .when()
            .get("/api/v1/data-availability/coverage")
        .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("overallCoverage", lessThan(100.0f))
            .body("isSufficient", is(false))
            .body("gaps", not(empty()));
    }

    @Test
    @DisplayName("GET /coverage - should handle multiple symbols")
    void shouldHandleMultipleSymbols() {
        DataCoverageMetrics day = new DataCoverageMetrics(
            new BigDecimal("100"),
            Map.of(EventType.PRICE, new BigDecimal("100")),
            Map.of(BTC, new BigDecimal("100"), ETH, new BigDecimal("100"))
        );
        DataAvailability availability = DataAvailability.of(
            TimeRange.of(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 1)),
            Set.of(BTC, ETH),
            Set.of(EventType.PRICE),
            Map.of(LocalDate.of(2024, 1, 1), day)
        );
        when(analyzeService.execute(any())).thenReturn(availability);

        given()
            .queryParam("symbols", "BTC,ETH")
            .queryParam("dataTypes", "PRICE")
            .queryParam("from", "2024-01-01")
            .queryParam("to", "2024-01-01")
        .when()
            .get("/api/v1/data-availability/coverage")
        .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("overallCoverage", equalTo(100.0f))
            .body("perSymbolCoverage.BTC", equalTo(100.0f))
            .body("perSymbolCoverage.ETH", equalTo(100.0f))
            .body("isSufficient", is(true));
    }

    @Test
    @DisplayName("GET /coverage - should return 400 when missing required parameters")
    void shouldReturn400_WhenMissingRequiredParameters() {
        given()
            .queryParam("symbols", "BTC")
            .queryParam("dataTypes", "PRICE")
            .queryParam("to", "2024-01-31")
        .when()
            .get("/api/v1/data-availability/coverage")
        .then()
            .statusCode(400);
    }

    // --- Backfill endpoint tests ---

    @Test
    @DisplayName("POST /backfill - should create backfill job when data is insufficient")
    void shouldCreateBackfillJob_WhenDataIsInsufficient() {
        UUID jobId = UUID.randomUUID();
        when(backfillService.execute(any()))
            .thenReturn(RequestBackfillResult.backfillStarted(jobId, Instant.now()));

        given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "symbols": ["BTC"],
                    "dataTypes": ["PRICE", "NEWS"],
                    "from": "2024-01-01",
                    "to": "2024-01-03",
                    "forceBackfill": false
                }
                """)
        .when()
            .post("/api/v1/data-availability/backfill")
        .then()
            .statusCode(202)
            .contentType(ContentType.JSON)
            .body("backfillTriggered", is(true))
            .body("jobId", equalTo(jobId.toString()))
            .body("status", equalTo("PENDING"));
    }

    @Test
    @DisplayName("POST /backfill - should not create job when data is sufficient")
    void shouldNotCreateBackfillJob_WhenDataIsSufficient() {
        when(backfillService.execute(any()))
            .thenReturn(RequestBackfillResult.noBackfillNeeded());

        given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "symbols": ["BTC"],
                    "dataTypes": ["PRICE", "NEWS"],
                    "from": "2024-01-01",
                    "to": "2024-01-02",
                    "forceBackfill": false
                }
                """)
        .when()
            .post("/api/v1/data-availability/backfill")
        .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("backfillTriggered", is(false))
            .body("jobId", nullValue());
    }

    @Test
    @DisplayName("POST /backfill - should force backfill when forceBackfill=true")
    void shouldForceBackfill_WhenForceBackfillIsTrue() {
        UUID jobId = UUID.randomUUID();
        when(backfillService.execute(any()))
            .thenReturn(RequestBackfillResult.backfillStarted(jobId, Instant.now()));

        given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "symbols": ["BTC"],
                    "dataTypes": ["PRICE"],
                    "from": "2024-01-01",
                    "to": "2024-01-01",
                    "forceBackfill": true
                }
                """)
        .when()
            .post("/api/v1/data-availability/backfill")
        .then()
            .statusCode(202)
            .body("backfillTriggered", is(true))
            .body("jobId", notNullValue());
    }

    @Test
    @DisplayName("POST /backfill - should return 400 when validation fails")
    void shouldReturn400_WhenValidationFails() {
        given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "symbols": [],
                    "dataTypes": ["PRICE"],
                    "from": "2024-01-01"
                }
                """)
        .when()
            .post("/api/v1/data-availability/backfill")
        .then()
            .statusCode(400);
    }

    // --- Backfill status endpoint tests ---

    @Test
    @DisplayName("GET /backfill/{jobId} - should return job status when job exists")
    void shouldReturnJobStatus_WhenJobExists() {
        UUID jobId = UUID.randomUUID();
        when(progressService.execute(any()))
            .thenReturn(new BackfillProgress(
                jobId, BackfillStatus.PENDING, 0, Instant.now(), null, null, 100, 0
            ));

        given()
            .pathParam("jobId", jobId.toString())
        .when()
            .get("/api/v1/data-availability/backfill/{jobId}")
        .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("jobId", equalTo(jobId.toString()))
            .body("status", equalTo("PENDING"))
            .body("progressPercentage", equalTo(0));
    }

    @Test
    @DisplayName("GET /backfill/{jobId} - should return job progress when in progress")
    void shouldReturnJobProgress_WhenJobInProgress() {
        UUID jobId = UUID.randomUUID();
        when(progressService.execute(any()))
            .thenReturn(new BackfillProgress(
                jobId, BackfillStatus.IN_PROGRESS, 50, Instant.now(), null, null, 100, 50
            ));

        given()
            .pathParam("jobId", jobId.toString())
        .when()
            .get("/api/v1/data-availability/backfill/{jobId}")
        .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("jobId", equalTo(jobId.toString()))
            .body("status", equalTo("IN_PROGRESS"))
            .body("progressPercentage", equalTo(50));
    }

    @Test
    @DisplayName("GET /backfill/{jobId} - should return 404 when job not found")
    void shouldReturn404_WhenJobNotFound() {
        when(progressService.execute(any())).thenReturn(null);

        given()
            .pathParam("jobId", "00000000-0000-0000-0000-000000000000")
        .when()
            .get("/api/v1/data-availability/backfill/{jobId}")
        .then()
            .statusCode(404);
    }

    @Test
    @DisplayName("GET /backfill/{jobId} - should return 400 when jobId is invalid")
    void shouldReturn400_WhenJobIdIsInvalid() {
        given()
            .pathParam("jobId", "invalid-uuid")
        .when()
            .get("/api/v1/data-availability/backfill/{jobId}")
        .then()
            .statusCode(400);
    }

    // --- Market-data status endpoint tests ---

    private static com.quorumtrading.application.usecase.marketdata.MarketDataStatus keylessInstallStatus() {
        return new com.quorumtrading.application.usecase.marketdata.MarketDataStatus(
            true,   // crypto served keyless
            false,  // no usable stock provider
            java.util.List.of(
                new com.quorumtrading.application.usecase.marketdata.MarketDataStatus.ProviderStatus(
                    "alpaca", true, false, java.util.List.of("CRYPTO", "STOCK"),
                    java.util.List.of("BACKFILL", "CANDLES"), null),
                new com.quorumtrading.application.usecase.marketdata.MarketDataStatus.ProviderStatus(
                    "kraken", true, true, java.util.List.of("CRYPTO"),
                    java.util.List.of("BACKFILL", "CANDLES"), null)
            ));
    }

    @Test
    @DisplayName("GET /market-data-status - reports keyless install honestly: crypto usable, stocks need a key")
    void shouldReportKeylessInstallProviderState() {
        when(marketDataStatusUseCase.get(false)).thenReturn(keylessInstallStatus());

        given()
        .when()
            .get("/api/v1/data-availability/market-data-status")
        .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("cryptoUsable", is(true))
            .body("stockUsable", is(false))
            .body("hint", containsString("No usable stock provider"))
            .body("providers", hasSize(2))
            .body("providers[0].name", equalTo("alpaca"))
            .body("providers[0].usable", is(false))
            .body("providers[0].credentialed", is(false))
            .body("providers[0].detail", containsString("Set its API key"))
            .body("providers[1].name", equalTo("kraken"))
            .body("providers[1].usable", is(true));
    }

    @Test
    @DisplayName("GET /market-data-status - does not probe by default, probes when asked")
    void shouldOnlyProbeWhenExplicitlyRequested() {
        when(marketDataStatusUseCase.get(anyBoolean())).thenReturn(keylessInstallStatus());

        given()
        .when()
            .get("/api/v1/data-availability/market-data-status")
        .then()
            .statusCode(200);
        org.mockito.Mockito.verify(marketDataStatusUseCase).get(false);

        given()
            .queryParam("probe", "true")
        .when()
            .get("/api/v1/data-availability/market-data-status")
        .then()
            .statusCode(200);
        org.mockito.Mockito.verify(marketDataStatusUseCase).get(true);
    }
}
