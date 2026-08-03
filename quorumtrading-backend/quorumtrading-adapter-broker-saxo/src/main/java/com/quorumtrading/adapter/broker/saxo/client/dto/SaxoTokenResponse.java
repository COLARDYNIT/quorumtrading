package com.quorumtrading.adapter.broker.saxo.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * OAuth2 token-endpoint response (RFC&nbsp;6749 §5.1) returned by Saxo's authorization server
 * for both the {@code authorization_code} and {@code refresh_token} grants.
 *
 * <p>Standard RFC-6749 snake-case JSON fields. Saxo rotates the refresh token on every
 * {@code refresh_token} grant, so {@link #refreshToken} carries a brand-new value that the
 * caller MUST persist (the supplied one is invalidated). Unknown Saxo-specific fields are
 * ignored so a future field addition cannot break deserialization.</p>
 *
 * <p>TODO(Saxo): confirm Saxo returns the RFC field names verbatim ({@code access_token},
 * {@code refresh_token}, {@code expires_in}, {@code token_type}) and whether
 * {@code refresh_token_expires_in} is also returned (used only for observability, not the
 * access-token lifecycle).</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class SaxoTokenResponse {

    /** The freshly issued short-lived access token (~20 min for Saxo). */
    @JsonProperty("access_token")
    private String accessToken;

    /** The rotated refresh token to persist for the next refresh. */
    @JsonProperty("refresh_token")
    private String refreshToken;

    /** Access-token lifetime in seconds (RFC {@code expires_in}). */
    @JsonProperty("expires_in")
    private Long expiresIn;

    /** Token type, expected {@code Bearer}. */
    @JsonProperty("token_type")
    private String tokenType;

    /** Optional refresh-token lifetime in seconds, for observability only. */
    @JsonProperty("refresh_token_expires_in")
    private Long refreshTokenExpiresIn;

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public Long getExpiresIn() {
        return expiresIn;
    }

    public void setExpiresIn(Long expiresIn) {
        this.expiresIn = expiresIn;
    }

    public String getTokenType() {
        return tokenType;
    }

    public void setTokenType(String tokenType) {
        this.tokenType = tokenType;
    }

    public Long getRefreshTokenExpiresIn() {
        return refreshTokenExpiresIn;
    }

    public void setRefreshTokenExpiresIn(Long refreshTokenExpiresIn) {
        this.refreshTokenExpiresIn = refreshTokenExpiresIn;
    }
}
