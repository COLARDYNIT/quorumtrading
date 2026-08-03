package com.quorumtrading.adapter.broker.saxo;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.quorumtrading.adapter.broker.saxo.SaxoBrokerAdapter.SaxoAccountContext;
import com.quorumtrading.adapter.broker.saxo.SaxoTradingWireMockResource.InjectWireMock;
import com.quorumtrading.model.entity.BrokerAccount;
import com.quorumtrading.model.enums.BrokerType;
import com.quorumtrading.model.enums.SaxoEnvironment;
import com.quorumtrading.model.valueobject.BrokerAccountId;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.SaxoOAuthCredentials;
import com.quorumtrading.model.valueobject.UserId;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * WireMock-backed test for the SB2 per-account auth + account-key resolution path in
 * {@link SaxoBrokerAdapter}.
 *
 * <p>Drives the real Quarkus rest-client + Jackson machinery through
 * {@link SaxoTradingWireMockResource} (which points {@code saxo-trading} at WireMock) and the real
 * {@link com.quorumtrading.adapter.broker.saxo.client.SaxoAuthHeadersFactory} +
 * {@link com.quorumtrading.adapter.broker.saxo.client.SaxoRequestTokenHolder}, proving the three
 * things the WP requires (D10):</p>
 * <ol>
 *   <li><b>Bearer from the holder:</b> {@code configureForAccount} pins the account on the
 *       request-scoped holder so the auth-headers factory attaches the per-account
 *       {@code Authorization: Bearer <token>} (resolved via the stub
 *       {@link com.quorumtrading.application.port.out.BrokerTokenProviderPort}) to both
 *       {@code /port/v1/users/me} and {@code /port/v1/accounts/me};</li>
 *   <li><b>ClientKey/AccountKey(EUR) resolution + caching:</b> {@code users/me} yields the
 *       {@code ClientKey}, {@code accounts/me} yields the <b>EUR</b> {@code AccountKey} (a
 *       non-EUR account in the same payload is skipped), and both are cached on the adapter; and</li>
 *   <li><b>Per-account cache isolation:</b> a second account resolves its own ClientKey/AccountKey
 *       under its own bearer, leaving the first account's cached context untouched.</li>
 * </ol>
 */
@QuarkusTest
@QuarkusTestResource(SaxoTradingWireMockResource.class)
class SaxoBrokerAuthAccountKeyWireMockTest {

    @Inject
    @Named("saxoBroker")
    SaxoBrokerAdapter adapter;

    @InjectWireMock
    WireMockServer wireMock;

    @BeforeEach
    void resetStubs() {
        wireMock.resetAll();
    }

    private static BrokerAccount saxoAccount(BrokerAccountId id) {
        SaxoOAuthCredentials creds = new SaxoOAuthCredentials(
                "app-key", "app-secret", "refresh-token-abc", null, null, SaxoEnvironment.SIM);
        return new BrokerAccount(
                id,
                new UserId(java.util.UUID.randomUUID()),
                "Saxo SIM test",
                BrokerType.SAXO_SIM,
                creds,
                Money.of(new BigDecimal("10000.00"), Money.Currency.EUR),
                null,
                Money.zero(Money.Currency.EUR),
                com.quorumtrading.model.enums.AccountStatus.ACTIVE,
                null,
                java.time.Instant.now(),
                java.time.Instant.now());
    }

    private void stubUsersMe(String clientKey) {
        wireMock.stubFor(get(urlPathEqualTo("/port/v1/users/me"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "ClientKey": "%s",
                                  "UserKey": "user-key-1",
                                  "Name": "Test User",
                                  "SomeFutureSaxoField": "ignored"
                                }
                                """.formatted(clientKey))));
    }

    /** accounts/me payload with a USD account first (to be skipped) and the EUR account second. */
    private void stubAccountsMeEurSecond(String eurAccountKey) {
        wireMock.stubFor(get(urlPathEqualTo("/port/v1/accounts/me"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "Data": [
                                    {
                                      "AccountKey": "usd-acct-key",
                                      "AccountId": "USD123",
                                      "Currency": "USD",
                                      "Active": true
                                    },
                                    {
                                      "AccountKey": "%s",
                                      "AccountId": "EUR456",
                                      "Currency": "EUR",
                                      "Active": true
                                    }
                                  ]
                                }
                                """.formatted(eurAccountKey))));
    }

    @Test
    void configureResolvesClientKeyAndEurAccountKeyAndSendsBearerFromHolder() {
        BrokerAccountId accountId = BrokerAccountId.generate();
        String expectedBearer = "Bearer " + TestBrokerTokenProviderProducer.tokenFor(accountId.value());

        stubUsersMe("client-key-eur-1");
        stubAccountsMeEurSecond("eur-acct-key-1");

        adapter.configureForAccount(saxoAccount(accountId));

        // ClientKey + EUR AccountKey resolved and cached on the adapter.
        assertThat(adapter.isAccountConfigured(accountId)).isTrue();
        Optional<SaxoAccountContext> ctx = adapter.getAccountContext(accountId);
        assertThat(ctx).isPresent();
        assertThat(ctx.get().clientKey()).isEqualTo("client-key-eur-1");
        assertThat(ctx.get().accountKey()).isEqualTo("eur-acct-key-1"); // EUR row picked, USD skipped
        assertThat(ctx.get().accountId()).isEqualTo("EUR456");
        assertThat(ctx.get().currency()).isEqualTo("EUR");

        // Per-account bearer (from the request-scoped holder via the real headers factory) was
        // attached to BOTH resolution calls.
        wireMock.verify(getRequestedFor(urlPathEqualTo("/port/v1/users/me"))
                .withHeader("Authorization", equalTo(expectedBearer)));
        wireMock.verify(getRequestedFor(urlPathEqualTo("/port/v1/accounts/me"))
                .withHeader("Authorization", equalTo(expectedBearer)));
    }

    @Test
    void secondConfigureUsesItsOwnBearerAndCacheIsIsolatedPerAccount() {
        // First account.
        BrokerAccountId accountA = BrokerAccountId.generate();
        stubUsersMe("client-key-eur-1");
        stubAccountsMeEurSecond("eur-acct-key-1");
        adapter.configureForAccount(saxoAccount(accountA));
        SaxoAccountContext ctxA = adapter.getAccountContext(accountA).orElseThrow();
        assertThat(ctxA.clientKey()).isEqualTo("client-key-eur-1");
        assertThat(ctxA.accountKey()).isEqualTo("eur-acct-key-1");

        // Second account — different Saxo handles behind the same WireMock surface.
        BrokerAccountId accountB = BrokerAccountId.generate();
        wireMock.resetAll();
        stubUsersMe("client-key-eur-2");
        stubAccountsMeEurSecond("eur-acct-key-2");
        adapter.configureForAccount(saxoAccount(accountB));

        // Account B resolved its own context.
        SaxoAccountContext ctxB = adapter.getAccountContext(accountB).orElseThrow();
        assertThat(ctxB.clientKey()).isEqualTo("client-key-eur-2");
        assertThat(ctxB.accountKey()).isEqualTo("eur-acct-key-2");

        // Account A's cache is untouched (isolation): it still holds its own handles.
        SaxoAccountContext ctxAAfter = adapter.getAccountContext(accountA).orElseThrow();
        assertThat(ctxAAfter.clientKey()).isEqualTo("client-key-eur-1");
        assertThat(ctxAAfter.accountKey()).isEqualTo("eur-acct-key-1");
        assertThat(ctxAAfter.accountKey()).isNotEqualTo(ctxB.accountKey());

        // Account B's resolution calls carried B's distinct bearer (not A's).
        String bearerB = "Bearer " + TestBrokerTokenProviderProducer.tokenFor(accountB.value());
        String bearerA = "Bearer " + TestBrokerTokenProviderProducer.tokenFor(accountA.value());
        assertThat(bearerB).isNotEqualTo(bearerA);
        wireMock.verify(getRequestedFor(urlPathEqualTo("/port/v1/users/me"))
                .withHeader("Authorization", equalTo(bearerB)));
        wireMock.verify(getRequestedFor(urlPathEqualTo("/port/v1/accounts/me"))
                .withHeader("Authorization", equalTo(bearerB)));
    }

    @Test
    void removeAccountConfigurationDropsTheCachedContext() {
        BrokerAccountId accountId = BrokerAccountId.generate();
        stubUsersMe("client-key-eur-1");
        stubAccountsMeEurSecond("eur-acct-key-1");
        adapter.configureForAccount(saxoAccount(accountId));
        assertThat(adapter.isAccountConfigured(accountId)).isTrue();

        adapter.removeAccountConfiguration(accountId);

        assertThat(adapter.isAccountConfigured(accountId)).isFalse();
        assertThat(adapter.getAccountContext(accountId)).isEmpty();
    }
}
