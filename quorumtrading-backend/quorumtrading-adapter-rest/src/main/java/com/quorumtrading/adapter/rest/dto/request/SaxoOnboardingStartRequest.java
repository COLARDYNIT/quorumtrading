package com.quorumtrading.adapter.rest.dto.request;

/**
 * Request body for beginning Saxo OAuth onboarding.
 *
 * <p>The body is optional — an empty {@code POST} defaults to the {@code SIM} environment, which is
 * the only environment the local edition links in v1. {@code environment} is accepted for
 * forward-compatibility but {@code LIVE} is rejected by the resource until live onboarding is
 * enabled.</p>
 *
 * @param environment the Saxo environment to link ({@code "SIM"} or {@code "LIVE"}); {@code null}
 *                    defaults to {@code "SIM"}
 * @param confirmLive whether the user acknowledged live trading (only meaningful for {@code LIVE})
 */
public record SaxoOnboardingStartRequest(
        String environment,
        boolean confirmLive
) {
}
