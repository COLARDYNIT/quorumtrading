package com.quorumtrading.application.port.out;

import com.quorumtrading.model.enums.SaxoEnvironment;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Transport value object carrying the token material returned by a Saxo OAuth2 token
 * exchange or refresh.
 *
 * <p>Saxo issues a short-lived access token (~20 min) together with a <strong>rotating</strong>
 * refresh token: every {@code grant_type=refresh_token} call yields a brand-new refresh token and
 * invalidates the previous one. This record captures the freshly issued pair plus the absolute
 * expiry of the access token, so callers can persist the rotation atomically (see
 * {@link com.quorumtrading.model.valueobject.SaxoOAuthCredentials#withRotatedTokens}).</p>
 *
 * <p>This is a boundary/transport type, not a domain entity: it lives in the application
 * out-port layer because it is the shape returned by {@link SaxoOAuthClientPort}. It carries no
 * JPA/JSON annotations. {@link #toString()} is redacted so no secret material reaches logs.</p>
 *
 * @param accessToken  the freshly issued short-lived access token (non-blank)
 * @param refreshToken the rotated refresh token to persist for the next refresh (non-blank)
 * @param expiry       the absolute expiry instant of {@code accessToken} (non-null)
 * @param environment  the Saxo environment (SIM/LIVE) these tokens are valid against (non-null)
 */
public record OAuthTokens(
        String accessToken,
        String refreshToken,
        Instant expiry,
        SaxoEnvironment environment
) {

    public OAuthTokens {
        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalArgumentException("accessToken cannot be null or blank");
        }
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new IllegalArgumentException("refreshToken cannot be null or blank");
        }
        Objects.requireNonNull(expiry, "expiry cannot be null");
        Objects.requireNonNull(environment, "environment cannot be null");
    }

    /**
     * Convenience factory that derives the absolute access-token expiry from a relative
     * lifetime expressed in seconds (as Saxo returns in {@code expires_in}).
     *
     * @param accessToken    the freshly issued access token (non-blank)
     * @param refreshToken   the rotated refresh token (non-blank)
     * @param issuedAt       the instant the token was issued (non-null)
     * @param expiresInSeconds the access-token lifetime in seconds (positive)
     * @param environment    the Saxo environment (non-null)
     * @return a new {@link OAuthTokens} with {@code expiry = issuedAt + expiresInSeconds}
     */
    public static OAuthTokens of(
            String accessToken,
            String refreshToken,
            Instant issuedAt,
            long expiresInSeconds,
            SaxoEnvironment environment) {
        Objects.requireNonNull(issuedAt, "issuedAt cannot be null");
        if (expiresInSeconds <= 0) {
            throw new IllegalArgumentException("expiresInSeconds must be positive");
        }
        return new OAuthTokens(
                accessToken,
                refreshToken,
                issuedAt.plusSeconds(expiresInSeconds),
                environment);
    }

    /**
     * Whether the access token is expired (or about to expire within {@code skew}) relative to
     * {@code now}. Applying a safety {@code skew} lets callers refresh proactively before the
     * token actually lapses.
     *
     * @param now  the reference instant (non-null)
     * @param skew a non-negative safety margin subtracted from the expiry (non-null)
     * @return {@code true} if the token should be considered expired
     */
    public boolean isExpired(Instant now, Duration skew) {
        Objects.requireNonNull(now, "now cannot be null");
        Objects.requireNonNull(skew, "skew cannot be null");
        if (skew.isNegative()) {
            throw new IllegalArgumentException("skew cannot be negative");
        }
        return !now.isBefore(expiry.minus(skew));
    }

    @Override
    public String toString() {
        return "OAuthTokens[accessToken=***, refreshToken=***, expiry=" + expiry
                + ", environment=" + environment + "]";
    }
}
