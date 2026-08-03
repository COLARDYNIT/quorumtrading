package com.quorumtrading.application.usecase.saxo;

import com.quorumtrading.model.entity.BrokerAccount;

/**
 * Input-port for completing Saxo OAuth onboarding on the redirect callback.
 *
 * <p>Validates and atomically consumes the single-use {@code state} (recovering the
 * {@link OnboardingState} stashed at start), exchanges the authorization {@code code} for the
 * initial token pair via {@link com.quorumtrading.application.port.out.SaxoOAuthClientPort}, and
 * creates the OAuth-backed {@link BrokerAccount}. A forged, expired, or already-consumed state is
 * rejected.</p>
 *
 * <p>Implementation (Wave 3 {@code OA1-onboarding-service}) is a POJO wired in the bootstrap
 * {@code ApplicationConfig}, not a CDI-scoped bean.</p>
 */
public interface CompleteSaxoOnboardingUseCase {

    /**
     * Completes onboarding: validates the state, exchanges the code for tokens, and persists the
     * new broker account.
     *
     * @param command the callback parameters (non-null)
     * @return the newly created OAuth-backed broker account
     * @throws IllegalArgumentException if the state is unknown/expired/already consumed, or the code
     *                                  exchange is rejected
     */
    BrokerAccount complete(Command command);

    /**
     * Command carrying the Saxo OAuth callback parameters.
     *
     * @param state             the single-use state echoed back by Saxo; must match a stored
     *                          {@link OnboardingState} (non-blank)
     * @param authorizationCode the one-time authorization code to exchange for tokens (non-blank)
     */
    record Command(String state, String authorizationCode) {
        public Command {
            if (state == null || state.isBlank()) {
                throw new IllegalArgumentException("state cannot be null or blank");
            }
            if (authorizationCode == null || authorizationCode.isBlank()) {
                throw new IllegalArgumentException("authorizationCode cannot be null or blank");
            }
        }
    }
}
