package com.quorumtrading.adapter.rest.resource;

import com.quorumtrading.application.usecase.ResolveCurrentUserUseCase;
import com.quorumtrading.application.usecase.command.MarkNotificationReadCommand;
import com.quorumtrading.application.usecase.notification.CountUnreadNotificationsUseCase;
import com.quorumtrading.application.usecase.notification.GetNotificationUseCase;
import com.quorumtrading.application.usecase.notification.ListNotificationsUseCase;
import com.quorumtrading.application.usecase.notification.MarkAllNotificationsReadUseCase;
import com.quorumtrading.application.usecase.notification.MarkNotificationReadUseCase;
import com.quorumtrading.application.usecase.query.ListNotificationsQuery;
import com.quorumtrading.model.entity.Notification;
import com.quorumtrading.model.enums.NotificationChannel;
import com.quorumtrading.model.enums.NotificationStatus;
import com.quorumtrading.model.enums.NotificationType;
import com.quorumtrading.model.valueobject.NotificationId;
import com.quorumtrading.model.valueobject.UserId;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The in-app notification inbox, which had no resource test at all.
 *
 * <p>Notifications are how a user learns that an autonomous action happened — a recurring
 * investment fired, a simulation finished, a budget ran out. Nothing exercised the endpoints that
 * surface them, and the MCP dog-fooding sweep found the surrounding subsystem to be where several
 * real defects lived. The behaviour worth pinning here is not the happy path but the
 * ownership boundary: another user's notification must be indistinguishable from one that does
 * not exist, or the id space becomes a probe.</p>
 */
@QuarkusTest
@DisplayName("NotificationResource")
class NotificationResourceTest {

    @InjectMock ListNotificationsUseCase listNotificationsUseCase;
    @InjectMock GetNotificationUseCase getNotificationUseCase;
    @InjectMock MarkNotificationReadUseCase markNotificationReadUseCase;
    @InjectMock MarkAllNotificationsReadUseCase markAllNotificationsReadUseCase;
    @InjectMock CountUnreadNotificationsUseCase countUnreadNotificationsUseCase;
    @InjectMock ResolveCurrentUserUseCase resolveCurrentUserUseCase;

    private static final String EMAIL = "notifications@quorumtrading.local";
    private static final UserId USER_ID = UserId.generate();

    @BeforeEach
    void setUp() {
        when(resolveCurrentUserUseCase.execute(EMAIL)).thenReturn(USER_ID);
    }

    private Notification notification(NotificationId id, NotificationStatus status) {
        return Notification.reconstitute(
                id, USER_ID, NotificationType.INVESTMENT_EXECUTED, NotificationChannel.IN_APP,
                "Contribution invested", "$250 went into BTC Momentum",
                Map.of("amount", "250.00"), UUID.randomUUID(),
                status, Instant.parse("2026-06-01T06:00:00Z"),
                Instant.parse("2026-06-01T06:00:01Z"),
                status == NotificationStatus.READ ? Instant.parse("2026-06-01T09:00:00Z") : null,
                null);
    }

    @Test
    @DisplayName("lists the current user's notifications")
    void shouldListNotifications() {
        NotificationId id = NotificationId.generate();
        when(listNotificationsUseCase.execute(any(ListNotificationsQuery.class)))
                .thenReturn(List.of(notification(id, NotificationStatus.DELIVERED)));

        given()
            .cookie("dev_session", EMAIL)
        .when()
            .get("/api/v1/notifications")
        .then()
            .statusCode(200)
            .body("$", hasSize(1))
            .body("[0].id", equalTo(id.value().toString()))
            .body("[0].type", equalTo("INVESTMENT_EXECUTED"))
            .body("[0].channel", equalTo("IN_APP"))
            .body("[0].title", equalTo("Contribution invested"));
    }

    @Test
    @DisplayName("status=unread narrows the query, and the value is passed through, not ignored")
    void shouldPassUnreadFilterToTheUseCase() {
        when(listNotificationsUseCase.execute(any(ListNotificationsQuery.class))).thenReturn(List.of());

        given()
            .cookie("dev_session", EMAIL)
            .queryParam("status", "unread")
            .queryParam("limit", 10)
        .when()
            .get("/api/v1/notifications")
        .then()
            .statusCode(200);

        ArgumentCaptor<ListNotificationsQuery> query = ArgumentCaptor.forClass(ListNotificationsQuery.class);
        verify(listNotificationsUseCase).execute(query.capture());
        assertThat(query.getValue().onlyUnread()).isTrue();
        assertThat(query.getValue().limit()).isEqualTo(10);
        assertThat(query.getValue().userId()).isEqualTo(USER_ID);
    }

    @Test
    @DisplayName("any other status value means 'everything', not 'unread'")
    void shouldTreatUnrecognisedStatusAsNoFilter() {
        when(listNotificationsUseCase.execute(any(ListNotificationsQuery.class))).thenReturn(List.of());

        given()
            .cookie("dev_session", EMAIL)
            .queryParam("status", "read")
        .when()
            .get("/api/v1/notifications")
        .then()
            .statusCode(200);

        ArgumentCaptor<ListNotificationsQuery> query = ArgumentCaptor.forClass(ListNotificationsQuery.class);
        verify(listNotificationsUseCase).execute(query.capture());
        assertThat(query.getValue().onlyUnread()).isFalse();
    }

    @Test
    @DisplayName("the bell badge count is served")
    void shouldReturnUnreadCount() {
        when(countUnreadNotificationsUseCase.countUnread(USER_ID)).thenReturn(7L);

        given()
            .cookie("dev_session", EMAIL)
        .when()
            .get("/api/v1/notifications/unread-count")
        .then()
            .statusCode(200)
            .body("unread", equalTo(7));
    }

    @Test
    @DisplayName("fetches one notification by id")
    void shouldGetNotificationById() {
        NotificationId id = NotificationId.generate();
        when(getNotificationUseCase.execute(any(NotificationId.class), any(UserId.class)))
                .thenReturn(notification(id, NotificationStatus.READ));

        given()
            .cookie("dev_session", EMAIL)
        .when()
            .get("/api/v1/notifications/" + id.value())
        .then()
            .statusCode(200)
            .body("id", equalTo(id.value().toString()))
            .body("status", equalTo("READ"));
    }

    @Test
    @DisplayName("someone else's notification reads as absent, not as forbidden")
    void shouldHideAnotherUsersNotificationBehindNotFound() {
        when(getNotificationUseCase.execute(any(NotificationId.class), any(UserId.class)))
                .thenThrow(new SecurityException("belongs to another user"));

        given()
            .cookie("dev_session", EMAIL)
        .when()
            .get("/api/v1/notifications/" + UUID.randomUUID())
        .then()
            // 404 rather than 403 on purpose: a 403 would confirm the id exists, turning the
            // endpoint into an oracle for other users' notification ids.
            .statusCode(404);
    }

    @Test
    @DisplayName("a malformed id is a 404, not a 500")
    void shouldRejectAMalformedIdCleanly() {
        given()
            .cookie("dev_session", EMAIL)
        .when()
            .get("/api/v1/notifications/not-a-uuid")
        .then()
            .statusCode(404);

        verify(getNotificationUseCase, never()).execute(any(), any());
    }

    @Test
    @DisplayName("marking one read returns no content and carries the owner")
    void shouldMarkOneRead() {
        NotificationId id = NotificationId.generate();

        given()
            .cookie("dev_session", EMAIL)
        .when()
            .post("/api/v1/notifications/" + id.value() + "/read")
        .then()
            .statusCode(204);

        ArgumentCaptor<MarkNotificationReadCommand> command =
                ArgumentCaptor.forClass(MarkNotificationReadCommand.class);
        verify(markNotificationReadUseCase).execute(command.capture());
        assertThat(command.getValue().userId()).isEqualTo(USER_ID);
        assertThat(command.getValue().notificationId().value()).isEqualTo(id.value());
    }

    @Test
    @DisplayName("marking an unknown notification read is a 404")
    void shouldReturnNotFoundWhenMarkingAMissingNotification() {
        org.mockito.Mockito.doThrow(new NoSuchElementException("gone"))
                .when(markNotificationReadUseCase).execute(any(MarkNotificationReadCommand.class));

        given()
            .cookie("dev_session", EMAIL)
        .when()
            .post("/api/v1/notifications/" + UUID.randomUUID() + "/read")
        .then()
            .statusCode(404);
    }

    @Test
    @DisplayName("mark-all reports how many it changed")
    void shouldMarkAllRead() {
        when(markAllNotificationsReadUseCase.execute(USER_ID)).thenReturn(4);

        given()
            .cookie("dev_session", EMAIL)
        .when()
            .post("/api/v1/notifications/read-all")
        .then()
            .statusCode(200)
            .body("unread", equalTo(4));
    }

    @Test
    @DisplayName("the inbox is not public")
    void shouldRequireAuthentication() {
        given()
        .when()
            .get("/api/v1/notifications")
        .then()
            .statusCode(401);
    }
}
