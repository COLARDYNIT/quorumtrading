package com.quorumtrading.model.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

class PlatformMarkupTest {

    @AfterEach
    void tearDown() {
        PlatformMarkup.reset();
    }

    @Test
    void defaultRateIsNoMarkup() {
        PlatformMarkup.reset();
        assertThat(PlatformMarkup.rate()).isEqualByComparingTo(BigDecimal.ONE);
    }

    @Test
    void configureChangesTheRate() {
        PlatformMarkup.configure(new BigDecimal("1.25"));
        assertThat(PlatformMarkup.rate()).isEqualByComparingTo(new BigDecimal("1.25"));
    }

    @Test
    void configureAcceptsExactlyOne() {
        PlatformMarkup.configure(BigDecimal.ONE);
        assertThat(PlatformMarkup.rate()).isEqualByComparingTo(BigDecimal.ONE);
    }

    @Test
    void configureRejectsNull() {
        assertThatThrownBy(() -> PlatformMarkup.configure(null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void configureRejectsRateBelowOne() {
        // Billing below the provider's own price is never intended
        assertThatThrownBy(() -> PlatformMarkup.configure(new BigDecimal("0.99")))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void resetRestoresTheDefault() {
        PlatformMarkup.configure(new BigDecimal("2.00"));
        PlatformMarkup.reset();
        assertThat(PlatformMarkup.rate()).isEqualByComparingTo(BigDecimal.ONE);
    }
}
