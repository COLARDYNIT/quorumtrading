package com.quorumtrading.adapter.broker.saxo;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.quorumtrading.adapter.broker.saxo.SaxoReferenceDataWireMockResource.InjectRefWireMock;
import com.quorumtrading.adapter.broker.saxo.SaxoTradingWireMockResource.InjectWireMock;
import com.quorumtrading.model.entity.BrokerAccount;
import com.quorumtrading.model.enums.AssetType;
import com.quorumtrading.model.enums.SaxoEnvironment;
import com.quorumtrading.model.valueobject.AssetInfo;
import com.quorumtrading.model.valueobject.BrokerAccountId;
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
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * WireMock-backed test for the SB3 account-aware tradability / asset-info path on
 * {@link SaxoBrokerAdapter}.
 *
 * <p>Wires <b>both</b> the {@code saxo-trading} surface ({@link SaxoTradingWireMockResource}, for
 * account configuration) and the {@code saxo-ref} surface ({@link SaxoReferenceDataWireMockResource},
 * for instrument resolution) to two WireMock servers, then drives the real adapter end-to-end:
 * configure the account (resolves ClientKey/AccountKey), then call the account-aware
 * {@code isSymbolTradable(Symbol, accountId)} and {@code getAssetInfo(ticker, accountId)} — proving
 * the resolver runs inside the per-account bearer context (D10) and the resolution is mapped onto
 * {@link AssetInfo} (Uic / AssetType / MIC / currency / tradability).</p>
 */
@QuarkusTest
@QuarkusTestResource(SaxoTradingWireMockResource.class)
@QuarkusTestResource(SaxoReferenceDataWireMockResource.class)
class SaxoBrokerTradabilityWireMockTest {

    @Inject
    @Named("saxoBroker")
    SaxoBrokerAdapter adapter;

    @Inject
    SaxoUicResolver resolver;

    @InjectWireMock
    WireMockServer tradingWireMock;

    @InjectRefWireMock
    WireMockServer refWireMock;

    @BeforeEach
    void resetStubs() {
        tradingWireMock.resetAll();
        refWireMock.resetAll();
        // The resolver is @ApplicationScoped and caches per canonical symbol across test methods;
        // clear it so a prior method's (e.g. not-tradable) resolution does not leak into this one.
        resolver.clearCache();
    }

    private static BrokerAccount saxoAccount(BrokerAccountId id) {
        SaxoOAuthCredentials creds = new SaxoOAuthCredentials(
                "app-key", "app-secret", "refresh-token-abc", null, null, SaxoEnvironment.SIM);
        return new BrokerAccount(
                id,
                new UserId(java.util.UUID.randomUUID()),
                "Saxo SIM test",
                com.quorumtrading.model.enums.BrokerType.SAXO_SIM,
                creds,
                Money.of(new BigDecimal("10000.00"), Money.Currency.EUR),
                null,
                Money.zero(Money.Currency.EUR),
                com.quorumtrading.model.enums.AccountStatus.ACTIVE,
                null,
                java.time.Instant.now(),
                java.time.Instant.now());
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

    private void stubResolveStock(long uic, boolean tradable) {
        refWireMock.stubFor(get(urlPathEqualTo("/ref/v1/instruments"))
                .withQueryParam("AssetTypes", equalTo("Etf"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"Data\":[]}")));
        refWireMock.stubFor(get(urlPathEqualTo("/ref/v1/instruments"))
                .withQueryParam("AssetTypes", equalTo("Stock"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"Data\":[{\"Identifier\":" + uic + ",\"Symbol\":\"RHM:xetr\","
                                + "\"AssetType\":\"Stock\",\"ExchangeId\":\"XETR\",\"CurrencyCode\":\"EUR\"}]}")));
        refWireMock.stubFor(get(urlPathEqualTo("/ref/v1/instruments/details/" + uic + "/Stock"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"Uic\":" + uic + ",\"AssetType\":\"Stock\",\"Symbol\":\"RHM:xetr\","
                                + "\"Description\":\"Rheinmetall AG\",\"CurrencyCode\":\"EUR\",\"ExchangeId\":\"XETR\","
                                + "\"IsTradable\":" + tradable + ",\"LotSize\":1,\"MinimumTradeSize\":1,"
                                + "\"AmountDecimals\":0}")));
    }

    @Test
    void accountAwareIsSymbolTradableResolvesViaRefDataUnderBearer() {
        BrokerAccountId accountId = BrokerAccountId.generate();
        configureAccount(accountId);
        stubResolveStock(210840L, true);

        boolean tradable = adapter.isSymbolTradable(Symbol.parse("RHM:XETR"), accountId);

        assertThat(tradable).isTrue();
    }

    @Test
    void accountAwareIsSymbolTradableFalseWhenInstrumentNotTradable() {
        BrokerAccountId accountId = BrokerAccountId.generate();
        configureAccount(accountId);
        stubResolveStock(210840L, false);

        assertThat(adapter.isSymbolTradable(Symbol.parse("RHM:XETR"), accountId)).isFalse();
    }

    @Test
    void accountAwareGetAssetInfoEnrichesUicCurrencyAndAssetType() {
        BrokerAccountId accountId = BrokerAccountId.generate();
        configureAccount(accountId);
        stubResolveStock(210840L, true);

        Optional<AssetInfo> info = adapter.getAssetInfo("RHM:XETR", accountId);

        assertThat(info).isPresent();
        AssetInfo a = info.get();
        assertThat(a.tradable()).isTrue();
        assertThat(a.uic()).isEqualTo(210840L);
        assertThat(a.assetType()).isEqualTo(AssetType.STOCK);
        assertThat(a.currency()).isEqualTo(Money.Currency.EUR);
        assertThat(a.mic().code()).isEqualTo("XETR");
        assertThat(a.name()).isEqualTo("Rheinmetall AG");
        assertThat(a.isFound()).isTrue();
    }

    @Test
    void getAssetInfoForUnconfiguredAccountReturnsNotFound() {
        BrokerAccountId accountId = BrokerAccountId.generate();
        // No configureAccount call → account not configured → cannot resolve.
        Optional<AssetInfo> info = adapter.getAssetInfo("RHM:XETR", accountId);

        assertThat(info).isPresent();
        assertThat(info.get().isFound()).isFalse();
    }

    @Test
    void isSymbolTradableForUnconfiguredAccountIsFalse() {
        BrokerAccountId accountId = BrokerAccountId.generate();
        assertThat(adapter.isSymbolTradable(Symbol.parse("RHM:XETR"), accountId)).isFalse();
    }
}
