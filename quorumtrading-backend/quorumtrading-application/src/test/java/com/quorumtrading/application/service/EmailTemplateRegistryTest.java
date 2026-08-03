package com.quorumtrading.application.service;

import com.quorumtrading.model.entity.Notification;
import com.quorumtrading.model.enums.NotificationChannel;
import com.quorumtrading.model.enums.NotificationType;
import com.quorumtrading.model.valueobject.NotificationId;
import com.quorumtrading.model.valueobject.UserId;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EmailTemplateRegistryTest {

    private static final String UNSUB = "https://example.com/unsub?token=abc";
    private static final String BASE = "https://example.com";

    private final EmailTemplateRegistry registry = new EmailTemplateRegistry();

    private Notification notification(NotificationType type, Map<String, Object> payload) {
        return Notification.create(
                NotificationId.generate(),
                new UserId(UUID.randomUUID()),
                type,
                NotificationChannel.EMAIL,
                "Test subject line",
                "Test body line",
                payload,
                UUID.randomUUID());
    }

    @Test
    void everyTypeRenders() {
        for (NotificationType type : NotificationType.values()) {
            Notification n = notification(type, Map.of());
            EmailTemplateRegistry.Rendered r = registry.render(n, UNSUB, BASE);
            assertThat(r.subject()).as("subject for " + type).isNotBlank();
            assertThat(r.plainText()).as("plain text for " + type).isNotBlank();
            assertThat(r.html()).as("html for " + type).isNotBlank();
        }
    }

    @Test
    void unsubscribeLinkAppearsInBothPlainAndHtml() {
        Notification n = notification(NotificationType.INVESTMENT_EXECUTED, Map.of());
        EmailTemplateRegistry.Rendered r = registry.render(n, UNSUB, BASE);
        assertThat(r.plainText()).contains(UNSUB);
        assertThat(r.html()).contains(UNSUB);
    }

    @Test
    void investmentExecutedSurfacesAmountInSubject() {
        Notification n = notification(NotificationType.INVESTMENT_EXECUTED,
                Map.of("actualAmount", "200.00", "currency", "USD"));
        EmailTemplateRegistry.Rendered r = registry.render(n, UNSUB, BASE);
        assertThat(r.subject()).contains("200.00").contains("USD");
    }

    @Test
    void failureReasonAppearsInSkippedAndFailedBodies() {
        Notification skipped = notification(NotificationType.INVESTMENT_SKIPPED,
                Map.of("failureReason", "Broker available balance $50 is less than $200"));
        Notification failed = notification(NotificationType.INVESTMENT_FAILED,
                Map.of("failureReason", "Broker order rejected: market closed"));

        EmailTemplateRegistry.Rendered skippedR = registry.render(skipped, UNSUB, BASE);
        EmailTemplateRegistry.Rendered failedR = registry.render(failed, UNSUB, BASE);

        assertThat(skippedR.plainText()).contains("Broker available balance $50 is less than $200");
        assertThat(failedR.plainText()).contains("market closed");
    }

    @Test
    void htmlEscapesUserSuppliedContent() {
        // Notification title is user-facing copy from the service layer, but it goes through escape().
        Notification n = Notification.create(
                NotificationId.generate(),
                new UserId(UUID.randomUUID()),
                NotificationType.GENERIC,
                NotificationChannel.EMAIL,
                "Title with <script>alert('xss')</script>",
                "Body",
                Map.of(),
                null);
        EmailTemplateRegistry.Rendered r = registry.render(n, UNSUB, BASE);
        assertThat(r.html()).doesNotContain("<script>");
        assertThat(r.html()).contains("&lt;script&gt;");
    }

    @Test
    void simulationLinkAppearsWhenPayloadHasSimulationId() {
        UUID simId = UUID.randomUUID();
        Notification n = notification(NotificationType.INVESTMENT_EXECUTED,
                Map.of("simulationId", simId.toString()));
        EmailTemplateRegistry.Rendered r = registry.render(n, UNSUB, BASE);
        assertThat(r.html()).contains("/simulations/" + simId);
    }
}
