package com.quorumtrading.adapter.broker.saxo.client;

import com.quorumtrading.model.valueobject.BrokerAccountId;
import jakarta.enterprise.context.RequestScoped;

import java.util.Optional;

/**
 * Request-scoped holder for the {@link BrokerAccountId} whose Saxo OAuth bearer the current
 * request (or activated request context) should authenticate with — the linchpin of decision
 * <b>D10</b>.
 *
 * <p>A JAX-RS {@link org.eclipse.microprofile.rest.client.ext.ClientHeadersFactory} has no
 * per-request account context, so it cannot resolve a <em>per-account</em> OAuth bearer on its
 * own. D10's resolution: the {@link com.quorumtrading.adapter.broker.saxo.SaxoBrokerAdapter}
 * sets the active account on this {@code @RequestScoped} holder before issuing a Saxo REST call,
 * and {@link SaxoAuthHeadersFactory} reads the holder to resolve the bearer for that exact
 * account via {@link com.quorumtrading.application.port.out.BrokerTokenProviderPort}. No
 * per-account REST-client instances are created.</p>
 *
 * <p>Because it is {@code @RequestScoped}, each in-flight HTTP request (or each
 * scheduler-activated request context) gets its own holder instance — so two accounts driven
 * concurrently never see each other's bearer.</p>
 */
@RequestScoped
public class SaxoRequestTokenHolder {

    private BrokerAccountId accountId;

    /**
     * Pins the broker account whose bearer the Saxo REST calls in this request context must use.
     *
     * @param accountId the active account id (non-null)
     */
    public void setAccountId(BrokerAccountId accountId) {
        this.accountId = accountId;
    }

    /**
     * @return the active account id for this request context, if one was pinned.
     */
    public Optional<BrokerAccountId> getAccountId() {
        return Optional.ofNullable(accountId);
    }

    /**
     * Clears the pinned account id (defensive — the request-scoped instance is normally
     * discarded when its context terminates).
     */
    public void clear() {
        this.accountId = null;
    }
}
