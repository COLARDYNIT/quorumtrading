package com.quorumtrading.adapter.rest.resource;

import com.quorumtrading.application.port.out.PaymentPort;
import com.quorumtrading.application.usecase.GetUserBudgetUseCase;
import com.quorumtrading.application.usecase.ResolveCurrentUserUseCase;
import com.quorumtrading.application.usecase.TopUpBudgetUseCase;
import com.quorumtrading.application.usecase.UpdateDailySpendLimitUseCase;
import com.quorumtrading.application.usecase.dto.UserBudgetDto;
import com.quorumtrading.model.valueobject.BudgetStatus;
import com.quorumtrading.model.valueobject.UserId;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@QuarkusTest
class BudgetResourceTest {

    @InjectMock
    ResolveCurrentUserUseCase resolveCurrentUserUseCase;

    @InjectMock
    TopUpBudgetUseCase topUpBudgetUseCase;

    @InjectMock
    GetUserBudgetUseCase getUserBudgetUseCase;

    @InjectMock
    UpdateDailySpendLimitUseCase updateDailySpendLimitUseCase;

    @InjectMock
    PaymentPort paymentPort;

    private static final UserId TEST_USER_ID = UserId.generate();
    private static final String TEST_EMAIL = "test@quorumtrading.local";

    @BeforeEach
    void setUp() {
        when(resolveCurrentUserUseCase.execute(TEST_EMAIL)).thenReturn(TEST_USER_ID);
    }

    private UserBudgetDto createMockBudget() {
        return new UserBudgetDto(
                TEST_USER_ID.value().toString(),
                TEST_USER_ID.value().toString(),
                new BigDecimal("10.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new BigDecimal("10.00"),
                BigDecimal.ZERO,
                BudgetStatus.AVAILABLE,
                false,
                null,
                null,
                BigDecimal.ZERO,
                BigDecimal.ZERO
        );
    }

    @Test
    void mockTopUp_shouldReturn403_whenStripeIsConfigured() {
        when(paymentPort.isConfigured()).thenReturn(true);

        given()
            .cookie("dev_session", TEST_EMAIL)
            .contentType("application/json")
            .body("{\"amount\": 10}")
        .when()
            .post("/api/v1/budget/top-up")
        .then()
            .statusCode(403)
            .body("detail", equalTo("Mock top-up is disabled when payment provider is configured. Use POST /api/v1/payment/checkout instead."));
    }

    @Test
    void mockTopUp_shouldSucceed_whenStripeIsNotConfigured() {
        when(paymentPort.isConfigured()).thenReturn(false);
        when(topUpBudgetUseCase.execute(eq(TEST_USER_ID), any(BigDecimal.class))).thenReturn(createMockBudget());

        given()
            .cookie("dev_session", TEST_EMAIL)
            .contentType("application/json")
            .body("{\"amount\": 10}")
        .when()
            .post("/api/v1/budget/top-up")
        .then()
            .statusCode(200);

        verify(topUpBudgetUseCase).execute(eq(TEST_USER_ID), any(BigDecimal.class));
    }

    @Test
    void updateDailyLimit_shouldSucceed_withValidLimit() {
        when(getUserBudgetUseCase.execute(TEST_USER_ID)).thenReturn(createMockBudget());

        given()
            .cookie("dev_session", TEST_EMAIL)
            .contentType("application/json")
            .body("{\"dailySpendLimit\": 5.00}")
        .when()
            .put("/api/v1/budget/daily-limit")
        .then()
            .statusCode(200)
            .body("dailySpendLimit", nullValue());

        // Scale-insensitive: Jackson's BigDecimal scale for "5.00" differs across versions
        // (5.00 vs 5.0) and the use case doesn't care — compare numerically, not by equals().
        verify(updateDailySpendLimitUseCase).execute(eq(TEST_USER_ID),
            argThat(v -> v != null && v.compareTo(new BigDecimal("5.00")) == 0));
    }

    @Test
    void updateDailyLimit_shouldSucceed_withNullToClear() {
        when(getUserBudgetUseCase.execute(TEST_USER_ID)).thenReturn(createMockBudget());

        given()
            .cookie("dev_session", TEST_EMAIL)
            .contentType("application/json")
            .body("{\"dailySpendLimit\": null}")
        .when()
            .put("/api/v1/budget/daily-limit")
        .then()
            .statusCode(200);

        verify(updateDailySpendLimitUseCase).execute(eq(TEST_USER_ID), eq(null));
    }

    @Test
    void updateDailyLimit_shouldReturn400_whenLimitIsNegative() {
        doThrow(new IllegalArgumentException("Daily spend limit must be positive"))
            .when(updateDailySpendLimitUseCase).execute(eq(TEST_USER_ID), any());

        given()
            .cookie("dev_session", TEST_EMAIL)
            .contentType("application/json")
            .body("{\"dailySpendLimit\": -1}")
        .when()
            .put("/api/v1/budget/daily-limit")
        .then()
            .statusCode(400)
            .body("detail", equalTo("Daily spend limit must be positive"));
    }
}
