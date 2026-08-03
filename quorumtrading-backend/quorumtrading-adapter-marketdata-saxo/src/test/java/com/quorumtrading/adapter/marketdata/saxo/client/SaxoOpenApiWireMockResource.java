package com.quorumtrading.adapter.marketdata.saxo.client;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

import java.util.Map;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

/**
 * {@link QuarkusTestResourceLifecycleManager} that starts a WireMock server and points the
 * {@code saxo-openapi} REST client at it, so {@link SaxoOpenApiClientWireMockTest} can stub
 * the Saxo OpenAPI surface and inspect the requests the rest-client actually sends.
 *
 * <p>The first WireMock harness in the reactor (per plan §6 {@code V1-wiremock-harness} and
 * {@code MD1}); kept deliberately small.</p>
 */
public class SaxoOpenApiWireMockResource implements QuarkusTestResourceLifecycleManager {

    private WireMockServer wireMock;

    @Override
    public Map<String, String> start() {
        wireMock = new WireMockServer(wireMockConfig().dynamicPort());
        wireMock.start();
        // Override the saxo-openapi rest-client base URL to the live WireMock port.
        return Map.of("quarkus.rest-client.saxo-openapi.url", wireMock.baseUrl());
    }

    @Override
    public void stop() {
        if (wireMock != null) {
            wireMock.stop();
        }
    }

    /**
     * Injects the running {@link WireMockServer} into the test via
     * {@code @InjectWireMock} (any field of type {@link WireMockServer}).
     */
    @Override
    public void inject(TestInjector testInjector) {
        testInjector.injectIntoFields(
                wireMock,
                new TestInjector.AnnotatedAndMatchesType(InjectWireMock.class, WireMockServer.class));
    }
}
