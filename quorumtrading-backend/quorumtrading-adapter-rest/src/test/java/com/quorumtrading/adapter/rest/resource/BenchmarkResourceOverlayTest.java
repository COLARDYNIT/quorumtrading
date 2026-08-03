package com.quorumtrading.adapter.rest.resource;

import com.quorumtrading.application.usecase.CalculateBenchmarkOverlaysUseCase;
import com.quorumtrading.model.valueobject.BuyAndHoldDataPoint;
import com.quorumtrading.model.valueobject.OverlayResult;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@QuarkusTest
class BenchmarkResourceOverlayTest {

    @InjectMock
    CalculateBenchmarkOverlaysUseCase calculateBenchmarkOverlaysUseCase;

    private static final String TEST_EMAIL = "test@quorumtrading.local";
    private static final String BENCHMARK_ID = "550e8400-e29b-41d4-a716-446655440000";

    @Test
    void shouldReturnOverlaysForValidRequest() {
        List<OverlayResult> mockResults = List.of(
            OverlayResult.ready("SPY", "STOCK",
                List.of(
                    new BuyAndHoldDataPoint(Instant.parse("2024-01-02T00:00:00Z"), new BigDecimal("100000.00")),
                    new BuyAndHoldDataPoint(Instant.parse("2024-06-30T00:00:00Z"), new BigDecimal("112340.00"))
                ),
                new BigDecimal("12.3400"))
        );
        when(calculateBenchmarkOverlaysUseCase.execute(any())).thenReturn(mockResults);

        given()
            .cookie("dev_session", TEST_EMAIL)
            .queryParam("symbols", "SPY")
        .when()
            .get("/api/v1/benchmarks/{id}/overlays", BENCHMARK_ID)
        .then()
            .statusCode(200)
            .body("$.size()", is(1))
            .body("[0].symbol", equalTo("SPY"))
            .body("[0].assetType", equalTo("STOCK"))
            .body("[0].status", equalTo("READY"))
            .body("[0].dataPoints.size()", is(2))
            .body("[0].returnPercent", comparesEqualTo(12.34f));
    }

    @Test
    void shouldReturnNoDataOverlay() {
        List<OverlayResult> mockResults = List.of(
            OverlayResult.noData("XYZ", "STOCK", "No price data available for XYZ")
        );
        when(calculateBenchmarkOverlaysUseCase.execute(any())).thenReturn(mockResults);

        given()
            .cookie("dev_session", TEST_EMAIL)
            .queryParam("symbols", "XYZ")
        .when()
            .get("/api/v1/benchmarks/{id}/overlays", BENCHMARK_ID)
        .then()
            .statusCode(200)
            .body("[0].status", equalTo("NO_DATA"))
            .body("[0].errorMessage", containsString("XYZ"))
            .body("[0].dataPoints.size()", is(0));
    }

    @Test
    void shouldReturn400WhenSymbolsQueryParamMissing() {
        given()
            .cookie("dev_session", TEST_EMAIL)
        .when()
            .get("/api/v1/benchmarks/{id}/overlays", BENCHMARK_ID)
        .then()
            .statusCode(400);
    }

    @Test
    void shouldReturn400WhenSymbolsQueryParamEmpty() {
        given()
            .cookie("dev_session", TEST_EMAIL)
            .queryParam("symbols", "")
        .when()
            .get("/api/v1/benchmarks/{id}/overlays", BENCHMARK_ID)
        .then()
            .statusCode(400);
    }

    @Test
    void shouldReturn400WhenBenchmarkIdInvalid() {
        given()
            .cookie("dev_session", TEST_EMAIL)
            .queryParam("symbols", "SPY")
        .when()
            .get("/api/v1/benchmarks/{id}/overlays", "not-a-uuid")
        .then()
            .statusCode(400);
    }

    @Test
    void shouldAcceptMultipleSymbols() {
        List<OverlayResult> mockResults = List.of(
            OverlayResult.ready("SPY", "STOCK", List.of(), new BigDecimal("10.00")),
            OverlayResult.ready("BTC", "CRYPTO", List.of(), new BigDecimal("50.00"))
        );
        when(calculateBenchmarkOverlaysUseCase.execute(any())).thenReturn(mockResults);

        given()
            .cookie("dev_session", TEST_EMAIL)
            .queryParam("symbols", "SPY,BTC")
        .when()
            .get("/api/v1/benchmarks/{id}/overlays", BENCHMARK_ID)
        .then()
            .statusCode(200)
            .body("$.size()", is(2))
            .body("[0].symbol", equalTo("SPY"))
            .body("[1].symbol", equalTo("BTC"));
    }

    @Test
    void shouldAcceptOptionalInitialCapital() {
        List<OverlayResult> mockResults = List.of(
            OverlayResult.ready("SPY", "STOCK", List.of(), new BigDecimal("10.00"))
        );
        when(calculateBenchmarkOverlaysUseCase.execute(any())).thenReturn(mockResults);

        given()
            .cookie("dev_session", TEST_EMAIL)
            .queryParam("symbols", "SPY")
            .queryParam("initialCapital", "50000")
        .when()
            .get("/api/v1/benchmarks/{id}/overlays", BENCHMARK_ID)
        .then()
            .statusCode(200)
            .body("$.size()", is(1));
    }
}
