package com.quorumtrading.adapter.rest.resource;

import com.quorumtrading.application.usecase.GetLLMUsageStatisticsUseCase;
import com.quorumtrading.application.usecase.ResolveCurrentUserUseCase;
import com.quorumtrading.application.usecase.dto.LLMUsageStatistics;
import com.quorumtrading.model.valueobject.UserId;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The AI spend report, previously untested.
 *
 * <p>This is the only place a user sees what the agents have cost them, so the figure has to be
 * theirs: the endpoint takes no id, deriving the account from the session, which makes a wrong
 * user resolution invisible rather than an error. Zero spend must also render as a real zero
 * rather than an empty body — a new account that opens this page should see 0.00, not a blank.</p>
 */
@QuarkusTest
@DisplayName("UsageResource")
class UsageResourceTest {

    @InjectMock GetLLMUsageStatisticsUseCase getLLMUsageStatisticsUseCase;
    @InjectMock ResolveCurrentUserUseCase resolveCurrentUserUseCase;

    private static final String EMAIL = "usage@quorumtrading.local";
    private static final UserId USER_ID = UserId.generate();

    @BeforeEach
    void setUp() {
        when(resolveCurrentUserUseCase.execute(EMAIL)).thenReturn(USER_ID);
    }

    @Test
    @DisplayName("returns the signed-in user's spend, broken down by day")
    void shouldReturnUsageStatistics() {
        when(getLLMUsageStatisticsUseCase.execute(USER_ID)).thenReturn(new LLMUsageStatistics(
                List.of(new LLMUsageStatistics.DailyUsage(
                        LocalDate.parse("2026-06-01"),
                        new BigDecimal("1.25"), new BigDecimal("1.50"), 12)),
                new BigDecimal("3.10"), new BigDecimal("9.40"), new BigDecimal("25.00")));

        given()
            .cookie("dev_session", EMAIL)
        .when()
            .get("/api/v1/usage/llm")
        .then()
            .statusCode(200)
            .body("dailyUsage", hasSize(1))
            .body("weeklyTotalUsd", equalTo(3.10f))
            .body("monthlyTotalUsd", equalTo(9.40f))
            .body("allTimeTotalUsd", equalTo(25.00f));
    }

    @Test
    @DisplayName("the report is scoped to the session, since the endpoint takes no id")
    void shouldScopeToTheSignedInUser() {
        when(getLLMUsageStatisticsUseCase.execute(USER_ID)).thenReturn(new LLMUsageStatistics(
                List.of(), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));

        given()
            .cookie("dev_session", EMAIL)
        .when()
            .get("/api/v1/usage/llm")
        .then()
            .statusCode(200);

        verify(getLLMUsageStatisticsUseCase).execute(USER_ID);
    }

    @Test
    @DisplayName("a new account sees zeros, not an empty response")
    void shouldRenderZeroSpendExplicitly() {
        when(getLLMUsageStatisticsUseCase.execute(USER_ID)).thenReturn(new LLMUsageStatistics(
                List.of(), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));

        given()
            .cookie("dev_session", EMAIL)
        .when()
            .get("/api/v1/usage/llm")
        .then()
            .statusCode(200)
            .body("dailyUsage", hasSize(0))
            .body("weeklyTotalUsd", equalTo(0))
            .body("allTimeTotalUsd", equalTo(0));
    }

    @Test
    @DisplayName("spend is not public")
    void shouldRequireAuthentication() {
        given()
        .when()
            .get("/api/v1/usage/llm")
        .then()
            .statusCode(401);
    }
}
