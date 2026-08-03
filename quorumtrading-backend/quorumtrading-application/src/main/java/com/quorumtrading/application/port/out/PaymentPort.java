package com.quorumtrading.application.port.out;

import com.quorumtrading.model.valueobject.UserId;

import java.math.BigDecimal;

/**
 * Outbound port for payment processing.
 * Adapters implement this interface to integrate with payment providers (e.g., Stripe).
 */
public interface PaymentPort {

    /**
     * Result of creating a checkout session.
     *
     * @param sessionId      The provider's session/transaction ID
     * @param checkoutUrl    URL to redirect user for payment
     * @param clientSecret   Client secret for embedded checkout (Stripe Elements)
     * @param amountCents    Amount in smallest currency unit (cents)
     * @param currency       Currency code (e.g., "usd")
     */
    record CheckoutSessionResult(
            String sessionId,
            String checkoutUrl,
            String clientSecret,
            long amountCents,
            String currency
    ) {}

    /**
     * Result of processing a webhook event.
     *
     * @param eventType         The type of event (e.g., "checkout.session.completed")
     * @param providerTxId      The provider's transaction ID
     * @param userId            The user ID associated with the payment (if available)
     * @param amountCents       Amount in cents (if available)
     * @param currency          Currency code (if available)
     * @param succeeded         Whether the payment succeeded
     * @param shouldTopUpBudget Whether the budget should be topped up
     */
    record WebhookResult(
            String eventType,
            String providerTxId,
            UserId userId,
            Long amountCents,
            String currency,
            boolean succeeded,
            boolean shouldTopUpBudget
    ) {}

    /**
     * Create a checkout session for budget top-up.
     *
     * @param userId      The user requesting the top-up
     * @param amountUsd   The amount to top up in USD
     * @param successUrl  URL to redirect after successful payment
     * @param cancelUrl   URL to redirect if payment is canceled
     * @return Checkout session details
     * @throws PaymentException if session creation fails
     */
    CheckoutSessionResult createCheckoutSession(
            UserId userId,
            BigDecimal amountUsd,
            String successUrl,
            String cancelUrl
    );

    /**
     * Process a webhook event from the payment provider.
     *
     * @param payload   Raw webhook payload
     * @param signature Webhook signature header for verification
     * @return Webhook processing result
     * @throws PaymentException if verification fails or event cannot be processed
     */
    WebhookResult processWebhook(String payload, String signature);

    /**
     * Verify the payment provider is configured and operational.
     *
     * @return true if the provider is ready to accept payments
     */
    boolean isConfigured();

    /**
     * Get the name of the payment provider.
     *
     * @return Provider name (e.g., "stripe")
     */
    String getProviderName();

    /**
     * Exception thrown when payment operations fail.
     */
    class PaymentException extends RuntimeException {
        private final String errorCode;

        public PaymentException(String message) {
            super(message);
            this.errorCode = "PAYMENT_ERROR";
        }

        public PaymentException(String message, String errorCode) {
            super(message);
            this.errorCode = errorCode;
        }

        public PaymentException(String message, Throwable cause) {
            super(message, cause);
            this.errorCode = "PAYMENT_ERROR";
        }

        public PaymentException(String message, String errorCode, Throwable cause) {
            super(message, cause);
            this.errorCode = errorCode;
        }

        public String getErrorCode() {
            return errorCode;
        }
    }
}
