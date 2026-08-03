package com.quorumtrading.adapter.rest.resource;

import com.quorumtrading.application.usecase.ResolveCurrentUserUseCase;
import com.quorumtrading.application.usecase.command.UpdateNotificationPreferenceCommand;
import com.quorumtrading.application.usecase.notification.GetNotificationPreferencesUseCase;
import com.quorumtrading.application.usecase.notification.UpdateNotificationPreferenceUseCase;
import com.quorumtrading.model.entity.NotificationPreference;
import com.quorumtrading.model.enums.NotificationChannel;
import com.quorumtrading.model.enums.NotificationType;
import com.quorumtrading.model.valueobject.UserId;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Per-type notification channel preferences, previously untested.
 *
 * <p>This endpoint is the only way a user turns email off. Getting it wrong is not a cosmetic
 * failure: an unrecognised channel silently dropped would leave someone still receiving mail they
 * asked to stop, and an empty array has to mean "opt out entirely" rather than "no change" — the
 * two are one keystroke apart in a UI and opposite in effect.</p>
 */
@QuarkusTest
@DisplayName("NotificationPreferenceResource")
class NotificationPreferenceResourceTest {

    @InjectMock GetNotificationPreferencesUseCase getNotificationPreferencesUseCase;
    @InjectMock UpdateNotificationPreferenceUseCase updateNotificationPreferenceUseCase;
    @InjectMock ResolveCurrentUserUseCase resolveCurrentUserUseCase;

    private static final String EMAIL = "prefs@quorumtrading.local";
    private static final UserId USER_ID = UserId.generate();

    @BeforeEach
    void setUp() {
        when(resolveCurrentUserUseCase.execute(EMAIL)).thenReturn(USER_ID);
    }

    private NotificationPreference preference(NotificationType type, NotificationChannel... channels) {
        Set<NotificationChannel> set = channels.length == 0
                ? EnumSet.noneOf(NotificationChannel.class)
                : EnumSet.copyOf(List.of(channels));
        return NotificationPreference.create(USER_ID, type, set);
    }

    @Test
    @DisplayName("lists one row per type, including defaults the user never set")
    void shouldListPreferences() {
        when(getNotificationPreferencesUseCase.listForUser(USER_ID)).thenReturn(List.of(
                preference(NotificationType.INVESTMENT_EXECUTED,
                        NotificationChannel.IN_APP, NotificationChannel.EMAIL),
                preference(NotificationType.INVESTMENT_FAILED, NotificationChannel.IN_APP)));

        given()
            .cookie("dev_session", EMAIL)
        .when()
            .get("/api/v1/notification-preferences")
        .then()
            .statusCode(200)
            .body("$", hasSize(2))
            .body("type", hasItems("INVESTMENT_EXECUTED", "INVESTMENT_FAILED"))
            .body("[0].channels", hasItems("IN_APP", "EMAIL"));
    }

    @Test
    @DisplayName("updating a type stores exactly the channels asked for")
    void shouldUpdateChannels() {
        when(updateNotificationPreferenceUseCase.execute(any(UpdateNotificationPreferenceCommand.class)))
                .thenReturn(preference(NotificationType.INVESTMENT_EXECUTED, NotificationChannel.IN_APP));

        given()
            .cookie("dev_session", EMAIL)
            .contentType("application/json")
            .body("{\"channels\": [\"IN_APP\"]}")
        .when()
            .put("/api/v1/notification-preferences/INVESTMENT_EXECUTED")
        .then()
            .statusCode(200)
            .body("type", equalTo("INVESTMENT_EXECUTED"))
            .body("channels", hasSize(1));

        ArgumentCaptor<UpdateNotificationPreferenceCommand> command =
                ArgumentCaptor.forClass(UpdateNotificationPreferenceCommand.class);
        verify(updateNotificationPreferenceUseCase).execute(command.capture());
        assertThat(command.getValue().userId()).isEqualTo(USER_ID);
        assertThat(command.getValue().type()).isEqualTo(NotificationType.INVESTMENT_EXECUTED);
        assertThat(command.getValue().channels()).containsExactly(NotificationChannel.IN_APP);
    }

    @Test
    @DisplayName("a lowercase type and channel are accepted")
    void shouldAcceptLowercaseNames() {
        when(updateNotificationPreferenceUseCase.execute(any(UpdateNotificationPreferenceCommand.class)))
                .thenReturn(preference(NotificationType.INVESTMENT_EXECUTED, NotificationChannel.EMAIL));

        given()
            .cookie("dev_session", EMAIL)
            .contentType("application/json")
            .body("{\"channels\": [\"email\"]}")
        .when()
            .put("/api/v1/notification-preferences/investment_executed")
        .then()
            .statusCode(200);

        ArgumentCaptor<UpdateNotificationPreferenceCommand> command =
                ArgumentCaptor.forClass(UpdateNotificationPreferenceCommand.class);
        verify(updateNotificationPreferenceUseCase).execute(command.capture());
        assertThat(command.getValue().channels()).containsExactly(NotificationChannel.EMAIL);
    }

    @Test
    @DisplayName("an empty array opts out entirely rather than meaning 'unchanged'")
    void shouldTreatEmptyChannelsAsOptOut() {
        when(updateNotificationPreferenceUseCase.execute(any(UpdateNotificationPreferenceCommand.class)))
                .thenReturn(preference(NotificationType.INVESTMENT_EXECUTED));

        given()
            .cookie("dev_session", EMAIL)
            .contentType("application/json")
            .body("{\"channels\": []}")
        .when()
            .put("/api/v1/notification-preferences/INVESTMENT_EXECUTED")
        .then()
            .statusCode(200)
            .body("channels", hasSize(0));

        ArgumentCaptor<UpdateNotificationPreferenceCommand> command =
                ArgumentCaptor.forClass(UpdateNotificationPreferenceCommand.class);
        verify(updateNotificationPreferenceUseCase).execute(command.capture());
        assertThat(command.getValue().channels()).isEmpty();
    }

    @Test
    @DisplayName("an unknown notification type is refused, not silently ignored")
    void shouldRejectUnknownType() {
        given()
            .cookie("dev_session", EMAIL)
            .contentType("application/json")
            .body("{\"channels\": [\"IN_APP\"]}")
        .when()
            .put("/api/v1/notification-preferences/NOT_A_REAL_TYPE")
        .then()
            .statusCode(400);

        verify(updateNotificationPreferenceUseCase, never()).execute(any());
    }

    @Test
    @DisplayName("an unknown channel is refused — silently dropping it would keep sending mail")
    void shouldRejectUnknownChannel() {
        given()
            .cookie("dev_session", EMAIL)
            .contentType("application/json")
            .body("{\"channels\": [\"CARRIER_PIGEON\"]}")
        .when()
            .put("/api/v1/notification-preferences/INVESTMENT_EXECUTED")
        .then()
            .statusCode(400);

        verify(updateNotificationPreferenceUseCase, never()).execute(any());
    }

    @Test
    @DisplayName("preferences are not public")
    void shouldRequireAuthentication() {
        given()
        .when()
            .get("/api/v1/notification-preferences")
        .then()
            .statusCode(401);
    }
}
