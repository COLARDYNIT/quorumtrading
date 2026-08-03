package com.quorumtrading.adapter.broker.saxo.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response from the Saxo OpenAPI "current user" endpoint
 * ({@code GET /port/v1/users/me}).
 *
 * <p><b>Scaffold (SB1).</b> Saxo's API addresses resources by opaque
 * {@code ClientKey}/{@code UserKey} handles rather than account numbers, so the
 * adapter resolves and caches these per linked broker account before any trade.
 * That resolution + caching is wired in {@code SB2-auth-accountkey}; this DTO only
 * models the user payload.</p>
 *
 * <p><b>TODO(SB2):</b> confirm the {@code /port/v1/users/me} field names —
 * {@code ClientKey}, {@code UserKey}, {@code Name}, {@code DefaultAccountKey},
 * {@code Culture}/{@code Language}; verify casing and which key the trade/balance
 * calls require.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class SaxoUserResponse {

    /** Opaque client handle. TODO(SB2): confirm {@code ClientKey}. */
    @JsonProperty("ClientKey")
    private String clientKey;

    /** Opaque user handle. TODO(SB2): confirm {@code UserKey}. */
    @JsonProperty("UserKey")
    private String userKey;

    /** The account the trade calls default to. TODO(SB2): confirm {@code DefaultAccountKey}. */
    @JsonProperty("DefaultAccountKey")
    private String defaultAccountKey;

    /** Display name. TODO(SB2): confirm {@code Name}. */
    @JsonProperty("Name")
    private String name;

    public String getClientKey() {
        return clientKey;
    }

    public void setClientKey(String clientKey) {
        this.clientKey = clientKey;
    }

    public String getUserKey() {
        return userKey;
    }

    public void setUserKey(String userKey) {
        this.userKey = userKey;
    }

    public String getDefaultAccountKey() {
        return defaultAccountKey;
    }

    public void setDefaultAccountKey(String defaultAccountKey) {
        this.defaultAccountKey = defaultAccountKey;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
