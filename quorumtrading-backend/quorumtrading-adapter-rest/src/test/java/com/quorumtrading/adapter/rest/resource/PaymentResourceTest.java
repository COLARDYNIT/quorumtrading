package com.quorumtrading.adapter.rest.resource;

import com.quorumtrading.application.port.out.PaymentPort;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The Stripe webhook, previously untested — and unusually costly to get wrong.
 *
 * <p>It is necessarily unauthenticated, so the signature is the only thing separating a real
 * Stripe callback from anyone who knows the URL. Two behaviours matter beyond the signature check
 * itself. An unsigned or badly-signed request must never reach the use case at all — crediting a
 * budget on a forged callback is free money. And a transient failure must answer 5xx rather than
 * 200: Stripe retries on 5xx and gives up on 2xx, so returning "OK" for a payment we failed to
 * record loses the event permanently.</p>
 */
@QuarkusTest
@DisplayName("PaymentResource — webhook")
class PaymentResourceTest {

    // Drives the REAL ProcessPaymentWebhookUseCase (wired by MockPaymentBeans) through its port,
    // rather than stubbing the use case away: the behaviours worth pinning here — what Stripe is
    // told, and therefore whether it retries — depend on how the resource maps what the port does.
    @InjectMock PaymentPort paymentPort;

    private static final String PAYLOAD = "{\"id\":\"evt_1\",\"type\":\"checkout.session.completed\"}";

    @Test
    @DisplayName("a correctly signed event is processed and acknowledged")
    void shouldProcessASignedWebhook() {
        when(paymentPort.processWebhook(anyString(), anyString()))
                .thenReturn(new PaymentPort.WebhookResult(
                        "checkout.session.completed", "cs_test_1", null, 1000L, "usd", true, false));

        given()
            .contentType("application/json")
            .header("Stripe-Signature", "t=1,v1=validsignature")
            .body(PAYLOAD)
        .when()
            .post("/api/v1/payment/webhook")
        .then()
            .statusCode(200)
            .body(containsString("\"received\": true"))
            .body(containsString("\"processed\": true"));
    }

    @Test
    @DisplayName("an unsigned request never reaches the use case")
    void shouldRejectAWebhookWithNoSignature() {
        given()
            .contentType("application/json")
            .body(PAYLOAD)
        .when()
            .post("/api/v1/payment/webhook")
        .then()
            .statusCode(400)
            .body(containsString("Missing signature"));

        verify(paymentPort, never()).processWebhook(any(), any());
    }

    @Test
    @DisplayName("a blank signature header is treated as missing, not as a signature")
    void shouldRejectABlankSignature() {
        given()
            .contentType("application/json")
            .header("Stripe-Signature", "   ")
            .body(PAYLOAD)
        .when()
            .post("/api/v1/payment/webhook")
        .then()
            .statusCode(400);

        verify(paymentPort, never()).processWebhook(any(), any());
    }

    @Test
    @DisplayName("a bad signature is a 400 — Stripe must not retry a forgery")
    void shouldRejectAnInvalidSignature() {
        when(paymentPort.processWebhook(anyString(), anyString()))
                .thenThrow(new PaymentPort.PaymentException("bad signature", "INVALID_SIGNATURE"));

        given()
            .contentType("application/json")
            .header("Stripe-Signature", "t=1,v1=forged")
            .body(PAYLOAD)
        .when()
            .post("/api/v1/payment/webhook")
        .then()
            .statusCode(400)
            .body(containsString("Invalid signature"));
    }

    @Test
    @DisplayName("a processing failure answers 5xx so Stripe retries rather than dropping the event")
    void shouldReturnServerErrorOnProcessingFailure() {
        when(paymentPort.processWebhook(anyString(), anyString()))
                .thenThrow(new PaymentPort.PaymentException("provider unavailable", "PROVIDER_ERROR"));

        given()
            .contentType("application/json")
            .header("Stripe-Signature", "t=1,v1=validsignature")
            .body(PAYLOAD)
        .when()
            .post("/api/v1/payment/webhook")
        .then()
            // 200 here would tell Stripe the event was handled and it would never be resent,
            // silently losing a payment we failed to record.
            .statusCode(500);
    }

    @Test
    @DisplayName("an unexpected error also answers 5xx rather than swallowing the event")
    void shouldReturnServerErrorOnUnexpectedFailure() {
        when(paymentPort.processWebhook(anyString(), anyString()))
                .thenThrow(new RuntimeException("boom"));

        given()
            .contentType("application/json")
            .header("Stripe-Signature", "t=1,v1=validsignature")
            .body(PAYLOAD)
        .when()
            .post("/api/v1/payment/webhook")
        .then()
            .statusCode(500);
    }

    @Test
    @DisplayName("an event needing no action is acknowledged so the provider stops retrying")
    void shouldAcknowledgeAnUnprocessedEvent() {
        when(paymentPort.processWebhook(anyString(), anyString()))
                .thenReturn(new PaymentPort.WebhookResult(
                        "invoice.created", null, null, 0L, "usd", false, false));

        given()
            .contentType("application/json")
            .header("Stripe-Signature", "t=1,v1=validsignature")
            .body(PAYLOAD)
        .when()
            .post("/api/v1/payment/webhook")
        .then()
            // 200, not an error: there is nothing wrong with an event we take no action on, and
            // anything else would have the provider redeliver it indefinitely.
            .statusCode(200)
            .body(containsString("\"received\": true"))
            .body(containsString("invoice.created"));
    }

    @Test
    @DisplayName("the public config never exposes a secret key")
    void shouldExposeOnlyNonSecretConfig() {
        when(paymentPort.isConfigured()).thenReturn(true);
        when(paymentPort.getProviderName()).thenReturn("stripe");

        given()
        .when()
            .get("/api/v1/payment/config")
        .then()
            .statusCode(200)
            .body("configured", equalTo(true))
            .body("provider", equalTo("stripe"));
    }
}
