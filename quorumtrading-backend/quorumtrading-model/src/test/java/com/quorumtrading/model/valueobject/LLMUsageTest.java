package com.quorumtrading.model.valueobject;

import com.quorumtrading.model.service.PlatformMarkup;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

class LLMUsageTest {

    // Non-trivial rate so the markup multiplication stays observable in assertions
    private static final BigDecimal MARKUP_RATE = new BigDecimal("1.50");

    @BeforeEach
    void setUp() {
        PlatformMarkup.configure(MARKUP_RATE);
    }

    @AfterEach
    void tearDown() {
        PlatformMarkup.reset();
    }

    @Test
    void billedCostShouldEnforceMinimumFloorForTinyCosts() {
        // A rawCost so tiny that after markup it would be below MINIMUM_BILLABLE_COST
        LLMUsage usage = LLMUsage.of("test-model", 10, 5, new BigDecimal("0.0000001"));

        BigDecimal billed = usage.billedCost();

        // 0.0000001 * 1.50 = 0.00000015, which is below 0.000001
        assertThat(billed).isEqualByComparingTo(LLMUsage.MINIMUM_BILLABLE_COST);
    }

    @Test
    void billedCostShouldReturnZeroForZeroRawCost() {
        LLMUsage usage = LLMUsage.of("test-model", 10, 5, BigDecimal.ZERO);

        BigDecimal billed = usage.billedCost();

        assertThat(billed).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void billedCostShouldNotAffectNormalCosts() {
        LLMUsage usage = LLMUsage.of("test-model", 100, 50, new BigDecimal("0.001"));

        BigDecimal billed = usage.billedCost();

        // 0.001 * 1.50 = 0.0015, well above the minimum
        assertThat(billed).isEqualByComparingTo(new BigDecimal("0.0015"));
    }

    @Test
    void billedCostShouldDefaultToRawCostWithoutConfiguration() {
        PlatformMarkup.reset();
        LLMUsage usage = LLMUsage.of("test-model", 100, 50, new BigDecimal("0.001"));

        // Unconfigured (self-hosted) installs bill the provider's own price
        assertThat(usage.billedCost()).isEqualByComparingTo(new BigDecimal("0.001"));
    }

    @Test
    void billedCostShouldEnforceFloorWithCustomMarkupRate() {
        LLMUsage usage = LLMUsage.of("test-model", 10, 5, new BigDecimal("0.0000001"));

        BigDecimal billed = usage.billedCost(new BigDecimal("1.30"));

        // 0.0000001 * 1.30 = 0.00000013, which is below 0.000001
        assertThat(billed).isEqualByComparingTo(LLMUsage.MINIMUM_BILLABLE_COST);
    }

    @Test
    void billedCostAccumulationShouldBeAccurate() {
        // 100,000 calls at $0.001 each should accumulate to $100 raw cost
        LLMUsage accumulated = LLMUsage.ZERO;
        LLMUsage singleCall = LLMUsage.of("test-model", 10, 5, new BigDecimal("0.001"));

        for (int i = 0; i < 100_000; i++) {
            accumulated = accumulated.add(singleCall);
        }

        // rawCost = 100.000, billedCost = 100.000 * 1.50 = 150.000
        assertThat(accumulated.rawCost()).isEqualByComparingTo(new BigDecimal("100"));
        assertThat(accumulated.billedCost()).isEqualByComparingTo(new BigDecimal("150"));
    }

    @Test
    void zeroUsageShouldHaveZeroBilledCost() {
        assertThat(LLMUsage.ZERO.billedCost()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void billedCostAtExactMinimumShouldNotBeRaisedFurther() {
        // A rawCost that produces exactly MINIMUM_BILLABLE_COST after markup
        LLMUsage usage = LLMUsage.of("test-model", 10, 5, new BigDecimal("0.000001"));

        BigDecimal billed = usage.billedCost(BigDecimal.ONE);

        // 0.000001 * 1.0 = 0.000001, exactly at minimum — should pass through
        assertThat(billed).isEqualByComparingTo(LLMUsage.MINIMUM_BILLABLE_COST);
    }
}
