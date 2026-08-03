package com.quorumtrading.adapter.rest.resource;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

/**
 * Tests for {@code GET /api/v1/market-data/financial-metrics} error semantics.
 *
 * <p>In the isolated adapter-rest test context no {@code FinancialDataPort} bean is
 * registered, which exercises the "provider not available" path. That path used to
 * return 404 with an ad-hoc {@code {"error": ...}} body; it must now be a 503 with
 * an RFC 7807 Problem Details body (a missing provider is service unavailability,
 * not a missing resource).</p>
 */
@QuarkusTest
class MarketDataResourceFinancialMetricsTest {

    @Test
    void shouldReturn503ProblemDetails_whenFinancialDataProviderNotAvailable() {
        given()
            .queryParam("symbol", "AAPL")
        .when()
            .get("/api/v1/market-data/financial-metrics")
        .then()
            .statusCode(503)
            .body("status", is(503))
            .body("title", is("Service Unavailable"))
            .body("detail", containsString("Financial data provider not available"));
    }

    @Test
    void shouldReturn400ProblemDetails_whenSymbolMissing() {
        given()
        .when()
            .get("/api/v1/market-data/financial-metrics")
        .then()
            .statusCode(400)
            .body("status", is(400))
            .body("detail", containsString("symbol query parameter is required"));
    }
}
