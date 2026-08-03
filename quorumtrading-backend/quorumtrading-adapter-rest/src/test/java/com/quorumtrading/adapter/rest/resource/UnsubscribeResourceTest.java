package com.quorumtrading.adapter.rest.resource;

import com.quorumtrading.application.service.UnsubscribeTokenService;
import com.quorumtrading.application.usecase.command.UpdateNotificationPreferenceCommand;
import com.quorumtrading.application.usecase.notification.GetNotificationPreferencesUseCase;
import com.quorumtrading.application.usecase.notification.UpdateNotificationPreferenceUseCase;
import com.quorumtrading.model.entity.NotificationPreference;
import com.quorumtrading.model.enums.NotificationChannel;
import com.quorumtrading.model.enums.NotificationType;
import com.quorumtrading.model.valueobject.UserId;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The one-click unsubscribe link at the bottom of every outgoing email.
 *
 * <p>It is unauthenticated by necessity — someone clicking it from a mail client has no session —
 * so the signed token is the only thing standing between a stranger and another person's
 * preferences. It is also the endpoint most likely to be hit twice, by a mail client prefetching
 * links, so a replay must not error.</p>
 *
 * <p>Uses the real {@link UnsubscribeTokenService} rather than a mock: the point of the endpoint
 * is that a token it did not sign is refused, which a stubbed verifier cannot demonstrate. The
 * HMAC itself is covered separately by {@code UnsubscribeTokenServiceTest}.</p>
 */
@QuarkusTest
@DisplayName("UnsubscribeResource")
class UnsubscribeResourceTest {

    @Inject UnsubscribeTokenService tokenService;

    @InjectMock GetNotificationPreferencesUseCase getPreferencesUseCase;
    @InjectMock UpdateNotificationPreferenceUseCase updatePreferenceUseCase;

    private static final UserId USER_ID = UserId.generate();

    private NotificationPreference preference(NotificationType type, NotificationChannel... channels) {
        Set<NotificationChannel> set = channels.length == 0
                ? EnumSet.noneOf(NotificationChannel.class)
                : EnumSet.copyOf(List.of(channels));
        return NotificationPreference.create(USER_ID, type, set);
    }

    private UpdateNotificationPreferenceCommand capturedCommand() {
        ArgumentCaptor<UpdateNotificationPreferenceCommand> captor =
                ArgumentCaptor.forClass(UpdateNotificationPreferenceCommand.class);
        verify(updatePreferenceUseCase).execute(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("a valid token turns EMAIL off and leaves in-app notifications alone")
    void shouldDisableEmailButKeepInApp() {
        when(getPreferencesUseCase.listForUser(USER_ID)).thenReturn(List.of(
                preference(NotificationType.INVESTMENT_EXECUTED,
                        NotificationChannel.IN_APP, NotificationChannel.EMAIL)));
        String token = tokenService.mint(USER_ID, NotificationType.INVESTMENT_EXECUTED);

        given()
            .queryParam("token", token)
        .when()
            .get("/api/v1/notification-preferences/unsubscribe")
        .then()
            .statusCode(200)
            .body(containsString("Unsubscribed"));

        UpdateNotificationPreferenceCommand command = capturedCommand();
        assertThat(command.userId()).isEqualTo(USER_ID);
        assertThat(command.type()).isEqualTo(NotificationType.INVESTMENT_EXECUTED);
        assertThat(command.channels())
                .as("in-app delivery must survive an email unsubscribe")
                .containsExactly(NotificationChannel.IN_APP);
    }

    @Test
    @DisplayName("only the encoded type is affected, not every email")
    void shouldOnlyTouchTheEncodedType() {
        when(getPreferencesUseCase.listForUser(USER_ID)).thenReturn(List.of(
                preference(NotificationType.INVESTMENT_EXECUTED, NotificationChannel.EMAIL),
                preference(NotificationType.INVESTMENT_FAILED, NotificationChannel.EMAIL)));
        String token = tokenService.mint(USER_ID, NotificationType.INVESTMENT_FAILED);

        given()
            .queryParam("token", token)
        .when()
            .get("/api/v1/notification-preferences/unsubscribe")
        .then()
            .statusCode(200);

        assertThat(capturedCommand().type()).isEqualTo(NotificationType.INVESTMENT_FAILED);
    }

    @Test
    @DisplayName("clicking twice is harmless — mail clients prefetch links")
    void shouldBeIdempotent() {
        when(getPreferencesUseCase.listForUser(USER_ID)).thenReturn(List.of(
                preference(NotificationType.INVESTMENT_EXECUTED, NotificationChannel.IN_APP)));
        String token = tokenService.mint(USER_ID, NotificationType.INVESTMENT_EXECUTED);

        for (int i = 0; i < 2; i++) {
            given()
                .queryParam("token", token)
            .when()
                .get("/api/v1/notification-preferences/unsubscribe")
            .then()
                .statusCode(200)
                .body(containsString("Unsubscribed"));
        }
    }

    @Test
    @DisplayName("a user with no stored preference can still unsubscribe")
    void shouldHandleAUserWithNoStoredPreference() {
        when(getPreferencesUseCase.listForUser(USER_ID)).thenReturn(List.of());
        String token = tokenService.mint(USER_ID, NotificationType.INVESTMENT_EXECUTED);

        given()
            .queryParam("token", token)
        .when()
            .get("/api/v1/notification-preferences/unsubscribe")
        .then()
            .statusCode(200);

        assertThat(capturedCommand().channels()).doesNotContain(NotificationChannel.EMAIL);
    }

    @Test
    @DisplayName("a tampered token changes nothing")
    void shouldRefuseATamperedToken() {
        String token = tokenService.mint(USER_ID, NotificationType.INVESTMENT_EXECUTED);
        String tampered = token.substring(0, token.length() - 4) + "AAAA";

        given()
            .queryParam("token", tampered)
        .when()
            .get("/api/v1/notification-preferences/unsubscribe")
        .then()
            .statusCode(400)
            .body(containsString("Invalid unsubscribe link"));

        verify(updatePreferenceUseCase, never()).execute(any());
    }

    @Test
    @DisplayName("a missing token changes nothing")
    void shouldRefuseAMissingToken() {
        given()
        .when()
            .get("/api/v1/notification-preferences/unsubscribe")
        .then()
            .statusCode(400);

        verify(updatePreferenceUseCase, never()).execute(any());
    }

    @Test
    @DisplayName("a token from a different secret is refused")
    void shouldRefuseAForeignlySignedToken() {
        UnsubscribeTokenService attacker =
                new UnsubscribeTokenService("a-completely-different-secret-value");
        String forged = attacker.mint(USER_ID, NotificationType.INVESTMENT_EXECUTED);

        given()
            .queryParam("token", forged)
        .when()
            .get("/api/v1/notification-preferences/unsubscribe")
        .then()
            .statusCode(400);

        verify(updatePreferenceUseCase, never()).execute(any());
    }

    @Test
    @DisplayName("the page never leaks who the link belonged to")
    void shouldNotRevealTheAccount() {
        when(getPreferencesUseCase.listForUser(USER_ID)).thenReturn(List.of(
                preference(NotificationType.INVESTMENT_EXECUTED, NotificationChannel.EMAIL)));
        String token = tokenService.mint(USER_ID, NotificationType.INVESTMENT_EXECUTED);

        given()
            .queryParam("token", token)
        .when()
            .get("/api/v1/notification-preferences/unsubscribe")
        .then()
            .statusCode(200)
            // Anyone who gets hold of the link — a forwarded mail, a proxy log — must not learn
            // whose account it is.
            .body(not(containsString(USER_ID.value().toString())));
    }
}
