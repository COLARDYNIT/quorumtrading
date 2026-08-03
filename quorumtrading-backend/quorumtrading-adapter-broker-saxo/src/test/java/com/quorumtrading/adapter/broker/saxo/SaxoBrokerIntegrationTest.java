package com.quorumtrading.adapter.broker.saxo;

import com.quorumtrading.model.entity.BrokerAccount;
import com.quorumtrading.model.entity.Portfolio;
import com.quorumtrading.model.enums.AccountStatus;
import com.quorumtrading.model.enums.BrokerType;
import com.quorumtrading.model.enums.SaxoEnvironment;
import com.quorumtrading.model.valueobject.BrokerAccountId;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.SaxoOAuthCredentials;
import com.quorumtrading.model.valueobject.UserId;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test for {@link SaxoBrokerAdapter} against a <b>real Saxo SIM account</b>.
 *
 * <p><b>Gated.</b> Runs only when {@code SAXO_INTEGRATION_TESTS=true} and the SIM OAuth credentials
 * are present in the environment; otherwise the whole class is skipped (so CI and a credential-less
 * local {@code ./mvnw test} stay green). Mirrors the {@code AlpacaBrokerIntegrationTest} pattern.</p>
 *
 * <p><b>Read-only by design.</b> It exercises the live OAuth refresh → bearer → account-key resolution
 * → portfolio/balance read path end-to-end (the parts SAXO_ANSWERS §2.8 flags as needing confirmation
 * against a funded account: the exact cash field and {@code /positions} envelope). It does <em>not</em>
 * place an order — order placement on SIM moves real (paper) state and would need a separate, explicitly
 * opt-in flag; a real funded-account order round-trip is left to a manual smoke test (see the Go-Live
 * checklist's "Manual execute on Alpaca paper" analogue for Saxo).</p>
 *
 * <h2>Required environment to run</h2>
 * <ul>
 *   <li>{@code SAXO_INTEGRATION_TESTS=true} — the gate;</li>
 *   <li>{@code SAXO_SIM_APP_KEY}, {@code SAXO_SIM_APP_SECRET} — the SIM app credentials;</li>
 *   <li>{@code SAXO_SIM_REFRESH_TOKEN} — a <em>fresh</em> rotating refresh token (single-use, ~40-min
 *       TTL per SAXO_ANSWERS §1.4 — obtain it immediately before running);</li>
 *   <li>{@code quarkus.rest-client.saxo-trading.url} / {@code .saxo-ref.url} / {@code .saxo-oauth.url}
 *       pointed at the real SIM gateway + logon hosts (the test {@code application.properties}
 *       placeholders point at {@code http://localhost:1} for the WireMock suite, so override these via
 *       system properties / env when running the integration test against SIM).</li>
 * </ul>
 *
 * <p>Because the gate also requires the credential env vars to be non-blank (asserted defensively at
 * the top of each test), a half-configured environment fails loudly rather than silently passing.</p>
 */
@QuarkusTest
@EnabledIfEnvironmentVariable(named = "SAXO_INTEGRATION_TESTS", matches = "true")
class SaxoBrokerIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(SaxoBrokerIntegrationTest.class);

    @Inject
    @Named("saxoBroker")
    SaxoBrokerAdapter adapter;

    private static String env(String name) {
        return System.getenv(name);
    }

    private static SaxoOAuthCredentials simCredsFromEnv() {
        String appKey = env("SAXO_SIM_APP_KEY");
        String appSecret = env("SAXO_SIM_APP_SECRET");
        String refreshToken = env("SAXO_SIM_REFRESH_TOKEN");
        assertNotNull(appKey, "SAXO_SIM_APP_KEY must be set to run the Saxo integration test");
        assertNotNull(appSecret, "SAXO_SIM_APP_SECRET must be set to run the Saxo integration test");
        assertNotNull(refreshToken, "SAXO_SIM_REFRESH_TOKEN must be set (fresh, single-use) to run it");
        assertTrue(!appKey.isBlank() && !appSecret.isBlank() && !refreshToken.isBlank(),
                "Saxo SIM credentials must be non-blank");
        return new SaxoOAuthCredentials(appKey, appSecret, refreshToken, null, null, SaxoEnvironment.SIM);
    }

    private BrokerAccount simAccount(BrokerAccountId id) {
        return new BrokerAccount(
                id,
                new UserId(java.util.UUID.randomUUID()),
                "Saxo SIM integration",
                BrokerType.SAXO_SIM,
                simCredsFromEnv(),
                Money.of(new BigDecimal("0.00"), Money.Currency.EUR),
                null,
                Money.zero(Money.Currency.EUR),
                AccountStatus.ACTIVE,
                null,
                Instant.now(),
                Instant.now());
    }

    @Test
    void adapterIsInjectableAndSaxoTyped() {
        assertNotNull(adapter, "SaxoBrokerAdapter should be injectable via @Named(\"saxoBroker\")");
        assertTrue(adapter.supports(BrokerType.SAXO_SIM), "adapter must support SAXO_SIM");
    }

    @Test
    void resolvesAccountKeyAndReadsEurBalanceFromRealSim() {
        BrokerAccountId accountId = BrokerAccountId.generate();

        // Live OAuth refresh → bearer → /port/v1/users/me + /port/v1/accounts/me resolution.
        adapter.configureForAccount(simAccount(accountId));
        assertTrue(adapter.isAccountConfigured(accountId),
                "configureForAccount must resolve + cache the SIM ClientKey/AccountKey");

        var ctx = adapter.getAccountContext(accountId).orElseThrow(() ->
                new AssertionError("expected a resolved SaxoAccountContext for the SIM account"));
        assertNotNull(ctx.clientKey(), "ClientKey must resolve from /port/v1/users/me");
        assertNotNull(ctx.accountKey(), "AccountKey must resolve from /port/v1/accounts/me");
        log.info("Saxo SIM resolved: accountId={}, currency={}", ctx.accountId(), ctx.currency());

        // Read the real balance (read-only). A SIM demo account may legitimately hold 0 EUR.
        Optional<Money> balance = adapter.getAccountBalance(accountId);
        assertTrue(balance.isPresent(),
                "getAccountBalance must read the real SIM cash (empty likely means an auth/field-name "
                        + "failure — confirm the SAXO_ANSWERS §2.8 cash field against the live payload)");
        assertTrue(balance.get().amount().signum() >= 0, "SIM cash balance must be >= 0");
        log.info("Saxo SIM balance: {}", balance.get().format());

        // Read the real portfolio (read-only) — positions + cash fold into one Portfolio.
        Optional<Portfolio> portfolio = adapter.getAccountPortfolio(accountId);
        assertTrue(portfolio.isPresent(), "getAccountPortfolio must read the real SIM account");
        assertNotNull(portfolio.get().cashAvailable(), "portfolio cash must be non-null");
        assertNotNull(portfolio.get().positions(), "portfolio positions map must be non-null");
        log.info("Saxo SIM portfolio: cash={}, positions={}",
                portfolio.get().cashAvailable().format(), portfolio.get().positions().size());
    }
}
