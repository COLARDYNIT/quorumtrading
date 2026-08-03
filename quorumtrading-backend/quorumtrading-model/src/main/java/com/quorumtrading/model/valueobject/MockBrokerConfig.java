package com.quorumtrading.model.valueobject;

import com.quorumtrading.model.enums.CostPreset;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * Value object that configures mock broker cost parameters.
 * Can be created from a preset or with custom values.
 *
 * <p><b>Fractional shares:</b> {@code fractionalShares} models whether the broker
 * the mock is impersonating fills fractional equity quantities. {@code true} is the
 * historical mock behaviour (Alpaca-flavoured: fractional on every asset class). A
 * Saxo-flavoured mock sets it {@code false} so equity BUY orders are rounded down to
 * whole shares with the cash residue carried, while crypto stays fractional. All
 * factory methods default it to {@code true}; derive a non-fractional config with
 * {@link #withFractionalShares(boolean)}.</p>
 */
public record MockBrokerConfig(
    Money initialCapital,
    BigDecimal commissionRate,
    BigDecimal cryptoCommissionRate,
    BigDecimal slippageBase,
    BigDecimal slippageImpactPerUnit,
    BigDecimal slippageVolatilityMultiplier,
    CostPreset preset,
    boolean fractionalShares
) {
    public MockBrokerConfig {
        Objects.requireNonNull(initialCapital, "initialCapital cannot be null");
        Objects.requireNonNull(commissionRate, "commissionRate cannot be null");
        Objects.requireNonNull(cryptoCommissionRate, "cryptoCommissionRate cannot be null");
        Objects.requireNonNull(slippageBase, "slippageBase cannot be null");
        Objects.requireNonNull(slippageImpactPerUnit, "slippageImpactPerUnit cannot be null");
        Objects.requireNonNull(slippageVolatilityMultiplier, "slippageVolatilityMultiplier cannot be null");
        Objects.requireNonNull(preset, "preset cannot be null");
    }

    /**
     * Creates a MockBrokerConfig from a predefined cost preset.
     *
     * @param initialCapital the initial capital for the mock broker
     * @param preset the cost preset to use (cannot be CUSTOM)
     * @return a new MockBrokerConfig with values from the preset
     * @throws IllegalArgumentException if preset is CUSTOM
     */
    public static MockBrokerConfig fromPreset(Money initialCapital, CostPreset preset) {
        if (preset == CostPreset.CUSTOM) {
            throw new IllegalArgumentException("Use custom() factory for CUSTOM preset");
        }
        return new MockBrokerConfig(
            initialCapital,
            preset.getCommissionRate(),
            preset.getCryptoCommissionRate(),
            preset.getSlippageBase(),
            preset.getSlippageImpactPerUnit(),
            preset.getSlippageVolatilityMultiplier(),
            preset,
            true // Alpaca-flavoured default: fractional shares on every asset class
        );
    }

    /**
     * Creates a MockBrokerConfig with custom cost parameters.
     *
     * @param initialCapital the initial capital for the mock broker
     * @param commissionRate stock commission rate as a decimal (e.g., 0.001 for 0.1%)
     * @param cryptoCommissionRate crypto commission rate as a decimal (e.g., 0.0025 for 0.25%)
     * @param slippageBase base slippage rate as a decimal
     * @param slippageImpactPerUnit market impact per unit traded
     * @param slippageVolatilityMultiplier multiplier for volatility-adjusted slippage
     * @return a new MockBrokerConfig with CUSTOM preset
     */
    public static MockBrokerConfig custom(
        Money initialCapital,
        BigDecimal commissionRate,
        BigDecimal cryptoCommissionRate,
        BigDecimal slippageBase,
        BigDecimal slippageImpactPerUnit,
        BigDecimal slippageVolatilityMultiplier
    ) {
        return new MockBrokerConfig(
            initialCapital,
            commissionRate,
            cryptoCommissionRate,
            slippageBase,
            slippageImpactPerUnit,
            slippageVolatilityMultiplier,
            CostPreset.CUSTOM,
            true // Alpaca-flavoured default: fractional shares on every asset class
        );
    }

    /**
     * Returns a copy of this config with the fractional-share behaviour overridden.
     * Use {@code withFractionalShares(false)} to obtain a Saxo-flavoured mock that
     * fills only whole shares on equities/ETFs (cash residue carried), while crypto
     * remains fractional. All other cost parameters are preserved unchanged.
     *
     * @param fractionalShares whether the mock fills fractional equity quantities
     * @return a new MockBrokerConfig identical except for the fractional-share flag
     */
    public MockBrokerConfig withFractionalShares(boolean fractionalShares) {
        return new MockBrokerConfig(
            initialCapital,
            commissionRate,
            cryptoCommissionRate,
            slippageBase,
            slippageImpactPerUnit,
            slippageVolatilityMultiplier,
            preset,
            fractionalShares
        );
    }

    /**
     * The broker capabilities this mock config impersonates. A fractional config maps
     * to {@link BrokerCapabilities#fractional()} (Alpaca-flavoured); a non-fractional
     * config maps to {@link BrokerCapabilities#wholeShareEquities()} (Saxo-flavoured:
     * whole-share equities, fractional crypto).
     *
     * @return the matching broker capabilities
     */
    public BrokerCapabilities capabilities() {
        return fractionalShares
            ? BrokerCapabilities.fractional()
            : BrokerCapabilities.wholeShareEquities();
    }

    /**
     * Detects if the current configuration matches any predefined preset.
     * Compares all cost parameters against each preset's values.
     *
     * @return the matching CostPreset, or CUSTOM if no preset matches
     */
    public CostPreset detectPreset() {
        for (CostPreset p : CostPreset.values()) {
            if (p == CostPreset.CUSTOM) continue;
            if (commissionRate.compareTo(p.getCommissionRate()) == 0 &&
                cryptoCommissionRate.compareTo(p.getCryptoCommissionRate()) == 0 &&
                slippageBase.compareTo(p.getSlippageBase()) == 0 &&
                slippageImpactPerUnit.compareTo(p.getSlippageImpactPerUnit()) == 0 &&
                slippageVolatilityMultiplier.compareTo(p.getSlippageVolatilityMultiplier()) == 0) {
                return p;
            }
        }
        return CostPreset.CUSTOM;
    }
}
