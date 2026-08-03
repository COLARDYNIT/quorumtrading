package com.quorumtrading.adapter.mcp.auth;

import com.quorumtrading.application.event.ApiTokenUsedEvent;
import com.quorumtrading.application.usecase.apitoken.ResolveUserByApiTokenUseCase;
import com.quorumtrading.application.usecase.result.ResolvedApiToken;
import com.quorumtrading.model.valueobject.UserId;
import io.vertx.core.http.HttpServerRequest;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;

/**
 * Request-scoped CDI bean that resolves the current user from an API token.
 * Reads the Authorization header, hashes the bearer token, and resolves
 * to a UserId via the application-layer ResolveUserByApiTokenUseCase.
 *
 * <p>On successful authentication, fires an {@link ApiTokenUsedEvent} to update
 * the token's last-used timestamp asynchronously.</p>
 */
@RequestScoped
public class McpUserContext {

    private static final Logger logger = LoggerFactory.getLogger(McpUserContext.class);
    private static final String BEARER_PREFIX = "Bearer ";

    private final HttpServerRequest httpServerRequest;
    private final ResolveUserByApiTokenUseCase resolveUserByApiTokenUseCase;
    private final Event<ApiTokenUsedEvent> apiTokenUsedEvent;

    private UserId cachedUserId;

    @Inject
    public McpUserContext(HttpServerRequest httpServerRequest,
                          ResolveUserByApiTokenUseCase resolveUserByApiTokenUseCase,
                          Event<ApiTokenUsedEvent> apiTokenUsedEvent) {
        this.httpServerRequest = httpServerRequest;
        this.resolveUserByApiTokenUseCase = resolveUserByApiTokenUseCase;
        this.apiTokenUsedEvent = apiTokenUsedEvent;
    }

    /**
     * Resolves and returns the current user's ID from the API token.
     * Result is cached within the request scope.
     *
     * @return the authenticated user's UserId
     * @throws SecurityException if the token is missing, invalid, expired, or revoked
     */
    public UserId getUserId() {
        if (cachedUserId != null) {
            return cachedUserId;
        }

        String rawToken = extractBearerToken();
        if (rawToken == null) {
            throw new SecurityException("Missing or invalid Authorization header. Expected: Bearer qt_...");
        }

        logger.debug("Resolving MCP user from API token (prefix={}...)", rawToken.substring(0, Math.min(8, rawToken.length())));
        ResolvedApiToken resolved = resolveUserByApiTokenUseCase.execute(rawToken);
        cachedUserId = resolved.userId();

        // Fire async event to update last_used_at without blocking the response
        apiTokenUsedEvent.fireAsync(new ApiTokenUsedEvent(resolved.tokenId(), Instant.now()));

        return cachedUserId;
    }

    private String extractBearerToken() {
        String authHeader = httpServerRequest.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            return null;
        }
        String token = authHeader.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }
}
