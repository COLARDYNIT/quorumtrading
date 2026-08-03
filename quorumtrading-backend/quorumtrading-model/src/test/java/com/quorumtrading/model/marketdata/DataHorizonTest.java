package com.quorumtrading.model.marketdata;

import com.quorumtrading.model.enums.EventType;
import com.quorumtrading.model.enums.GranularityBucket;
import com.quorumtrading.model.enums.HorizonSource;
import com.quorumtrading.model.enums.InvestmentType;
import com.quorumtrading.model.valueobject.Symbol;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DataHorizonTest {

    private static final Symbol BTC = new Symbol("BTC", InvestmentType.CRYPTO);
    private static final Instant NOW = Instant.parse("2026-05-31T00:00:00Z");

    private DataHorizon withEarliest(LocalDate earliest) {
        return new DataHorizon("alpaca", BTC, EventType.PRICE, GranularityBucket.DAILY,
                Optional.ofNullable(earliest), NOW, HorizonSource.PROBE);
    }

    @Test
    void hasDataReflectsEarliestPresence() {
        assertThat(withEarliest(LocalDate.of(2024, 7, 1)).hasData()).isTrue();
        assertThat(withEarliest(null).hasData()).isFalse();
    }

    @Test
    void isReachableTrueWhenNoFloorKnown() {
        assertThat(withEarliest(null).isReachable(LocalDate.of(1999, 1, 1))).isTrue();
    }

    @Test
    void isReachableHonoursFloor() {
        DataHorizon h = withEarliest(LocalDate.of(2024, 7, 31));
        assertThat(h.isReachable(LocalDate.of(2024, 7, 31))).isTrue();  // on the floor
        assertThat(h.isReachable(LocalDate.of(2025, 1, 1))).isTrue();   // after
        assertThat(h.isReachable(LocalDate.of(2024, 7, 30))).isFalse(); // before
    }

    @Test
    void isStaleAfterTtlElapses() {
        DataHorizon probed = new DataHorizon("alpaca", BTC, EventType.PRICE, GranularityBucket.DAILY,
                Optional.of(LocalDate.of(2024, 1, 1)),
                Instant.parse("2026-05-01T00:00:00Z"), HorizonSource.PROBE);

        assertThat(probed.isStale(Duration.ofDays(14), NOW)).isTrue();   // 30 days old
        assertThat(probed.isStale(Duration.ofDays(60), NOW)).isFalse();  // within TTL
    }

    @Test
    void rejectsNulls() {
        assertThatThrownBy(() -> new DataHorizon(null, BTC, EventType.PRICE, GranularityBucket.DAILY,
                Optional.empty(), NOW, HorizonSource.PROBE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DataHorizon("alpaca", null, EventType.PRICE, GranularityBucket.DAILY,
                Optional.empty(), NOW, HorizonSource.PROBE))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new DataHorizon("alpaca", BTC, EventType.PRICE, GranularityBucket.DAILY,
                null, NOW, HorizonSource.PROBE))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new DataHorizon("alpaca", BTC, EventType.PRICE, GranularityBucket.DAILY,
                Optional.empty(), null, HorizonSource.PROBE))
                .isInstanceOf(NullPointerException.class);
    }
}
