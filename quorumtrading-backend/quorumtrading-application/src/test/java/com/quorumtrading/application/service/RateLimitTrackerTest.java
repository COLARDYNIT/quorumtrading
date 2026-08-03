package com.quorumtrading.application.service;

import com.quorumtrading.model.marketdata.RateLimit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for RateLimitTracker using sliding window rate limiting.
 */
class RateLimitTrackerTest {

    private RateLimitTracker tracker;

    @BeforeEach
    void setUp() {
        tracker = new InMemoryRateLimitTracker();
    }

    @Test
    void shouldAllowFirstCall() {
        RateLimit limit = RateLimit.perMinute(10);

        assertFalse(tracker.wouldExceedLimit("provider1", limit),
            "First call should not exceed limit");
    }

    @Test
    void shouldAllowCallsUnderLimit() {
        RateLimit limit = RateLimit.perMinute(3);
        String provider = "provider1";

        // Record 2 calls - should not exceed limit of 3
        tracker.recordCall(provider);
        tracker.recordCall(provider);

        assertFalse(tracker.wouldExceedLimit(provider, limit),
            "Should allow call when under limit (2/3)");
        assertEquals(2, tracker.getCallsInWindow(provider, limit),
            "Should have 2 calls recorded");
    }

    @Test
    void shouldBlockCallsAtLimit() {
        RateLimit limit = RateLimit.perMinute(3);
        String provider = "provider1";

        // Record 3 calls - at limit
        tracker.recordCall(provider);
        tracker.recordCall(provider);
        tracker.recordCall(provider);

        assertTrue(tracker.wouldExceedLimit(provider, limit),
            "Should block call when at limit (3/3)");
        assertEquals(3, tracker.getCallsInWindow(provider, limit),
            "Should have 3 calls recorded");
    }

    @Test
    void shouldAllowUnlimitedCalls() {
        RateLimit unlimited = RateLimit.unlimited();
        String provider = "unlimited-provider";

        // Record many calls
        for (int i = 0; i < 1000; i++) {
            tracker.recordCall(provider);
        }

        assertFalse(tracker.wouldExceedLimit(provider, unlimited),
            "Unlimited rate limit should never be exceeded");
        assertEquals(0, tracker.getCallsInWindow(provider, unlimited),
            "Unlimited should report 0 calls in window");
    }

    @Test
    void shouldTrackMultipleProvidersIndependently() {
        RateLimit limit = RateLimit.perMinute(3);
        String provider1 = "provider1";
        String provider2 = "provider2";

        // Provider 1: record 3 calls (at limit)
        tracker.recordCall(provider1);
        tracker.recordCall(provider1);
        tracker.recordCall(provider1);

        // Provider 2: record 1 call (under limit)
        tracker.recordCall(provider2);

        assertTrue(tracker.wouldExceedLimit(provider1, limit),
            "Provider 1 should be at limit");
        assertFalse(tracker.wouldExceedLimit(provider2, limit),
            "Provider 2 should be under limit");
        assertEquals(3, tracker.getCallsInWindow(provider1, limit));
        assertEquals(1, tracker.getCallsInWindow(provider2, limit));
    }

    @Test
    void shouldRespectDifferentTimePeriods() {
        RateLimit perSecond = RateLimit.perSecond(5);
        RateLimit perMinute = RateLimit.perMinute(10);
        RateLimit perHour = RateLimit.perHour(100);
        RateLimit perDay = RateLimit.perDay(1000);

        assertEquals(Duration.ofSeconds(1), perSecond.period());
        assertEquals(Duration.ofMinutes(1), perMinute.period());
        assertEquals(Duration.ofHours(1), perHour.period());
        assertEquals(Duration.ofDays(1), perDay.period());
    }

    @Test
    void shouldResetProvider() {
        RateLimit limit = RateLimit.perMinute(3);
        String provider = "provider1";

        // Record calls
        tracker.recordCall(provider);
        tracker.recordCall(provider);
        tracker.recordCall(provider);

        assertEquals(3, tracker.getCallsInWindow(provider, limit));

        // Reset
        tracker.reset(provider);

        assertEquals(0, tracker.getCallsInWindow(provider, limit),
            "Should have 0 calls after reset");
        assertFalse(tracker.wouldExceedLimit(provider, limit),
            "Should allow calls after reset");
    }

    @Test
    void shouldResetAllProviders() {
        RateLimit limit = RateLimit.perMinute(3);

        // Record calls for multiple providers
        tracker.recordCall("provider1");
        tracker.recordCall("provider1");
        tracker.recordCall("provider2");
        tracker.recordCall("provider3");

        // Reset all
        tracker.resetAll();

        assertEquals(0, tracker.getCallsInWindow("provider1", limit));
        assertEquals(0, tracker.getCallsInWindow("provider2", limit));
        assertEquals(0, tracker.getCallsInWindow("provider3", limit));
    }

    @Test
    void shouldHandleSlidingWindow() throws InterruptedException {
        // Use a very short window for testing (1 second)
        RateLimit limit = RateLimit.perSecond(2);
        String provider = "provider1";

        // Record 2 calls - at limit
        tracker.recordCall(provider);
        tracker.recordCall(provider);

        assertTrue(tracker.wouldExceedLimit(provider, limit),
            "Should be at limit immediately");

        // Wait for window to expire (1.1 seconds to be safe)
        Thread.sleep(1100);

        assertFalse(tracker.wouldExceedLimit(provider, limit),
            "Should allow calls after window expires");
        assertEquals(0, tracker.getCallsInWindow(provider, limit),
            "Old calls should be expired");
    }

    @Test
    void shouldHandleNewsAPIRateLimit() {
        // NewsAPI: 100 requests per day
        RateLimit newsApiLimit = RateLimit.perDay(100);
        String provider = "NewsAPI";

        assertEquals(100, newsApiLimit.maxRequests());
        assertEquals(Duration.ofDays(1), newsApiLimit.period());
        assertFalse(newsApiLimit.isUnlimited());

        // Simulate scheduler running every 5 minutes for one day
        // 24 hours * 12 runs/hour = 288 runs
        // Should hit limit after 100 runs
        for (int i = 0; i < 100; i++) {
            assertFalse(tracker.wouldExceedLimit(provider, newsApiLimit),
                "Should allow calls up to 100");
            tracker.recordCall(provider);
        }

        // 101st call should be blocked
        assertTrue(tracker.wouldExceedLimit(provider, newsApiLimit),
            "Should block call after 100 requests in 24 hours");
    }
}
