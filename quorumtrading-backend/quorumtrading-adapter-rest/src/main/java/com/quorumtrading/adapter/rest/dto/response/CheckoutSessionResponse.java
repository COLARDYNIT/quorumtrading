package com.quorumtrading.adapter.rest.dto.response;

/**
 * Response DTO for checkout session creation.
 */
public record CheckoutSessionResponse(
        String sessionId,
        String checkoutUrl,
        String clientSecret,
        long amountCents,
        String currency
) {}
