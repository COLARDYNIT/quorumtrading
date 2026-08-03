package com.quorumtrading.application.port.out;

import com.quorumtrading.model.enums.SaxoEnvironment;

/**
 * Out-port for the low-level Saxo OAuth2 token endpoint.
 *
 * <p>Speaks the raw OAuth2 protocol against Saxo's authorization server: it exchanges an
 * authorization code (after the interactive consent redirect) for an initial token pair, and
 * refreshes an access token using a rotating refresh token. It is a thin protocol client — it
 * holds no state, performs no persistence, and applies no lease/locking. Higher-level
 * concerns (single-flight refresh, persistence of rotated tokens, re-auth notification) live in
 * the application services that consume this port.</p>
 *
 * <p>The Wave-3 adapter implements this against Saxo's OpenAPI token endpoint; this interface is
 * the seam that lets the OAuth ports land before either Saxo adapter, breaking the
 * saxo-oauth ↔ saxo-broker-adapter dependency cycle.</p>
 */
public interface SaxoOAuthClientPort {

    /**
     * Exchanges an authorization code for an initial access/refresh token pair.
     *
     * <p>Called once at the end of the interactive onboarding redirect, after the user has
     * granted consent and Saxo redirected back with a {@code code} parameter.</p>
     *
     * @param authorizationCode the one-time authorization code from the OAuth redirect (non-blank)
     * @param redirectUri       the redirect URI registered with the app; must match the value used
     *                          to obtain the code (non-blank)
     * @param environment       the Saxo environment (SIM/LIVE) whose token endpoint to call (non-null)
     * @return the freshly issued token pair
     * @throws IllegalArgumentException if the code/redirect is rejected by the token endpoint
     */
    OAuthTokens exchangeAuthorizationCode(
            String authorizationCode,
            String redirectUri,
            SaxoEnvironment environment);

    /**
     * Refreshes an access token using the current rotating refresh token.
     *
     * <p>Saxo rotates the refresh token on every call: the returned {@link OAuthTokens} carries a
     * brand-new refresh token that callers MUST persist, because the supplied one is invalidated.
     * If the supplied refresh token is no longer valid (already rotated, expired, or revoked), the
     * chain cannot be advanced and a {@link com.quorumtrading.application.exception.RefreshChainBrokenException}
     * is raised by the calling service.</p>
     *
     * @param refreshToken the current rotating refresh token to spend (non-blank)
     * @param environment  the Saxo environment (SIM/LIVE) whose token endpoint to call (non-null)
     * @return the refreshed token pair, including the rotated refresh token to persist
     * @throws IllegalArgumentException if the refresh token is rejected by the token endpoint
     */
    OAuthTokens refreshAccessToken(String refreshToken, SaxoEnvironment environment);

    /**
     * Exchanges a client certificate for an access token (unattended, belt-and-braces auth path).
     *
     * <p><strong>Deferred / gated stub.</strong> Certificate-based service auth is only available
     * once Saxo confirms eligibility for the app; until then implementations throw a clear
     * disabled error. This method exists on the port so the cert-grant path can be wired without a
     * later interface change (see the deferred {@code OA6-cert-stub} work package).</p>
     *
     * @param environment the Saxo environment (SIM/LIVE) to authenticate against (non-null)
     * @return an access token pair obtained via the certificate grant
     * @throws UnsupportedOperationException if certificate-based auth is disabled / not yet eligible
     */
    default OAuthTokens exchangeCertificate(SaxoEnvironment environment) {
        throw new UnsupportedOperationException(
                "Certificate-based Saxo auth is not enabled (gated on broker eligibility)");
    }
}
