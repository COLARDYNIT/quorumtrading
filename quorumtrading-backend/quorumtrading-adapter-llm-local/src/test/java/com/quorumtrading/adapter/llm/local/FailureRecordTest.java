package com.quorumtrading.adapter.llm.local;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link FailureRecord}.
 * <p>
 * Tests validation logic in compact constructor and behavior methods.
 * </p>
 *
 * @author QuorumTrading
 * @since 1.0.0
 */
class FailureRecordTest {

    private static final String VALID_TOOL_NAME = "qwen";
    private static final int VALID_FAILURE_COUNT = 1;
    private static final Instant VALID_LAST_FAILURE = Instant.parse("2025-11-07T10:00:00Z");
    private static final long VALID_BACKOFF_MINUTES = 5;

    // --- Validation Tests ---

    @Test
    void constructor_shouldAcceptValidParameters() {
        // Act
        FailureRecord record = new FailureRecord(
            VALID_TOOL_NAME,
            VALID_FAILURE_COUNT,
            VALID_LAST_FAILURE,
            VALID_BACKOFF_MINUTES
        );

        // Assert
        assertThat(record.toolName()).isEqualTo(VALID_TOOL_NAME);
        assertThat(record.failureCount()).isEqualTo(VALID_FAILURE_COUNT);
        assertThat(record.lastFailureTime()).isEqualTo(VALID_LAST_FAILURE);
        assertThat(record.currentBackoffMinutes()).isEqualTo(VALID_BACKOFF_MINUTES);
    }

    @Test
    void constructor_shouldRejectNullToolName() {
        // Act & Assert
        assertThatThrownBy(() -> new FailureRecord(
            null,
            VALID_FAILURE_COUNT,
            VALID_LAST_FAILURE,
            VALID_BACKOFF_MINUTES
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("toolName cannot be null or blank");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "  ", "\t", "\n"})
    void constructor_shouldRejectBlankToolName(String blankName) {
        // Act & Assert
        assertThatThrownBy(() -> new FailureRecord(
            blankName,
            VALID_FAILURE_COUNT,
            VALID_LAST_FAILURE,
            VALID_BACKOFF_MINUTES
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("toolName cannot be null or blank");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, -100})
    void constructor_shouldRejectInvalidFailureCount(int invalidCount) {
        // Act & Assert
        assertThatThrownBy(() -> new FailureRecord(
            VALID_TOOL_NAME,
            invalidCount,
            VALID_LAST_FAILURE,
            VALID_BACKOFF_MINUTES
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("failureCount must be at least 1");
    }

    @Test
    void constructor_shouldRejectNullLastFailureTime() {
        // Act & Assert
        assertThatThrownBy(() -> new FailureRecord(
            VALID_TOOL_NAME,
            VALID_FAILURE_COUNT,
            null,
            VALID_BACKOFF_MINUTES
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("lastFailureTime cannot be null");
    }

    @ParameterizedTest
    @ValueSource(longs = {-1, -5, -1000})
    void constructor_shouldRejectNegativeBackoffMinutes(long negativeBackoff) {
        // Act & Assert
        assertThatThrownBy(() -> new FailureRecord(
            VALID_TOOL_NAME,
            VALID_FAILURE_COUNT,
            VALID_LAST_FAILURE,
            negativeBackoff
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("currentBackoffMinutes must be non-negative");
    }

    @Test
    void constructor_shouldAcceptZeroBackoffMinutes() {
        // Act
        FailureRecord record = new FailureRecord(
            VALID_TOOL_NAME,
            VALID_FAILURE_COUNT,
            VALID_LAST_FAILURE,
            0L
        );

        // Assert
        assertThat(record.currentBackoffMinutes()).isZero();
    }

    // --- isBlacklisted() Tests ---

    @Test
    void isBlacklisted_shouldReturnTrue_whenWithinBackoffPeriod() {
        // Arrange
        Instant lastFailure = Instant.parse("2025-11-07T10:00:00Z");
        FailureRecord record = new FailureRecord("qwen", 1, lastFailure, 10L);

        // Act: Check 5 minutes after failure (still within 10-minute backoff)
        Instant checkTime = lastFailure.plusSeconds(5 * 60);
        boolean isBlacklisted = record.isBlacklisted(checkTime);

        // Assert
        assertThat(isBlacklisted).isTrue();
    }

    @Test
    void isBlacklisted_shouldReturnFalse_whenBackoffExpired() {
        // Arrange
        Instant lastFailure = Instant.parse("2025-11-07T10:00:00Z");
        FailureRecord record = new FailureRecord("qwen", 1, lastFailure, 10L);

        // Act: Check 11 minutes after failure (past 10-minute backoff)
        Instant checkTime = lastFailure.plusSeconds(11 * 60);
        boolean isBlacklisted = record.isBlacklisted(checkTime);

        // Assert
        assertThat(isBlacklisted).isFalse();
    }

    @Test
    void isBlacklisted_shouldReturnFalse_whenExactlyAtExpirationTime() {
        // Arrange
        Instant lastFailure = Instant.parse("2025-11-07T10:00:00Z");
        FailureRecord record = new FailureRecord("qwen", 1, lastFailure, 10L);

        // Act: Check exactly at 10 minutes after failure
        Instant checkTime = lastFailure.plusSeconds(10 * 60);
        boolean isBlacklisted = record.isBlacklisted(checkTime);

        // Assert - at exact expiration time, not before it anymore
        assertThat(isBlacklisted).isFalse();
    }

    // --- minutesUntilExpiration() Tests ---

    @Test
    void minutesUntilExpiration_shouldReturnZero_whenAlreadyExpired() {
        // Arrange
        Instant lastFailure = Instant.parse("2025-11-07T10:00:00Z");
        FailureRecord record = new FailureRecord("qwen", 1, lastFailure, 10L);

        // Act: Check 11 minutes after failure
        Instant checkTime = lastFailure.plusSeconds(11 * 60);
        long minutesRemaining = record.minutesUntilExpiration(checkTime);

        // Assert
        assertThat(minutesRemaining).isZero();
    }

    @Test
    void minutesUntilExpiration_shouldReturnRemainingMinutes_whenStillBlacklisted() {
        // Arrange
        Instant lastFailure = Instant.parse("2025-11-07T10:00:00Z");
        FailureRecord record = new FailureRecord("qwen", 1, lastFailure, 10L);

        // Act: Check 5 minutes after failure (5 minutes remaining)
        Instant checkTime = lastFailure.plusSeconds(5 * 60);
        long minutesRemaining = record.minutesUntilExpiration(checkTime);

        // Assert
        assertThat(minutesRemaining).isEqualTo(5);
    }

    @Test
    void minutesUntilExpiration_shouldRoundUp_whenPartialMinuteRemains() {
        // Arrange
        Instant lastFailure = Instant.parse("2025-11-07T10:00:00Z");
        FailureRecord record = new FailureRecord("qwen", 1, lastFailure, 10L);

        // Act: Check 9 minutes 1 second after failure (should round up to 1 minute)
        Instant checkTime = lastFailure.plusSeconds(9 * 60 + 1);
        long minutesRemaining = record.minutesUntilExpiration(checkTime);

        // Assert: 59 seconds remaining should round up to 1 minute
        assertThat(minutesRemaining).isEqualTo(1);
    }

    @Test
    void minutesUntilExpiration_shouldNotRoundUp_whenExactMinuteRemains() {
        // Arrange
        Instant lastFailure = Instant.parse("2025-11-07T10:00:00Z");
        FailureRecord record = new FailureRecord("qwen", 1, lastFailure, 10L);

        // Act: Check 9 minutes exactly after failure (1 minute remains)
        Instant checkTime = lastFailure.plusSeconds(9 * 60);
        long minutesRemaining = record.minutesUntilExpiration(checkTime);

        // Assert: Exactly 1 minute should round to 1 minute
        assertThat(minutesRemaining).isEqualTo(1);
    }

    @Test
    void minutesUntilExpiration_shouldHandleSmallRemainingSeconds() {
        // Arrange
        Instant lastFailure = Instant.parse("2025-11-07T10:00:00Z");
        FailureRecord record = new FailureRecord("qwen", 1, lastFailure, 10L);

        // Act: Check 9 minutes 59 seconds after failure (1 second remains)
        Instant checkTime = lastFailure.plusSeconds(9 * 60 + 59);
        long minutesRemaining = record.minutesUntilExpiration(checkTime);

        // Assert: 1 second remaining should round up to 1 minute
        assertThat(minutesRemaining).isEqualTo(1);
    }

    @Test
    void minutesUntilExpiration_shouldHandleZeroBackoffMinutes() {
        // Arrange: Zero backoff means immediate expiration
        Instant lastFailure = Instant.parse("2025-11-07T10:00:00Z");
        FailureRecord record = new FailureRecord("qwen", 1, lastFailure, 0L);

        // Act: Check immediately after failure
        long minutesRemaining = record.minutesUntilExpiration(lastFailure);

        // Assert: Zero backoff means already expired
        assertThat(minutesRemaining).isZero();
    }
}
