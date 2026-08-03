package com.quorumtrading.model.enums;

public enum AccountStatus {
    ACTIVE,
    INVALID_CREDENTIALS,
    DISABLED,
    /**
     * The account's OAuth refresh chain is broken and cannot be renewed unattended
     * (e.g. the rotating refresh token was invalidated). The user must re-authorize
     * the broker connection before the account can trade again. Applies to OAuth
     * brokers (Saxo SIM/LIVE); API-key brokers never enter this state.
     */
    NEEDS_REAUTH
}
