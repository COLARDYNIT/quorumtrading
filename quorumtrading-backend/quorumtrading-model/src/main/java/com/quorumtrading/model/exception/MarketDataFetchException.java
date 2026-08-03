package com.quorumtrading.model.exception;

/**
 * Domain exception thrown when a market data provider fails to fetch data.
 * Carries structured error info to allow callers to distinguish retryable
 * errors (rate limits, transient failures) from permanent ones (auth failures).
 */
public class MarketDataFetchException extends RuntimeException {

    private final int httpStatus;
    private final String providerName;
    private final boolean retryable;

    private MarketDataFetchException(String message, Throwable cause, int httpStatus, String providerName, boolean retryable) {
        super(message, cause);
        this.httpStatus = httpStatus;
        this.providerName = providerName;
        this.retryable = retryable;
    }

    private MarketDataFetchException(String message, int httpStatus, String providerName, boolean retryable) {
        super(message);
        this.httpStatus = httpStatus;
        this.providerName = providerName;
        this.retryable = retryable;
    }

    public static MarketDataFetchException rateLimited(String providerName) {
        return new MarketDataFetchException(
            "Rate limited by " + providerName + " (HTTP 429)",
            429, providerName, true
        );
    }

    public static MarketDataFetchException authFailed(String providerName, int httpStatus) {
        return new MarketDataFetchException(
            "Authentication failed for " + providerName + " (HTTP " + httpStatus + ")",
            httpStatus, providerName, false
        );
    }

    public static MarketDataFetchException connectionFailed(String providerName, Throwable cause) {
        return new MarketDataFetchException(
            "Connection failed for " + providerName + ": " + cause.getMessage(),
            cause, 0, providerName, true
        );
    }

    public static MarketDataFetchException fromHttpStatus(String providerName, int httpStatus, String message) {
        boolean retryable = httpStatus == 429 || httpStatus == 503 || httpStatus == 502 || httpStatus == 504;
        return new MarketDataFetchException(
            providerName + " returned HTTP " + httpStatus + ": " + message,
            httpStatus, providerName, retryable
        );
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public String getProviderName() {
        return providerName;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
