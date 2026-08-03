package com.quorumtrading.adapter.rest.resource;

import com.quorumtrading.adapter.rest.dto.response.AuthProvidersResponse;
import com.quorumtrading.adapter.rest.dto.response.AuthProvidersResponse.AuthProvider;
import com.quorumtrading.adapter.rest.dto.response.UserResponse;
import com.quorumtrading.adapter.rest.security.CurrentUserProvider;
import com.quorumtrading.application.port.out.UserRepository;
import com.quorumtrading.application.usecase.AuthenticateUserUseCase;
import com.quorumtrading.application.usecase.DeleteUserAccountUseCase;
import com.quorumtrading.application.usecase.command.AuthenticateUserCommand;
import com.quorumtrading.model.entity.User;
import com.quorumtrading.model.enums.OAuthProvider;
import io.quarkus.oidc.IdToken;
import io.quarkus.oidc.OidcSession;
import io.quarkus.security.Authenticated;
import jakarta.annotation.security.PermitAll;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.jboss.logging.Logger;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * REST resource for OAuth2/OIDC authentication operations.
 * <p>
 * Provides endpoints for initiating OAuth login flows, handling provider callbacks,
 * session management, and retrieving current user information. Supports multiple
 * OAuth providers (Google, Apple) through Quarkus OIDC integration.
 * </p>
 */
@Path("/api/v1/auth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
@Tag(name = "Authentication", description = "OAuth2/OIDC authentication endpoints")
@PermitAll
public class AuthResource {

    private static final Logger LOG = Logger.getLogger(AuthResource.class);

    @Inject
    CurrentUserProvider currentUser;

    @Inject
    AuthenticateUserUseCase authenticateUserUseCase;

    @Inject
    DeleteUserAccountUseCase deleteUserAccountUseCase;

    @Inject
    UserRepository userRepository;

    @Inject
    @IdToken
    JsonWebToken idToken;

    @Inject
    OidcSession oidcSession;

    @ConfigProperty(name = "quorumtrading.auth.frontend-callback-url")
    String frontendCallbackUrl;

    @ConfigProperty(name = "quarkus.oidc.google.tenant-enabled", defaultValue = "false")
    boolean googleEnabled;

    @ConfigProperty(name = "quarkus.oidc.apple.tenant-enabled", defaultValue = "false")
    boolean appleEnabled;

    @ConfigProperty(name = "quarkus.oidc.github.tenant-enabled", defaultValue = "false")
    boolean githubEnabled;

    @ConfigProperty(name = "quarkus.oidc.microsoft.tenant-enabled", defaultValue = "false")
    boolean microsoftEnabled;

    @ConfigProperty(name = "quarkus.oidc.gitlab.tenant-enabled", defaultValue = "false")
    boolean gitlabEnabled;

    @ConfigProperty(name = "quarkus.oidc.amazon.tenant-enabled", defaultValue = "false")
    boolean amazonEnabled;

    @ConfigProperty(name = "quorumtrading.auth.dev-login-enabled", defaultValue = "false")
    boolean devLoginEnabled;

    @ConfigProperty(name = "quorumtrading.auth.dev-session-max-age-seconds", defaultValue = "28800")
    int devSessionMaxAgeSeconds;

    @ConfigProperty(name = "quorumtrading.auth.cookie-domain", defaultValue = "localhost")
    String cookieDomain;

    @Context
    HttpHeaders httpHeaders;

    @GET
    @Path("/providers")
    @Operation(summary = "Get enabled auth providers", description = "Returns list of enabled OAuth providers")
    @APIResponse(responseCode = "200", description = "List of enabled providers")
    public Response getProviders() {
        List<AuthProvider> providers = new ArrayList<>();

        if (googleEnabled) {
            providers.add(new AuthProvider("google", "Google", "google"));
        }
        if (microsoftEnabled) {
            providers.add(new AuthProvider("microsoft", "Microsoft", "microsoft"));
        }
        if (gitlabEnabled) {
            providers.add(new AuthProvider("gitlab", "GitLab", "gitlab"));
        }
        if (githubEnabled) {
            providers.add(new AuthProvider("github", "GitHub", "github"));
        }
        if (appleEnabled) {
            providers.add(new AuthProvider("apple", "Apple", "apple"));
        }
        if (amazonEnabled) {
            providers.add(new AuthProvider("amazon", "Amazon", "amazon"));
        }

        return Response.ok(new AuthProvidersResponse(providers, devLoginEnabled)).build();
    }

    @GET
    @Path("/dev-login")
    @Operation(summary = "Dev login bypass", description = "Creates a session for the single fixed local user. Used by E2E tests, dev mode and the standalone self-host edition. Disabled in production.")
    @APIResponse(responseCode = "302", description = "Redirect to frontend callback with session")
    @APIResponse(responseCode = "404", description = "Endpoint disabled in production")
    public Response devLogin() {
        if (!devLoginEnabled) {
            throw new NotFoundException("Dev login is not enabled");
        }

        // The single fixed local identity — shared by E2E tests, dev mode and the standalone
        // self-host edition (one flow, one user). Existing rows under the legacy e2e-test name
        // are renamed by migration V121.
        String email = "local@quorumtrading.local";
        String name = "Local";
        String sub = "dev-login-local-sub";

        AuthenticateUserCommand command = new AuthenticateUserCommand(
            email, name, null, OAuthProvider.GOOGLE, sub
        );

        User user = authenticateUserUseCase.execute(command);

        LOG.infof("Dev login successful for user: %s", email);

        // Create dev session cookie with user email
        // Set domain to localhost to share across ports (3000 frontend, 8080 backend)
        NewCookie devSessionCookie = new NewCookie.Builder("dev_session")
            .value(email)
            .path("/")
            .domain(cookieDomain)
            .maxAge(devSessionMaxAgeSeconds)
            .httpOnly(true)
            .sameSite(NewCookie.SameSite.LAX)
            .build();

        // Redirect to frontend callback with success and session cookie
        return Response.status(Response.Status.FOUND)
            .location(URI.create(frontendCallbackUrl + "?success=true"))
            .cookie(devSessionCookie)
            .build();
    }

    @GET
    @Path("/fut-login")
    @Operation(summary = "FUT login bypass", description = "Creates a fresh new user session for functional user testing. Only available in dev/local profiles.")
    @APIResponse(responseCode = "302", description = "Redirect to frontend callback with session")
    @APIResponse(responseCode = "404", description = "Endpoint disabled in production")
    public Response futLogin() {
        if (!devLoginEnabled) {
            throw new NotFoundException("FUT login is not enabled");
        }

        // Generate a unique user for each FUT session
        String uniqueId = java.util.UUID.randomUUID().toString().substring(0, 8);
        String email = "fut-" + uniqueId + "@quorumtrading.local";
        String name = "FUT User " + uniqueId;
        String sub = "fut-login-" + uniqueId + "-sub";

        AuthenticateUserCommand command = new AuthenticateUserCommand(
            email, name, null, OAuthProvider.GOOGLE, sub
        );

        User user = authenticateUserUseCase.execute(command);

        LOG.infof("FUT login successful for new user: %s (userId=%s)", email, user.getId());

        NewCookie futSessionCookie = new NewCookie.Builder("dev_session")
            .value(email)
            .path("/")
            .domain(cookieDomain)
            .maxAge(devSessionMaxAgeSeconds)
            .httpOnly(true)
            .sameSite(NewCookie.SameSite.LAX)
            .build();

        return Response.status(Response.Status.FOUND)
            .location(URI.create(frontendCallbackUrl + "?success=true"))
            .cookie(futSessionCookie)
            .build();
    }

    @GET
    @Path("/login/{provider}")
    @Operation(summary = "Initiate OAuth login", description = "Redirects to OAuth provider for authentication")
    @APIResponse(responseCode = "302", description = "Redirect to OAuth provider")
    public Response login(@PathParam("provider") String provider) {
        // Quarkus OIDC handles the redirect automatically via tenant resolver
        // This endpoint exists for explicit login initiation
        String authUrl = switch (provider.toLowerCase()) {
            case "google" -> "/api/v1/auth/callback/google";
            case "apple" -> "/api/v1/auth/callback/apple";
            case "github" -> "/api/v1/auth/callback/github";
            case "microsoft" -> "/api/v1/auth/callback/microsoft";
            case "gitlab" -> "/api/v1/auth/callback/gitlab";
            case "amazon" -> "/api/v1/auth/callback/amazon";
            default -> throw new BadRequestException("Unknown provider: " + provider);
        };

        Response.ResponseBuilder response = Response.status(Response.Status.FOUND)
            .location(URI.create(authUrl));

        // Clear stale OIDC session/state cookies to force a fresh OAuth flow.
        // Without this, browsers with expired session cookies from previous logins
        // fail with "Not Authenticated" because Quarkus tries to validate the stale
        // session instead of initiating a new OAuth redirect.
        clearStaleOidcCookies(response);

        return response.build();
    }

    /**
     * Clears any Quarkus OIDC session (q_session_*) and state (q_auth_*) cookies
     * found in the current request. Uses raw Set-Cookie headers to avoid JAX-RS
     * cookie name deduplication when clearing multiple cookies.
     */
    private void clearStaleOidcCookies(Response.ResponseBuilder response) {
        if (httpHeaders == null) return;

        var cookies = httpHeaders.getCookies();
        for (String name : cookies.keySet()) {
            if (name.startsWith("q_session") || name.startsWith("q_auth")) {
                // Clear without explicit domain (matches cookies set on exact host)
                response.header("Set-Cookie", name + "=; Path=/; Max-Age=0");
                // Also clear with configured cookie domain (matches domain-scoped cookies)
                if (cookieDomain != null && !"localhost".equals(cookieDomain)) {
                    response.header("Set-Cookie",
                        name + "=; Path=/; Domain=" + cookieDomain + "; Max-Age=0");
                }
            }
        }
    }

    @GET
    @Path("/callback/{provider}")
    @Authenticated
    @Operation(summary = "OAuth callback", description = "Handles OAuth provider callback and creates/updates user")
    public Response callback(@PathParam("provider") String provider) {
        // Extract user info from ID token (populated by Quarkus OIDC)
        String email = idToken.getClaim("email");
        String name = idToken.getClaim("name");
        String picture = idToken.getClaim("picture");
        String sub = idToken.getSubject();

        if (email == null) {
            LOG.errorf("OAuth callback for provider '%s': email claim is null (sub=%s)", provider, sub);
            throw new BadRequestException("Email not provided by OAuth provider");
        }

        OAuthProvider oauthProvider = OAuthProvider.valueOf(provider.toUpperCase());

        AuthenticateUserCommand command = new AuthenticateUserCommand(
            email, name, picture, oauthProvider, sub
        );

        User user = authenticateUserUseCase.execute(command);

        LOG.infof("OAuth login successful for %s via %s (userId=%s)", email, provider, user.getId());

        // Redirect to frontend with session established
        return Response.status(Response.Status.FOUND)
            .location(URI.create(frontendCallbackUrl + "?success=true"))
            .build();
    }

    @GET
    @Path("/error")
    @Operation(summary = "OAuth error", description = "Handles OIDC authentication errors by redirecting to frontend")
    public Response authError(@QueryParam("error") String error,
                              @QueryParam("error_description") String errorDescription) {
        String clientIp = extractClientIp();
        String userAgent = httpHeaders != null ? httpHeaders.getHeaderString("User-Agent") : "unknown";
        LOG.errorf("SECURITY: OIDC authentication failure — error=%s, description=%s, ip=%s, user-agent=%s",
            error, errorDescription, clientIp, userAgent);
        String errorParam = error != null ? error : "authentication_failed";
        return Response.status(Response.Status.FOUND)
            .location(URI.create(frontendCallbackUrl + "?error=" + errorParam))
            .build();
    }

    @POST
    @Path("/logout")
    @Authenticated
    @Operation(summary = "Logout", description = "Terminates the current session")
    @APIResponse(responseCode = "204", description = "Successfully logged out")
    public Response logout() {
        oidcSession.logout().await().indefinitely();

        // Clear the dev_session cookie (used by dev/e2e/FUT login flows)
        NewCookie clearDevSession = new NewCookie.Builder("dev_session")
            .value("")
            .path("/")
            .domain(cookieDomain)
            .maxAge(0)
            .httpOnly(true)
            .sameSite(NewCookie.SameSite.LAX)
            .build();

        return Response.noContent()
            .cookie(clearDevSession)
            .build();
    }

    @GET
    @Path("/me")
    @Operation(summary = "Get current user", description = "Returns the currently authenticated user's information")
    @APIResponse(responseCode = "200", description = "Current user information")
    @APIResponse(responseCode = "401", description = "Not authenticated")
    public Response getCurrentUser() {
        String email = currentUser.getEmail()
            .orElseThrow(() -> new NotAuthorizedException("Authentication required"));

        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new NotFoundException("User not found"));

        return Response.ok(buildUserResponse(user)).build();
    }

    @DELETE
    @Path("/account")
    @Operation(summary = "Delete account", description = "Permanently deletes the authenticated user's account and all associated data (GDPR right to erasure)")
    @APIResponse(responseCode = "204", description = "Account successfully deleted")
    @APIResponse(responseCode = "401", description = "Not authenticated")
    @APIResponse(responseCode = "404", description = "User not found")
    public Response deleteAccount() {
        String email = currentUser.getEmail()
            .orElseThrow(() -> new NotAuthorizedException("Authentication required"));

        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new NotFoundException("User not found"));

        LOG.infof("Account deletion requested by user %s (%s)", user.getId(), email);

        deleteUserAccountUseCase.execute(user.getId());

        return Response.noContent().build();
    }

    private String extractClientIp() {
        if (httpHeaders == null) return "unknown";
        String forwarded = httpHeaders.getHeaderString("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return "unknown";
    }

    private UserResponse buildUserResponse(User user) {
        return new UserResponse(
            user.getId().value().toString(),
            user.getEmail(),
            user.getDisplayName(),
            user.getAvatarUrl(),
            user.getRole().name(),
            user.getOauthProviders().stream()
                .map(p -> p.getProvider().name())
                .collect(Collectors.toList()),
            user.getCreatedAt(),
            user.getLastLoginAt(),
            user.isWelcomeBudgetCredited(),
            user.isReturningUser()
        );
    }
}
