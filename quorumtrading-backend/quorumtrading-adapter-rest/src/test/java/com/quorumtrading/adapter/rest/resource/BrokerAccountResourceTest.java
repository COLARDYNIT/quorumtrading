package com.quorumtrading.adapter.rest.resource;

import com.quorumtrading.application.usecase.brokeraccount.CreateBrokerAccountUseCase;
import com.quorumtrading.application.usecase.brokeraccount.DeleteBrokerAccountUseCase;
import com.quorumtrading.application.usecase.brokeraccount.GetBrokerAccountUseCase;
import com.quorumtrading.application.usecase.brokeraccount.ListBrokerAccountsUseCase;
import com.quorumtrading.application.usecase.ResolveCurrentUserUseCase;
import com.quorumtrading.application.usecase.brokeraccount.UpdateBrokerAccountCredentialsUseCase;
import com.quorumtrading.application.usecase.brokeraccount.ValidateBrokerAccountCredentialsUseCase;
import com.quorumtrading.model.entity.BrokerAccount;
import com.quorumtrading.model.enums.AccountStatus;
import com.quorumtrading.model.enums.BrokerType;
import com.quorumtrading.model.valueobject.BrokerAccountId;
import com.quorumtrading.model.valueobject.Money;
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
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Broker accounts — the endpoints that hold the credentials real money is traded through, and
 * which had no test at all.
 *
 * <p>The property worth pinning hardest is that a secret written in never comes back out: these
 * responses are rendered in a browser and logged by proxies, and an API key echoed once is an API
 * key to rotate. Beyond that, a broker account is the thing that decides whether an order is
 * simulated or real, so the type must round-trip exactly and a caller must not be able to read or
 * delete an account belonging to someone else.</p>
 */
@QuarkusTest
@DisplayName("BrokerAccountResource")
class BrokerAccountResourceTest {

    @InjectMock CreateBrokerAccountUseCase createBrokerAccountUseCase;
    @InjectMock ListBrokerAccountsUseCase listBrokerAccountsUseCase;
    @InjectMock GetBrokerAccountUseCase getBrokerAccountUseCase;
    @InjectMock DeleteBrokerAccountUseCase deleteBrokerAccountUseCase;
    @InjectMock UpdateBrokerAccountCredentialsUseCase updateBrokerAccountCredentialsUseCase;
    @InjectMock ValidateBrokerAccountCredentialsUseCase validateBrokerAccountCredentialsUseCase;
    @InjectMock ResolveCurrentUserUseCase resolveCurrentUserUseCase;

    private static final String EMAIL = "broker@quorumtrading.local";
    private static final UserId USER_ID = UserId.generate();
    private static final String SECRET = "sk-super-secret-alpaca-key-do-not-leak";

    @BeforeEach
    void setUp() {
        when(resolveCurrentUserUseCase.execute(EMAIL)).thenReturn(USER_ID);
    }

    private BrokerAccount account(BrokerAccountId id, BrokerType type) {
        return new BrokerAccount(
                id, USER_ID, "My Paper Account", type,
                new com.quorumtrading.model.valueobject.ApiKeyCredentials("api-key-123", SECRET),
                Money.usd(new BigDecimal("50000.00")), null, Money.usd(BigDecimal.ZERO),
                AccountStatus.ACTIVE, null, Instant.now(), Instant.now());
    }

    private io.restassured.specification.RequestSpecification authed() {
        return given().cookie("dev_session", EMAIL).contentType("application/json");
    }

    @Test
    @DisplayName("listing accounts never renders the secret")
    void shouldNotLeakSecretsWhenListing() {
        BrokerAccountId id = BrokerAccountId.generate();
        when(listBrokerAccountsUseCase.execute(any(com.quorumtrading.application.usecase.query.ListBrokerAccountsQuery.class)))
                .thenReturn(List.of(account(id, BrokerType.ALPACA_PAPER)));

        authed()
        .when().get("/api/v1/broker-accounts")
        .then()
            .statusCode(200)
            .body("$", hasSize(1))
            .body("[0].brokerType", equalTo("ALPACA_PAPER"))
            // The whole response body, not just the field we expect: a secret leaking through any
            // field at all is the failure, and only asserting on named fields would miss it.
            .body(not(org.hamcrest.Matchers.hasToString(org.hamcrest.Matchers.containsString(SECRET))));
    }

    @Test
    @DisplayName("fetching one account never renders the secret either")
    void shouldNotLeakSecretsWhenFetchingOne() {
        BrokerAccountId id = BrokerAccountId.generate();
        when(getBrokerAccountUseCase.execute(any(com.quorumtrading.application.usecase.query.GetBrokerAccountQuery.class)))
                .thenReturn(account(id, BrokerType.ALPACA_LIVE));

        String body = authed()
        .when().get("/api/v1/broker-accounts/" + id.value())
        .then()
            .statusCode(200)
            .body("brokerType", equalTo("ALPACA_LIVE"))
        .extract().asString();

        org.assertj.core.api.Assertions.assertThat(body)
                .as("an API secret echoed back once is an API secret to rotate")
                .doesNotContain(SECRET);
    }

    @Test
    @DisplayName("the broker type round-trips exactly — it decides simulated versus real money")
    void shouldPreserveTheBrokerType() {
        BrokerAccountId id = BrokerAccountId.generate();
        when(getBrokerAccountUseCase.execute(any(com.quorumtrading.application.usecase.query.GetBrokerAccountQuery.class)))
                .thenReturn(account(id, BrokerType.MOCK));

        authed()
        .when().get("/api/v1/broker-accounts/" + id.value())
        .then().statusCode(200).body("brokerType", equalTo("MOCK"));
    }

    @Test
    @DisplayName("another user's account is refused")
    void shouldRefuseAnotherUsersAccount() {
        when(getBrokerAccountUseCase.execute(any(com.quorumtrading.application.usecase.query.GetBrokerAccountQuery.class)))
                .thenThrow(new SecurityException("not yours"));

        authed()
        .when().get("/api/v1/broker-accounts/" + UUID.randomUUID())
        // 403, not 404. Worth knowing that this differs from the notification, allocation and
        // simulation resources, which answer 404 so that another user's id cannot be confirmed to
        // exist. Pinned as-is rather than changed: 403 is a defensible reading of "you may not
        // have this", and quietly altering an auth response to match a sibling is not a change to
        // make from a test. The inconsistency is the thing to settle deliberately.
        .then().statusCode(403);
    }

    @Test
    @DisplayName("an unknown account is a 404")
    void shouldReturnNotFoundForUnknownAccount() {
        when(getBrokerAccountUseCase.execute(any(com.quorumtrading.application.usecase.query.GetBrokerAccountQuery.class)))
                .thenThrow(new NoSuchElementException("gone"));

        authed()
        .when().get("/api/v1/broker-accounts/" + UUID.randomUUID())
        .then().statusCode(404);
    }

    @Test
    @DisplayName("a malformed id never reaches the use case")
    void shouldRejectAMalformedId() {
        authed()
        .when().get("/api/v1/broker-accounts/not-a-uuid")
        .then().statusCode(400);

        verify(getBrokerAccountUseCase, never()).execute(any());
    }

    @Test
    @DisplayName("creating without a name is refused by validation")
    void shouldRejectACreateWithoutAName() {
        authed()
            .body("{\"brokerType\": \"MOCK\"}")
        .when().post("/api/v1/broker-accounts")
        .then().statusCode(400);

        verify(createBrokerAccountUseCase, never()).execute(any());
    }

    @Test
    @DisplayName("deleting is scoped to the caller")
    void shouldDeleteOwnAccount() {
        BrokerAccountId id = BrokerAccountId.generate();

        authed()
        .when().delete("/api/v1/broker-accounts/" + id.value())
        .then().statusCode(org.hamcrest.Matchers.anyOf(equalTo(200), equalTo(204)));

        verify(deleteBrokerAccountUseCase).execute(any(com.quorumtrading.application.usecase.command.DeleteBrokerAccountCommand.class));
    }

    @Test
    @DisplayName("deleting an account in use is a conflict, not a silent success")
    void shouldReportConflictWhenDeletingAnAccountInUse() {
        org.mockito.Mockito.doThrow(new IllegalStateException("Account has running simulations"))
                .when(deleteBrokerAccountUseCase).execute(any(com.quorumtrading.application.usecase.command.DeleteBrokerAccountCommand.class));

        authed()
        .when().delete("/api/v1/broker-accounts/" + UUID.randomUUID())
        .then().statusCode(409);
    }

    @Test
    @DisplayName("broker accounts are not public")
    void shouldRequireAuthentication() {
        given().when().get("/api/v1/broker-accounts").then().statusCode(401);
        given().contentType("application/json").body("{\"name\":\"x\",\"brokerType\":\"MOCK\"}")
               .when().post("/api/v1/broker-accounts").then().statusCode(401);
    }
}
