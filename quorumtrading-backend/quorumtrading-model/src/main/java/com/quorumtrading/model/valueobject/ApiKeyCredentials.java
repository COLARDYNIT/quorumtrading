package com.quorumtrading.model.valueobject;

/**
 * Static API key / secret credential pair, used by Alpaca paper and live brokers.
 *
 * <p>This is the original {@code BrokerCredentials} shape, preserved as a concrete
 * {@link BrokerCredentials} implementation. Both fields are mandatory and non-blank.</p>
 *
 * <p>Domain value object: no JPA/JSON/Jackson annotations. {@link #toString()} is
 * redacted so secrets never leak into logs.</p>
 *
 * @param apiKey    the broker API key (non-blank)
 * @param apiSecret the broker API secret (non-blank)
 */
public record ApiKeyCredentials(String apiKey, String apiSecret) implements BrokerCredentials {

    public ApiKeyCredentials {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("apiKey cannot be null or blank");
        }
        if (apiSecret == null || apiSecret.isBlank()) {
            throw new IllegalArgumentException("apiSecret cannot be null or blank");
        }
    }

    @Override
    public String toString() {
        return "ApiKeyCredentials[apiKey=***, apiSecret=***]";
    }
}
