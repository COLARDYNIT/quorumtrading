package com.quorumtrading.model.valueobject;

import com.quorumtrading.model.enums.InvestmentType;

/**
 * Value object describing what a broker is capable of, independent of any specific
 * order. Today the only capability modelled is fractional-share support, split by
 * asset class because brokers differ: Alpaca and the mock broker support fractional
 * quantities for both equities and crypto, whereas Saxo (EU) only fills whole shares
 * on equities/ETFs while still allowing fractional crypto.
 *
 * <p>Fractional capability is a broker capability bit, not a hard-coded scale-8 floor
 * in the sizing pipeline. Callers consult {@link #supportsFractional(InvestmentType)}
 * to decide whether an order quantity may carry a fractional component for the asset
 * class being traded.</p>
 *
 * <p><b>Invariants:</b> immutable; both flags are independent booleans.</p>
 *
 * <p><b>Usage:</b></p>
 * <pre>
 * BrokerCapabilities alpaca = BrokerCapabilities.fractional();            // both true
 * BrokerCapabilities saxo   = BrokerCapabilities.wholeShareEquities();   // equities whole, crypto fractional
 * boolean ok = saxo.supportsFractional(InvestmentType.STOCK);            // false
 * </pre>
 *
 * @param fractionalEquities true if the broker fills fractional quantities on equities/ETFs
 * @param fractionalCrypto   true if the broker fills fractional quantities on crypto
 */
public record BrokerCapabilities(boolean fractionalEquities, boolean fractionalCrypto) {

    /**
     * Capabilities for a broker that supports fractional quantities on every asset
     * class (e.g. Alpaca, the mock broker). This is the default behavior inherited by
     * existing adapters via {@code BrokerPort.capabilities()}.
     *
     * @return capabilities with both fractional flags true
     */
    public static BrokerCapabilities fractional() {
        return new BrokerCapabilities(true, true);
    }

    /**
     * Capabilities for a broker that only fills whole shares on equities/ETFs but still
     * allows fractional crypto (e.g. Saxo EU). Equity orders must be rounded down to an
     * integral share count with the cash residue carried to the next tick.
     *
     * @return capabilities with fractional equities false and fractional crypto true
     */
    public static BrokerCapabilities wholeShareEquities() {
        return new BrokerCapabilities(false, true);
    }

    /**
     * Whether the broker supports fractional quantities for the given asset class.
     *
     * @param type the investment type being traded (STOCK or CRYPTO)
     * @return true if fractional quantities are supported for that asset class
     * @throws IllegalArgumentException if {@code type} is null
     */
    public boolean supportsFractional(InvestmentType type) {
        if (type == null) {
            throw new IllegalArgumentException("InvestmentType cannot be null");
        }
        return switch (type) {
            case STOCK -> fractionalEquities;
            case CRYPTO -> fractionalCrypto;
        };
    }
}
