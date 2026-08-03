package com.quorumtrading.adapter.rest.filter;

import com.quorumtrading.adapter.rest.exception.ProblemDetails;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.keys.KeyCommands;
import io.quarkus.redis.datasource.value.ValueCommands;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.security.Principal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link RateLimitFilter}.
 * Tests the filter logic in isolation without a running Quarkus application.
 */
class RateLimitFilterTest {

    private RateLimitFilter filter;
    private RedisDataSource redisDataSource;
    private ValueCommands<String, String> valueCommands;
    private KeyCommands<String> keyCommands;
    private ContainerRequestContext requestContext;
    private UriInfo uriInfo;
    private SecurityIdentity securityIdentity;

    @BeforeEach
    void setUp() throws Exception {
        filter = new RateLimitFilter();

        // Set up Redis mocks
        redisDataSource = mock(RedisDataSource.class);
        valueCommands = mock(ValueCommands.class);
        keyCommands = mock(KeyCommands.class);
        when(redisDataSource.value(String.class)).thenReturn(valueCommands);
        when(redisDataSource.key()).thenReturn(keyCommands);

        // Set up request context mock
        requestContext = mock(ContainerRequestContext.class);
        uriInfo = mock(UriInfo.class);
        when(requestContext.getUriInfo()).thenReturn(uriInfo);
        when(requestContext.getHeaderString("X-Forwarded-For")).thenReturn(null);

        // Set up anonymous SecurityIdentity by default
        securityIdentity = mock(SecurityIdentity.class);
        when(securityIdentity.isAnonymous()).thenReturn(true);

        // Inject fields via reflection
        setField(filter, "redisDataSource", redisDataSource);
        setField(filter, "enabled", true);
        setField(filter, "requestsPerMinute", 100);
        setField(filter, "expensiveRequestsPerHour", 20);
        setField(filter, "securityIdentity", securityIdentity);
    }

    @Test
    void shouldSkipWhenDisabled() {
        setField(filter, "enabled", false);

        filter.filter(requestContext);

        // Should not interact with Redis at all
        verifyNoInteractions(redisDataSource);
        verify(requestContext, never()).abortWith(any());
    }

    @Test
    void shouldSkipHealthCheckEndpoints() {
        when(uriInfo.getPath()).thenReturn("/q/health");

        filter.filter(requestContext);

        verifyNoInteractions(redisDataSource);
        verify(requestContext, never()).abortWith(any());
    }

    @Test
    void shouldSkipSwaggerEndpoints() {
        when(uriInfo.getPath()).thenReturn("/q/swagger-ui");

        filter.filter(requestContext);

        verifyNoInteractions(redisDataSource);
        verify(requestContext, never()).abortWith(any());
    }

    @Test
    void shouldAllowRequestWithinLimit() {
        when(uriInfo.getPath()).thenReturn("/api/v1/simulations");
        when(valueCommands.incr(anyString())).thenReturn(1L);

        filter.filter(requestContext);

        verify(valueCommands).incr(anyString());
        verify(requestContext, never()).abortWith(any());
    }

    @Test
    void shouldSetTtlOnFirstRequestInBucket() {
        when(uriInfo.getPath()).thenReturn("/api/v1/simulations");
        when(valueCommands.incr(anyString())).thenReturn(1L);

        filter.filter(requestContext);

        // TTL should be set when count is 1 (first request in bucket)
        verify(keyCommands).expire(anyString(), eq(120L));
    }

    @Test
    void shouldNotSetTtlOnSubsequentRequests() {
        when(uriInfo.getPath()).thenReturn("/api/v1/simulations");
        when(valueCommands.incr(anyString())).thenReturn(50L);

        filter.filter(requestContext);

        // TTL should NOT be set when count > 1
        verify(keyCommands, never()).expire(anyString(), anyLong());
    }

    @Test
    void shouldReturn429WhenLimitExceeded() {
        when(uriInfo.getPath()).thenReturn("/api/v1/simulations");
        when(valueCommands.incr(anyString())).thenReturn(101L);

        filter.filter(requestContext);

        ArgumentCaptor<Response> responseCaptor = ArgumentCaptor.forClass(Response.class);
        verify(requestContext).abortWith(responseCaptor.capture());

        Response response = responseCaptor.getValue();
        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeaderString("Retry-After")).isNotNull();
        assertThat(Integer.parseInt(response.getHeaderString("Retry-After"))).isBetween(0, 60);
    }

    @Test
    void shouldAllowRequestAtExactLimit() {
        when(uriInfo.getPath()).thenReturn("/api/v1/simulations");
        when(valueCommands.incr(anyString())).thenReturn(100L); // Exactly at limit

        filter.filter(requestContext);

        // Request at exactly the limit should be allowed (only > limit is blocked)
        verify(requestContext, never()).abortWith(any());
    }

    @Test
    void shouldUseAuthenticatedUserEmailAsIdentifier() {
        when(uriInfo.getPath()).thenReturn("/api/v1/simulations");
        when(securityIdentity.isAnonymous()).thenReturn(false);
        Principal principal = mock(Principal.class);
        when(principal.getName()).thenReturn("user@example.com");
        when(securityIdentity.getPrincipal()).thenReturn(principal);
        when(valueCommands.incr(anyString())).thenReturn(1L);

        filter.filter(requestContext);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueCommands).incr(keyCaptor.capture());

        assertThat(keyCaptor.getValue()).startsWith("rate-limit:user:user@example.com:");
    }

    @Test
    void shouldUseIpAddressWhenNoAuthAvailable() {
        when(uriInfo.getPath()).thenReturn("/api/v1/simulations");
        when(requestContext.getHeaderString("X-Forwarded-For")).thenReturn("192.168.1.100");
        when(valueCommands.incr(anyString())).thenReturn(1L);

        filter.filter(requestContext);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueCommands).incr(keyCaptor.capture());

        assertThat(keyCaptor.getValue()).startsWith("rate-limit:ip:192.168.1.100:");
    }

    @Test
    void shouldUseRightmostIpFromXForwardedForHeader() {
        // Left entries are client-controlled (spoofable); the RIGHTMOST is appended by our own ALB.
        when(uriInfo.getPath()).thenReturn("/api/v1/simulations");
        when(requestContext.getHeaderString("X-Forwarded-For")).thenReturn("10.0.0.1, 10.0.0.2, 10.0.0.3");
        when(valueCommands.incr(anyString())).thenReturn(1L);

        filter.filter(requestContext);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueCommands).incr(keyCaptor.capture());

        assertThat(keyCaptor.getValue()).startsWith("rate-limit:ip:10.0.0.3:");
    }

    @Test
    void shouldNotGiveFreshBucketWhenClientPrependsFakeXffEntries() {
        // Attacker rotating fake leading XFF entries must still land in the same bucket
        when(uriInfo.getPath()).thenReturn("/api/v1/simulations");
        when(valueCommands.incr(anyString())).thenReturn(1L);

        when(requestContext.getHeaderString("X-Forwarded-For")).thenReturn("1.2.3.4, 203.0.113.9");
        filter.filter(requestContext);
        when(requestContext.getHeaderString("X-Forwarded-For")).thenReturn("5.6.7.8, 203.0.113.9");
        filter.filter(requestContext);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueCommands, times(2)).incr(keyCaptor.capture());

        assertThat(keyCaptor.getAllValues().get(0)).isEqualTo(keyCaptor.getAllValues().get(1));
        assertThat(keyCaptor.getAllValues().get(0)).startsWith("rate-limit:ip:203.0.113.9:");
    }

    @Test
    void shouldFallBackToUnknownWhenNoIpSourceAvailable() {
        when(uriInfo.getPath()).thenReturn("/api/v1/simulations");
        when(valueCommands.incr(anyString())).thenReturn(1L);

        filter.filter(requestContext);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueCommands).incr(keyCaptor.capture());

        assertThat(keyCaptor.getValue()).startsWith("rate-limit:ip:unknown:");
    }

    @Test
    void shouldReturnProblemDetailsBodyOn429() {
        when(uriInfo.getPath()).thenReturn("/api/v1/simulations");
        when(valueCommands.incr(anyString())).thenReturn(101L);

        filter.filter(requestContext);

        ArgumentCaptor<Response> responseCaptor = ArgumentCaptor.forClass(Response.class);
        verify(requestContext).abortWith(responseCaptor.capture());

        Response response = responseCaptor.getValue();
        assertThat(response.getEntity()).isInstanceOf(ProblemDetails.class);
        ProblemDetails problem = (ProblemDetails) response.getEntity();
        assertThat(problem.getStatus()).isEqualTo(429);
        assertThat(problem.getTitle()).isEqualTo("Rate Limit Exceeded");
        assertThat(problem.getDetail()).contains("100 requests per minute");
        assertThat(problem.getInstance()).isEqualTo("/api/v1/simulations");
    }

    @Test
    void shouldFailOpenWhenRedisUnavailable() {
        when(uriInfo.getPath()).thenReturn("/api/v1/simulations");
        when(valueCommands.incr(anyString())).thenThrow(new RuntimeException("Redis connection refused"));

        filter.filter(requestContext);

        // Should not abort - fail open
        verify(requestContext, never()).abortWith(any());
    }

    @Test
    void shouldEnforceInMemoryFallbackLimitForExpensiveOpsWhenRedisDown() {
        when(uriInfo.getPath()).thenReturn("/api/v1/simulations");
        when(requestContext.getMethod()).thenReturn("POST");
        when(requestContext.getHeaderString("X-Forwarded-For")).thenReturn("203.0.113.9");
        when(valueCommands.incr(anyString())).thenThrow(new RuntimeException("Redis connection refused"));

        // 20 requests allowed (expensiveRequestsPerHour = 20)
        for (int i = 0; i < 20; i++) {
            filter.filter(requestContext);
        }
        verify(requestContext, never()).abortWith(any());

        // 21st request must be blocked by the in-memory fallback limiter
        filter.filter(requestContext);

        ArgumentCaptor<Response> responseCaptor = ArgumentCaptor.forClass(Response.class);
        verify(requestContext).abortWith(responseCaptor.capture());
        Response response = responseCaptor.getValue();
        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getEntity()).isInstanceOf(ProblemDetails.class);
    }

    @Test
    void shouldKeepSeparateFallbackCountersPerIdentifierWhenRedisDown() {
        when(uriInfo.getPath()).thenReturn("/api/v1/simulations");
        when(requestContext.getMethod()).thenReturn("POST");
        when(valueCommands.incr(anyString())).thenThrow(new RuntimeException("Redis connection refused"));

        when(requestContext.getHeaderString("X-Forwarded-For")).thenReturn("203.0.113.9");
        for (int i = 0; i < 20; i++) {
            filter.filter(requestContext);
        }

        // A different (real, ALB-appended) client IP still has its own budget
        when(requestContext.getHeaderString("X-Forwarded-For")).thenReturn("198.51.100.7");
        filter.filter(requestContext);

        verify(requestContext, never()).abortWith(any());
    }

    /**
     * Sets a field value via reflection for testing purposes.
     */
    private void setField(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set field " + fieldName, e);
        }
    }
}
