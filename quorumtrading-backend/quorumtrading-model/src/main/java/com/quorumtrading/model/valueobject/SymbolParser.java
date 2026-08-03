package com.quorumtrading.model.valueobject;

import com.quorumtrading.model.enums.InvestmentType;

import java.util.Map;

/**
 * Shared, stateless helper for turning a raw user-supplied ticker token into a
 * {@link Symbol}, accepting an optional exchange / currency qualifier and
 * defaulting to the US venue / USD when none is present.
 *
 * <p>This is the single source of truth for "ticker string from a request →
 * Symbol" used across the REST and MCP adapters (simulation/benchmark/research
 * ticker inputs, backfill, data-availability, etc.). It mirrors the resolution
 * rules the strategy-YAML parser applies (see {@code StrategyConfigParser}) so a
 * ticker typed into an API call and the same ticker written into a strategy
 * frontmatter produce the <em>identical</em> canonical {@link Symbol}.</p>
 *
 * <p><b>Resolution rules</b> (for {@link #parse(String, InvestmentType)}):</p>
 * <ul>
 *   <li><b>Bare US ticker</b> — no exchange qualifier ({@code AAPL}, {@code BRK.B})
 *       → {@code new Symbol(ticker, type)}, the legacy 2-arg path. Byte-identical
 *       to the pre-Saxo behaviour: {@code canonical() == ticker}, currency USD,
 *       so every existing US request payload behaves exactly as before.</li>
 *   <li><b>Colon-qualified</b> ({@code RHM:XETR}) → the segment after the colon is
 *       parsed as a {@link MIC} via {@link Symbol#parse(String)} and the symbol is
 *       re-stamped with the caller's explicit investment type and the venue's
 *       settlement currency.</li>
 *   <li><b>Dotted EU suffix</b> ({@code RHM.DE}, {@code ASML.AS}) → the suffix is
 *       mapped to its MIC and processed through the same exchange-qualified path.
 *       A dotted suffix that is <em>not</em> a known European venue (e.g.
 *       {@code BRK.B}) is left untouched as a US ticker.</li>
 * </ul>
 *
 * <p>Pure domain helper — NO JPA/JSON/Jackson annotations, no I/O.</p>
 */
public final class SymbolParser {

    /**
     * Dotted-suffix → MIC mapping for European venue-qualified tickers (e.g.
     * {@code RHM.DE} → Xetra). The canonical {@code ROOT:MIC} form is preferred,
     * but the dotted suffix form is accepted as an ergonomic alias. A bare ticker
     * whose suffix is NOT in this table (e.g. {@code BRK.B}) is left untouched as
     * a US ticker.
     *
     * <p>Kept deliberately in lock-step with {@code StrategyConfigParser.MIC_BY_SUFFIX}.</p>
     */
    private static final Map<String, MIC> MIC_BY_SUFFIX = Map.ofEntries(
        Map.entry("DE", new MIC("XETR")),   // Germany — Xetra
        Map.entry("PA", new MIC("XPAR")),   // France — Euronext Paris
        Map.entry("AS", new MIC("XAMS")),   // Netherlands — Euronext Amsterdam
        Map.entry("BR", new MIC("XBRU")),   // Belgium — Euronext Brussels
        Map.entry("MI", new MIC("XMIL")),   // Italy — Borsa Italiana / Euronext Milan
        Map.entry("MC", new MIC("XMAD")),   // Spain — Bolsa de Madrid
        Map.entry("LS", new MIC("XLIS")),   // Portugal — Euronext Lisbon
        Map.entry("L", new MIC("XLON")),    // UK — London Stock Exchange
        Map.entry("SW", new MIC("XSWX")),   // Switzerland — SIX Swiss Exchange
        Map.entry("VI", new MIC("XWBO")),   // Austria — Wiener Börse
        Map.entry("HE", new MIC("XHEL")),   // Finland — Nasdaq Helsinki
        Map.entry("ST", new MIC("XSTO")),   // Sweden — Nasdaq Stockholm
        Map.entry("CO", new MIC("XCSE")),   // Denmark — Nasdaq Copenhagen
        Map.entry("OL", new MIC("XOSL")),   // Norway — Oslo Børs
        Map.entry("IR", new MIC("XDUB"))    // Ireland — Euronext Dublin
    );

    /**
     * MIC → settlement-currency default for European venues. Used when a caller
     * does not pass an explicit currency. Unlisted (e.g. US) MICs fall back to USD.
     *
     * <p>Kept deliberately in lock-step with {@code StrategyConfigParser.CURRENCY_BY_MIC}.</p>
     */
    private static final Map<String, Money.Currency> CURRENCY_BY_MIC = Map.ofEntries(
        Map.entry("XETR", Money.Currency.EUR),
        Map.entry("XPAR", Money.Currency.EUR),
        Map.entry("XAMS", Money.Currency.EUR),
        Map.entry("XBRU", Money.Currency.EUR),
        Map.entry("XMIL", Money.Currency.EUR),
        Map.entry("XMAD", Money.Currency.EUR),
        Map.entry("XLIS", Money.Currency.EUR),
        Map.entry("XHEL", Money.Currency.EUR),
        Map.entry("XWBO", Money.Currency.EUR),
        Map.entry("XDUB", Money.Currency.EUR)
    );

    private SymbolParser() {
        // static helper — not instantiable
    }

    /**
     * Resolve the natural settlement currency for an exchange (MIC), reusing the
     * single {@link #CURRENCY_BY_MIC} table that already backs ticker parsing.
     *
     * <p>This is the reusable MIC → currency hook the currency-thread (CUR1) relies
     * on whenever a price's currency must be <em>inferred</em> from where the
     * instrument trades — e.g. a EU-venue {@link Symbol} priced off a stored market
     * event that does not itself carry a currency. European venues settle in
     * {@code EUR}; every unlisted (US / unknown) MIC falls back to {@code USD}, so
     * the US path is unchanged.</p>
     *
     * @param mic the exchange MIC, or {@code null} for the US default
     * @return the venue's settlement currency ({@code USD} for null / US / unknown MICs)
     */
    public static Money.Currency currencyFor(MIC mic) {
        if (mic == null) {
            return Money.Currency.USD;
        }
        return CURRENCY_BY_MIC.getOrDefault(mic.code(), Money.Currency.USD);
    }

    /**
     * Resolve the settlement currency a {@link Symbol} prices/settles in.
     *
     * <p>Prefers the symbol's explicit {@link Symbol#currency()} (already normalised
     * to a non-null currency by the {@code Symbol} constructor); this is the value
     * {@code SymbolParser.parse} / {@code Symbol.parse} stamp from the venue. The
     * exchange-derived {@link #currencyFor(MIC)} is the fallback when, for any
     * reason, the currency is unset. US symbols resolve to {@code USD}, EU symbols to
     * {@code EUR}.</p>
     *
     * @param symbol the instrument; must not be null
     * @return the currency this instrument settles in
     */
    public static Money.Currency currencyFor(Symbol symbol) {
        if (symbol.currency() != null) {
            return symbol.currency();
        }
        return currencyFor(symbol.exchange());
    }

    /**
     * Parse a raw ticker token into a {@link Symbol} of the given investment type,
     * accepting an optional exchange qualifier and defaulting to the US venue /
     * USD when none is present.
     *
     * @param rawToken the ticker string from a request (e.g. {@code "AAPL"},
     *     {@code "RHM:XETR"}, {@code "rhm.de"}); leading/trailing whitespace is
     *     trimmed and the token is upper-cased
     * @param type the investment type the caller assigns (STOCK / CRYPTO)
     * @return the resolved Symbol
     * @throws IllegalArgumentException if {@code rawToken} is null/blank or the
     *     resulting ticker / MIC is malformed
     */
    public static Symbol parse(String rawToken, InvestmentType type) {
        return parse(rawToken, type, null);
    }

    /**
     * Auto-classifying overload: infers the investment type from the ticker root
     * via {@link InvestmentType#fromTicker(String)}. Use when the caller does not
     * already know the asset class (e.g. comma-separated backfill / data-availability
     * symbol lists).
     *
     * @param rawToken the ticker string from a request
     * @return the resolved Symbol, with the investment type inferred from the root
     * @throws IllegalArgumentException if {@code rawToken} is null/blank or malformed
     */
    public static Symbol parseAutoType(String rawToken) {
        if (rawToken == null) {
            throw new IllegalArgumentException("Cannot parse null ticker");
        }
        String root = stripExchangeQualifier(rawToken.trim().toUpperCase());
        return parse(rawToken, InvestmentType.fromTicker(root), null);
    }

    /**
     * Parse a market-research target-asset token in the shared {@code "TICKER[:TYPE]"}
     * convention used by both the REST endpoint and the MCP tool: the colon delimits
     * an optional asset TYPE ({@code "BTC:CRYPTO"}, {@code "AAPL:STOCK"}), <em>not</em>
     * a MIC, and a bare ticker defaults to {@link InvestmentType#STOCK}. The ticker
     * root may still be a dotted EU venue alias ({@code "RHM.DE:STOCK"}) which is
     * enriched by {@link #parse(String, InvestmentType)} as usual.
     *
     * <p>No heuristic classification happens on this path — a bare {@code "BTC"}
     * resolves to STOCK; callers who mean crypto must say {@code "BTC:CRYPTO"}.</p>
     *
     * @param rawToken the target-asset token (e.g. {@code "AAPL"}, {@code "BTC:CRYPTO"});
     *     leading/trailing whitespace around ticker and type is trimmed, type is
     *     case-insensitive
     * @return the resolved Symbol
     * @throws IllegalArgumentException if {@code rawToken} is null/blank, the ticker
     *     is malformed, or the type suffix is not a valid {@link InvestmentType}
     */
    public static Symbol parseWithTypeSuffix(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new IllegalArgumentException("Cannot parse null or blank ticker");
        }
        String[] parts = rawToken.trim().split(":");
        String ticker = parts[0].trim();
        InvestmentType type = parts.length > 1
            ? parseInvestmentType(parts[1].trim(), rawToken.trim())
            : InvestmentType.STOCK;
        return parse(ticker, type);
    }

    /**
     * Inverse of {@link #parseWithTypeSuffix(String)}: format a Symbol as a
     * target-asset token. STOCK (the default) is emitted bare so existing US-stock
     * payloads stay byte-identical; any other type carries an explicit suffix so a
     * response value round-trips through {@code parseWithTypeSuffix} losslessly
     * (e.g. {@code "BTC:CRYPTO"}).
     *
     * @param symbol the instrument; must not be null
     * @return {@code ticker} for STOCK, {@code ticker:TYPE} otherwise
     */
    public static String formatWithTypeSuffix(Symbol symbol) {
        return symbol.type() == InvestmentType.STOCK
            ? symbol.ticker()
            : symbol.ticker() + ":" + symbol.type().name();
    }

    private static InvestmentType parseInvestmentType(String value, String token) {
        try {
            return InvestmentType.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            String validValues = java.util.Arrays.stream(InvestmentType.values())
                .map(Enum::name)
                .collect(java.util.stream.Collectors.joining(", "));
            throw new IllegalArgumentException(
                "Invalid asset type '" + value + "' in target asset '" + token
                    + "'. Valid values: " + validValues);
        }
    }

    /**
     * Full overload with an explicit settlement currency override (wins over the
     * venue default). A null {@code explicitCurrency} resolves to the venue's
     * natural currency for a European MIC, otherwise USD.
     *
     * @param rawToken the ticker string from a request
     * @param type the investment type the caller assigns
     * @param explicitCurrency an explicit currency, or {@code null} for the venue default
     * @return the resolved Symbol
     * @throws IllegalArgumentException if {@code rawToken} is null/blank or malformed
     */
    public static Symbol parse(String rawToken, InvestmentType type, Money.Currency explicitCurrency) {
        if (rawToken == null) {
            throw new IllegalArgumentException("Cannot parse null ticker");
        }
        String token = rawToken.trim().toUpperCase();
        if (token.isEmpty()) {
            throw new IllegalArgumentException("Cannot parse blank ticker");
        }
        if (type == null) {
            throw new IllegalArgumentException("InvestmentType cannot be null");
        }

        // Canonical exchange-qualified form "ROOT:MIC".
        int colon = token.indexOf(':');
        if (colon >= 0) {
            return buildEuropeanSymbol(token.substring(0, colon), token.substring(colon + 1), type, explicitCurrency);
        }

        // Dotted EU suffix form ("RHM.DE") — only when the suffix maps to a known venue.
        int lastDot = token.lastIndexOf('.');
        if (lastDot > 0 && lastDot < token.length() - 1) {
            String suffix = token.substring(lastDot + 1);
            MIC mic = MIC_BY_SUFFIX.get(suffix);
            if (mic != null) {
                return buildEuropeanSymbol(token.substring(0, lastDot), mic.code(), type, explicitCurrency);
            }
        }

        // Bare US ticker — unchanged 2-arg path (byte-identical canonical form, USD).
        return new Symbol(token, type);
    }

    /**
     * Strip an exchange qualifier (colon-form or known dotted EU suffix) from a
     * token, returning the bare root for investment-type inference. A US dotted
     * ticker (e.g. {@code BRK.B}) is returned untouched.
     */
    private static String stripExchangeQualifier(String token) {
        int colon = token.indexOf(':');
        if (colon >= 0) {
            return token.substring(0, colon);
        }
        int lastDot = token.lastIndexOf('.');
        if (lastDot > 0 && lastDot < token.length() - 1) {
            String suffix = token.substring(lastDot + 1);
            if (MIC_BY_SUFFIX.containsKey(suffix)) {
                return token.substring(0, lastDot);
            }
        }
        return token;
    }

    /**
     * Build an exchange-qualified Symbol from a root + MIC code, preserving the
     * explicitly-assigned investment type and resolving the settlement currency
     * (explicit currency wins, otherwise the venue's natural currency, otherwise USD).
     */
    private static Symbol buildEuropeanSymbol(String root, String micCode, InvestmentType type,
                                              Money.Currency explicitCurrency) {
        // Reuse Symbol.parse() for MIC parsing/validation so the canonical form is
        // produced identically to the rest of the system; it infers a placeholder
        // type which we discard in favour of the caller's explicit type below.
        Symbol parsed = Symbol.parse(root + ":" + micCode);
        MIC exchange = parsed.exchange();
        Money.Currency currency = explicitCurrency != null
            ? explicitCurrency
            : CURRENCY_BY_MIC.getOrDefault(micCode, Money.Currency.USD);
        return new Symbol(parsed.ticker(), type, exchange, currency, null, null);
    }
}
