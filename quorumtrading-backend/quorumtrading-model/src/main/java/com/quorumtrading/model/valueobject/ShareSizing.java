package com.quorumtrading.model.valueobject;

import com.quorumtrading.model.enums.InvestmentType;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Domain helper that turns a cash budget into a tradable order quantity, honouring the
 * fractional-share capability of the broker the order will be routed to.
 *
 * <p>Historically the sizing pipeline floored every quantity to a hard-coded scale-8
 * {@link RoundingMode#DOWN} division ({@code cash / price} rounded down to 8 decimal
 * places). That scale was a proxy for "fractional shares allowed" — it works for Alpaca
 * and the mock broker, which fill fractional quantities on every asset class. Saxo (EU)
 * only fills whole shares on equities/ETFs, so the fractional scale is now a
 * {@link BrokerCapabilities} bit rather than a constant baked into the service.</p>
 *
 * <p>This helper centralises that decision:</p>
 * <ul>
 *   <li><b>Fractional capability for the asset class</b> → floor to scale 8 DOWN, which is
 *       byte-identical to the previous inlined behaviour.</li>
 *   <li><b>Non-fractional capability for the asset class</b> → floor to scale 0 DOWN
 *       (whole shares), with the cash residue carried to the next tick by the caller.</li>
 * </ul>
 *
 * <p><b>Stateless and pure:</b> all methods are static and side-effect free; no broker or
 * I/O dependencies. Callers pre-apply any slippage/commission buffer to the cash budget
 * before invoking {@link #maxAffordableQuantity}, exactly as the previous inlined code did.</p>
 */
public final class ShareSizing {

    /** Decimal scale used when the broker fills fractional quantities (legacy behaviour). */
    private static final int FRACTIONAL_SCALE = 8;

    /** Decimal scale used when the broker only fills whole shares. */
    private static final int WHOLE_SHARE_SCALE = 0;

    private ShareSizing() {
        // utility holder — not instantiable
    }

    /**
     * Compute the maximum quantity of {@code type} that {@code cash} can buy at {@code price},
     * rounded down to the lot granularity the broker supports for that asset class.
     *
     * <p>For a fractional-capable broker this is exactly {@code cash / price} floored to
     * 8 decimal places (the long-standing behaviour). For a whole-share broker it is the
     * same division floored to an integral share count, leaving the cash residue unspent.</p>
     *
     * @param cash         the cash budget already net of any slippage/commission buffer
     * @param price        the per-share market price (must be positive)
     * @param type         the asset class being traded (selects the equity vs crypto flag)
     * @param capabilities the routed broker's fractional-share capabilities
     * @return the largest affordable, lot-rounded quantity; never negative
     * @throws IllegalArgumentException if any argument is null, or {@code price} is not positive
     */
    public static BigDecimal maxAffordableQuantity(
            Money cash, Money price, InvestmentType type, BrokerCapabilities capabilities) {
        if (cash == null || price == null) {
            throw new IllegalArgumentException("cash and price cannot be null");
        }
        if (!price.isPositive()) {
            throw new IllegalArgumentException("price must be positive, was: " + price.amount());
        }
        int scale = lotScale(type, capabilities);
        return cash.amount().divide(price.amount(), scale, RoundingMode.DOWN);
    }

    /**
     * Round an already-computed quantity down to the broker's lot granularity for the given
     * asset class. Fractional-capable → unchanged (scale 8 floor); whole-share → integral
     * floor. Used where a quantity has been derived from a value/limit constraint rather
     * than directly from a cash budget.
     *
     * @param quantity     the candidate quantity (must not be null)
     * @param type         the asset class being traded
     * @param capabilities the routed broker's fractional-share capabilities
     * @return the quantity floored to the supported lot granularity
     * @throws IllegalArgumentException if any argument is null
     */
    public static BigDecimal roundToLot(
            BigDecimal quantity, InvestmentType type, BrokerCapabilities capabilities) {
        if (quantity == null) {
            throw new IllegalArgumentException("quantity cannot be null");
        }
        int scale = lotScale(type, capabilities);
        return quantity.setScale(scale, RoundingMode.DOWN);
    }

    private static int lotScale(InvestmentType type, BrokerCapabilities capabilities) {
        if (capabilities == null) {
            throw new IllegalArgumentException("capabilities cannot be null");
        }
        // supportsFractional validates a null type for us.
        return capabilities.supportsFractional(type) ? FRACTIONAL_SCALE : WHOLE_SHARE_SCALE;
    }
}
