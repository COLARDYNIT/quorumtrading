package com.quorumtrading.model.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MarketDataFetchExceptionTest {

    @Test
    void rateLimited_shouldBeRetryable() {
        MarketDataFetchException ex = MarketDataFetchException.rateLimited("alpaca");

        assertThat(ex.isRetryable()).isTrue();
        assertThat(ex.getHttpStatus()).isEqualTo(429);
        assertThat(ex.getProviderName()).isEqualTo("alpaca");
        assertThat(ex.getMessage()).contains("Rate limited");
    }

    @Test
    void authFailed_shouldNotBeRetryable() {
        MarketDataFetchException ex = MarketDataFetchException.authFailed("alpaca", 401);

        assertThat(ex.isRetryable()).isFalse();
        assertThat(ex.getHttpStatus()).isEqualTo(401);
        assertThat(ex.getProviderName()).isEqualTo("alpaca");
        assertThat(ex.getMessage()).contains("Authentication failed");
    }

    @Test
    void authFailed403_shouldNotBeRetryable() {
        MarketDataFetchException ex = MarketDataFetchException.authFailed("alpaca", 403);

        assertThat(ex.isRetryable()).isFalse();
        assertThat(ex.getHttpStatus()).isEqualTo(403);
    }

    @Test
    void connectionFailed_shouldBeRetryable() {
        RuntimeException cause = new RuntimeException("Connection refused");
        MarketDataFetchException ex = MarketDataFetchException.connectionFailed("alpaca", cause);

        assertThat(ex.isRetryable()).isTrue();
        assertThat(ex.getHttpStatus()).isEqualTo(0);
        assertThat(ex.getProviderName()).isEqualTo("alpaca");
        assertThat(ex.getCause()).isSameAs(cause);
    }

    @Test
    void fromHttpStatus_429_shouldBeRetryable() {
        MarketDataFetchException ex = MarketDataFetchException.fromHttpStatus("alpaca", 429, "Too Many Requests");

        assertThat(ex.isRetryable()).isTrue();
        assertThat(ex.getHttpStatus()).isEqualTo(429);
    }

    @Test
    void fromHttpStatus_503_shouldBeRetryable() {
        MarketDataFetchException ex = MarketDataFetchException.fromHttpStatus("alpaca", 503, "Service Unavailable");

        assertThat(ex.isRetryable()).isTrue();
        assertThat(ex.getHttpStatus()).isEqualTo(503);
    }

    @Test
    void fromHttpStatus_500_shouldNotBeRetryable() {
        MarketDataFetchException ex = MarketDataFetchException.fromHttpStatus("alpaca", 500, "Internal Server Error");

        assertThat(ex.isRetryable()).isFalse();
        assertThat(ex.getHttpStatus()).isEqualTo(500);
    }

    @Test
    void fromHttpStatus_400_shouldNotBeRetryable() {
        MarketDataFetchException ex = MarketDataFetchException.fromHttpStatus("alpaca", 400, "Bad Request");

        assertThat(ex.isRetryable()).isFalse();
    }
}
