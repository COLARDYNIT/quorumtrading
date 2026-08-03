package com.quorumtrading.model.enums;

/**
 * How a particular ticker relates to the company behind it.
 *
 * <p><b>Why this exists.</b> A company is not a ticker — a company has
 * <em>listings</em>, and they are not interchangeable. Toyota Motor
 * Corporation is quoted as {@code 7203} in Tokyo in yen and as {@code TM} in
 * New York in dollars; the second is a depositary receipt whose price tracks
 * the first plus the currency pair. Answering "Toyota" with a bare {@code TM}
 * is not wrong, but it is incomplete in a way that matters: the user believes
 * they hold the Tokyo line and they do not, and their returns carry an FX leg
 * they never chose.</p>
 *
 * <p>Labelling the relationship lets the platform stay honest about what is
 * actually being traded without refusing to trade it.</p>
 */
public enum ListingType {

    /** The company's home listing — the venue where the shares themselves trade. */
    PRIMARY,

    /**
     * A depositary receipt (ADR/ADS/GDR): a certificate representing shares held
     * abroad, trading on a local venue in the local currency.
     *
     * <p>Tradable and often the only route available to a US broker, but it is
     * a proxy: its price is the home listing translated through an exchange
     * rate, and its ratio to the underlying share is not always 1:1.</p>
     */
    DEPOSITARY_RECEIPT,

    /**
     * An ordinary share of the same company admitted to trading on a second
     * venue — the same security, not a receipt over it.
     */
    CROSS_LISTING,

    /** A cryptocurrency, which has no listing venue in the equity sense. */
    CRYPTO,

    /** The relationship could not be determined. */
    UNKNOWN
}
