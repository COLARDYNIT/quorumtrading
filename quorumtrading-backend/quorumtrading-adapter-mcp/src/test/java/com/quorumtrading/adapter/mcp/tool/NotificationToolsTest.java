package com.quorumtrading.adapter.mcp.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quorumtrading.adapter.mcp.auth.McpUserContext;
import com.quorumtrading.application.usecase.command.MarkNotificationReadCommand;
import com.quorumtrading.application.usecase.command.UpdateNotificationPreferenceCommand;
import com.quorumtrading.application.usecase.notification.CountUnreadNotificationsUseCase;
import com.quorumtrading.application.usecase.notification.GetNotificationPreferencesUseCase;
import com.quorumtrading.application.usecase.notification.GetNotificationUseCase;
import com.quorumtrading.application.usecase.notification.ListNotificationsUseCase;
import com.quorumtrading.application.usecase.notification.MarkAllNotificationsReadUseCase;
import com.quorumtrading.application.usecase.notification.MarkNotificationReadUseCase;
import com.quorumtrading.application.usecase.notification.UpdateNotificationPreferenceUseCase;
import com.quorumtrading.application.usecase.query.ListNotificationsQuery;
import com.quorumtrading.model.entity.Notification;
import com.quorumtrading.model.entity.NotificationPreference;
import com.quorumtrading.model.enums.NotificationChannel;
import com.quorumtrading.model.enums.NotificationStatus;
import com.quorumtrading.model.enums.NotificationType;
import com.quorumtrading.model.valueobject.NotificationId;
import com.quorumtrading.model.valueobject.UserId;
import io.quarkiverse.mcp.server.ToolResponse;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The seven notification MCP tools, which had no test.
 *
 * <p>Notifications are how an agent learns that something happened while it was not looking — a
 * recurring investment fired, a simulation finished, a budget ran out. The preference tools are
 * also the only way to turn email off from an agent session, and an unrecognised channel quietly
 * dropped would leave someone still receiving mail they asked to stop.</p>
 */
@QuarkusTest
@DisplayName("NotificationTools")
class NotificationToolsTest {

    @Inject NotificationTools tools;

    @InjectMock ListNotificationsUseCase listNotificationsUseCase;
    @InjectMock GetNotificationUseCase getNotificationUseCase;
    @InjectMock MarkNotificationReadUseCase markNotificationReadUseCase;
    @InjectMock MarkAllNotificationsReadUseCase markAllNotificationsReadUseCase;
    @InjectMock CountUnreadNotificationsUseCase countUnreadNotificationsUseCase;
    @InjectMock GetNotificationPreferencesUseCase getNotificationPreferencesUseCase;
    @InjectMock UpdateNotificationPreferenceUseCase updateNotificationPreferenceUseCase;
    @InjectMock McpUserContext mcpUserContext;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final UserId USER_ID = UserId.generate();

    @BeforeEach
    void setUp() {
        when(mcpUserContext.getUserId()).thenReturn(USER_ID);
    }

    private Notification notification(NotificationId id) {
        return Notification.reconstitute(
                id, USER_ID, NotificationType.INVESTMENT_EXECUTED, NotificationChannel.IN_APP,
                "Contribution invested", "$250 went into BTC Momentum",
                Map.of(), UUID.randomUUID(), NotificationStatus.DELIVERED,
                Instant.parse("2026-06-01T06:00:00Z"), Instant.parse("2026-06-01T06:00:01Z"),
                null, null);
    }

    private JsonNode json(ToolResponse response) throws Exception {
        assertThat(response.isError()).isFalse();
        return objectMapper.readTree(response.firstContent().asText().text());
    }

    @Test
    @DisplayName("lists the caller's notifications")
    void shouldListNotifications() throws Exception {
        Notification n = notification(NotificationId.generate());
        when(listNotificationsUseCase.execute(any(ListNotificationsQuery.class))).thenReturn(List.of(n));

        JsonNode result = json(tools.listNotifications(Optional.empty(), Optional.empty()));

        assertThat(result.toString()).contains("Contribution invested");
    }

    @Test
    @DisplayName("status=unread narrows the query rather than being ignored")
    void shouldPassTheUnreadFilterThrough() {
        when(listNotificationsUseCase.execute(any(ListNotificationsQuery.class))).thenReturn(List.of());

        tools.listNotifications(Optional.of("unread"), Optional.of(10));

        ArgumentCaptor<ListNotificationsQuery> query = ArgumentCaptor.forClass(ListNotificationsQuery.class);
        verify(listNotificationsUseCase).execute(query.capture());
        assertThat(query.getValue().onlyUnread()).isTrue();
        assertThat(query.getValue().limit()).isEqualTo(10);
        assertThat(query.getValue().userId()).isEqualTo(USER_ID);
    }

    @Test
    @DisplayName("'all' means everything, not unread")
    void shouldTreatAllAsNoFilter() {
        when(listNotificationsUseCase.execute(any(ListNotificationsQuery.class))).thenReturn(List.of());

        tools.listNotifications(Optional.of("all"), Optional.empty());

        ArgumentCaptor<ListNotificationsQuery> query = ArgumentCaptor.forClass(ListNotificationsQuery.class);
        verify(listNotificationsUseCase).execute(query.capture());
        assertThat(query.getValue().onlyUnread()).isFalse();
    }

    @Test
    @DisplayName("UNREAD in any case still narrows — case-insensitivity is part of the contract")
    void shouldAcceptUppercaseUnread() {
        when(listNotificationsUseCase.execute(any(ListNotificationsQuery.class))).thenReturn(List.of());

        tools.listNotifications(Optional.of("UNREAD"), Optional.empty());

        ArgumentCaptor<ListNotificationsQuery> query = ArgumentCaptor.forClass(ListNotificationsQuery.class);
        verify(listNotificationsUseCase).execute(query.capture());
        assertThat(query.getValue().onlyUnread()).isTrue();
    }

    @Test
    @DisplayName("status='read' is refused rather than silently meaning 'all'")
    void shouldRejectReadAsAStatus() {
        ToolResponse response = tools.listNotifications(Optional.of("read"), Optional.empty());

        assertThat(response.isError()).isTrue();
        assertThat(response.firstContent().asText().text())
                .isEqualTo("Invalid status 'read'. Valid values: UNREAD, ALL");
        verify(listNotificationsUseCase, never()).execute(any());
    }

    @Test
    @DisplayName("an unrecognised status is refused, the way list_challenges already refuses one")
    void shouldRejectAnUnknownStatus() {
        ToolResponse response = tools.listNotifications(Optional.of("banana"), Optional.empty());

        assertThat(response.isError()).isTrue();
        assertThat(response.firstContent().asText().text())
                .contains("Invalid status 'banana'")
                .contains("UNREAD")
                .contains("ALL");
        verify(listNotificationsUseCase, never()).execute(any());
    }

    @Test
    @DisplayName("a blank status means all, not an error — an unset filter is not a bad filter")
    void shouldTreatBlankStatusAsAll() {
        when(listNotificationsUseCase.execute(any(ListNotificationsQuery.class))).thenReturn(List.of());

        ToolResponse response = tools.listNotifications(Optional.of("  "), Optional.empty());

        assertThat(response.isError()).isFalse();
        ArgumentCaptor<ListNotificationsQuery> query = ArgumentCaptor.forClass(ListNotificationsQuery.class);
        verify(listNotificationsUseCase).execute(query.capture());
        assertThat(query.getValue().onlyUnread()).isFalse();
    }

    // There is deliberately no over-max clamp test here. ListNotificationsQuery's own compact
    // constructor already rewrites anything above 200 down to 200, so such a test passes whether
    // or not the tool clamps and would be worthless as evidence. The clamp is asserted where it
    // can fail instead: McpToolBaseLimitTest, and ListToolLimitContractTest via list_strategies,
    // whose query record does not second-guess the bound.

    @Test
    @DisplayName("the unread count is served")
    void shouldCountUnread() throws Exception {
        when(countUnreadNotificationsUseCase.countUnread(USER_ID)).thenReturn(3L);

        JsonNode result = json(tools.countUnreadNotifications());

        assertThat(result.get("unread").asLong()).isEqualTo(3L);
    }

    @Test
    @DisplayName("one notification is fetched, scoped to its owner")
    void shouldGetOneNotification() throws Exception {
        NotificationId id = NotificationId.generate();
        when(getNotificationUseCase.execute(any(NotificationId.class), any(UserId.class)))
                .thenReturn(notification(id));

        JsonNode result = json(tools.getNotification(id.value().toString()));

        assertThat(result.get("id").asText()).isEqualTo(id.value().toString());
    }

    @Test
    @DisplayName("another user's notification is an error, not data")
    void shouldNotReturnAnotherUsersNotification() {
        when(getNotificationUseCase.execute(any(NotificationId.class), any(UserId.class)))
                .thenThrow(new SecurityException("not yours"));

        assertThat(tools.getNotification(UUID.randomUUID().toString()).isError()).isTrue();
    }

    @Test
    @DisplayName("a malformed id errors without reaching the use case")
    void shouldRejectAMalformedId() {
        assertThat(tools.getNotification("not-a-uuid").isError()).isTrue();
        verify(getNotificationUseCase, never()).execute(any(), any());
    }

    @Test
    @DisplayName("marking one read carries the caller as the owner")
    void shouldMarkOneRead() throws Exception {
        NotificationId id = NotificationId.generate();

        json(tools.markNotificationRead(id.value().toString()));

        ArgumentCaptor<MarkNotificationReadCommand> command =
                ArgumentCaptor.forClass(MarkNotificationReadCommand.class);
        verify(markNotificationReadUseCase).execute(command.capture());
        assertThat(command.getValue().userId()).isEqualTo(USER_ID);
        assertThat(command.getValue().notificationId().value()).isEqualTo(id.value());
    }

    @Test
    @DisplayName("marking an unknown notification read is an error, not a silent success")
    void shouldSurfaceAMissingNotificationOnMarkRead() {
        org.mockito.Mockito.doThrow(new NoSuchElementException("gone"))
                .when(markNotificationReadUseCase).execute(any(MarkNotificationReadCommand.class));

        assertThat(tools.markNotificationRead(UUID.randomUUID().toString()).isError()).isTrue();
    }

    @Test
    @DisplayName("mark-all reports how many it changed")
    void shouldMarkAllRead() throws Exception {
        when(markAllNotificationsReadUseCase.execute(USER_ID)).thenReturn(5);

        JsonNode result = json(tools.markAllNotificationsRead());

        assertThat(result.get("markedRead").asInt()).isEqualTo(5);
    }

    @Test
    @DisplayName("preferences list one row per type")
    void shouldListPreferences() throws Exception {
        NotificationPreference pref = NotificationPreference.create(
                USER_ID, NotificationType.INVESTMENT_EXECUTED,
                EnumSet.of(NotificationChannel.IN_APP, NotificationChannel.EMAIL));
        when(getNotificationPreferencesUseCase.listForUser(USER_ID)).thenReturn(List.of(pref));

        JsonNode result = json(tools.getNotificationPreferences());

        assertThat(result.toString()).contains("INVESTMENT_EXECUTED").contains("EMAIL");
    }

    /**
     * A preference row says which channels the user wants; it says nothing about whether the
     * instance can deliver on them. Outgoing email is off by default in every environment, so
     * listing EMAIL for INVESTMENT_FAILED and BROKER_REAUTH_REQUIRED told a user they would be
     * warned when they would not — and GENERIC is email-only, so it reached them on no channel at
     * all. The DCA scheduler already solves this with automaticExecutionEnabled; this applies the
     * same pattern.
     */
    @Test
    @DisplayName("preferences say whether EMAIL can actually deliver on this instance")
    void shouldDiscloseWhetherEmailDeliveryIsEnabled() throws Exception {
        NotificationPreference pref = NotificationPreference.create(
                USER_ID, NotificationType.INVESTMENT_FAILED,
                EnumSet.of(NotificationChannel.IN_APP, NotificationChannel.EMAIL));
        when(getNotificationPreferencesUseCase.listForUser(USER_ID)).thenReturn(List.of(pref));

        JsonNode result = json(tools.getNotificationPreferences());

        assertThat(result.has("emailDeliveryEnabled"))
                .as("an EMAIL channel is unreadable without knowing whether email is on").isTrue();
        // Email is off in the test profile, as it is by default everywhere.
        assertThat(result.get("emailDeliveryEnabled").asBoolean()).isFalse();
        assertThat(result.get("emailDeliveryNote").asText())
                .contains("delivers nothing");
    }

    @Test
    @DisplayName("updating a preference stores exactly the channels asked for")
    void shouldUpdateAPreference() throws Exception {
        NotificationPreference updated = NotificationPreference.create(
                USER_ID, NotificationType.INVESTMENT_EXECUTED, EnumSet.of(NotificationChannel.IN_APP));
        when(updateNotificationPreferenceUseCase.execute(any(UpdateNotificationPreferenceCommand.class)))
                .thenReturn(updated);

        json(tools.updateNotificationPreference("INVESTMENT_EXECUTED", List.of("IN_APP")));

        ArgumentCaptor<UpdateNotificationPreferenceCommand> command =
                ArgumentCaptor.forClass(UpdateNotificationPreferenceCommand.class);
        verify(updateNotificationPreferenceUseCase).execute(command.capture());
        assertThat(command.getValue().type()).isEqualTo(NotificationType.INVESTMENT_EXECUTED);
        assertThat(command.getValue().channels()).containsExactly(NotificationChannel.IN_APP);
    }

    @Test
    @DisplayName("an empty channel list opts out entirely rather than meaning 'unchanged'")
    void shouldTreatEmptyChannelsAsOptOut() throws Exception {
        NotificationPreference optedOut = NotificationPreference.create(
                USER_ID, NotificationType.INVESTMENT_EXECUTED,
                EnumSet.noneOf(NotificationChannel.class));
        when(updateNotificationPreferenceUseCase.execute(any(UpdateNotificationPreferenceCommand.class)))
                .thenReturn(optedOut);

        json(tools.updateNotificationPreference("INVESTMENT_EXECUTED", List.of()));

        ArgumentCaptor<UpdateNotificationPreferenceCommand> command =
                ArgumentCaptor.forClass(UpdateNotificationPreferenceCommand.class);
        verify(updateNotificationPreferenceUseCase).execute(command.capture());
        assertThat(command.getValue().channels()).isEmpty();
    }

    @Test
    @DisplayName("an unknown channel is refused — dropping it would keep sending mail")
    void shouldRejectAnUnknownChannel() {
        ToolResponse response = tools.updateNotificationPreference(
                "INVESTMENT_EXECUTED", List.of("CARRIER_PIGEON"));

        assertThat(response.isError()).isTrue();
        verify(updateNotificationPreferenceUseCase, never()).execute(any());
    }

    @Test
    @DisplayName("an unknown notification type is refused")
    void shouldRejectAnUnknownType() {
        ToolResponse response = tools.updateNotificationPreference("NOT_A_TYPE", List.of("IN_APP"));

        assertThat(response.isError()).isTrue();
        verify(updateNotificationPreferenceUseCase, never()).execute(any());
    }
}
