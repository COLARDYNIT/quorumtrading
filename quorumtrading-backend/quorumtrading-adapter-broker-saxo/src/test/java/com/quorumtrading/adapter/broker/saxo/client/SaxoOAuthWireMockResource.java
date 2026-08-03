package com.quorumtrading.adapter.broker.saxo.client;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

import java.util.Map;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

/**
 * {@link QuarkusTestResourceLifecycleManager} that starts a WireMock server and points the
 * {@code saxo-oauth} REST client (the token endpoint) at it, plus pins the platform app
 * credentials, so {@link SaxoTokenEndpointClientWireMockTest} can stub Saxo's OAuth2 token
 * endpoint and inspect the form body the rest-client actually sends.
 */
public class SaxoOAuthWireMockResource implements QuarkusTestResourceLifecycleManager {

    private WireMockServer wireMock;

    @Override
    public Map<String, String> start() {
        wireMock = new WireMockServer(wireMockConfig().dynamicPort());
        wireMock.start();
        // Override the saxo-oauth token-endpoint URL to the live WireMock port, and pin the
        // platform app credentials so the form body is deterministic in the test.
        return Map.of(
                "quarkus.rest-client.saxo-oauth.url", wireMock.baseUrl(),
                "quorumtrading.broker.saxo.app-key", "test-app-key",
                "quorumtrading.broker.saxo.app-secret", "test-app-secret");
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
