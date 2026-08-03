package com.quorumtrading.model.valueobject;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class IndicatorValueTest {

    @Test
    void shouldCreateMovingAverageValue() {
        var ma = new IndicatorValue.MovingAverageValue(
            new BigDecimal("185.42"),
            new BigDecimal("178.91")
        );
        assertEquals(new BigDecimal("185.42"), ma.sma50());
        assertEquals(new BigDecimal("178.91"), ma.sma200());
    }

    @Test
    void shouldAllowNullSma200WhenInsufficientData() {
        var ma = new IndicatorValue.MovingAverageValue(
            new BigDecimal("185.42"),
            null  // Only 150 days of data
        );
        assertEquals(new BigDecimal("185.42"), ma.sma50());
        assertNull(ma.sma200());
    }

    @Test
    void shouldCreateRSIValue() {
        var rsi = new IndicatorValue.RSIValue(new BigDecimal("62.3"));
        assertEquals(new BigDecimal("62.3"), rsi.rsi14());
    }

    @Test
    void shouldCreateMACDValue() {
        var macd = new IndicatorValue.MACDValue(
            new BigDecimal("2.15"),
            new BigDecimal("1.89"),
            new BigDecimal("0.26")
        );
        assertEquals(new BigDecimal("2.15"), macd.macdLine());
        assertEquals(new BigDecimal("1.89"), macd.signalLine());
        assertEquals(new BigDecimal("0.26"), macd.histogram());
    }

    @Test
    void shouldCreateBollingerValue() {
        var bb = new IndicatorValue.BollingerValue(
            new BigDecimal("192.10"),
            new BigDecimal("185.00"),
            new BigDecimal("177.90")
        );
        assertEquals(new BigDecimal("192.10"), bb.upper());
        assertEquals(new BigDecimal("185.00"), bb.middle());
        assertEquals(new BigDecimal("177.90"), bb.lower());
    }
}
