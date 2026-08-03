package com.quorumtrading.adapter.marketdata.saxo;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.quorumtrading.adapter.marketdata.saxo.client.InjectWireMock;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

import java.util.Map;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

/**
 * {@link QuarkusTestResourceLifecycleManager} for {@link SaxoMarketDataAdapterWireMockTest}.
 *
 * <p>Starts a single WireMock server and points <b>both</b> Saxo market-data rest clients at it:
 * the reference-data search ({@code saxo-md-ref}, for Uic resolution) and the OpenApi price
 * surface ({@code saxo-openapi}, for InfoPrices). Both share the same stub server so one test can
 * stub the resolve→quote chain end-to-end. No real Saxo gateway is ever contacted.</p>
 */
public class SaxoMarketDataWireMockResource implements QuarkusTestResourceLifecycleManager {

    private WireMockServer wireMock;

    @Override
    public Map<String, String> start() {
        wireMock = new WireMockServer(wireMockConfig().dynamicPort());
        wireMock.start();
        return Map.of(
                "quarkus.rest-client.saxo-openapi.url", wireMock.baseUrl(),
                "quarkus.rest-client.saxo-md-ref.url", wireMock.baseUrl());
    }

    @Override
    public void stop() {
        if (wireMock != null) {
            wireMock.stop();
        }
    }

    @Override
    public void inject(TestInjector testInjector) {
        testInjector.injectIntoFields(
                wireMock,
                new TestInjector.AnnotatedAndMatchesType(InjectWireMock.class, WireMockServer.class));
    }
}
