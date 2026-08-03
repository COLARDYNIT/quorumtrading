package com.quorumtrading.application.port.out;

import com.quorumtrading.model.valueobject.BrokerAccountId;

/**
 * Out-port that supplies a currently-valid bearer access token for a broker account.
 *
 * <p>This is the single seam every Saxo adapter (broker + market-data) calls before issuing an
 * authenticated request. It hides the entire OAuth lifecycle: the implementation returns a cached
 * access token when it is still fresh, and transparently performs a lease-protected refresh (using
 * {@link SaxoOAuthClientPort} + {@link BrokerTokenLeasePort}) when the token is near expiry,
 * persisting the rotated refresh token before handing back the new access token.</p>
 *
 * <p>Decoupling adapters from the refresh machinery behind this port is what breaks the
 * saxo-oauth ↔ saxo-broker-adapter cycle: adapters depend only on this interface.</p>
 */
public interface BrokerTokenProviderPort {

    /**
     * Returns a valid bearer access token for the given broker account, refreshing it first if it
     * is expired or about to expire.
     *
     * <p>For non-OAuth brokers (e.g. Alpaca API-key accounts) implementations may reject the call;
     * this port is intended for OAuth-backed accounts.</p>
     *
     * @param brokerAccountId the account whose access token is needed (non-null)
     * @return a non-blank bearer access token safe to place on an {@code Authorization: Bearer} header
     * @throws com.quorumtrading.application.exception.RefreshChainBrokenException
     *         if the refresh token chain is broken and the account must be re-authorized
     * @throws IllegalArgumentException if the account does not exist or is not OAuth-backed
     */
    String getValidAccessToken(BrokerAccountId brokerAccountId);
}
