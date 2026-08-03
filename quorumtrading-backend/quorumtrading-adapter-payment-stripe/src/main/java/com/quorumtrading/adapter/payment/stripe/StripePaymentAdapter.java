package com.quorumtrading.adapter.payment.stripe;

import com.quorumtrading.application.port.out.PaymentPort;
import com.quorumtrading.model.valueobject.UserId;
import com.stripe.Stripe;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.StripeObject;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.checkout.SessionCreateParams;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Stripe implementation of the PaymentPort.
 * Handles checkout session creation and webhook processing.
 */
@ApplicationScoped
public class StripePaymentAdapter implements PaymentPort {

    private static final Logger LOG = Logger.getLogger(StripePaymentAdapter.class);
    private static final String PROVIDER_NAME = "stripe";

    private final StripeConfig config;
    private boolean configured = false;

    @Inject
    public StripePaymentAdapter(StripeConfig config) {
        this.config = config;
    }

    @PostConstruct
    void init() {
        if (!config.enabled()) {
            LOG.info("Stripe payment adapter is disabled");
            return;
        }

        if (config.secretKey().isEmpty() || config.secretKey().get().isBlank()) {
            throw new IllegalStateException(
                    "Stripe is enabled but STRIPE_SECRET_KEY is not set. "
                    + "Either set the environment variable or disable Stripe (quorumtrading.stripe.enabled=false).");
        }

        Stripe.apiKey = config.secretKey().get();
        configured = true;
        LOG.info("Stripe payment adapter initialized");
    }

    @Override
    public CheckoutSessionResult createCheckoutSession(
            UserId userId,
            BigDecimal amountUsd,
            String successUrl,
            String cancelUrl
    ) {
        if (!configured) {
            throw new PaymentException("Stripe is not configured", "STRIPE_NOT_CONFIGURED");
        }

        try {
            // Convert USD to cents
            long amountCents = amountUsd.multiply(BigDecimal.valueOf(100)).longValue();

            // Build the checkout session
            SessionCreateParams params = SessionCreateParams.builder()
                    .setMode(SessionCreateParams.Mode.PAYMENT)
                    .setSuccessUrl(successUrl + (successUrl.contains("?") ? "&" : "?") + "session_id={CHECKOUT_SESSION_ID}")
                    .setCancelUrl(cancelUrl)
                    .setClientReferenceId(userId.value().toString())
                    .addLineItem(
                            SessionCreateParams.LineItem.builder()
                                    .setQuantity(1L)
                                    .setPriceData(
                                            SessionCreateParams.LineItem.PriceData.builder()
                                                    .setCurrency(config.currency())
                                                    .setUnitAmount(amountCents)
                                                    .setProductData(
                                                            SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                                                    .setName(config.productName())
                                                                    .setDescription("AI budget top-up: $" + amountUsd)
                                                                    .build()
                                                    )
                                                    .build()
                                    )
                                    .build()
                    )
                    .putMetadata("user_id", userId.value().toString())
                    .putMetadata("amount_usd", amountUsd.toString())
                    .setPaymentIntentData(
                            SessionCreateParams.PaymentIntentData.builder()
                                    .putMetadata("user_id", userId.value().toString())
                                    .putMetadata("amount_usd", amountUsd.toString())
                                    .build()
                    )
                    .build();

            Session session = Session.create(params);

            LOG.infof("Created Stripe checkout session %s for user %s, amount $%.2f",
                    session.getId(), userId, amountUsd);

            return new CheckoutSessionResult(
                    session.getId(),
                    session.getUrl(),
                    session.getClientSecret(),
                    amountCents,
                    config.currency()
            );

        } catch (StripeException e) {
            LOG.errorf(e, "Failed to create Stripe checkout session for user %s", userId);
            throw new PaymentException("Failed to create checkout session: " + e.getMessage(), "STRIPE_ERROR", e);
        }
    }

    @Override
    public WebhookResult processWebhook(String payload, String signature) {
        if (!configured) {
            throw new PaymentException("Stripe is not configured", "STRIPE_NOT_CONFIGURED");
        }

        if (config.webhookSecret().isEmpty()) {
            throw new PaymentException("Webhook secret is not configured", "WEBHOOK_SECRET_MISSING");
        }

        Event event;
        try {
            event = Webhook.constructEvent(payload, signature, config.webhookSecret().get());
        } catch (SignatureVerificationException e) {
            LOG.warnf("Invalid webhook signature: %s", e.getMessage());
            throw new PaymentException("Invalid webhook signature", "INVALID_SIGNATURE", e);
        }

        LOG.infof("Processing Stripe webhook event: %s", event.getType());

        return switch (event.getType()) {
            case "checkout.session.completed" -> handleCheckoutSessionCompleted(event);
            case "checkout.session.expired" -> handleCheckoutSessionExpired(event);
            case "payment_intent.succeeded" -> handlePaymentIntentSucceeded(event);
            case "payment_intent.payment_failed" -> handlePaymentIntentFailed(event);
            default -> {
                LOG.debugf("Ignoring unhandled event type: %s", event.getType());
                yield new WebhookResult(event.getType(), null, null, null, null, false, false);
            }
        };
    }

    private WebhookResult handleCheckoutSessionCompleted(Event event) {
        Session session = (Session) deserializeEventData(event, "checkout session");

        String userIdStr = session.getClientReferenceId();
        if (userIdStr == null) {
            userIdStr = session.getMetadata().get("user_id");
        }

        UserId userId = userIdStr != null ? new UserId(UUID.fromString(userIdStr)) : null;
        Long amountCents = session.getAmountTotal();

        LOG.infof("Checkout session completed: %s, user: %s, amount: %d cents",
                session.getId(), userId, amountCents);

        return new WebhookResult(
                event.getType(),
                session.getId(),
                userId,
                amountCents,
                session.getCurrency(),
                true,
                true // Should top up budget
        );
    }

    private WebhookResult handleCheckoutSessionExpired(Event event) {
        Session session = (Session) deserializeEventData(event, "checkout session");

        LOG.infof("Checkout session expired: %s", session.getId());

        return new WebhookResult(
                event.getType(),
                session.getId(),
                null,
                null,
                null,
                false,
                false
        );
    }

    private WebhookResult handlePaymentIntentSucceeded(Event event) {
        // Payment intent succeeded - this is a backup for checkout.session.completed
        // We don't top up budget here since checkout.session.completed handles it
        LOG.debug("Payment intent succeeded (handled via checkout session)");
        return new WebhookResult(event.getType(), null, null, null, null, true, false);
    }

    private WebhookResult handlePaymentIntentFailed(Event event) {
        LOG.warn("Payment intent failed");
        return new WebhookResult(event.getType(), null, null, null, null, false, false);
    }

    /**
     * Deserializes the event data object, falling back to unsafe deserialization
     * when the webhook API version doesn't match the SDK version.
     */
    private StripeObject deserializeEventData(Event event, String objectDescription) {
        EventDataObjectDeserializer deserializer = event.getDataObjectDeserializer();
        if (deserializer.getObject().isPresent()) {
            return deserializer.getObject().get();
        }
        LOG.warnf("API version mismatch for %s event, using unsafe deserialization", event.getType());
        try {
            return deserializer.deserializeUnsafe();
        } catch (Exception e) {
            throw new PaymentException(
                    "Failed to deserialize " + objectDescription + ": " + e.getMessage(),
                    "DESERIALIZATION_ERROR", e);
        }
    }

    @Override
    public boolean isConfigured() {
        return configured;
    }

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }
}
