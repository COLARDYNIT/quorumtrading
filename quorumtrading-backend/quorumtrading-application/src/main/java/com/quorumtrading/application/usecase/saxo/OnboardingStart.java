package com.quorumtrading.application.usecase.saxo;

import java.net.URI;
import java.util.Objects;

/**
 * Result of beginning Saxo OAuth onboarding: where to send the user and the single-use state that
 * binds the eventual callback to this request.
 *
 * <p>{@link StartSaxoOnboardingUseCase} produces this; the REST edge issues a redirect to
 * {@link #authorizationUrl()} and the matching {@code state} is later validated on callback by
 * {@link CompleteSaxoOnboardingUseCase}.</p>
 *
 * @param authorizationUrl the Saxo consent URL to redirect the user to (non-null)
 * @param state            the opaque single-use state value embedded in the URL (non-blank)
 */
public record OnboardingStart(URI authorizationUrl, String state) {

    public OnboardingStart {
        Objects.requireNonNull(authorizationUrl, "authorizationUrl cannot be null");
        if (state == null || state.isBlank()) {
            throw new IllegalArgumentException("state cannot be null or blank");
        }
    }
}
