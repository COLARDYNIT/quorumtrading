package com.quorumtrading.adapter.broker.saxo.client;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.quorumtrading.adapter.broker.saxo.client.SaxoOAuthWireMockResource.InjectWireMock;
import com.quorumtrading.application.port.out.OAuthTokens;
import com.quorumtrading.application.port.out.SaxoOAuthClientPort;
import com.quorumtrading.model.enums.SaxoEnvironment;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.notContaining;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * WireMock-backed test for the single active {@link SaxoOAuthClientPort} bean
 * ({@link SaxoTokenEndpointClient}) — the RFC-6749 OAuth2 client over Saxo's configurable token
 * endpoint.
 *
 * <p>Drives the real Quarkus rest-client + Jackson machinery through {@link SaxoOAuthWireMockResource},
 * which points the {@code saxo-oauth} rest-client at WireMock. Proves:</p>
 * <ol>
 *   <li>the confidential-client token call authenticates with an HTTP&nbsp;<b>Basic</b>
 *       {@code Authorization} header carrying {@code base64(client_id:client_secret)} — and
 *       <b>not</b> {@code client_id}/{@code client_secret} form fields (SAXO_ANSWERS §1.5);</li>
 *   <li>the {@code refresh_token} grant sends the correct form body
 *       ({@code grant_type=refresh_token} + the current refresh token), and parses the response
 *       into {@link OAuthTokens} carrying the <b>rotated</b> refresh token + derived expiry; and</li>
 *   <li>a 4xx from the token endpoint (the refresh token was rejected) surfaces as an
 *       {@link IllegalArgumentException}, which the refresh service translates into a broken chain.</li>
 * </ol>
 */
@QuarkusTest
@QuarkusTestResource(SaxoOAuthWireMockResource.class)
class SaxoTokenEndpointClientWireMockTest {

    /** {@code Basic base64("test-app-key:test-app-secret")} — the credentials pinned by the resource. */
    private static final String EXPECTED_BASIC_AUTH = "Basic " + Base64.getEncoder()
            .encodeToString("test-app-key:test-app-secret".getBytes(StandardCharsets.UTF_8));

    @Inject
    SaxoOAuthClientPort oauthClient;

    @InjectWireMock
    WireMockServer wireMock;

    @BeforeEach
    void resetStubs() {
        wireMock.resetAll();
    }

    @Test
    void refreshSendsRfc6749FormBodyAndReturnsRotatedTokens() {
        wireMock.stubFor(post(urlPathEqualTo("/"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "access_token": "new-access-xyz",
                                  "refresh_token": "rotated-refresh-789",
                                  "expires_in": 1200,
                                  "token_type": "Bearer",
                                  "refresh_token_expires_in": 3600
                                }
                                """)));

        Instant before = Instant.now();
        OAuthTokens tokens = oauthClient.refreshAccessToken("current-refresh-123", SaxoEnvironment.SIM);
        Instant after = Instant.now();

        // Parsed into OAuthTokens with the rotated refresh token.
        assertThat(tokens.accessToken()).isEqualTo("new-access-xyz");
        assertThat(tokens.refreshToken()).isEqualTo("rotated-refresh-789");
        assertThat(tokens.environment()).isEqualTo(SaxoEnvironment.SIM);
        // Expiry derived from expires_in (1200s) relative to the issue instant.
        assertThat(tokens.expiry()).isBetween(
                before.plus(Duration.ofSeconds(1200)).minusSeconds(2),
                after.plus(Duration.ofSeconds(1200)).plusSeconds(2));

        // The RFC-6749 refresh-token grant form body was sent, and the confidential client
        // authenticated via the HTTP Basic Authorization header — NOT via form-field credentials.
        wireMock.verify(postRequestedFor(urlPathEqualTo("/"))
                .withHeader("Content-Type", containing("application/x-www-form-urlencoded"))
                .withHeader("Authorization", equalTo(EXPECTED_BASIC_AUTH))
                .withRequestBody(containing("grant_type=refresh_token"))
                .withRequestBody(containing("refresh_token=current-refresh-123"))
                .withRequestBody(notContaining("client_id"))
                .withRequestBody(notContaining("client_secret")));
    }

    @Test
    void rejectedRefreshTokenSurfacesAsIllegalArgument() {
        // Saxo rejects the refresh token (already rotated / expired / revoked).
        wireMock.stubFor(post(urlPathEqualTo("/"))
                .willReturn(aResponse()
                        .withStatus(401)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":\"invalid_grant\"}")));

        assertThatThrownBy(() ->
                oauthClient.refreshAccessToken("dead-refresh", SaxoEnvironment.SIM))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("401");

        wireMock.verify(postRequestedFor(urlPathEqualTo("/"))
                .withRequestBody(containing("grant_type=refresh_token")));
    }

    @Test
    void authorizationCodeExchangeSendsCorrectGrant() {
        wireMock.stubFor(post(urlPathEqualTo("/"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "access_token": "initial-access",
                                  "refresh_token": "initial-refresh",
                                  "expires_in": 1200,
                                  "token_type": "Bearer"
                                }
                                """)));

        OAuthTokens tokens = oauthClient.exchangeAuthorizationCode(
                "auth-code-abc", "https://app/callback", SaxoEnvironment.SIM);

        assertThat(tokens.accessToken()).isEqualTo("initial-access");
        assertThat(tokens.refreshToken()).isEqualTo("initial-refresh");

        // Confidential client: the code exchange also authenticates via HTTP Basic, and the
        // redirect_uri is echoed in the body per RFC-6749 §4.1.3. Credentials are NOT form fields.
        wireMock.verify(postRequestedFor(urlPathEqualTo("/"))
                .withHeader("Authorization", equalTo(EXPECTED_BASIC_AUTH))
                .withRequestBody(containing("grant_type=authorization_code"))
                .withRequestBody(containing("code=auth-code-abc"))
                .withRequestBody(containing("redirect_uri="))
                .withRequestBody(notContaining("client_id"))
                .withRequestBody(notContaining("client_secret")));
    }
}
