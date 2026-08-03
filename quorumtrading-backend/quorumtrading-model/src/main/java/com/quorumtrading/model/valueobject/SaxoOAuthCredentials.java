package com.quorumtrading.model.valueobject;

import com.quorumtrading.model.enums.SaxoEnvironment;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * OAuth2 credential material for a Saxo broker account.
 *
 * <p>Saxo authenticates with an app key/secret pair plus a <strong>rotating</strong>
 * refresh token. Each refresh call returns a new short-lived access token (~20 min) and
 * a brand-new refresh token; the old refresh token is invalidated. Token rotation is
 * captured by {@link #withRotatedTokens}, which yields a new immutable value reflecting
 * the latest tokens while preserving the app credentials and environment.</p>
 *
 * <p>{@code appKey}, {@code appSecret}, {@code refreshToken} and {@code environment} are
 * mandatory. {@code accessToken} and {@code accessTokenExpiry} are nullable: a freshly
 * onboarded credential set may hold only a refresh token until the first refresh runs.</p>
 *
 * <p>Domain value object: no JPA/JSON/Jackson annotations. {@link #toString()} is
 * redacted so no secret material reaches logs.</p>
 *
 * @param appKey            the Saxo OAuth application key (non-blank)
 * @param appSecret         the Saxo OAuth application secret (non-blank)
 * @param refreshToken      the current rotating refresh token (non-blank)
 * @param accessToken       the current short-lived access token, or {@code null} if not yet obtained
 * @param accessTokenExpiry the absolute expiry of {@code accessToken}, or {@code null} if unknown
 * @param environment       the Saxo environment (SIM/LIVE) these credentials target
 */
public record SaxoOAuthCredentials(
        String appKey,
        String appSecret,
        String refreshToken,
        String accessToken,
        Instant accessTokenExpiry,
        SaxoEnvironment environment
) implements BrokerCredentials {

    public SaxoOAuthCredentials {
        if (appKey == null || appKey.isBlank()) {
            throw new IllegalArgumentException("appKey cannot be null or blank");
        }
        if (appSecret == null || appSecret.isBlank()) {
            throw new IllegalArgumentException("appSecret cannot be null or blank");
        }
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new IllegalArgumentException("refreshToken cannot be null or blank");
        }
        if (environment == null) {
            throw new IllegalArgumentException("environment cannot be null");
        }
    }

    /**
     * Returns a copy reflecting a completed token rotation: a new refresh token, a new
     * access token, and its expiry. App credentials and environment are preserved.
     *
     * @param newRefreshToken      the rotated refresh token (non-blank)
     * @param newAccessToken       the freshly issued access token (non-blank)
     * @param newAccessTokenExpiry the absolute expiry of the new access token (non-null)
     * @return a new {@link SaxoOAuthCredentials} carrying the rotated tokens
     */
    public SaxoOAuthCredentials withRotatedTokens(
            String newRefreshToken,
            String newAccessToken,
            Instant newAccessTokenExpiry) {
        if (newAccessToken == null || newAccessToken.isBlank()) {
            throw new IllegalArgumentException("newAccessToken cannot be null or blank");
        }
        Objects.requireNonNull(newAccessTokenExpiry, "newAccessTokenExpiry cannot be null");
        return new SaxoOAuthCredentials(
                appKey,
                appSecret,
                newRefreshToken,
                newAccessToken,
                newAccessTokenExpiry,
                environment
        );
    }

    /**
     * Whether the current access token is expired (or absent) relative to {@code now},
     * applying a safety {@code skew} so a token about to expire is treated as already
     * expired and refreshed proactively.
     *
     * <p>A credential set with no access token or no recorded expiry is considered
     * expired, forcing a refresh before use.</p>
     *
     * @param now  the reference instant (non-null)
     * @param skew a non-negative safety margin subtracted from the expiry (non-null)
     * @return {@code true} if a refresh is required before the access token can be used
     */
    public boolean isAccessTokenExpired(Instant now, Duration skew) {
        Objects.requireNonNull(now, "now cannot be null");
        Objects.requireNonNull(skew, "skew cannot be null");
        if (skew.isNegative()) {
            throw new IllegalArgumentException("skew cannot be negative");
        }
        if (accessToken == null || accessToken.isBlank() || accessTokenExpiry == null) {
            return true;
        }
        return !now.isBefore(accessTokenExpiry.minus(skew));
    }

    @Override
    public String toString() {
        return "SaxoOAuthCredentials[appKey=***, appSecret=***, refreshToken=***, "
                + "accessToken=" + (accessToken == null ? "null" : "***")
                + ", accessTokenExpiry=" + accessTokenExpiry
                + ", environment=" + environment + "]";
    }
}
