package com.quorumtrading.model.enums;

/**
 * Identifies the broker backing a {@code BrokerAccount}.
 *
 * <p>Each constant carries two capability bits:
 * <ul>
 *   <li>{@code requiresCredentials} — whether linking the account needs user-supplied
 *       credentials (API key/secret for Alpaca, OAuth app credentials for Saxo). {@code MOCK}
 *       needs none.</li>
 *   <li>{@code supportsFractionalShares} — whether the broker can settle fractional share
 *       quantities. Saxo equities/ETFs are whole-share only (cash residue carried to the next
 *       tick), so both Saxo environments report {@code false}.</li>
 * </ul>
 */
public enum BrokerType {
    MOCK(false, true),
    ALPACA_PAPER(true, true),
    ALPACA_LIVE(true, true),
    SAXO_SIM(true, false),
    SAXO_LIVE(true, false);

    private final boolean requiresCredentials;
    private final boolean supportsFractionalShares;

    BrokerType(boolean requiresCredentials, boolean supportsFractionalShares) {
        this.requiresCredentials = requiresCredentials;
        this.supportsFractionalShares = supportsFractionalShares;
    }

    public boolean requiresCredentials() {
        return requiresCredentials;
    }

    public boolean supportsFractionalShares() {
        return supportsFractionalShares;
    }

    public boolean isSaxo() {
        return this == SAXO_SIM || this == SAXO_LIVE;
    }

    public boolean isAlpaca() {
        return this == ALPACA_PAPER || this == ALPACA_LIVE;
    }

    public boolean isLive() {
        return this == ALPACA_LIVE || this == SAXO_LIVE;
    }

    /**
     * Whether this broker authenticates via OAuth2 (rotating refresh tokens) rather than
     * a static API key/secret pair. Saxo uses OAuth; Alpaca and the mock broker do not.
     */
    public boolean usesOAuth() {
        return isSaxo();
    }
}
