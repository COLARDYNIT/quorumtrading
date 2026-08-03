package com.quorumtrading.application.usecase.saxo;

import com.quorumtrading.model.enums.BrokerType;
import com.quorumtrading.model.enums.SaxoEnvironment;
import com.quorumtrading.model.valueobject.UserId;

import java.util.Objects;

/**
 * The per-request onboarding context stashed under the single-use OAuth {@code state} when Saxo
 * onboarding begins, and restored on the callback.
 *
 * <p>{@link StartSaxoOnboardingUseCase} mints this and persists it via
 * {@link com.quorumtrading.application.port.out.OAuthStateStorePort}; on callback,
 * {@link CompleteSaxoOnboardingUseCase} consumes the state to recover who initiated onboarding,
 * which Saxo environment / broker family the resulting account targets, the redirect URI to send
 * to the token endpoint, and whether the user acknowledged live trading (required for
 * {@code SAXO_LIVE}).</p>
 *
 * <p>Domain value object: carries domain VOs/enums, no primitives for identity, no JPA/JSON
 * annotations.</p>
 *
 * @param userId       the user who initiated onboarding (non-null)
 * @param brokerType   the Saxo broker type the new account targets ({@code SAXO_SIM}/{@code SAXO_LIVE})
 * @param environment  the Saxo environment the tokens target (non-null)
 * @param redirectUri  the redirect URI used in the authorization request; replayed to the token
 *                     endpoint on code exchange (non-blank)
 * @param confirmLive  whether the user explicitly acknowledged live trading (must be {@code true}
 *                     for a {@code SAXO_LIVE} target; ignored for {@code SAXO_SIM})
 */
public record OnboardingState(
        UserId userId,
        BrokerType brokerType,
        SaxoEnvironment environment,
        String redirectUri,
        boolean confirmLive
) {

    public OnboardingState {
        Objects.requireNonNull(userId, "userId cannot be null");
        Objects.requireNonNull(brokerType, "brokerType cannot be null");
        if (!brokerType.isSaxo()) {
            throw new IllegalArgumentException("brokerType must be a Saxo broker type, was: " + brokerType);
        }
        Objects.requireNonNull(environment, "environment cannot be null");
        if (redirectUri == null || redirectUri.isBlank()) {
            throw new IllegalArgumentException("redirectUri cannot be null or blank");
        }
    }
}
