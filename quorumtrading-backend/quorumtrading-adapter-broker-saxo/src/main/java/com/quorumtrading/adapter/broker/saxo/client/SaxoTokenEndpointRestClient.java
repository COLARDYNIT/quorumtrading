package com.quorumtrading.adapter.broker.saxo.client;

import com.quorumtrading.adapter.broker.saxo.client.dto.SaxoTokenResponse;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

/**
 * Low-level, form-encoded HTTP surface for Saxo's OAuth2 token endpoint (RFC&nbsp;6749).
 *
 * <p>This is the typed {@code application/x-www-form-urlencoded} {@code POST} the standard
 * OAuth2 {@code authorization_code} and {@code refresh_token} grants require. It carries no
 * business logic.</p>
 *
 * <p><b>Confidential client — HTTP Basic auth.</b> Saxo's server-side OAuth app is a
 * <em>confidential</em> client: the token call is authenticated with the app key/secret sent as
 * an HTTP&nbsp;Basic {@code Authorization} header (RFC&nbsp;6749 §2.3.1 — {@code client_id} as the
 * username, {@code client_secret} as the password), <b>not</b> as {@code client_id}/
 * {@code client_secret} form fields. {@link SaxoTokenEndpointClient} computes the Basic header and
 * passes it here; this interface stays a dumb HTTP surface. (PKCE is the public/native-app variant
 * and is not used for this flow — see docs/operations/SAXO_ANSWERS.md §1.5.) The bearer it
 * <em>returns</em> is what the rest of the adapter then uses.
 * {@link SaxoTokenEndpointClient} is the single active
 * {@link com.quorumtrading.application.port.out.SaxoOAuthClientPort} bean that drives it.</p>
 *
 * <p>The base URL is the <b>configurable Saxo token endpoint</b>
 * ({@code quarkus.rest-client.saxo-oauth.url}); {@code CFG1} owns the real per-environment
 * value, the WireMock test points it at a stub. The published Saxo token host is
 * {@code https://{sim|live}.logonvalidation.net/token} (SAXO_ANSWERS §1.5) — TODO-flagged because
 * the exact LIVE host string must be reconfirmed against the LIVE credentials packet, and the
 * current single rest-client points at one configured endpoint regardless of environment.</p>
 */
@RegisterRestClient(configKey = "saxo-oauth")
@Consumes(MediaType.APPLICATION_FORM_URLENCODED)
@Produces(MediaType.APPLICATION_JSON)
public interface SaxoTokenEndpointRestClient {

    /**
     * Exchanges an authorization code for an initial token pair (RFC&nbsp;6749 §4.1.3).
     *
     * <p>{@code POST {token-endpoint}} with the Basic {@code Authorization} header carrying the
     * confidential client's {@code client_id:client_secret}, and form body
     * {@code grant_type=authorization_code&code=…&redirect_uri=…}.</p>
     *
     * <p>TODO(Saxo): confirm the exact token-endpoint path segment and whether Saxo requires
     * any non-RFC parameters (e.g. an explicit {@code redirect_uri} echo or app-context field).</p>
     *
     * @param authorizationHeader the {@code Basic base64(client_id:client_secret)} header value
     * @param grantType           must be {@code authorization_code}
     * @param code                the one-time authorization code from the redirect
     * @param redirectUri         the redirect URI registered with the app
     * @return the issued token pair
     */
    @POST
    @Path("")
    SaxoTokenResponse exchangeAuthorizationCode(
            @HeaderParam(HttpHeaders.AUTHORIZATION) String authorizationHeader,
            @FormParam("grant_type") String grantType,
            @FormParam("code") String code,
            @FormParam("redirect_uri") String redirectUri);

    /**
     * Refreshes an access token using the current rotating refresh token (RFC&nbsp;6749 §6).
     *
     * <p>{@code POST {token-endpoint}} with the Basic {@code Authorization} header carrying the
     * confidential client's {@code client_id:client_secret}, and form body
     * {@code grant_type=refresh_token&refresh_token=…}. Saxo rotates the refresh token on every
     * call, so the response carries a brand-new refresh token that MUST be persisted.</p>
     *
     * @param authorizationHeader the {@code Basic base64(client_id:client_secret)} header value
     * @param grantType           must be {@code refresh_token}
     * @param refreshToken        the current rotating refresh token to spend
     * @return the refreshed token pair, including the rotated refresh token to persist
     */
    @POST
    @Path("")
    SaxoTokenResponse refreshAccessToken(
            @HeaderParam(HttpHeaders.AUTHORIZATION) String authorizationHeader,
            @FormParam("grant_type") String grantType,
            @FormParam("refresh_token") String refreshToken);
}
