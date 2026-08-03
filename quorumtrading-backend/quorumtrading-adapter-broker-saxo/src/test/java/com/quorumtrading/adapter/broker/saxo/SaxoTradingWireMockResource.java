package com.quorumtrading.adapter.broker.saxo;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

import java.util.Map;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

/**
 * {@link QuarkusTestResourceLifecycleManager} that starts a WireMock server and points the
 * {@code saxo-trading} REST client (the {@code /port/v1} surface: users, accounts) at it, so
 * {@link SaxoBrokerAuthAccountKeyWireMockTest} can stub Saxo's {@code /port/v1/users/me} and
 * {@code /port/v1/accounts/me} endpoints and inspect the bearer header the adapter sends through
 * the real {@link com.quorumtrading.adapter.broker.saxo.client.SaxoAuthHeadersFactory}.
 */
public class SaxoTradingWireMockResource implements QuarkusTestResourceLifecycleManager {

    private WireMockServer wireMock;

    @Override
    public Map<String, String> start() {
        wireMock = new WireMockServer(wireMockConfig().dynamicPort());
        wireMock.start();
        return Map.of("quarkus.rest-client.saxo-trading.url", wireMock.baseUrl());
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

    /** Marks a {@link WireMockServer} field in a test to receive the running WireMock instance. */
    @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
    @java.lang.annotation.Target(java.lang.annotation.ElementType.FIELD)
    public @interface InjectWireMock {
    }
}
