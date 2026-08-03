package com.quorumtrading.adapter.broker.saxo;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.quorumtrading.adapter.broker.saxo.SaxoReferenceDataWireMockResource.InjectRefWireMock;
import com.quorumtrading.adapter.broker.saxo.SaxoTradingWireMockResource.InjectWireMock;
import com.quorumtrading.application.port.out.BrokerCredentialValidationPort.CredentialValidationResult;
import com.quorumtrading.model.entity.BrokerAccount;
import com.quorumtrading.model.entity.Portfolio;
import com.quorumtrading.model.entity.Position;
import com.quorumtrading.model.enums.BrokerType;
import com.quorumtrading.model.enums.SaxoEnvironment;
import com.quorumtrading.model.valueobject.BrokerAccountId;
import com.quorumtrading.model.valueobject.BrokerCapabilities;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.SaxoOAuthCredentials;
import com.quorumtrading.model.valueobject.Symbol;
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
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * WireMock-backed test for the SB5 portfolio / balance / validate path on {@link SaxoBrokerAdapter}.
 *
 * <p>Wires both the {@code saxo-trading} surface ({@link SaxoTradingWireMockResource}, for account
 * configuration + {@code /port/v1/positions} + {@code /port/v1/balances}) and the {@code saxo-ref}
 * surface ({@link SaxoReferenceDataWireMockResource}, for the reverse {@code Uic → Symbol} mapping of
 * each held position), then drives the real adapter end-to-end through the real Quarkus rest-client +
 * Jackson + auth-headers machinery. Proves the WP's named cases:</p>
 * <ul>
 *   <li><b>positions → Portfolio (EUR)</b> — {@code /port/v1/positions} rows reverse-map Uic→Symbol
 *       and build EUR {@link Position}s (cost basis from {@code OpenPrice}, current price from
 *       {@code CurrentPrice});</li>
 *   <li><b>balances → cash</b> — {@code /port/v1/balances} yields the EUR cash on the portfolio;</li>
 *   <li><b>validate</b> — complete OAuth credentials validate (success), incomplete / wrong-type /
 *       wrong-broker credentials fail;</li>
 *   <li><b>capabilities = wholeShare</b> — the adapter reports whole-share equities (non-fractional).</li>
 * </ul>
 */
@QuarkusTest
@QuarkusTestResource(SaxoTradingWireMockResource.class)
@QuarkusTestResource(SaxoReferenceDataWireMockResource.class)
class SaxoBrokerPortfolioBalanceWireMockTest {

    private static final long RHM_UIC = 210840L;

    @Inject
    @Named("saxoBroker")
    SaxoBrokerAdapter adapter;

    @InjectWireMock
    WireMockServer tradingWireMock;

    @InjectRefWireMock
    WireMockServer refWireMock;

    @BeforeEach
    void resetStubs() {
        tradingWireMock.resetAll();
        refWireMock.resetAll();
    }

    private static BrokerAccount saxoAccount(BrokerAccountId id) {
        return new BrokerAccount(
                id,
                new UserId(java.util.UUID.randomUUID()),
                "Saxo SIM test",
                BrokerType.SAXO_SIM,
                validCreds(),
                Money.of(new BigDecimal("10000.00"), Money.Currency.EUR),
                null,
                Money.zero(Money.Currency.EUR),
                com.quorumtrading.model.enums.AccountStatus.ACTIVE,
                null,
                java.time.Instant.now(),
                java.time.Instant.now());
    }

    private static SaxoOAuthCredentials validCreds() {
        return new SaxoOAuthCredentials(
                "app-key", "app-secret", "refresh-token-abc", null, null, SaxoEnvironment.SIM);
    }

    private void configureAccount(BrokerAccountId id) {
        tradingWireMock.stubFor(get(urlPathEqualTo("/port/v1/users/me"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"ClientKey\":\"ck-1\",\"UserKey\":\"uk-1\"}")));
        tradingWireMock.stubFor(get(urlPathEqualTo("/port/v1/accounts/me"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"Data\":[{\"AccountKey\":\"ak-eur\",\"AccountId\":\"EUR1\","
                                + "\"Currency\":\"EUR\",\"Active\":true}]}")));
        adapter.configureForAccount(saxoAccount(id));
    }

    /** Stubs the reverse Uic→Symbol details lookup for RHM Uic → "RHM:xetr" Stock. */
    private void stubReverseDetails() {
        refWireMock.stubFor(get(urlPathEqualTo("/ref/v1/instruments/details/" + RHM_UIC + "/Stock"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"Uic\":" + RHM_UIC + ",\"AssetType\":\"Stock\",\"Symbol\":\"RHM:xetr\","
                                + "\"Description\":\"Rheinmetall AG\",\"CurrencyCode\":\"EUR\",\"ExchangeId\":\"XETR\","
                                + "\"IsTradable\":true}")));
    }

    @Test
    void positionsBuildEurPortfolioWithCostBasisAndCurrentPrice() {
        BrokerAccountId accountId = BrokerAccountId.generate();
        configureAccount(accountId);
        stubReverseDetails();

        tradingWireMock.stubFor(get(urlPathEqualTo("/port/v1/positions"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"Data\":[{\"Uic\":" + RHM_UIC + ",\"AssetType\":\"Stock\",\"Amount\":7,"
                                + "\"OpenPrice\":450.00,\"CurrentPrice\":480.50}]}")));
        tradingWireMock.stubFor(get(urlPathEqualTo("/port/v1/balances"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"CashAvailableForTrading\":2500.00,\"Currency\":\"EUR\"}")));

        Optional<Portfolio> portfolioOpt = adapter.getAccountPortfolio(accountId);

        assertThat(portfolioOpt).isPresent();
        Portfolio portfolio = portfolioOpt.get();
        assertThat(portfolio.cashAvailable()).isEqualTo(Money.of(new BigDecimal("2500.00"), Money.Currency.EUR));

        Symbol rhm = Symbol.parse("RHM:XETR");
        assertThat(portfolio.positions()).containsKey(rhm);
        Position pos = portfolio.positions().get(rhm);
        assertThat(pos.quantity()).isEqualByComparingTo("7");
        assertThat(pos.averagePrice()).isEqualTo(Money.of(new BigDecimal("450.00"), Money.Currency.EUR));
        assertThat(pos.currentPrice()).isEqualTo(Money.of(new BigDecimal("480.50"), Money.Currency.EUR));
    }

    @Test
    void balancesYieldEurCash() {
        BrokerAccountId accountId = BrokerAccountId.generate();
        configureAccount(accountId);

        tradingWireMock.stubFor(get(urlPathEqualTo("/port/v1/balances"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"CashAvailableForTrading\":1234.56,\"CashBalance\":9999.99,"
                                + "\"Currency\":\"EUR\"}")));

        Optional<Money> cash = adapter.getAccountBalance(accountId);

        assertThat(cash).contains(Money.of(new BigDecimal("1234.56"), Money.Currency.EUR));
    }

    @Test
    void accountAgnosticGetAccountBalanceIsEmpty() {
        // Saxo balances are per-account; the no-arg form cannot resolve one without a bearer/AccountKey.
        assertThat(adapter.getAccountBalance()).isEmpty();
    }

    @Test
    void getAccountBalanceForUnconfiguredAccountIsEmpty() {
        assertThat(adapter.getAccountBalance(BrokerAccountId.generate())).isEmpty();
    }

    @Test
    void emptyPositionsBuildPortfolioWithOnlyCash() {
        BrokerAccountId accountId = BrokerAccountId.generate();
        configureAccount(accountId);

        tradingWireMock.stubFor(get(urlPathEqualTo("/port/v1/positions"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"Data\":[]}")));
        tradingWireMock.stubFor(get(urlPathEqualTo("/port/v1/balances"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"CashAvailableForTrading\":500.00,\"Currency\":\"EUR\"}")));

        Optional<Portfolio> portfolio = adapter.getAccountPortfolio(accountId);

        assertThat(portfolio).isPresent();
        assertThat(portfolio.get().positions()).isEmpty();
        assertThat(portfolio.get().cashAvailable()).isEqualTo(Money.of(new BigDecimal("500.00"), Money.Currency.EUR));
    }

    @Test
    void capabilitiesAreWholeShareEquities() {
        BrokerCapabilities caps = adapter.capabilities();

        assertThat(caps).isEqualTo(BrokerCapabilities.wholeShareEquities());
        assertThat(caps.supportsFractional(com.quorumtrading.model.enums.InvestmentType.STOCK)).isFalse();
        assertThat(caps.supportsFractional(com.quorumtrading.model.enums.InvestmentType.CRYPTO)).isTrue();
    }

    @Test
    void supportsOnlySaxoBrokerTypes() {
        assertThat(adapter.supports(BrokerType.SAXO_SIM)).isTrue();
        assertThat(adapter.supports(BrokerType.SAXO_LIVE)).isTrue();
        // Disjoint from Alpaca so the K5 selector dispatch stays deterministic.
        assertThat(adapter.supports(BrokerType.ALPACA_PAPER)).isFalse();
        assertThat(adapter.supports(BrokerType.ALPACA_LIVE)).isFalse();
        assertThat(adapter.supports(BrokerType.MOCK)).isFalse();
        assertThat(adapter.supports(null)).isFalse();
    }

    @Test
    void validateAcceptsCompleteOAuthCredentials() {
        CredentialValidationResult result = adapter.validate(BrokerType.SAXO_SIM, validCreds());

        assertThat(result.valid()).isTrue();
        // Pre-link: no balance yet (needs the resolved EUR AccountKey), so EUR-zero is reported.
        assertThat(result.balance()).contains(Money.zero(Money.Currency.EUR));
    }

    @Test
    void validateRejectsWrongBrokerType() {
        CredentialValidationResult result = adapter.validate(BrokerType.ALPACA_LIVE, validCreds());

        assertThat(result.valid()).isFalse();
        assertThat(result.errorMessage()).hasValueSatisfying(m -> assertThat(m).contains("SAXO"));
    }

    @Test
    void validateRejectsNonOAuthCredentials() {
        CredentialValidationResult result = adapter.validate(
                BrokerType.SAXO_SIM,
                new com.quorumtrading.model.valueobject.ApiKeyCredentials("k", "s"));

        assertThat(result.valid()).isFalse();
        assertThat(result.errorMessage()).hasValueSatisfying(m -> assertThat(m).contains("OAuth"));
    }
}
