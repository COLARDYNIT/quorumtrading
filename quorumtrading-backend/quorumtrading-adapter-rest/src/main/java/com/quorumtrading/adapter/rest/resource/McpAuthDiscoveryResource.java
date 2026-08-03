package com.quorumtrading.adapter.rest.resource;

import jakarta.annotation.security.PermitAll;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Stub endpoints for OAuth 2.x discovery + Dynamic Client Registration probes
 * made by MCP clients (Claude Code, mcp-inspector, etc.).
 *
 * <p>QuorumTrading's MCP server authenticates with pre-issued API tokens
 * ({@code Authorization: Bearer qt_...}, generated at /settings/api-tokens),
 * not OAuth. But when a client's bearer token is rejected, the SDK falls back
 * to the OAuth flow defined in the MCP 2025-06-18 spec — it probes:
 *
 * <ul>
 *   <li>{@code GET  /.well-known/oauth-protected-resource} (RFC 9728)</li>
 *   <li>{@code GET  /.well-known/oauth-authorization-server} (RFC 8414)</li>
 *   <li>{@code POST /register} (RFC 7591 Dynamic Client Registration)</li>
 * </ul>
 *
 * Without these endpoints the global ProblemDetailsExceptionMapper returns an
 * RFC 7807 body. The SDK's OAuth client tries to parse that body as an OAuth
 * error response (which expects {@code {"error":"...","error_description":"..."}})
 * and crashes with a Zod validation failure:
 *
 * <pre>
 * SDK auth failed: HTTP 404: Invalid OAuth error response: ZodError [
 *   { path: ["error"], message: "expected string, received undefined" }
 * ]
 * </pre>
 *
 * <p>By returning OAuth-shaped 404/400 bodies here, the SDK parses cleanly and
 * surfaces the {@code error_description} to the user — telling them exactly
 * where to regenerate an API token and how to configure it.
 *
 * <p>None of these endpoints actually implement OAuth — they document a dead
 * end. If we ever bridge OAuth to API tokens (so Claude Code can self-onboard
 * via an OAuth consent screen), replace these stubs.
 */
@Path("/")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
@PermitAll
public class McpAuthDiscoveryResource {

    private static final String API_TOKENS_URL = "https://quorumtrading.com/settings/api-tokens";

    /**
     * Per RFC 7591: failed Dynamic Client Registration MUST return an
     * {@code error} field. We return 404 so caches don't store this as a real
     * client registration; the body carries the human-readable redirect.
     */
    @POST
    @Path("/register")
    public Response register() {
        return oauthError(
            404,
            "registration_not_supported",
            "Dynamic Client Registration is not supported. Configure an API token at "
                + API_TOKENS_URL + " and pass it via --header \"Authorization: Bearer <token>\"."
        );
    }

    /**
     * Per RFC 9728, this should return a JSON metadata document. We don't run
     * an authorization server, so we return 404 in OAuth-error shape — the SDK
     * understands "no OAuth available here" and stops probing.
     */
    @GET
    @Path("/.well-known/oauth-protected-resource")
    public Response oauthProtectedResource() {
        return oauthError(
            404,
            "no_authorization_server",
            "This MCP server authenticates with pre-issued API tokens. Generate one at "
                + API_TOKENS_URL + " and pass it via --header \"Authorization: Bearer <token>\"."
        );
    }

    /**
     * Same rationale as {@link #oauthProtectedResource()} — RFC 8414 AS metadata
     * is not served because we are not an OAuth authorization server.
     */
    @GET
    @Path("/.well-known/oauth-authorization-server")
    public Response oauthAuthorizationServer() {
        return oauthError(
            404,
            "no_authorization_server",
            "This MCP server is not an OAuth authorization server. Use API tokens from "
                + API_TOKENS_URL + " instead."
        );
    }

    private static Response oauthError(int status, String error, String description) {
        // LinkedHashMap so field order matches OAuth error convention (error, error_description).
        Map<String, String> body = new LinkedHashMap<>();
        body.put("error", error);
        body.put("error_description", description);
        return Response.status(status)
            .type(MediaType.APPLICATION_JSON)
            .entity(body)
            .build();
    }
}
