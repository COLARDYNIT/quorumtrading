package com.quorumtrading.adapter.rest.resource;

import com.quorumtrading.application.usecase.ResolveCurrentUserUseCase;
import com.quorumtrading.application.usecase.investmentplan.GetInvestmentAllocationUseCase;
import com.quorumtrading.application.usecase.investmentplan.ListInvestmentAllocationsUseCase;
import com.quorumtrading.model.entity.InvestmentAllocation;
import com.quorumtrading.model.enums.AllocationAction;
import com.quorumtrading.model.enums.AllocationStatus;
import com.quorumtrading.model.valueobject.InvestmentAllocationId;
import com.quorumtrading.model.valueobject.InvestmentPlanId;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.SimulationId;
import com.quorumtrading.model.valueobject.StrategyId;
import com.quorumtrading.model.valueobject.UserId;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The recurring-investment audit trail, previously untested at the REST layer.
 *
 * <p>One allocation row is written per plan tick whatever the outcome — executed, skipped, failed —
 * and it is the only record a user has of money moving on their behalf while they were not
 * watching. A skip has to stay legible as a skip, with its reason intact: the dog-fooding sweep
 * found several plan-execution defects whose only visible trace would have been these rows.</p>
 */
@QuarkusTest
@DisplayName("InvestmentAllocationResource")
class InvestmentAllocationResourceTest {

    @InjectMock ListInvestmentAllocationsUseCase listUseCase;
    @InjectMock GetInvestmentAllocationUseCase getUseCase;
    @InjectMock ResolveCurrentUserUseCase resolveCurrentUserUseCase;

    private static final String EMAIL = "allocations@quorumtrading.local";
    private static final UserId USER_ID = UserId.generate();

    @BeforeEach
    void setUp() {
        when(resolveCurrentUserUseCase.execute(EMAIL)).thenReturn(USER_ID);
    }

    private InvestmentAllocation executed(InvestmentAllocationId id) {
        return InvestmentAllocation.create(
                id, InvestmentPlanId.generate(), USER_ID, Instant.parse("2026-06-01T06:00:00Z"),
                AllocationStatus.EXECUTED,
                Money.usd(new BigDecimal("250.00")), Money.usd(new BigDecimal("250.00")),
                StrategyId.generate(), "picked the most recent strategy", BigDecimal.ZERO,
                AllocationAction.NEW_SIMULATION, new SimulationId(UUID.randomUUID()), null,
                List.of(), null, Instant.parse("2026-06-01T06:00:05Z"));
    }

    private InvestmentAllocation skipped(InvestmentAllocationId id, String reason) {
        return InvestmentAllocation.create(
                id, InvestmentPlanId.generate(), USER_ID, Instant.parse("2026-06-01T06:00:00Z"),
                AllocationStatus.SKIPPED_INSUFFICIENT_FUNDS,
                Money.usd(new BigDecimal("250.00")), null,
                null, null, null, null, null, null, List.of(), reason,
                Instant.parse("2026-06-01T06:00:05Z"));
    }

    @Test
    @DisplayName("lists the user's allocations")
    void shouldListAllocations() {
        InvestmentAllocationId id = InvestmentAllocationId.generate();
        when(listUseCase.forUser(any(UserId.class), anyInt())).thenReturn(List.of(executed(id)));

        given()
            .cookie("dev_session", EMAIL)
        .when()
            .get("/api/v1/investment-allocations")
        .then()
            .statusCode(200)
            .body("$", hasSize(1))
            .body("[0].id", equalTo(id.value().toString()))
            .body("[0].status", equalTo("EXECUTED"));
    }

    @Test
    @DisplayName("the limit is passed through rather than ignored")
    void shouldHonourTheLimitParameter() {
        when(listUseCase.forUser(any(UserId.class), anyInt())).thenReturn(List.of());

        given()
            .cookie("dev_session", EMAIL)
            .queryParam("limit", 5)
        .when()
            .get("/api/v1/investment-allocations")
        .then()
            .statusCode(200);

        verify(listUseCase).forUser(USER_ID, 5);
    }

    @Test
    @DisplayName("omitting the limit uses the documented default of 100")
    void shouldDefaultTheLimit() {
        when(listUseCase.forUser(any(UserId.class), anyInt())).thenReturn(List.of());

        given()
            .cookie("dev_session", EMAIL)
        .when()
            .get("/api/v1/investment-allocations")
        .then()
            .statusCode(200);

        verify(listUseCase).forUser(USER_ID, 100);
    }

    @Test
    @DisplayName("a skipped tick keeps its reason — the row is the only trace of a missed contribution")
    void shouldExposeTheSkipReason() {
        InvestmentAllocationId id = InvestmentAllocationId.generate();
        when(getUseCase.getOwnedBy(any(InvestmentAllocationId.class), any(UserId.class)))
                .thenReturn(skipped(id, "Broker available balance $12.00 is less than the plan amount $250.00"));

        given()
            .cookie("dev_session", EMAIL)
        .when()
            .get("/api/v1/investment-allocations/" + id.value())
        .then()
            .statusCode(200)
            .body("status", equalTo("SKIPPED_INSUFFICIENT_FUNDS"))
            .body("failureReason", equalTo(
                    "Broker available balance $12.00 is less than the plan amount $250.00"));
    }

    @Test
    @DisplayName("another user's allocation reads as absent")
    void shouldHideAnotherUsersAllocation() {
        when(getUseCase.getOwnedBy(any(InvestmentAllocationId.class), any(UserId.class)))
                .thenThrow(new SecurityException("not yours"));

        given()
            .cookie("dev_session", EMAIL)
        .when()
            .get("/api/v1/investment-allocations/" + UUID.randomUUID())
        .then()
            .statusCode(404);
    }

    @Test
    @DisplayName("an unknown allocation is a 404")
    void shouldReturnNotFoundForMissingAllocation() {
        when(getUseCase.getOwnedBy(any(InvestmentAllocationId.class), any(UserId.class)))
                .thenThrow(new NoSuchElementException("gone"));

        given()
            .cookie("dev_session", EMAIL)
        .when()
            .get("/api/v1/investment-allocations/" + UUID.randomUUID())
        .then()
            .statusCode(404);
    }

    @Test
    @DisplayName("a malformed id is a 400, distinct from a well-formed id that does not exist")
    void shouldRejectAMalformedId() {
        given()
            .cookie("dev_session", EMAIL)
        .when()
            .get("/api/v1/investment-allocations/not-a-uuid")
        .then()
            .statusCode(400);

        verify(getUseCase, never()).getOwnedBy(any(), any());
    }

    @Test
    @DisplayName("the audit trail is not public")
    void shouldRequireAuthentication() {
        given()
        .when()
            .get("/api/v1/investment-allocations")
        .then()
            .statusCode(401);
    }
}
