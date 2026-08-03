package com.quorumtrading.adapter.rest.dto.response;

import com.quorumtrading.application.usecase.saxo.OnboardingStart;

/**
 * Response returned when Saxo OAuth onboarding begins.
 *
 * <p>An agent-friendly payload: the caller (a Claude Code user or the web UI) opens
 * {@link #authorizationUrl} in a browser to grant consent. Saxo then redirects to the onboarding
 * callback carrying the matching {@link #state}.</p>
 *
 * @param authorizationUrl the Saxo consent URL to open in a browser
 * @param state            the opaque single-use state bound to this onboarding request
 */
public record SaxoOnboardingStartResponse(
        String authorizationUrl,
        String state
) {
    public static SaxoOnboardingStartResponse from(OnboardingStart start) {
        return new SaxoOnboardingStartResponse(
                start.authorizationUrl().toString(),
                start.state()
        );
    }
}
