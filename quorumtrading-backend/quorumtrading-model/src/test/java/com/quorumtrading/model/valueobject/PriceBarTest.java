package com.quorumtrading.model.valueobject;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class PriceBarTest {

    @Test
    void shouldCreatePriceBarWithAllFields() {
        Instant timestamp = Instant.parse("2024-11-24T10:00:00Z");
        PriceBar bar = new PriceBar(
            timestamp,
            new BigDecimal("100.00"),
            new BigDecimal("105.00"),
            new BigDecimal("99.00"),
            new BigDecimal("103.00"),
            new BigDecimal("1000000")
        );

        assertEquals(timestamp, bar.timestamp());
        assertEquals(new BigDecimal("100.00"), bar.open());
        assertEquals(new BigDecimal("105.00"), bar.high());
        assertEquals(new BigDecimal("99.00"), bar.low());
        assertEquals(new BigDecimal("103.00"), bar.close());
        assertEquals(new BigDecimal("1000000"), bar.volume());
    }

    @Test
    void shouldRejectNullTimestamp() {
        assertThrows(NullPointerException.class, () ->
            new PriceBar(null, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE)
        );
    }

    @Test
    void shouldRejectNullClose() {
        assertThrows(NullPointerException.class, () ->
            new PriceBar(Instant.now(), BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, null, BigDecimal.ONE)
        );
    }
}
