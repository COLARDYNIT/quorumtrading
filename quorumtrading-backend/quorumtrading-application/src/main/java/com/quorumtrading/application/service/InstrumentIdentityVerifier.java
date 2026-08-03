package com.quorumtrading.application.service;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Cross-checks a claimed instrument identity against the broker's own record of
 * the ticker that was probed.
 *
 * <p><b>The problem this solves.</b> Resolving a company name to a ticker used
 * to be a single unchecked hop: ask a language model for a symbol, ask the
 * broker whether that symbol is tradable, report success. The broker's answer
 * confirms only that <em>some</em> instrument with that symbol exists and can
 * be traded — never that it is the company that was asked for. Root tickers
 * collide across venues, so the check passed for the wrong company and said so
 * confidently: asking for LVMH (quoted {@code MC} in Paris) returned
 * {@code MC} on NYSE, which is Moelis &amp; Company, an unrelated investment
 * bank, flagged tradable. Asking for Airbus ({@code AIR} in Paris) returned
 * AAR Corp. Nothing in the result hinted at the substitution.</p>
 *
 * <p>The correction needs no new data. The broker already returns the company
 * name and exchange for every ticker probed; the resolution path simply
 * discarded both. Comparing them against what was asked for yields two
 * independent signals, and their <em>combination</em> is what makes this
 * useful:</p>
 *
 * <table border="1">
 *   <caption>How the two signals combine</caption>
 *   <tr><th>Name</th><th>Venue</th><th>Conclusion</th></tr>
 *   <tr><td>agrees</td><td>agrees</td><td>the company's own listing</td></tr>
 *   <tr><td>agrees</td><td>differs</td><td>a depositary receipt or cross-listing — tradable, but a proxy</td></tr>
 *   <tr><td>differs</td><td>either</td><td>a different company — refuse to report it as tradable</td></tr>
 * </table>
 *
 * <p>The same comparison that catches the wrong company also identifies the
 * ADR, which is why both behaviours are implemented here rather than bolted on
 * separately.</p>
 *
 * <p><b>Bias: only positive evidence downgrades a result.</b> Absence of a
 * broker name yields {@link NameMatch#UNKNOWN}, never a conflict. Brokers that
 * expose no company name (the mock broker used by every backtest) must keep
 * resolving exactly as before — a verifier that failed closed on missing data
 * would break the common path in order to fix the rare one. Only a genuine
 * disagreement, where both names are known and share no meaningful token, is
 * treated as a conflict.</p>
 *
 * <p>Stateless pure functions — no ports, no CDI, no wiring.</p>
 */
public final class InstrumentIdentityVerifier {

    private InstrumentIdentityVerifier() {
    }

    /** How well a claimed company name agrees with the broker's name for the probed ticker. */
    public enum NameMatch {
        /** The names are identical once legal form and punctuation are normalised away. */
        EXACT,
        /** Substantial overlap — one name contains the other, or they share their distinguishing tokens. */
        STRONG,
        /** Some overlap, but too little to confirm identity on its own. */
        WEAK,
        /** Both names are known and share no meaningful token — almost certainly different companies. */
        CONFLICT,
        /** At least one name is missing or carries no comparable content. */
        UNKNOWN
    }

    /** Whether a claimed listing venue and the broker's venue describe the same market. */
    public enum VenueAgreement {
        /** Both venues resolve to the same market region. */
        AGREE,
        /** The venues resolve to different market regions — e.g. Paris versus New York. */
        DIFFERENT_REGION,
        /** At least one venue could not be placed. */
        UNKNOWN
    }

    /**
     * Corporate-form and share-class tokens that carry no distinguishing
     * information. Stripped before comparison so "Apple Inc." and "Apple"
     * compare equal, and so "Moelis &amp; Company" reduces to its one
     * meaningful token rather than matching anything else ending in "Company".
     */
    private static final Set<String> NOISE_TOKENS = Set.of(
        // Anglo-American forms
        "INC", "INCORPORATED", "CORP", "CORPORATION", "CO", "COMPANY", "COMPANIES",
        "LTD", "LIMITED", "LLC", "LP", "PLC", "TRUST", "FUND",
        // Continental European forms
        "AG", "SE", "SA", "SAS", "SARL", "NV", "BV", "SPA", "SPRL", "AB", "ASA", "AS",
        "OYJ", "OY", "KGAA", "GMBH", "KG", "AD", "DAC",
        // Structural words
        "HOLDING", "HOLDINGS", "GROUP", "THE", "AND",
        // Share-class and instrument decoration
        "CLASS", "COMMON", "ORDINARY", "SHARES", "SHARE", "STOCK", "STOCKS",
        "ADR", "ADS", "GDR", "DEPOSITARY", "DEPOSITORY", "RECEIPT", "RECEIPTS",
        "REPRESENTING", "SPONSORED", "UNSPONSORED", "NEW", "SERIES"
    );

    /**
     * Compare a claimed company name with the broker's name for the ticker.
     *
     * @param claimedName the company the user asked about, as resolved by the search
     * @param brokerName  the broker's own name for the probed ticker; null when unavailable
     * @return the strength of agreement; {@link NameMatch#UNKNOWN} when either side is unusable
     */
    public static NameMatch compareNames(String claimedName, String brokerName) {
        Set<String> claimed = significantTokens(claimedName);
        Set<String> broker = significantTokens(brokerName);

        // Missing or content-free input is not evidence of anything.
        if (claimed.isEmpty() || broker.isEmpty()) {
            return NameMatch.UNKNOWN;
        }
        if (claimed.equals(broker)) {
            return NameMatch.EXACT;
        }

        Set<String> overlap = new LinkedHashSet<>(claimed);
        overlap.retainAll(broker);

        if (overlap.isEmpty()) {
            // Both names are known and share nothing — the ticker belongs to someone else.
            return NameMatch.CONFLICT;
        }

        // One name fully contains the other ("Toyota Motor" vs "Toyota Motor Corp").
        if (overlap.equals(claimed) || overlap.equals(broker)) {
            return NameMatch.STRONG;
        }

        // Otherwise judge by how much of the smaller name is accounted for.
        double coverage = (double) overlap.size() / Math.min(claimed.size(), broker.size());
        return coverage >= 0.5 ? NameMatch.STRONG : NameMatch.WEAK;
    }

    /**
     * Compare a claimed listing venue with the broker's venue for the ticker.
     *
     * <p>Comparison is by market region rather than by exact venue, because the
     * question being answered is "is this the same market?", and the two sides
     * describe venues in incompatible vocabularies — a model says "Euronext
     * Paris" where a broker says "NYSE" or emits a MIC. Region is the coarsest
     * granularity at which a genuine contradiction still shows up.</p>
     *
     * @param claimedExchange venue claimed for the listing, human name or MIC; nullable
     * @param brokerExchange  venue reported by the broker, human name or MIC; nullable
     */
    public static VenueAgreement compareVenues(String claimedExchange, String brokerExchange) {
        String claimed = region(claimedExchange);
        String broker = region(brokerExchange);
        if (claimed == null || broker == null) {
            return VenueAgreement.UNKNOWN;
        }
        return claimed.equals(broker) ? VenueAgreement.AGREE : VenueAgreement.DIFFERENT_REGION;
    }

    /**
     * Whether this pair of signals indicates the probed ticker belongs to a
     * different company than the one asked about.
     */
    public static boolean isIdentityConflict(NameMatch name) {
        return name == NameMatch.CONFLICT;
    }

    /**
     * Whether this pair of signals indicates a depositary receipt or
     * cross-listing: the company is confirmed, but it is being traded somewhere
     * other than where the search said it lists.
     */
    public static boolean indicatesForeignListing(NameMatch name, VenueAgreement venue) {
        return venue == VenueAgreement.DIFFERENT_REGION
            && (name == NameMatch.EXACT || name == NameMatch.STRONG);
    }

    /**
     * Splits a company name into comparable tokens: accents folded, punctuation
     * removed, corporate forms and share-class decoration dropped.
     *
     * <p>Ampersands become nothing rather than a separator so "AT&amp;T"
     * survives as one token, and single characters are kept only when the name
     * would otherwise be empty (they carry share-class meaning, as in
     * "Berkshire Hathaway Class B").</p>
     */
    private static Set<String> significantTokens(String name) {
        if (name == null || name.isBlank()) {
            return Set.of();
        }
        String folded = Normalizer.normalize(name, Normalizer.Form.NFKD)
            .replaceAll("\\p{M}+", "")          // strip diacritics: Moët -> Moet
            .toUpperCase();
        String cleaned = folded
            .replace("&", "")                    // AT&T -> ATT
            .replaceAll("[^A-Z0-9]+", " ")       // punctuation and separators to spaces
            .trim();
        if (cleaned.isEmpty()) {
            return Set.of();
        }
        Set<String> tokens = new LinkedHashSet<>(Arrays.asList(cleaned.split("\\s+")));
        tokens.removeIf(t -> t.isEmpty() || NOISE_TOKENS.contains(t));
        return tokens;
    }

    /**
     * Places a venue description into a coarse market region.
     *
     * <p>Accepts both human exchange names and ISO 10383 MICs, because the
     * broker and the resolver disagree about which they emit. Returns null when
     * the venue cannot be placed — including synthetic venues such as the mock
     * broker's, which must never be read as a real contradiction.</p>
     */
    private static String region(String exchange) {
        if (exchange == null || exchange.isBlank()) {
            return null;
        }
        String e = exchange.toUpperCase().replaceAll("[^A-Z0-9]+", " ").trim();

        // Synthetic / non-venue markers carry no regional meaning.
        if (e.equals("MOCK") || e.equals("CRYPTO") || e.equals("UNKNOWN")) {
            return null;
        }

        // Nasdaq operates European venues too. These must be placed before the US
        // branch, whose "NASDAQ" match would otherwise swallow Stockholm and Helsinki.
        if (containsAny(e, "NASDAQ NORDIC", "NASDAQ STOCKHOLM", "NASDAQ COPENHAGEN",
                "NASDAQ HELSINKI", "NASDAQ BALTIC", "NASDAQ ICELAND", "NASDAQ RIGA",
                "NASDAQ TALLINN", "NASDAQ VILNIUS")) {
            return "EU";
        }
        if (containsAny(e, "NYSE", "NASDAQ", "NYSEARCA", "ARCA", "AMEX", "BATS", "IEX", "OTC",
                "NYSE AMERICAN", "CBOE", "XNAS", "XNYS", "XASE", "ARCX", "IEXG", "XCBO", "OOTC",
                "UNITED STATES", "US EQUITY")) {
            return "US";
        }
        if (containsAny(e, "TOKYO", "TSE", "JPX", "XTKS", "OSAKA", "JAPAN")) {
            return "JP";
        }
        if (containsAny(e, "HONG KONG", "HKEX", "XHKG", "SEHK")) {
            return "HK";
        }
        if (containsAny(e, "SHANGHAI", "SHENZHEN", "XSHG", "XSHE")) {
            return "CN";
        }
        if (containsAny(e, "KOREA", "KRX", "XKRX", "KOSPI")) {
            return "KR";
        }
        if (containsAny(e, "TORONTO", "TSX", "XTSE", "XTSX")) {
            return "CA";
        }
        if (containsAny(e, "AUSTRALIAN", "ASX", "XASX")) {
            return "AU";
        }
        if (containsAny(e, "NATIONAL STOCK EXCHANGE OF INDIA", "BOMBAY", "XNSE", "XBOM", "NSE INDIA")) {
            return "IN";
        }
        if (containsAny(e, "XETRA", "XETR", "FRANKFURT", "DEUTSCHE BOERSE", "EURONEXT", "PARIS",
                "AMSTERDAM", "BRUSSELS", "LISBON", "MILAN", "BORSA ITALIANA", "MADRID", "BME",
                "LONDON", "LSE", "SIX", "SWISS", "ZURICH", "VIENNA", "WIENER", "STOCKHOLM",
                "COPENHAGEN", "OSLO", "HELSINKI", "DUBLIN", "NASDAQ NORDIC", "XPAR", "XAMS",
                "XBRU", "XLIS", "XMIL", "XMAD", "XLON", "XSWX", "XWBO", "XSTO", "XCSE", "XOSL",
                "XHEL", "XDUB", "XFRA", "XSTU", "XMUN")) {
            return "EU";
        }
        return null;
    }

    private static boolean containsAny(String haystack, String... needles) {
        for (String needle : needles) {
            if (haystack.equals(needle) || haystack.contains(needle)) {
                return true;
            }
        }
        return false;
    }
}
