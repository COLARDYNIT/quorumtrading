package com.quorumtrading.adapter.broker.saxo.client;

import com.quorumtrading.application.port.out.OAuthTokens;
import com.quorumtrading.application.port.out.SaxoOAuthClientPort;
import com.quorumtrading.model.enums.SaxoEnvironment;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Typed;
import jakarta.inject.Named;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.util.Objects;

/**
 * Certificate-grant Saxo OAuth client — <b>GATED, DISABLED STUB (OA6-cert-stub)</b>.
 *
 * <p><b>Permanently disabled for retail.</b> Saxo offers Certificate-Based Authentication (CBA)
 * to <b>institutional partners only</b>; retail/direct clients cannot request it because MiFID
 * disclaimers must be shown at login and cannot be automated (docs/operations/SAXO_ANSWERS.md §1.2).
 * For our retail Saxo accounts this path is therefore a dead end: authentication relies solely on
 * the rotating single-use refresh-token OAuth model ({@link SaxoTokenEndpointClient}) plus a
 * resilient refresher. This stub stays in place only so the port is satisfied and so the gate can
 * be flipped <em>if</em> Saxo ever grants this app institutional CBA eligibility (not expected for
 * retail).</p>
 *
 * <p>Certificate-based (service-account / unattended) authentication would otherwise be the
 * belt-and-braces fallback for running the DCA scheduler without an interactive refresh-token
 * chain. It remains a disabled stub gated by {@code quorumtrading.broker.saxo.cert.enabled}
 * (default {@code false}, and expected to stay {@code false} for retail):</p>
 * <ul>
 *   <li><b>disabled</b> (the default) — {@link #exchangeCertificate(SaxoEnvironment)}
 *       throws a clear {@link IllegalStateException} ("certificate auth not enabled /
 *       partner-gated") and performs <b>no</b> token exchange;</li>
 *   <li><b>enabled</b> — the real certificate exchange is still not built (it lands
 *       only after Saxo grants eligibility), so it throws
 *       {@link UnsupportedOperationException} to signal the deferred boundary rather
 *       than silently returning a fake token.</li>
 * </ul>
 *
 * <p><b>Why this is NOT a {@link SaxoOAuthClientPort} CDI bean:</b> the interactive
 * token-endpoint client ({@link SaxoTokenEndpointClient}, the real
 * {@code exchangeAuthorizationCode} / {@code refreshAccessToken} impl, landed in OA2) is
 * the single active {@link SaxoOAuthClientPort} bean. To guarantee <b>exactly one</b>
 * active port bean (no {@code AmbiguousResolutionException} at boot), this cert skeleton
 * is restricted with {@code @Typed(SaxoCertificateOAuthClient.class)} so its only bean
 * type is its own class — it does not participate in {@link SaxoOAuthClientPort}
 * resolution at all. It is still reachable by concrete type under
 * {@code @Named("saxoCertAuthClient")}; {@link SaxoTokenEndpointClient} injects it that
 * way and delegates the gated {@link #exchangeCertificate} path to it. The
 * auth-code/refresh methods here remain scaffold throwers so this stub can never be
 * mistaken for the production OAuth client.</p>
 *
 * <p><b>Boundary:</b> disabled stub + gating only — no real certificate exchange,
 * no private-key handling, no HTTP. Implementing the actual grant is deferred until
 * Saxo grants eligibility.</p>
 */
@ApplicationScoped
@Named("saxoCertAuthClient")
@Typed(SaxoCertificateOAuthClient.class)
public class SaxoCertificateOAuthClient implements SaxoOAuthClientPort {

    private static final Logger LOG = Logger.getLogger(SaxoCertificateOAuthClient.class);

    /** Config key gating the certificate-grant path. Default OFF (partner-gated). */
    static final String CERT_ENABLED_PROPERTY = "quorumtrading.broker.saxo.cert.enabled";

    @ConfigProperty(name = CERT_ENABLED_PROPERTY, defaultValue = "false")
    boolean certEnabled;

    /** CDI no-arg constructor — {@link #certEnabled} is field-injected from config. */
    public SaxoCertificateOAuthClient() {
    }

    /**
     * Test/manual factory that pins the gate flag explicitly, so the disabled path can
     * be asserted with a plain JUnit test (no Quarkus boot / config required).
     *
     * @param certEnabled whether the certificate-grant path is enabled
     * @return a client wired with the given gate state
     */
    public static SaxoCertificateOAuthClient withCertEnabled(boolean certEnabled) {
        SaxoCertificateOAuthClient client = new SaxoCertificateOAuthClient();
        client.certEnabled = certEnabled;
        return client;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Gated, disabled stub. When {@code quorumtrading.broker.saxo.cert.enabled} is
     * {@code false} (the default), this throws a clear {@link IllegalStateException}
     * and exchanges nothing. When the flag is flipped on, it throws
     * {@link UnsupportedOperationException} because the real certificate exchange is
     * deferred until Saxo grants eligibility (OA6 boundary).</p>
     *
     * @throws IllegalStateException         if certificate auth is disabled (the default)
     * @throws UnsupportedOperationException if enabled but the real grant is not yet built
     */
    @Override
    public OAuthTokens exchangeCertificate(SaxoEnvironment environment) {
        Objects.requireNonNull(environment, "environment cannot be null");
        if (!certEnabled) {
            LOG.warnf("Saxo certificate-grant auth requested for environment=%s but it is disabled "
                    + "(%s=false); CBA is institutional-only and not available to retail accounts.",
                    environment, CERT_ENABLED_PROPERTY);
            throw new IllegalStateException(
                    "Saxo certificate auth (CBA) is not enabled: it is offered to INSTITUTIONAL "
                    + "PARTNERS ONLY and is permanently unavailable to retail accounts "
                    + "(SAXO_ANSWERS §1.2). Retail uses the rotating refresh-token OAuth model. Set "
                    + CERT_ENABLED_PROPERTY + "=true only if Saxo grants this app institutional CBA "
                    + "eligibility. The certificate-grant path is deferred (OA6-cert-stub).");
        }
        // Flag flipped on but the real exchange is intentionally not built yet.
        throw new UnsupportedOperationException(
                "Saxo certificate-grant exchange is not implemented yet (deferred OA6 boundary): "
                + CERT_ENABLED_PROPERTY + " is enabled but the real certificate exchange ships only "
                + "after Saxo grants certificate-auth eligibility.");
    }

    /**
     * {@inheritDoc}
     *
     * <p>Not implemented here. The interactive authorization-code exchange is owned by
     * a later OAuth work package on the <em>default</em> {@link SaxoOAuthClientPort}
     * bean; this cert skeleton only carries the gated {@link #exchangeCertificate}
     * path.</p>
     *
     * @throws UnsupportedOperationException always (owned by the OAuth token-endpoint WP)
     */
    @Override
    public OAuthTokens exchangeAuthorizationCode(
            String authorizationCode, String redirectUri, SaxoEnvironment environment) {
        throw new UnsupportedOperationException(
                "exchangeAuthorizationCode is not implemented on the certificate-grant skeleton "
                + "(SaxoCertificateOAuthClient handles only the gated certificate path; the "
                + "interactive token-endpoint client is a later OAuth work package).");
    }

    /**
     * {@inheritDoc}
     *
     * <p>Not implemented here. The rotating refresh-token exchange is owned by a later
     * OAuth work package on the <em>default</em> {@link SaxoOAuthClientPort} bean; this
     * cert skeleton only carries the gated {@link #exchangeCertificate} path.</p>
     *
     * @throws UnsupportedOperationException always (owned by the OAuth token-endpoint WP)
     */
    @Override
    public OAuthTokens refreshAccessToken(String refreshToken, SaxoEnvironment environment) {
        throw new UnsupportedOperationException(
                "refreshAccessToken is not implemented on the certificate-grant skeleton "
                + "(SaxoCertificateOAuthClient handles only the gated certificate path; the "
                + "interactive token-endpoint client is a later OAuth work package).");
    }
}
