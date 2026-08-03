package com.quorumtrading.adapter.broker.saxo;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

import java.util.Map;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

/**
 * {@link QuarkusTestResourceLifecycleManager} that starts a WireMock server and points the
 * {@code saxo-ref} Reference Data REST client (the {@code /ref/v1} surface: instrument search +
 * instrument details) at it, so {@link SaxoUicResolverWireMockTest} can stub Saxo's
 * {@code /ref/v1/instruments} and {@code /ref/v1/instruments/details/{uic}/{assetType}} endpoints
 * and drive the {@link SaxoUicResolver} through the real Quarkus rest-client + Jackson machinery.
 */
public class SaxoReferenceDataWireMockResource implements QuarkusTestResourceLifecycleManager {

    private WireMockServer wireMock;

    @Override
    public Map<String, String> start() {
        wireMock = new WireMockServer(wireMockConfig().dynamicPort());
        wireMock.start();
        return Map.of("quarkus.rest-client.saxo-ref.url", wireMock.baseUrl());
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
                new TestInjector.AnnotatedAndMatchesType(InjectRefWireMock.class, WireMockServer.class));
    }

    /** Marks a {@link WireMockServer} field in a test to receive the running WireMock instance. */
    @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
    @java.lang.annotation.Target(java.lang.annotation.ElementType.FIELD)
    public @interface InjectRefWireMock {
    }
}
