package com.quorumtrading.model.enums;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class CostPresetTest {

    @Test
    void shouldHaveExpectedValues() {
        assertEquals(4, CostPreset.values().length);
        assertNotNull(CostPreset.ZERO_FEES);
        assertNotNull(CostPreset.TYPICAL_RETAIL);
        assertNotNull(CostPreset.HIGH_FRICTION);
        assertNotNull(CostPreset.CUSTOM);
    }

    @Test
    void zeroFeesShouldHaveZeroCosts() {
        CostPreset preset = CostPreset.ZERO_FEES;
        assertEquals(BigDecimal.ZERO, preset.getCommissionRate());
        assertEquals(BigDecimal.ZERO, preset.getCryptoCommissionRate());
        assertEquals(BigDecimal.ZERO, preset.getSlippageBase());
        assertEquals(BigDecimal.ZERO, preset.getSlippageImpactPerUnit());
        assertEquals(BigDecimal.ONE, preset.getSlippageVolatilityMultiplier());
    }

    @Test
    void typicalRetailShouldHaveAlpacaAlignedCosts() {
        CostPreset preset = CostPreset.TYPICAL_RETAIL;
        assertEquals(BigDecimal.ZERO, preset.getCommissionRate()); // Stocks: 0% (Alpaca commission-free)
        assertEquals(new BigDecimal("0.0025"), preset.getCryptoCommissionRate()); // Crypto: 0.25% (Alpaca taker tier 1)
        assertEquals(new BigDecimal("0.0005"), preset.getSlippageBase());
        assertEquals(new BigDecimal("0.00001"), preset.getSlippageImpactPerUnit());
        assertEquals(new BigDecimal("1.5"), preset.getSlippageVolatilityMultiplier());
    }

    @Test
    void typicalRetailShouldReturnCorrectRateByAssetType() {
        CostPreset preset = CostPreset.TYPICAL_RETAIL;
        assertEquals(BigDecimal.ZERO, preset.getCommissionRate(InvestmentType.STOCK));
        assertEquals(new BigDecimal("0.0025"), preset.getCommissionRate(InvestmentType.CRYPTO));
    }

    @Test
    void highFrictionShouldHaveExpectedCosts() {
        CostPreset preset = CostPreset.HIGH_FRICTION;
        assertEquals(new BigDecimal("0.005"), preset.getCommissionRate());
        assertEquals(new BigDecimal("0.005"), preset.getCryptoCommissionRate());
        assertEquals(new BigDecimal("0.002"), preset.getSlippageBase());
        assertEquals(new BigDecimal("0.0001"), preset.getSlippageImpactPerUnit());
        assertEquals(new BigDecimal("2.0"), preset.getSlippageVolatilityMultiplier());
    }

    @Test
    void customShouldHaveNullValues() {
        CostPreset preset = CostPreset.CUSTOM;
        assertNull(preset.getCommissionRate());
        assertNull(preset.getCryptoCommissionRate());
        assertNull(preset.getSlippageBase());
        assertNull(preset.getSlippageImpactPerUnit());
        assertNull(preset.getSlippageVolatilityMultiplier());
    }

    @Test
    void customShouldReturnNullForAssetTypeCommission() {
        CostPreset preset = CostPreset.CUSTOM;
        assertNull(preset.getCommissionRate(InvestmentType.STOCK));
        assertNull(preset.getCommissionRate(InvestmentType.CRYPTO));
    }
}
