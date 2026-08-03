package com.quorumtrading.adapter.rest.resource;

import com.quorumtrading.adapter.rest.dto.request.CreateCheckoutRequest;
import com.quorumtrading.adapter.rest.dto.response.CheckoutSessionResponse;
import com.quorumtrading.adapter.rest.exception.ProblemDetails;
import com.quorumtrading.adapter.rest.security.CurrentUserProvider;
import com.quorumtrading.application.port.out.PaymentPort;
import com.quorumtrading.application.usecase.payment.CreateCheckoutSessionUseCase;
import com.quorumtrading.application.usecase.payment.ProcessPaymentWebhookUseCase;
import com.quorumtrading.model.valueobject.UserId;
import io.quarkus.security.Authenticated;
import jakarta.annotation.security.PermitAll;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.logging.Logger;

/**
 * REST resource for payment operations.
 * Handles Stripe checkout session creation and webhook processing.
 */
@Path("/api/v1/payment")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
@Authenticated
@Tag(name = "Payment", description = "Payment operations for budget top-ups")
public class PaymentResource {

    private static final Logger LOG = Logger.getLogger(PaymentResource.class);
    private static final String STRIPE_SIGNATURE_HEADER = "Stripe-Signature";

    @Inject
    CreateCheckoutSessionUseCase createCheckoutSessionUseCase;

    @Inject
    ProcessPaymentWebhookUseCase processPaymentWebhookUseCase;

    @Inject
    CurrentUserProvider currentUser;

    @Inject
    PaymentPort paymentPort;

    @POST
    @Path("/checkout")
    @Operation(summary = "Create checkout session", description = "Create a Stripe checkout session for budget top-up")
    @APIResponse(responseCode = "200", description = "Checkout session created",
            content = @Content(schema = @Schema(implementation = CheckoutSessionResponse.class)))
    @APIResponse(responseCode = "400", description = "Invalid request")
    @APIResponse(responseCode = "401", description = "Not authenticated")
    @APIResponse(responseCode = "503", description = "Payment provider not configured")
    public Response createCheckout(
            @RequestBody(required = true) @Valid CreateCheckoutRequest request
    ) {
        UserId userId = currentUser.getCurrentUserId();

        if (!paymentPort.isConfigured()) {
            return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                    .entity(new ProblemDetails(
                            "https://api.quorumtrading.com/problems/service-unavailable",
                            "Service Unavailable",
                            503,
                            "Payment provider is not configured",
                            "/api/v1/payment/checkout"))
                    .build();
        }

        LOG.infof("AUDIT: Checkout session requested — user=%s, amount=%s", userId, request.amount());

        try {
            CreateCheckoutSessionUseCase.Result result = createCheckoutSessionUseCase.execute(
                    userId,
                    request.amount(),
                    request.successUrl(),
                    request.cancelUrl()
            );

            CheckoutSessionResponse response = new CheckoutSessionResponse(
                    result.sessionId(),
                    result.checkoutUrl(),
                    result.clientSecret(),
                    result.amountCents(),
                    result.currency()
            );

            LOG.infof("AUDIT: Checkout session created — user=%s, sessionId=%s, amountCents=%d",
                    userId, result.sessionId(), result.amountCents());

            return Response.ok(response).build();

        } catch (PaymentPort.PaymentException e) {
            LOG.warnf("AUDIT: Payment error — user=%s, error=%s, code=%s",
                    userId, e.getMessage(), e.getErrorCode());
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ProblemDetails(
                            "https://api.quorumtrading.com/problems/payment-error",
                            "Payment Error",
                            400,
                            "Payment processing failed (code: PAYMENT_ERROR)",
                            "/api/v1/payment/checkout"))
                    .build();
        }
    }

    @POST
    @Path("/webhook")
    @PermitAll
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Handle webhook", description = "Process Stripe webhook events")
    @APIResponse(responseCode = "200", description = "Webhook processed")
    @APIResponse(responseCode = "400", description = "Invalid webhook")
    public Response handleWebhook(
            String payload,
            @HeaderParam(STRIPE_SIGNATURE_HEADER) String signature
    ) {
        LOG.debug("Received payment webhook");

        if (signature == null || signature.isBlank()) {
            LOG.warn("Missing Stripe signature header");
            return Response.status(400)
                    .entity("{\"error\": \"Missing signature\"}")
                    .build();
        }

        try {
            ProcessPaymentWebhookUseCase.Result result = processPaymentWebhookUseCase.execute(payload, signature);

            LOG.infof("AUDIT: Webhook processed — event=%s, processed=%s", result.eventType(), result.processed());
            return Response.ok("{\"received\": true, \"processed\": " + result.processed() +
                    ", \"event\": \"" + result.eventType() + "\"}").build();

        } catch (PaymentPort.PaymentException e) {
            if (e.getErrorCode() != null && e.getErrorCode().equals("INVALID_SIGNATURE")) {
                LOG.warnf("Invalid webhook signature: %s", e.getMessage());
                return Response.status(400)
                        .entity("{\"error\": \"Invalid signature\"}")
                        .build();
            }
            // Other payment errors — return 500 so Stripe retries
            LOG.errorf("Webhook processing failed: %s", e.getMessage());
            return Response.status(500)
                    .entity("{\"error\": \"Processing failed\"}")
                    .build();

        } catch (Exception e) {
            LOG.errorf(e, "Unexpected error processing webhook");
            // Return 500 so Stripe retries on transient failures
            return Response.status(500)
                    .entity("{\"error\": \"Internal error\"}")
                    .build();
        }
    }

    @GET
    @Path("/config")
    @PermitAll
    @Operation(summary = "Get payment config", description = "Get public payment configuration (publishable key)")
    @APIResponse(responseCode = "200", description = "Payment configuration")
    public Response getConfig() {
        boolean configured = paymentPort.isConfigured();
        String provider = paymentPort.getProviderName();

        return Response.ok("{\"configured\": " + configured + ", \"provider\": \"" + provider + "\"}").build();
    }

}
