package com.quorumtrading.model.valueobject;

import com.quorumtrading.model.enums.ListingType;

/**
 * One venue's quotation of a company: the ticker as <em>that</em> exchange
 * writes it, where it trades, in what currency, and how it relates to the
 * company (primary line, depositary receipt, cross-listing).
 *
 * <p>Ticker resolution returns a selected listing plus the alternatives it
 * considered, so a caller can see that "Toyota" means {@code 7203} in Tokyo
 * and {@code TM} in New York rather than being handed one of them with no
 * indication the other exists.</p>
 *
 * <p><b>Two independent questions, two fields.</b> Whether this platform can
 * <em>express</em> a ticker and whether you can <em>trade</em> it are unrelated,
 * and collapsing them misleads in both directions. A venue may quote a ticker
 * the {@link Symbol} grammar cannot represent — Tokyo's four-digit codes being
 * the standing example — while a perfectly well-formed ticker may belong to
 * another company entirely. So {@code symbolSupported} answers only the
 * grammatical question and {@code tradableHere} answers only the practical one.
 *
 * <p>The distinction is not academic: an earlier single {@code usable} flag
 * meant "the grammar accepts this string", but every consumer of a field by
 * that name reads it as "you can act on this" — and machine consumers filter on
 * it. Listings that were rejected as the wrong company, or absent from the
 * broker entirely, were carrying {@code usable: true} alongside a note saying
 * exactly the opposite.</p>
 *
 * <p>Reporting an unusable listing is still worthwhile: "Toyota's primary
 * listing is 7203 on the Tokyo Stock Exchange, which this platform cannot yet
 * express" is useful and true, whereas silently returning {@code 7203} as if it
 * were actionable strands the caller, since every downstream tool rejects it.</p>
 *
 * <p>Pure domain value object — NO JPA/JSON/Jackson annotations.</p>
 *
 * @param ticker          the symbol as the listing venue quotes it (e.g. {@code 7203}, {@code TM})
 * @param exchangeName    human-readable venue (e.g. {@code Tokyo Stock Exchange}), nullable
 * @param mic             ISO 10383 code of the venue, nullable when unknown
 * @param currency        settlement currency of this listing, nullable when unknown or unsupported
 * @param type            how this listing relates to the company
 * @param symbolSupported whether this platform's {@link Symbol} grammar accepts {@code ticker};
 *                        says nothing about tradability
 * @param tradableHere    whether this listing can actually be traded, as the company that was
 *                        asked for, through the validating broker: {@code TRUE} yes, {@code FALSE}
 *                        no (absent, untradable, or a different company), {@code null} not probed
 * @param note            why the listing was rejected, or any other caveat; nullable
 */
public record InstrumentListing(
    String ticker,
    String exchangeName,
    MIC mic,
    Money.Currency currency,
    ListingType type,
    boolean symbolSupported,
    Boolean tradableHere,
    String note
) {

    public InstrumentListing {
        if (ticker == null || ticker.isBlank()) {
            throw new IllegalArgumentException("InstrumentListing requires a ticker");
        }
        if (type == null) {
            type = ListingType.UNKNOWN;
        }
    }

    /** Returns a copy recording what probing this listing against the broker established. */
    public InstrumentListing withProbeResult(boolean tradable, String note) {
        String combined = this.note == null ? note : this.note + " " + note;
        return new InstrumentListing(ticker, exchangeName, mic, currency, type,
            symbolSupported, tradable, combined);
    }

    /**
     * Builds a listing, deriving {@code symbolSupported} from the platform's
     * symbol grammar and attaching an explanatory note when the ticker cannot be
     * expressed. Tradability is left unprobed.
     */
    public static InstrumentListing of(String ticker, String exchangeName, MIC mic,
                                       Money.Currency currency, ListingType type) {
        boolean supported = Symbol.isValidTicker(ticker);
        String note = supported ? null : unusableTickerNote(ticker);
        // A ticker the grammar cannot express can never be traded here, so that much
        // is already settled without asking the broker.
        Boolean tradable = supported ? null : Boolean.FALSE;
        return new InstrumentListing(ticker, exchangeName, mic, currency, type, supported,
            tradable, note);
    }

    /**
     * Builds a listing from raw, untrusted strings — the shape an external
     * resolver (a language model, a broker payload) actually produces.
     *
     * <p>This is the single place that decides what the platform can and cannot
     * express, so every caveat reaches the user from one source rather than
     * being re-derived, or quietly dropped, at each call site. A MIC that is
     * not ISO 10383 and a currency this platform does not carry are both
     * <b>recorded as notes rather than discarded</b>: the listing is still real
     * and worth showing, and a user told "Toyota's Tokyo line settles in JPY,
     * which this platform does not support" is better served than one shown a
     * blank currency field.</p>
     *
     * @param ticker       venue ticker; must be non-blank
     * @param exchangeName human-readable venue, nullable
     * @param micCode      candidate ISO 10383 code, nullable/invalid tolerated
     * @param currencyCode candidate ISO 4217 code, nullable/unsupported tolerated
     * @param type         relationship to the company, nullable ⇒ UNKNOWN
     */
    public static InstrumentListing ofRaw(String ticker, String exchangeName, String micCode,
                                          String currencyCode, ListingType type) {
        MIC mic = null;
        if (micCode != null && !micCode.isBlank()) {
            try {
                mic = new MIC(micCode.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                mic = null; // not ISO 10383 — the human exchange name still carries the meaning
            }
        }

        Money.Currency currency = null;
        String currencyNote = null;
        if (currencyCode != null && !currencyCode.isBlank()) {
            String code = currencyCode.trim().toUpperCase();
            try {
                currency = Money.Currency.valueOf(code);
            } catch (IllegalArgumentException e) {
                currencyNote = "This listing settles in " + code
                    + ", which this platform does not support (supported: USD, EUR, BTC).";
            }
        }

        boolean supported = Symbol.isValidTicker(ticker);
        String note = joinNotes(supported ? null : unusableTickerNote(ticker), currencyNote);
        Boolean tradable = supported ? null : Boolean.FALSE;
        return new InstrumentListing(ticker, exchangeName, mic, currency, type, supported,
            tradable, note);
    }

    private static String unusableTickerNote(String ticker) {
        return "This platform's symbol format cannot yet express '" + ticker
            + "' (tickers must start with a letter), so this listing cannot be traded or"
            + " backfilled here even where the broker covers the venue.";
    }

    private static String joinNotes(String first, String second) {
        if (first == null) {
            return second;
        }
        return second == null ? first : first + " " + second;
    }

    /** @return this listing's ticker qualified by its venue, e.g. {@code RHM:XETR}; bare when the MIC is unknown or US. */
    public String canonical() {
        if (mic == null || mic.isUS()) {
            return ticker;
        }
        return ticker + ":" + mic.code();
    }

    /** @return a compact human description, e.g. {@code TM on NYSE (USD)}. */
    public String describe() {
        StringBuilder sb = new StringBuilder(ticker);
        if (exchangeName != null && !exchangeName.isBlank()) {
            sb.append(" on ").append(exchangeName);
        }
        if (currency != null) {
            sb.append(" (").append(currency).append(')');
        }
        return sb.toString();
    }
}
