package com.quorumtrading.model.marketdata;

import com.quorumtrading.model.enums.BackfillStatus;
import com.quorumtrading.model.enums.EventType;
import com.quorumtrading.model.valueobject.Symbol;
import com.quorumtrading.model.enums.InvestmentType;
import com.quorumtrading.model.valueobject.TimeRange;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BackfillJobTest {

    private static final List<MarketDataGap> GAPS = List.of(
        new MarketDataGap(
            new Symbol("AAPL", InvestmentType.STOCK),
            EventType.PRICE,
            TimeRange.of(java.time.LocalDate.of(2020, 1, 1), java.time.LocalDate.of(2020, 3, 31))
        )
    );

    @Test
    void scheduleRetry_shouldSetStatusToRetryPending() {
        BackfillJob request = new BackfillJob(GAPS);
        request.startProcessing();

        Instant retryTime = Instant.now().plus(10, ChronoUnit.MINUTES);
        request.scheduleRetry(retryTime);

        assertThat(request.getStatus()).isEqualTo(BackfillStatus.RETRY_PENDING);
        assertThat(request.getRetryAfter()).isEqualTo(retryTime);
    }

    @Test
    void isReadyForRetry_shouldReturnFalse_whenRetryTimeInFuture() {
        BackfillJob request = new BackfillJob(GAPS);
        request.startProcessing();

        Instant futureRetry = Instant.now().plus(10, ChronoUnit.MINUTES);
        request.scheduleRetry(futureRetry);

        assertThat(request.isReadyForRetry()).isFalse();
    }

    @Test
    void isReadyForRetry_shouldReturnTrue_whenRetryTimeInPast() {
        BackfillJob request = new BackfillJob(GAPS);
        request.startProcessing();

        Instant pastRetry = Instant.now().minus(1, ChronoUnit.MINUTES);
        request.scheduleRetry(pastRetry);

        assertThat(request.isReadyForRetry()).isTrue();
    }

    @Test
    void isReadyForRetry_shouldReturnFalse_whenNoRetryScheduled() {
        BackfillJob request = new BackfillJob(GAPS);

        assertThat(request.isReadyForRetry()).isFalse();
    }

    @Test
    void startProcessing_shouldWorkFromRetryPending() {
        BackfillJob request = new BackfillJob(GAPS);
        request.startProcessing();
        request.scheduleRetry(Instant.now().minus(1, ChronoUnit.MINUTES));

        assertThat(request.getStatus()).isEqualTo(BackfillStatus.RETRY_PENDING);

        request.startProcessing();

        assertThat(request.getStatus()).isEqualTo(BackfillStatus.IN_PROGRESS);
        assertThat(request.getRetryAfter()).isNull();
    }

    @Test
    void startProcessing_shouldThrow_whenCompleted() {
        BackfillJob request = new BackfillJob(GAPS);
        request.startProcessing();
        request.markCompleted();

        assertThatThrownBy(request::startProcessing)
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void reconstitute_shouldRestoreRetryAfter() {
        Instant retryAfter = Instant.now().plus(5, ChronoUnit.MINUTES);

        BackfillJob request = BackfillJob.reconstitute(
            java.util.UUID.randomUUID(),
            GAPS,
            BackfillStatus.RETRY_PENDING,
            50,
            Instant.now().minus(1, ChronoUnit.HOURS),
            null,
            null,
            10,
            5,
            null,
            retryAfter,
            null
        );

        assertThat(request.getStatus()).isEqualTo(BackfillStatus.RETRY_PENDING);
        assertThat(request.getRetryAfter()).isEqualTo(retryAfter);
    }
}
