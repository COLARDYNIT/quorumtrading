package com.quorumtrading.adapter.rest.resource;

import com.quorumtrading.application.usecase.GetDividendInfoUseCase;
import com.quorumtrading.model.valueobject.DividendInfo;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@QuarkusTest
class DividendResourceTest {

    @InjectMock
    GetDividendInfoUseCase getDividendInfoUseCase;

    @Test
    void shouldReturn200WithItems_whenSymbolsProvided() {
        Map<String, DividendInfo> mockResult = Map.of(
            "AAPL", DividendInfo.withDividends("AAPL", new BigDecimal("0.96"), 4),
            "MSFT", DividendInfo.withDividends("MSFT", new BigDecimal("3.00"), 4)
        );
        when(getDividendInfoUseCase.execute(any())).thenReturn(mockResult);

        given()
            .queryParam("symbols", "AAPL,MSFT")
        .when()
            .get("/api/v1/dividends/info")
        .then()
            .statusCode(200)
            .body("items", hasSize(2))
            .body("items.ticker", hasItems("AAPL", "MSFT"))
            .body("items.find { it.ticker == 'AAPL' }.paysDividends", is(true))
            .body("items.find { it.ticker == 'AAPL' }.dividendCountLastYear", is(4))
            .body("items.find { it.ticker == 'MSFT' }.paysDividends", is(true));
    }

    @Test
    void shouldReturn400WithProblemDetails_whenSymbolsParamIsMissing() {
        given()
        .when()
            .get("/api/v1/dividends/info")
        .then()
            .statusCode(400)
            .body("status", is(400))
            .body("title", is("Bad Request"))
            .body("detail", is("symbols query parameter is required"));
    }

    @Test
    void shouldReturn400WithProblemDetails_whenSymbolsParamIsEmpty() {
        given()
            .queryParam("symbols", "")
        .when()
            .get("/api/v1/dividends/info")
        .then()
            .statusCode(400)
            .body("status", is(400))
            .body("title", is("Bad Request"))
            .body("detail", is("symbols query parameter is required"));
    }

    @Test
    void shouldReturn400WithProblemDetails_whenSymbolsAreOnlyCommas() {
        given()
            .queryParam("symbols", ", ,")
        .when()
            .get("/api/v1/dividends/info")
        .then()
            .statusCode(400)
            .body("status", is(400))
            .body("detail", is("At least one ticker symbol is required"));
    }
}
