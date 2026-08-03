package com.quorumtrading.model.valueobject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("LLMPricing Value Object Tests")
class LLMPricingTest {

    @Test
    @DisplayName("FREE constant should have all zero costs")
    void freeConstantShouldHaveZeroCosts() {
        LLMPricing free = LLMPricing.FREE;

        assertEquals(BigDecimal.ZERO, free.promptCostPerToken());
        assertEquals(BigDecimal.ZERO, free.completionCostPerToken());
        assertEquals(BigDecimal.ZERO, free.requestCost());
        assertEquals(BigDecimal.ZERO, free.imageCost());
        assertTrue(free.isFree());
    }

    @Test
    @DisplayName("of() factory should create pricing with prompt and completion costs")
    void ofFactoryShouldCreatePricing() {
        BigDecimal promptCost = new BigDecimal("0.000005");
        BigDecimal completionCost = new BigDecimal("0.000015");

        LLMPricing pricing = LLMPricing.of(promptCost, completionCost);

        assertEquals(promptCost, pricing.promptCostPerToken());
        assertEquals(completionCost, pricing.completionCostPerToken());
        assertEquals(BigDecimal.ZERO, pricing.requestCost());
        assertFalse(pricing.isFree());
    }

    @Test
    @DisplayName("fromStrings() should parse string values correctly")
    void fromStringsShouldParseValues() {
        LLMPricing pricing = LLMPricing.fromStrings(
            "0.000005",
            "0.000015",
            "0.01",
            "0.002"
        );

        assertEquals(new BigDecimal("0.000005"), pricing.promptCostPerToken());
        assertEquals(new BigDecimal("0.000015"), pricing.completionCostPerToken());
        assertEquals(new BigDecimal("0.01"), pricing.requestCost());
        assertEquals(new BigDecimal("0.002"), pricing.imageCost());
    }

    @Test
    @DisplayName("fromStrings() should handle null and empty values")
    void fromStringsShouldHandleNullAndEmpty() {
        LLMPricing pricing = LLMPricing.fromStrings(null, "", "  ", null);

        assertEquals(BigDecimal.ZERO, pricing.promptCostPerToken());
        assertEquals(BigDecimal.ZERO, pricing.completionCostPerToken());
        assertEquals(BigDecimal.ZERO, pricing.requestCost());
        assertEquals(BigDecimal.ZERO, pricing.imageCost());
        assertTrue(pricing.isFree());
    }

    @Test
    @DisplayName("fromStrings() should handle invalid number format")
    void fromStringsShouldHandleInvalidFormat() {
        LLMPricing pricing = LLMPricing.fromStrings("abc", "not-a-number", "123", "456");

        assertEquals(BigDecimal.ZERO, pricing.promptCostPerToken());
        assertEquals(BigDecimal.ZERO, pricing.completionCostPerToken());
        assertEquals(new BigDecimal("123"), pricing.requestCost());
        assertEquals(new BigDecimal("456"), pricing.imageCost());
    }

    @Test
    @DisplayName("estimateCost() should calculate total correctly")
    void estimateCostShouldCalculateTotal() {
        LLMPricing pricing = LLMPricing.of(
            new BigDecimal("0.000005"),  // $5/million tokens
            new BigDecimal("0.000015")   // $15/million tokens
        );

        // 1000 prompt + 500 completion
        BigDecimal estimated = pricing.estimateCost(1000, 500);

        // 1000 * 0.000005 + 500 * 0.000015 = 0.005 + 0.0075 = 0.0125
        assertEquals(new BigDecimal("0.012500"), estimated);
    }

    @Test
    @DisplayName("estimateCost() should include request cost")
    void estimateCostShouldIncludeRequestCost() {
        LLMPricing pricing = new LLMPricing(
            new BigDecimal("0.000005"),
            new BigDecimal("0.000015"),
            new BigDecimal("0.01"), // Fixed request cost
            BigDecimal.ZERO
        );

        BigDecimal estimated = pricing.estimateCost(1000, 500);

        // 0.005 + 0.0075 + 0.01 = 0.0225
        assertEquals(new BigDecimal("0.022500"), estimated);
    }

    @Test
    @DisplayName("promptCostPerMillion() should return scaled value")
    void promptCostPerMillionShouldReturnScaled() {
        LLMPricing pricing = LLMPricing.of(
            new BigDecimal("0.000005"),  // $5/million
            new BigDecimal("0.000015")
        );

        assertEquals(new BigDecimal("5.0000"), pricing.promptCostPerMillion());
    }

    @Test
    @DisplayName("completionCostPerMillion() should return scaled value")
    void completionCostPerMillionShouldReturnScaled() {
        LLMPricing pricing = LLMPricing.of(
            new BigDecimal("0.000005"),
            new BigDecimal("0.000015")  // $15/million
        );

        assertEquals(new BigDecimal("15.0000"), pricing.completionCostPerMillion());
    }

    @Test
    @DisplayName("isFree() should return false if any cost is non-zero")
    void isFreeShouldReturnFalseIfAnyCostNonZero() {
        LLMPricing withPromptCost = new LLMPricing(
            new BigDecimal("0.001"),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO
        );
        assertFalse(withPromptCost.isFree());

        LLMPricing withRequestCost = new LLMPricing(
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            new BigDecimal("0.01"),
            BigDecimal.ZERO
        );
        assertFalse(withRequestCost.isFree());
    }

    @Test
    @DisplayName("record should handle null parameters by defaulting to zero")
    void recordShouldHandleNullParameters() {
        LLMPricing pricing = new LLMPricing(null, null, null, null);

        assertEquals(BigDecimal.ZERO, pricing.promptCostPerToken());
        assertEquals(BigDecimal.ZERO, pricing.completionCostPerToken());
        assertEquals(BigDecimal.ZERO, pricing.requestCost());
        assertEquals(BigDecimal.ZERO, pricing.imageCost());
    }

    // ===== withMarkup() Tests =====

    @Test
    @DisplayName("withMarkup() should multiply all costs by the given rate")
    void withMarkupShouldMultiplyAllCosts() {
        LLMPricing pricing = new LLMPricing(
            new BigDecimal("0.000005"),
            new BigDecimal("0.000015"),
            new BigDecimal("0.01"),
            new BigDecimal("0.002")
        );

        LLMPricing marked = pricing.withMarkup(new BigDecimal("1.30"));

        assertEquals(0, new BigDecimal("0.0000065").compareTo(marked.promptCostPerToken()));
        assertEquals(0, new BigDecimal("0.0000195").compareTo(marked.completionCostPerToken()));
        assertEquals(0, new BigDecimal("0.013").compareTo(marked.requestCost()));
        assertEquals(0, new BigDecimal("0.0026").compareTo(marked.imageCost()));
    }

    @Test
    @DisplayName("withMarkup() with rate 1.0 should return same instance")
    void withMarkupRateOneShouldReturnSameInstance() {
        LLMPricing pricing = LLMPricing.of(
            new BigDecimal("0.000005"),
            new BigDecimal("0.000015")
        );

        LLMPricing result = pricing.withMarkup(BigDecimal.ONE);

        assertSame(pricing, result);
    }

    @Test
    @DisplayName("withMarkup() with null rate should return same instance")
    void withMarkupNullRateShouldReturnSameInstance() {
        LLMPricing pricing = LLMPricing.of(
            new BigDecimal("0.000005"),
            new BigDecimal("0.000015")
        );

        LLMPricing result = pricing.withMarkup(null);

        assertSame(pricing, result);
    }

    @Test
    @DisplayName("withMarkup() on FREE pricing should return all zeros")
    void withMarkupOnFreeShouldReturnZeros() {
        LLMPricing marked = LLMPricing.FREE.withMarkup(new BigDecimal("1.30"));

        assertEquals(0, marked.promptCostPerToken().compareTo(BigDecimal.ZERO));
        assertEquals(0, marked.completionCostPerToken().compareTo(BigDecimal.ZERO));
        assertEquals(0, marked.requestCost().compareTo(BigDecimal.ZERO));
        assertEquals(0, marked.imageCost().compareTo(BigDecimal.ZERO));
    }

    @Test
    @DisplayName("withMarkup() should produce correct per-million costs")
    void withMarkupShouldProduceCorrectPerMillionCosts() {
        LLMPricing pricing = LLMPricing.of(
            new BigDecimal("0.000003"),  // $3/million
            new BigDecimal("0.000015")   // $15/million
        );

        LLMPricing marked = pricing.withMarkup(new BigDecimal("1.30"));

        // $3 * 1.30 = $3.90, $15 * 1.30 = $19.50
        assertEquals(new BigDecimal("3.9000"), marked.promptCostPerMillion());
        assertEquals(new BigDecimal("19.5000"), marked.completionCostPerMillion());
    }
}
