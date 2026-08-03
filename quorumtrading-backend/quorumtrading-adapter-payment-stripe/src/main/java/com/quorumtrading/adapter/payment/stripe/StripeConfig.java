package com.quorumtrading.adapter.payment.stripe;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.util.Optional;

/**
 * Configuration for Stripe payment integration.
 */
@ConfigMapping(prefix = "quorumtrading.stripe")
public interface StripeConfig {

    /**
     * Stripe secret API key.
     * Required for server-side operations.
     */
    Optional<String> secretKey();

    /**
     * Stripe webhook signing secret.
     * Required for verifying webhook signatures.
     */
    Optional<String> webhookSecret();

    /**
     * Stripe publishable key.
     * Used by frontend for Stripe.js.
     */
    Optional<String> publishableKey();

    /**
     * Whether Stripe integration is enabled.
     */
    @WithDefault("false")
    boolean enabled();

    /**
     * Default currency for payments.
     */
    @WithDefault("usd")
    String currency();

    /**
     * Product name shown in Stripe checkout.
     */
    @WithDefault("QuorumTrading AI Budget")
    String productName();

    /**
     * Predefined top-up amounts in cents (for quick-buy options).
     * Comma-separated list.
     */
    @WithDefault("500,1000,2500,5000")
    String predefinedAmounts();
}
