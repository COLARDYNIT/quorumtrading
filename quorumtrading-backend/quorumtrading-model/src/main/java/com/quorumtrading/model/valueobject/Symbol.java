package com.quorumtrading.model.valueobject;

import com.quorumtrading.model.enums.AssetType;
import com.quorumtrading.model.enums.InvestmentType;

import java.util.Objects;

/**
 * Value object representing a trading instrument: a ticker plus the metadata
 * needed to know where it trades (exchange / MIC), in what currency it settles,
 * and — for brokers with numeric instrument identity like Saxo — its Uic and
 * asset type.
 *
 * <p><b>Additive, non-breaking expansion (Saxo/EU keystone).</b> Historically
 * this was {@code record Symbol(String ticker, InvestmentType type)} with the
 * US-only universe in mind. The record is now enriched with {@code exchange},
 * {@code currency}, {@code uic} and {@code assetType}, but:</p>
 * <ul>
 *   <li>The legacy <b>2-arg constructor</b> {@code new Symbol(ticker, type)} is
 *       preserved and delegates with {@code exchange=null} (⇒ US default),
 *       {@code currency=USD}, {@code uic=null}, {@code assetType=null}. Every
 *       existing construction site compiles unchanged.</li>
 *   <li>{@link #canonical()} for a US bare ticker is <b>byte-identical</b> to the
 *       bare ticker string, so price-bar hashes ({@code symbol+"|"+ts}),
 *       {@code symbol=?} queries, JSONB blobs and position-limit keys stay
 *       stable.</li>
 *   <li>{@link #equals(Object)} / {@link #hashCode()} are hand-written over
 *       {@code (canonical(), type)} <b>only</b>. Two symbols are equal iff their
 *       canonical form and investment type match — Uic enrichment and the
 *       null-exchange-vs-US-default distinction are ignored, so
 *       {@code Map<Symbol,Position>}, {@code Map<Symbol,Money>} and
 *       {@code Set<Symbol>} keys never silently fork.</li>
 * </ul>
 *
 * <p><b>Invariants:</b></p>
 * <ul>
 *   <li>Ticker must be a non-blank symbology token (US bare tickers, suffixed EU
 *       names like {@code RHM.DE}, dotted/hyphenated venues) or the special value
 *       {@code "MARKET"}.</li>
 *   <li>Investment type must be explicitly provided (CRYPTO or STOCK).</li>
 *   <li>{@code exchange}, {@code currency}, {@code uic}, {@code assetType} are
 *       optional (nullable); a null {@code exchange} means the US default venue
 *       and a null {@code currency} means USD.</li>
 * </ul>
 *
 * <p>Pure domain value object — NO JPA/JSON/Jackson annotations.</p>
 *
 * <p><b>Usage:</b></p>
 * <pre>
 * Symbol btc  = new Symbol("BTC", InvestmentType.CRYPTO);                 // legacy 2-arg
 * Symbol rhm  = Symbol.parse("RHM:XETR");                                 // EU, exchange-qualified
 * String key  = aapl.canonical();                                         // "AAPL" for US
 * </pre>
 */
public record Symbol(
    String ticker,
    InvestmentType type,
    MIC exchange,
    Money.Currency currency,
    Long uic,
    AssetType assetType
) {

    /**
     * Ticker symbology accepted by the canonical constructor. Permits:
     * <ul>
     *   <li>US bare tickers ({@code AAPL}, {@code BRK.B})</li>
     *   <li>European suffixed / venue-qualified names ({@code RHM.DE},
     *       {@code ASML.AS}) and longer roots</li>
     * </ul>
     * The first character is a letter; the remainder may include letters,
     * digits, dots and hyphens. Length 1–20.
     */
    private static final String TICKER_PATTERN = "^[A-Z][A-Z0-9.\\-]{0,19}$";

    /**
     * Whether {@code ticker} is accepted by this record's symbology grammar —
     * i.e. whether constructing a {@code Symbol} with it would succeed.
     *
     * <p>Exposed so resolution paths can find out <em>before</em> handing a
     * symbol to a user whether the rest of the platform will accept it. A
     * venue may quote a ticker this grammar cannot express (Tokyo quotes
     * Toyota as {@code 7203}; the pattern requires a leading letter), and
     * returning such a token as if it were usable strands the caller: every
     * downstream tool rejects it. Callers should report those listings as
     * informational alternatives, not as the resolved symbol.</p>
     *
     * @param ticker candidate symbology token, may be null
     * @return {@code true} if a {@code Symbol} can be built from this ticker
     */
    public static boolean isValidTicker(String ticker) {
        return ticker != null && (ticker.matches(TICKER_PATTERN) || ticker.equals("MARKET"));
    }

    /**
     * Legacy 2-arg constructor. Delegates with {@code exchange=null} (US
     * default), {@code currency=USD}, {@code uic=null}, {@code assetType=null}.
     * Preserved so every existing {@code new Symbol(ticker, type)} call site
     * compiles unchanged.
     */
    public Symbol(String ticker, InvestmentType type) {
        this(ticker, type, null, Money.Currency.USD, null, null);
    }

    /**
     * Canonical constructor with validation.
     */
    public Symbol {
        if (!isValidTicker(ticker)) {
            // A slash means the caller wrote a currency pair, which is a crypto habit, not a
            // venue problem. Sending them to find_ticker "with the company name to find a
            // US-tradable listing" is advice about equities that cannot help anyone holding
            // BTC/USD — and it was the message crypto users actually got.
            if (ticker != null && ticker.contains("/")) {
                String base = ticker.split("/", 2)[0];
                throw new IllegalArgumentException("Invalid symbol format: " + ticker
                    + " (that is a currency pair. Crypto tickers are the bare asset — write \""
                    + base + "\". The quote currency is always USD)");
            }
            // Suggest only what the platform will actually accept. An earlier version of
            // this hint offered ROOT:MIC as the form for exchange-qualified names, which
            // then became rejected at strategy creation — so the one place a user of a
            // non-US listing is told what to type was telling them wrong.
            throw new IllegalArgumentException("Invalid symbol format: " + ticker
                + " (expected 1-20 characters starting with a letter, e.g. AAPL or BRK.B."
                + " Non-US venues are not supported — use find_ticker with the company name"
                + " to find a US-tradable listing)");
        }
        if (type == null) {
            throw new IllegalArgumentException("InvestmentType cannot be null");
        }
        // Normalise a null currency to USD so identity (canonical()+type) and the
        // US byte-identity invariant never depend on an unset currency.
        if (currency == null) {
            currency = Money.Currency.USD;
        }
    }

    /**
     * Stable string identity for this instrument, used as map-key / hash basis.
     *
     * <p>For a US instrument (no exchange, or a US MIC) this is <b>byte-identical
     * to the bare ticker</b> — preserving every existing hash, query and blob.
     * For a non-US (European) instrument it is {@code ticker + ":" + MIC} so two
     * different-venue listings of the same root are distinct and round-trip
     * through {@link #parse(String)}.</p>
     */
    public String canonical() {
        if (exchange == null || exchange.isUS()) {
            return ticker;
        }
        return ticker + ":" + exchange.code();
    }

    /**
     * @return {@code true} if this instrument trades on a non-US (European)
     *     venue — i.e. it carries an explicit, non-US MIC.
     */
    public boolean isEuropean() {
        return exchange != null && !exchange.isUS();
    }

    /**
     * Parse a canonical / human symbology token back into a {@code Symbol}.
     *
     * <p>Accepts:</p>
     * <ul>
     *   <li>{@code "AAPL"} — bare US ticker (exchange defaults to US, currency USD).</li>
     *   <li>{@code "RHM:XETR"} — exchange-qualified canonical EU form (MIC after the colon).</li>
     * </ul>
     * The investment type is inferred heuristically from the ticker root
     * ({@link InvestmentType#fromTicker(String)}). Use the full constructor when
     * Uic / asset type / currency are known.
     *
     * @param raw the symbology token (e.g. {@code "AAPL"}, {@code "RHM:XETR"})
     * @return the parsed {@code Symbol}
     * @throws IllegalArgumentException if {@code raw} is null/blank or malformed
     */
    public static Symbol parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Cannot parse blank symbol");
        }
        String trimmed = raw.trim();
        int colon = trimmed.indexOf(':');
        if (colon >= 0) {
            String root = trimmed.substring(0, colon);
            String mic = trimmed.substring(colon + 1);
            MIC exchange = new MIC(mic);
            InvestmentType type = InvestmentType.fromTicker(root);
            // Currency unknown at parse time → null normalises to USD in the ctor;
            // a US MIC keeps canonical() == bare ticker.
            return new Symbol(root, type, exchange, null, null, null);
        }
        InvestmentType type = InvestmentType.fromTicker(trimmed);
        return new Symbol(trimmed, type);
    }

    /**
     * Equality over {@code (canonical(), type)} ONLY. Two symbols are equal iff
     * their canonical identity and investment type match; incidental Uic /
     * exchange / currency enrichment does not split map keys.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Symbol other)) {
            return false;
        }
        return type == other.type && canonical().equals(other.canonical());
    }

    @Override
    public int hashCode() {
        return Objects.hash(canonical(), type);
    }
}
