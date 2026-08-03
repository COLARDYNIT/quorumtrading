package com.quorumtrading.application.usecase.saxo;

import com.quorumtrading.model.enums.BrokerType;
import com.quorumtrading.model.enums.SaxoEnvironment;
import com.quorumtrading.model.valueobject.UserId;

import java.util.Objects;

/**
 * Input-port for beginning Saxo OAuth onboarding.
 *
 * <p>Generates the Saxo consent URL and a single-use {@code state}, stashes the originating
 * {@link OnboardingState} under that state, and returns an {@link OnboardingStart} the REST edge
 * redirects the user to. No broker account exists yet at this stage — it is created on callback by
 * {@link CompleteSaxoOnboardingUseCase} once the user grants consent.</p>
 *
 * <p>Implementation (Wave 3 {@code OA1-onboarding-service}) is a POJO wired in the bootstrap
 * {@code ApplicationConfig}, not a CDI-scoped bean.</p>
 */
public interface StartSaxoOnboardingUseCase {

    /**
     * Begins onboarding: mints the authorization URL + single-use state and persists the request
     * context for the callback.
     *
     * @param command the onboarding request parameters (non-null)
     * @return the authorization URL to redirect to plus the single-use state
     */
    OnboardingStart start(Command command);

    /**
     * Command to begin Saxo OAuth onboarding.
     *
     * @param userId      the user initiating onboarding (non-null)
     * @param brokerType  the Saxo broker type to onboard ({@code SAXO_SIM}/{@code SAXO_LIVE})
     * @param environment the Saxo environment to target (non-null)
     * @param redirectUri the redirect URI Saxo calls back after consent; replayed on token exchange
     *                    (non-blank)
     * @param confirmLive whether the user acknowledged live trading (required {@code true} for
     *                    {@code SAXO_LIVE}; ignored for {@code SAXO_SIM})
     */
    record Command(
            UserId userId,
            BrokerType brokerType,
            SaxoEnvironment environment,
            String redirectUri,
            boolean confirmLive
    ) {
        public Command {
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
}
