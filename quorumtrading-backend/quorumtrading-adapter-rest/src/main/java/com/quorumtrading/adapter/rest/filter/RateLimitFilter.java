package com.quorumtrading.adapter.rest.filter;

import com.quorumtrading.adapter.rest.exception.ProblemDetails;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.value.ValueCommands;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.vertx.http.runtime.CurrentVertxRequest;
import io.vertx.ext.web.RoutingContext;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * JAX-RS request filter that enforces per-user API rate limiting using Redis counters.
 *
 * <p><b>How it works:</b></p>
 * <ul>
 *   <li>For authenticated users: rate limits by user email (extracted from OIDC token or dev session cookie)</li>
 *   <li>For unauthenticated requests: rate limits by IP address (rightmost X-Forwarded-For entry — the one
 *       appended by our own ALB — or the connection's remote address)</li>
 *   <li>Uses Redis INCR with TTL for per-minute counters</li>
 *   <li>Returns HTTP 429 Too Many Requests with Retry-After header when limit exceeded</li>
 * </ul>
 *
 * <p><b>Redis key format:</b> {@code rate-limit:{userId|ip}:{minuteBucket}}</p>
 * <p><b>TTL:</b> 120 seconds (ensures cleanup even with minor clock drift)</p>
 * <p><b>Fail-open:</b> If Redis is unavailable, general requests are allowed through (documented design),
 * but expensive (LLM-triggering) operations fall back to a bounded in-memory hourly counter so a Redis
 * outage does not remove the LLM-cost guardrail.</p>
 *
 * <p><b>X-Forwarded-For handling:</b> clients can prepend arbitrary entries to X-Forwarded-For, so the
 * FIRST entry is attacker-controlled. The ALB in front of the service appends the real client IP as the
 * LAST entry, so the rightmost entry is the only one we trust.</p>
 *
 * <p><b>Excluded paths:</b> Health check endpoints ({@code /q/}) are skipped.</p>
 */
@Provider
@Priority(Priorities.AUTHENTICATION + 1)
public class RateLimitFilter implements ContainerRequestFilter {

    private static final Logger LOG = Logger.getLogger(RateLimitFilter.class);
    private static final String KEY_PREFIX = "rate-limit:";
    private static final String EXPENSIVE_KEY_PREFIX = "rate-limit-expensive:";
    private static final long TTL_SECONDS = 120;
    private static final long EXPENSIVE_TTL_SECONDS = 3700; // just over 1 hour

    /** Cap on the in-memory fallback counter map so a Redis outage cannot exhaust heap. */
    private static final int FALLBACK_MAX_ENTRIES = 10_000;

    @Inject
    RedisDataSource redisDataSource;

    @ConfigProperty(name = "quorumtrading.rate-limit.enabled", defaultValue = "true")
    boolean enabled;

    @ConfigProperty(name = "quorumtrading.rate-limit.requests-per-minute", defaultValue = "100")
    int requestsPerMinute;

    @ConfigProperty(name = "quorumtrading.rate-limit.expensive-requests-per-hour", defaultValue = "20")
    int expensiveRequestsPerHour;

    @Inject
    SecurityIdentity securityIdentity;

    @Inject
    CurrentVertxRequest currentVertxRequest;

    /**
     * In-memory fallback counters for the expensive-operation tier, used only while Redis is
     * unavailable. Keyed by identifier; reset whenever the hour bucket rolls over.
     */
    private final ConcurrentHashMap<String, AtomicLong> fallbackExpensiveCounters = new ConcurrentHashMap<>();
    private volatile long fallbackHourBucket = -1;

    @Override
    public void filter(ContainerRequestContext requestContext) {
        if (!enabled) {
            return;
        }

        String path = requestContext.getUriInfo().getPath();

        // Skip rate limiting for Quarkus internal endpoints (health, metrics, swagger, openapi)
        if (path.startsWith("/q/") || path.startsWith("q/")) {
            return;
        }

        String identifier = resolveIdentifier(requestContext);

        try {
            long minuteBucket = Instant.now().getEpochSecond() / 60;
            String key = KEY_PREFIX + identifier + ":" + minuteBucket;

            ValueCommands<String, String> commands = redisDataSource.value(String.class);
            long currentCount = commands.incr(key);

            // Set TTL on first request in this bucket
            if (currentCount == 1) {
                redisDataSource.key().expire(key, TTL_SECONDS);
            }

            if (currentCount > requestsPerMinute) {
                LOG.warnf("Rate limit exceeded for identifier '%s': %d/%d requests in current minute",
                        identifier, currentCount, requestsPerMinute);

                long secondsInBucket = Instant.now().getEpochSecond() % 60;
                long retryAfter = 60 - secondsInBucket;

                requestContext.abortWith(tooManyRequests(
                        "Rate limit exceeded. Maximum " + requestsPerMinute + " requests per minute.",
                        path, retryAfter));
                return;
            }

            // Stricter hourly limit on expensive operations (POST that trigger LLM calls)
            if (isExpensiveOperation(requestContext, path)) {
                long hourBucket = Instant.now().getEpochSecond() / 3600;
                String expKey = EXPENSIVE_KEY_PREFIX + identifier + ":" + hourBucket;

                long expCount = commands.incr(expKey);
                if (expCount == 1) {
                    redisDataSource.key().expire(expKey, EXPENSIVE_TTL_SECONDS);
                }

                if (expCount > expensiveRequestsPerHour) {
                    LOG.warnf("Expensive operation rate limit exceeded for '%s': %d/%d per hour",
                            identifier, expCount, expensiveRequestsPerHour);

                    requestContext.abortWith(tooManyRequests(
                            "Rate limit exceeded for expensive operations. Maximum "
                                    + expensiveRequestsPerHour + " per hour.",
                            path, secondsUntilNextHour()));
                }
            }
        } catch (Exception e) {
            // Fail open for the general tier: if Redis is unavailable, allow the request through.
            // The expensive (LLM-cost) tier falls back to a bounded in-memory counter instead.
            LOG.warnf(e, "Rate limit check failed (Redis unavailable?) for identifier '%s'", identifier);

            if (isExpensiveOperation(requestContext, path) && fallbackExpensiveLimitExceeded(identifier)) {
                LOG.warnf("Fallback (in-memory) expensive operation rate limit exceeded for '%s': max %d per hour",
                        identifier, expensiveRequestsPerHour);
                requestContext.abortWith(tooManyRequests(
                        "Rate limit exceeded for expensive operations. Maximum "
                                + expensiveRequestsPerHour + " per hour.",
                        path, secondsUntilNextHour()));
            }
        }
    }

    /**
     * Builds the canonical RFC 7807 429 response from {@link ProblemDetails} so the body
     * cannot drift from the rest of the API's error contract.
     */
    private Response tooManyRequests(String detail, String instance, long retryAfterSeconds) {
        return Response.status(429)
                .header("Retry-After", retryAfterSeconds)
                .entity(ProblemDetails.rateLimitExceeded(detail, instance))
                .type("application/json")
                .build();
    }

    private long secondsUntilNextHour() {
        return 3600 - (Instant.now().getEpochSecond() % 3600);
    }

    /**
     * Bounded in-memory hourly counter for expensive operations, used only while Redis is down.
     * Counters reset when the hour bucket rolls over. When the map is full, new identifiers
     * fail open (bounded memory takes precedence; existing counters keep enforcing).
     */
    private boolean fallbackExpensiveLimitExceeded(String identifier) {
        long hourBucket = Instant.now().getEpochSecond() / 3600;
        if (hourBucket != fallbackHourBucket) {
            synchronized (fallbackExpensiveCounters) {
                if (hourBucket != fallbackHourBucket) {
                    fallbackExpensiveCounters.clear();
                    fallbackHourBucket = hourBucket;
                }
            }
        }
        AtomicLong counter = fallbackExpensiveCounters.get(identifier);
        if (counter == null) {
            if (fallbackExpensiveCounters.size() >= FALLBACK_MAX_ENTRIES) {
                LOG.warnf("Fallback rate limit map full (%d entries), failing open for '%s'",
                        FALLBACK_MAX_ENTRIES, identifier);
                return false;
            }
            counter = fallbackExpensiveCounters.computeIfAbsent(identifier, k -> new AtomicLong());
        }
        return counter.incrementAndGet() > expensiveRequestsPerHour;
    }

    /**
     * Resolves the rate limit identifier for the current request.
     * Prefers authenticated user email, falls back to IP address.
     */
    private String resolveIdentifier(ContainerRequestContext requestContext) {
        // Try SecurityIdentity (populated by OIDC or DevSessionAuthMechanism)
        try {
            if (securityIdentity != null && !securityIdentity.isAnonymous()) {
                String email = securityIdentity.getPrincipal().getName();
                if (email != null && !email.isBlank()) {
                    return "user:" + email;
                }
            }
        } catch (Exception e) {
            LOG.debugf("Could not extract identity: %s", e.getMessage());
        }

        // Fall back to IP address
        return "ip:" + extractClientIp(requestContext);
    }

    /**
     * Identifies POST operations that trigger expensive LLM calls or resource creation.
     */
    private boolean isExpensiveOperation(ContainerRequestContext requestContext, String path) {
        if (!"POST".equalsIgnoreCase(requestContext.getMethod())) {
            return false;
        }
        return path.endsWith("/simulations")
                || path.endsWith("/benchmarks")
                || path.contains("/market-research") && path.endsWith("/execute")
                || path.endsWith("/market-research")
                || path.endsWith("/strategies")
                || path.endsWith("/find-ticker")
                || path.endsWith("/payment/checkout");
    }

    /**
     * Extracts the client IP used as the unauthenticated rate-limit identifier.
     *
     * <p>X-Forwarded-For entries are client-controlled except for the LAST one, which is appended
     * by our own ALB from the actual TCP connection. Taking the first entry would let an attacker
     * mint a fresh rate-limit bucket per request by rotating a fake header value, so we take the
     * rightmost entry. When no X-Forwarded-For header is present (direct connection, e.g. dev),
     * we use the connection's remote address.</p>
     */
    private String extractClientIp(ContainerRequestContext requestContext) {
        String forwarded = requestContext.getHeaderString("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            // Rightmost entry = appended by our own ALB = trustworthy. Everything to the left
            // is attacker-controllable.
            String[] entries = forwarded.split(",");
            String rightmost = entries[entries.length - 1].trim();
            if (!rightmost.isBlank()) {
                return rightmost;
            }
        }

        // No X-Forwarded-For: use the actual remote address of the connection.
        String remote = remoteAddress();
        if (remote != null) {
            return remote;
        }

        LOG.warn("Could not determine client IP for rate limiting (no X-Forwarded-For, no remote address); "
                + "using shared 'unknown' bucket");
        return "unknown";
    }

    /** Remote address of the underlying HTTP connection, or {@code null} when unavailable. */
    private String remoteAddress() {
        try {
            if (currentVertxRequest != null) {
                RoutingContext ctx = currentVertxRequest.getCurrent();
                if (ctx != null && ctx.request().remoteAddress() != null) {
                    String host = ctx.request().remoteAddress().host();
                    if (host != null && !host.isBlank()) {
                        return host;
                    }
                }
            }
        } catch (Exception e) {
            LOG.debugf("Could not determine remote address: %s", e.getMessage());
        }
        return null;
    }
}
