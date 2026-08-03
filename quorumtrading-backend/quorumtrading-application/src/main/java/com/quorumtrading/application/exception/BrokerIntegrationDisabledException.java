package com.quorumtrading.application.exception;

/**
 * Thrown when an operation routes to a broker whose integration is switched off on this
 * instance (e.g. Saxo with {@code quorumtrading.broker.saxo.enabled=false}).
 *
 * <p>Disabling a broker integration must stop <em>everything</em> — including simulations and
 * investment plans linked to accounts that were created while the integration was on. Without
 * this guard, flipping the flag off would only hide the broker from new onboarding while
 * existing accounts kept trading.</p>
 *
 * <p>Extends {@link IllegalStateException} so call sites that already treat broker
 * misconfiguration as an illegal state keep working unchanged.</p>
 */
public class BrokerIntegrationDisabledException extends IllegalStateException {

    public BrokerIntegrationDisabledException(String message) {
        super(message);
    }
}
