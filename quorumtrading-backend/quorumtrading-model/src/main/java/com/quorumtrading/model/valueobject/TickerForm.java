package com.quorumtrading.model.valueobject;

import com.quorumtrading.model.enums.InvestmentType;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The single front door from "a ticker string a user typed" to a {@link Symbol}.
 *
 * <p><b>Why this exists.</b> Every market-data entry point used to do its own thing with the
 * raw string, and the results disagreed on the same input:</p>
 * <ul>
 *   <li>{@code get_price_coverage} built {@code new Symbol(ticker, STOCK)} directly, so
 *       {@code ASML:XAMS} was rejected outright by the record's grammar — while
 *       {@code backfill_price_data} went through {@link SymbolParser}, accepted it as a
 *       venue-qualified European symbol, called three live providers and blamed coverage.</li>
 *   <li>{@code BTC-USD} passed <em>both</em>, because {@link Symbol}'s grammar permits a hyphen
 *       for US share classes. Coverage then answered "No price bars stored for this ticker — run
 *       backfill_price_data first" for a form that no backfill can ever satisfy, and the tool
 *       that already knew that ({@code backfill_price_data}) only said so after firing the
 *       requests.</li>
 * </ul>
 *
 * <p>So a newcomer could not distinguish three different problems — a typo, a form the platform
 * rejects, and a ticker simply not backfilled yet — because the tools answered them identically.
 * Routing every caller through {@link #resolve} makes that impossible by construction: the form
 * verdict is computed once, in one place, before anything dispatches.</p>
 *
 * <p><b>Scope.</b> This is a <em>form</em> check, not an existence check. It answers "can this
 * string denote a tradable instrument here at all?" — never "does this asset exist?". A
 * correctly-formed ticker for an asset nobody carries is accepted here and refused downstream by
 * the provider, which is the only layer that knows.</p>
 *
 * <p>Pure domain helper — no I/O, no framework types.</p>
 */
public final class TickerForm {

    /**
     * Quote currencies a pair may be written against, longest-first so {@code BTCUSDT} strips
     * {@code USDT} rather than leaving a stray {@code T}.
     *
     * <p>A positive whitelist, not "anything ending in USD". A dollar-only rule let every
     * non-dollar pair through — {@code ETHBTC}, {@code BTCEUR}, {@code SOLETH} alike — and the
     * refusal message asserted "the quote currency is always USD", which is exactly the
     * assumption that made the whole family invisible.</p>
     */
    private static final List<String> QUOTE_SUFFIXES =
        List.of("USDT", "USDC", "USD", "EUR", "GBP", "BTC", "ETH", "DAI");

    /**
     * Assets whose own names end in something on {@link #QUOTE_SUFFIXES} and are therefore not
     * pair forms. An explicit list, because no length rule can separate these from real pairs.
     *
     * <p>The proof the shape has to be a name check: {@code SUSD} is simultaneously Sonic's pair
     * form and Synthetix's real {@code sUSD}. A "base of 3+ characters means it is a pair"
     * heuristic accepted {@code OPUSD}, {@code ARUSD} and {@code IDUSD} — genuine 1-2 character
     * assets whose pair forms are untradable — and refused {@code axlUSDC}, a real wrapped
     * stablecoin.</p>
     */
    private static final Set<String> ASSETS_ENDING_IN_A_QUOTE_NAME = Set.of(
        "USDT", "USDC", "TUSD", "PYUSD", "FDUSD", "CRVUSD", "AXLUSDC", "LUSD", "GUSD",
        "SUSD", "USDD", "USDE", "USDS", "USDP", "USDX", "EURC", "EURT", "WBTC", "WETH", "STETH");

    private TickerForm() {
        // static helper — not instantiable
    }

    /** What is wrong with the way a ticker was written, if anything. */
    public enum Problem {
        /** Nothing — the string is a form the platform can act on. */
        NONE,
        /** A trading pair ({@code BTC-USD}, {@code BTCUSDT}, {@code ETHBTC}) rather than an asset. */
        PAIR_FORM,
        /** Blank, or outside the symbology grammar {@link Symbol} accepts. */
        MALFORMED
    }

    /**
     * The verdict on one ticker string.
     *
     * @param problem    what is wrong, or {@link Problem#NONE}
     * @param ticker     the ticker as evaluated (trimmed, upper-cased)
     * @param correction the ticker the user most likely meant, or {@code null} when unknowable
     * @param message    a complete, user-facing sentence; {@code null} when {@code problem} is NONE
     */
    public record Verdict(Problem problem, String ticker, String correction, String message) {

        public boolean acceptable() {
            return problem == Problem.NONE;
        }

        static Verdict ok(String ticker) {
            return new Verdict(Problem.NONE, ticker, null, null);
        }
    }

    /**
     * Classify a ticker string without throwing, inferring the asset class from the ticker itself.
     *
     * <p>Used where the caller genuinely does not know the asset class — {@code get_price_coverage}
     * takes a bare ticker, because stored bars are keyed by symbol regardless of type.</p>
     *
     * @param rawTicker the ticker as the user wrote it; may be null or blank
     * @return the verdict; never null
     */
    public static Verdict check(String rawTicker) {
        if (rawTicker == null || rawTicker.isBlank()) {
            return new Verdict(Problem.MALFORMED, rawTicker, null,
                "A ticker is required (e.g. AAPL for a stock, BTC for crypto).");
        }
        // Locale.ROOT, not the default locale: a Turkish default maps "i" to "İ", so BITCOIN and
        // the ticker the rest of the platform stores would stop matching on a user's own machine.
        String token = rawTicker.trim().toUpperCase(Locale.ROOT);
        // A separator whose tail is a quote currency is a pair in any asset class, and has to be
        // caught before type inference: InvestmentType.fromTicker only knows a fixed list of
        // coins, so FAKECOIN-USD would be classified STOCK and the pair form would walk through.
        String separatorBase = separatedPairBaseOrNull(token);
        if (separatorBase != null) {
            return pairVerdict(token, separatorBase);
        }
        return check(token, InvestmentType.fromTicker(token));
    }

    /**
     * Classify a ticker string without throwing, for a caller that knows the asset class.
     *
     * @param rawTicker the ticker as the user wrote it; may be null or blank
     * @param type      the asset class the caller assigns; null is treated as unknown
     * @return the verdict; never null
     */
    public static Verdict check(String rawTicker, InvestmentType type) {
        if (rawTicker == null || rawTicker.isBlank()) {
            return new Verdict(Problem.MALFORMED, rawTicker, null,
                "A ticker is required (e.g. AAPL for a stock, BTC for crypto).");
        }
        if (type == null) {
            return check(rawTicker);
        }
        // Locale.ROOT, not the default locale: a Turkish default maps "i" to "İ", so BITCOIN and
        // the ticker the rest of the platform stores would stop matching on a user's own machine.
        String token = rawTicker.trim().toUpperCase(Locale.ROOT);

        if (type == InvestmentType.CRYPTO) {
            String base = cryptoPairBaseOrNull(token);
            if (base != null) {
                return pairVerdict(token, base);
            }
        } else {
            String base = separatedPairBaseOrNull(token);
            if (base != null) {
                return pairVerdict(token, base);
            }
        }

        // Grammar last: a pair form is a more useful diagnosis than "malformed", and BTC/USD is
        // both. Symbol's own message for the slash case says the same thing, but only that case.
        if (!isParseable(token)) {
            return new Verdict(Problem.MALFORMED, token, null,
                "Invalid ticker format: " + token
                    + " (expected 1-20 characters starting with a letter, e.g. AAPL or BRK.B;"
                    + " a venue-qualified name is written ROOT:MIC, e.g. ASML:XAMS).");
        }
        return Verdict.ok(token);
    }

    /**
     * Turn a raw ticker into a {@link Symbol}, refusing the forms nothing downstream can serve.
     *
     * <p>This is the call every market-data entry point should make. It is deliberately the only
     * way to get from a request string to a Symbol in those paths, so the three tools cannot
     * drift apart again.</p>
     *
     * @param rawTicker the ticker as the user wrote it
     * @param type      the asset class the caller assigns; null infers it from the ticker
     * @return the resolved Symbol
     * @throws IllegalArgumentException with a user-facing message when the form is unusable
     */
    public static Symbol resolve(String rawTicker, InvestmentType type) {
        Verdict verdict = type == null ? check(rawTicker) : check(rawTicker, type);
        if (!verdict.acceptable()) {
            throw new IllegalArgumentException(verdict.message());
        }
        return type == null
            ? SymbolParser.parseAutoType(verdict.ticker())
            : SymbolParser.parse(verdict.ticker(), type);
    }

    /**
     * Turn a raw ticker into a {@link Symbol}, inferring the asset class from the ticker.
     *
     * @param rawTicker the ticker as the user wrote it
     * @return the resolved Symbol
     * @throws IllegalArgumentException with a user-facing message when the form is unusable
     */
    public static Symbol resolve(String rawTicker) {
        return resolve(rawTicker, null);
    }

    /**
     * The base asset of a pair-form crypto ticker, or {@code null} when it is a bare asset.
     *
     * <p>For a ticker already known to be crypto, <em>any</em> separator means a pair — crypto
     * assets do not carry share-class suffixes, so there is nothing else a hyphen could be.</p>
     *
     * @param ticker the ticker as the user wrote it, upper-cased
     */
    public static String cryptoPairBaseOrNull(String ticker) {
        if (ticker == null) {
            return null;
        }
        int separator = separatorIndex(ticker);
        if (separator > 0) {
            return ticker.substring(0, separator);
        }
        // A known asset name wins over the suffix rule, however it happens to end.
        if (ASSETS_ENDING_IN_A_QUOTE_NAME.contains(ticker)) {
            return null;
        }
        for (String suffix : QUOTE_SUFFIXES) {
            if (ticker.length() > suffix.length() && ticker.endsWith(suffix)) {
                return ticker.substring(0, ticker.length() - suffix.length());
            }
        }
        return null;
    }

    /**
     * The base of a ticker written {@code BASE-QUOTE} or {@code BASE/QUOTE} where QUOTE is a
     * recognised quote currency, or {@code null}. Safe for equities: {@code BRK-B} keeps its
     * share-class suffix because {@code B} is not a quote currency.
     */
    private static String separatedPairBaseOrNull(String ticker) {
        int separator = separatorIndex(ticker);
        if (separator <= 0) {
            return null;
        }
        String tail = ticker.substring(separator + 1);
        return QUOTE_SUFFIXES.contains(tail) ? ticker.substring(0, separator) : null;
    }

    private static int separatorIndex(String ticker) {
        int hyphen = ticker.indexOf('-');
        return hyphen >= 0 ? hyphen : ticker.indexOf('/');
    }

    private static Verdict pairVerdict(String token, String base) {
        return new Verdict(Problem.PAIR_FORM, token, base,
            token + " is a trading pair, not an asset. Write the bare asset — \"" + base
                + "\" — because no price source here and no broker quotes a pair, in any"
                + " currency. Backfilling it can never succeed.");
    }

    /**
     * Whether {@link SymbolParser} can turn this token into a Symbol at all. The grammar lives on
     * {@link Symbol}; a venue qualifier ({@code ROOT:MIC} or a known dotted EU suffix) is stripped
     * first because the root, not the whole token, is what has to satisfy it.
     */
    private static boolean isParseable(String token) {
        int colon = token.indexOf(':');
        String root = colon > 0 ? token.substring(0, colon) : token;
        if (colon >= 0 && (colon == 0 || token.length() == colon + 1)) {
            return false;
        }
        return Symbol.isValidTicker(root);
    }
}
