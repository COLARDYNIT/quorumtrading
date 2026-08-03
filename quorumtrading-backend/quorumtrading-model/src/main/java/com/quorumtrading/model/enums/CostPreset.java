package com.quorumtrading.model.enums;

import java.math.BigDecimal;

public enum CostPreset {
    ZERO_FEES(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ONE),
    TYPICAL_RETAIL(BigDecimal.ZERO, new BigDecimal("0.0025"), new BigDecimal("0.0005"), new BigDecimal("0.00001"), new BigDecimal("1.5")),
    HIGH_FRICTION(new BigDecimal("0.005"), new BigDecimal("0.005"), new BigDecimal("0.002"), new BigDecimal("0.0001"), new BigDecimal("2.0")),
    CUSTOM(null, null, null, null, null);

    private final BigDecimal commissionRate;
    private final BigDecimal cryptoCommissionRate;
    private final BigDecimal slippageBase;
    private final BigDecimal slippageImpactPerUnit;
    private final BigDecimal slippageVolatilityMultiplier;

    CostPreset(BigDecimal commissionRate, BigDecimal cryptoCommissionRate, BigDecimal slippageBase,
               BigDecimal slippageImpactPerUnit, BigDecimal slippageVolatilityMultiplier) {
        this.commissionRate = commissionRate;
        this.cryptoCommissionRate = cryptoCommissionRate;
        this.slippageBase = slippageBase;
        this.slippageImpactPerUnit = slippageImpactPerUnit;
        this.slippageVolatilityMultiplier = slippageVolatilityMultiplier;
    }

    public BigDecimal getCommissionRate() {
        return commissionRate;
    }

    public BigDecimal getCryptoCommissionRate() {
        return cryptoCommissionRate;
    }

    /**
     * Returns the commission rate for the given investment type.
     * Stocks use commissionRate, crypto uses cryptoCommissionRate.
     */
    public BigDecimal getCommissionRate(InvestmentType type) {
        if (this == CUSTOM) {
            return null;
        }
        return type == InvestmentType.CRYPTO ? cryptoCommissionRate : commissionRate;
    }

    public BigDecimal getSlippageBase() {
        return slippageBase;
    }

    public BigDecimal getSlippageImpactPerUnit() {
        return slippageImpactPerUnit;
    }

    public BigDecimal getSlippageVolatilityMultiplier() {
        return slippageVolatilityMultiplier;
    }
}
