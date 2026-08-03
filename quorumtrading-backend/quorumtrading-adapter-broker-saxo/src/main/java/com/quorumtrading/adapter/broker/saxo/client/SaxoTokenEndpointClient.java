package com.quorumtrading.adapter.broker.saxo.client;

import com.quorumtrading.adapter.broker.saxo.client.dto.SaxoTokenResponse;
import com.quorumtrading.application.port.out.OAuthTokens;
import com.quorumtrading.application.port.out.SaxoOAuthClientPort;
import com.quorumtrading.model.enums.SaxoEnvironment;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.ws.rs.WebApplicationException;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;

/**
 * The single active {@link SaxoOAuthClientPort} bean: a thin RFC&nbsp;6749 OAuth2 client over
 * Saxo's <b>configurable</b> token endpoint.
 *
 * <p>It speaks the raw OAuth2 protocol — the {@code authorization_code} grant (consumed once at
 * the end of the interactive onboarding redirect) and the {@code refresh_token} grant (the
 * proactive, rotating-token refresh). It holds no state, performs no persistence, and applies no
 * lease/locking; the single-flight refresh, persistence of rotated tokens, and re-auth
 * notification all live in {@code BrokerTokenRefreshService}, which consumes this port.</p>
 *
 * <p><b>Exactly one active bean (CDI).</b> This is the only <em>unqualified</em>
 * {@link SaxoOAuthClientPort} producer, so it is the bean every consumer
 * ({@code BrokerTokenRefreshService}, the onboarding service) resolves. The certificate-grant
 * skeleton {@link SaxoCertificateOAuthClient} is exposed only under
 * {@code @Named("saxoCertAuthClient")} and never participates in default resolution, so there is
 * no {@code AmbiguousResolutionException} at boot. The deferred {@link #exchangeCertificate}
 * path delegates to that gated stub so the whole port is satisfied by this one bean.</p>
 *
 * <p><b>Credentials — confidential client, HTTP Basic.</b> Saxo's server-side OAuth app is a
 * <em>confidential</em> client (docs/operations/SAXO_ANSWERS.md §1.5): the token call is
 * authenticated with the platform-level app key/secret (the registered Saxo OpenAPI app, supplied
 * from config {@code SAXO_APP_KEY}/{@code SAXO_APP_SECRET}) sent as an HTTP&nbsp;<b>Basic</b>
 * {@code Authorization} header — {@code client_id} as the username, {@code client_secret} as the
 * password (RFC&nbsp;6749 §2.3.1) — <b>not</b> as form fields. PKCE is the public/native-app
 * variant and is deliberately not used for this confidential flow. The Basic header is computed
 * here and threaded into the dumb HTTP surface {@link SaxoTokenEndpointRestClient}
 * (config key {@code saxo-oauth}); {@code CFG1} owns the real per-environment token-endpoint URL
 * and the WireMock test points it at a stub.</p>
 *
 * <p><b>TODO(Saxo):</b> the rotating single-use refresh and HTTP-Basic confidential auth are
 * implemented per SAXO_ANSWERS §1.4–§1.5; a per-environment (SIM vs LIVE) token <em>host</em> split
 * still needs the exact LIVE host string reconfirmed against the LIVE credentials packet
 * (SAXO_ANSWERS §1.5 ⚠️). The current rest-client points at a single configured endpoint regardless
 * of {@code environment}.</p>
 */
@ApplicationScoped
public class SaxoTokenEndpointClient implements SaxoOAuthClientPort {

    private static final Logger LOG = Logger.getLogger(SaxoTokenEndpointClient.class);

    private static final String GRANT_AUTHORIZATION_CODE = "authorization_code";
    private static final String GRANT_REFRESH_TOKEN = "refresh_token";

    private final SaxoTokenEndpointRestClient tokenEndpoint;
    private final SaxoCertificateOAuthClient certificateClient;
    private final String appKey;
    private final String appSecret;
    private final Clock clock;

    @Inject
    public SaxoTokenEndpointClient(
            @RestClient SaxoTokenEndpointRestClient tokenEndpoint,
            @Named("saxoCertAuthClient") SaxoCertificateOAuthClient certificateClient,
            @ConfigProperty(name = "quorumtrading.broker.saxo.app-key", defaultValue = "not-configured")
            String appKey,
            @ConfigProperty(name = "quorumtrading.broker.saxo.app-secret", defaultValue = "not-configured")
            String appSecret) {
        this(tokenEndpoint, certificateClient, appKey, appSecret, Clock.systemUTC());
    }

    /** Visible-for-testing constructor pinning credentials + clock so token expiry is deterministic. */
    SaxoTokenEndpointClient(
            SaxoTokenEndpointRestClient tokenEndpoint,
            SaxoCertificateOAuthClient certificateClient,
            String appKey,
            String appSecret,
            Clock clock) {
        this.tokenEndpoint = Objects.requireNonNull(tokenEndpoint, "tokenEndpoint");
        this.certificateClient = Objects.requireNonNull(certificateClient, "certificateClient");
        this.appKey = Objects.requireNonNull(appKey, "appKey");
        this.appSecret = Objects.requireNonNull(appSecret, "appSecret");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public OAuthTokens exchangeAuthorizationCode(
            String authorizationCode, String redirectUri, SaxoEnvironment environment) {
        requireNonBlank(authorizationCode, "authorizationCode");
        requireNonBlank(redirectUri, "redirectUri");
        Objects.requireNonNull(environment, "environment cannot be null");

        // NOTE: client credentials and the code are intentionally NOT logged.
        Instant issuedAt = clock.instant();
        try {
            SaxoTokenResponse response = tokenEndpoint.exchangeAuthorizationCode(
                    basicAuthHeader(), GRANT_AUTHORIZATION_CODE, authorizationCode, redirectUri);
            OAuthTokens tokens = toTokens(response, issuedAt, environment);
            LOG.infof("Saxo authorization-code exchange succeeded for environment=%s (expiry=%s)",
                    environment, tokens.expiry());
            return tokens;
        } catch (WebApplicationException e) {
            // A 4xx here means the code/redirect was rejected (already used, mismatch, expired).
            int status = statusOf(e);
            LOG.warnf("Saxo authorization-code exchange rejected (status=%d) for environment=%s",
                    status, environment);
            throw new IllegalArgumentException(
                    "Saxo authorization-code exchange rejected (status=" + status + ")", e);
        }
    }

    @Override
    public OAuthTokens refreshAccessToken(String refreshToken, SaxoEnvironment environment) {
        requireNonBlank(refreshToken, "refreshToken");
        Objects.requireNonNull(environment, "environment cannot be null");

        Instant issuedAt = clock.instant();
        try {
            SaxoTokenResponse response = tokenEndpoint.refreshAccessToken(
                    basicAuthHeader(), GRANT_REFRESH_TOKEN, refreshToken);
            OAuthTokens tokens = toTokens(response, issuedAt, environment);
            LOG.debugf("Saxo token refresh succeeded for environment=%s (expiry=%s)",
                    environment, tokens.expiry());
            return tokens;
        } catch (WebApplicationException e) {
            // A 4xx here means the supplied refresh token is no longer valid (already rotated,
            // expired, or revoked) — the chain cannot be advanced. The calling refresh service
            // translates this IllegalArgumentException into a RefreshChainBrokenException + reauth.
            int status = statusOf(e);
            LOG.warnf("Saxo token refresh rejected (status=%d) for environment=%s; refresh chain broken",
                    status, environment);
            throw new IllegalArgumentException(
                    "Saxo refresh-token grant rejected (status=" + status + ")", e);
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Delegates to the gated certificate skeleton ({@code @Named("saxoCertAuthClient")}),
     * which throws a clear disabled error until Saxo grants certificate-auth eligibility (OA6).
     * Keeping the delegation here means this single bean satisfies the whole port.</p>
     */
    @Override
    public OAuthTokens exchangeCertificate(SaxoEnvironment environment) {
        return certificateClient.exchangeCertificate(environment);
    }

    private OAuthTokens toTokens(SaxoTokenResponse response, Instant issuedAt, SaxoEnvironment environment) {
        if (response == null) {
            throw new IllegalArgumentException("Saxo token endpoint returned an empty body");
        }
        if (response.getAccessToken() == null || response.getAccessToken().isBlank()) {
            throw new IllegalArgumentException("Saxo token response missing access_token");
        }
        if (response.getRefreshToken() == null || response.getRefreshToken().isBlank()) {
            throw new IllegalArgumentException("Saxo token response missing refresh_token");
        }
        if (response.getExpiresIn() == null || response.getExpiresIn() <= 0) {
            throw new IllegalArgumentException("Saxo token response missing/invalid expires_in");
        }
        return OAuthTokens.of(
                response.getAccessToken(),
                response.getRefreshToken(),
                issuedAt,
                response.getExpiresIn(),
                environment);
    }

    /**
     * Builds the confidential-client HTTP&nbsp;Basic {@code Authorization} header value:
     * {@code "Basic " + base64(client_id ":" client_secret)} (RFC&nbsp;6749 §2.3.1 /
     * RFC&nbsp;7617). Saxo authenticates the token call with the app key as the username and the
     * app secret as the password (SAXO_ANSWERS §1.5) — never as form fields.
     */
    private String basicAuthHeader() {
        String raw = appKey + ":" + appSecret;
        String encoded = Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
        return "Basic " + encoded;
    }

    private static int statusOf(WebApplicationException e) {
        return e.getResponse() != null ? e.getResponse().getStatus() : -1;
    }

    private static void requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " cannot be null or blank");
        }
    }
}
