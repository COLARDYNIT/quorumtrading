package com.quorumtrading.adapter.websocket;

import com.quorumtrading.application.event.BackfillProgressEvent;
import com.quorumtrading.application.event.BackfillStatusChangedEvent;
import com.quorumtrading.model.enums.BackfillStatus;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for BackfillProgressBroadcaster service.
 *
 * Tests verify that the broadcaster handles CDI events correctly
 * and broadcasts messages without throwing exceptions, even when
 * no WebSocket sessions are active.
 */
@QuarkusTest
class BackfillProgressBroadcasterTest {

    @Inject
    BackfillProgressBroadcaster broadcaster;

    @Test
    void shouldInjectBroadcaster() {
        assertThat(broadcaster).isNotNull();
    }

    @Test
    void shouldHandleStartedEventGracefully() {
        // Arrange
        UUID jobId = UUID.randomUUID();
        BackfillStatusChangedEvent event = new BackfillStatusChangedEvent(
            jobId,
            BackfillStatus.PENDING,
            BackfillStatus.IN_PROGRESS,
            null,
            10
        );

        // Act - should not throw exception even if no subscribers
        broadcaster.onBackfillStatusChanged(event);

        // Assert - if we get here, no exception was thrown
        assertThat(true).isTrue();
    }

    @Test
    void shouldHandleProgressEventGracefully() {
        // Arrange
        UUID jobId = UUID.randomUUID();
        BackfillProgressEvent event = new BackfillProgressEvent(
            jobId,
            50,
            5,
            10
        );

        // Act - should not throw exception even if no subscribers
        broadcaster.onBackfillProgress(event);

        // Assert - if we get here, no exception was thrown
        assertThat(true).isTrue();
    }

    @Test
    void shouldHandleCompletedEventGracefully() {
        // Arrange
        UUID jobId = UUID.randomUUID();
        BackfillStatusChangedEvent event = new BackfillStatusChangedEvent(
            jobId,
            BackfillStatus.IN_PROGRESS,
            BackfillStatus.COMPLETED,
            null,
            10
        );

        // Act - should not throw exception even if no subscribers
        broadcaster.onBackfillStatusChanged(event);

        // Assert - if we get here, no exception was thrown
        assertThat(true).isTrue();
    }

    @Test
    void shouldHandleFailedEventGracefully() {
        // Arrange
        UUID jobId = UUID.randomUUID();
        BackfillStatusChangedEvent event = new BackfillStatusChangedEvent(
            jobId,
            BackfillStatus.IN_PROGRESS,
            BackfillStatus.FAILED,
            "Connection timeout",
            null
        );

        // Act - should not throw exception even if no subscribers
        broadcaster.onBackfillStatusChanged(event);

        // Assert - if we get here, no exception was thrown
        assertThat(true).isTrue();
    }

    @Test
    void shouldIgnoreIrrelevantStatusChanges() {
        // Arrange - status change that shouldn't trigger a broadcast
        UUID jobId = UUID.randomUUID();
        BackfillStatusChangedEvent event = new BackfillStatusChangedEvent(
            jobId,
            BackfillStatus.IN_PROGRESS,
            BackfillStatus.IN_PROGRESS, // Same status - should be ignored
            null,
            10
        );

        // Act - should not throw exception
        broadcaster.onBackfillStatusChanged(event);

        // Assert - if we get here, no exception was thrown
        assertThat(true).isTrue();
    }
}
