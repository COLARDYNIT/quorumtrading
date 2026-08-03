package com.quorumtrading.model.valueobject;

/**
 * Value object representing an ISO 10383 Market Identifier Code (MIC) — the
 * four-character code that identifies the exchange / trading venue an instrument
 * is listed on (e.g. {@code XNAS} = Nasdaq, {@code XETR} = Xetra,
 * {@code XBRU} = Euronext Brussels).
 *
 * <p>The exchange is the primary key from which both <em>tradability</em>
 * (which broker account can execute it) and <em>pricing</em> (which market-data
 * adapter covers that venue, in what currency / latency) derive. See
 * europe.md §6.7.</p>
 *
 * <p><b>Invariants:</b></p>
 * <ul>
 *   <li>Code must be exactly four upper-case alphanumerics: {@code ^[A-Z0-9]{4}$}.</li>
 * </ul>
 *
 * <p><b>US default sentinel.</b> The pre-Saxo universe was effectively US-only
 * and {@link Symbol} carried no exchange at all. {@link #US_DEFAULT} is the
 * documented sentinel applied to bare US tickers at hydration so that existing
 * data is non-breaking. {@code XNAS} (Nasdaq) is chosen as the canonical US
 * default MIC; {@link #isUS()} recognises it as a US venue. This is a
 * <em>default</em>, not an assertion that every legacy ticker literally lists on
 * Nasdaq — it marks "US, exchange-unspecified" for routing purposes.</p>
 *
 * <p>This is a pure domain value object — NO JPA/JSON/Jackson annotations.</p>
 */
public record MIC(String code) {

    /** US default sentinel MIC (Nasdaq) applied to bare US tickers at hydration. */
    public static final MIC US_DEFAULT = new MIC("XNAS");

    /**
     * Canonical constructor with ISO 10383 validation.
     */
    public MIC {
        if (code == null || !code.matches("^[A-Z0-9]{4}$")) {
            throw new IllegalArgumentException(
                "Invalid MIC (must be 4 upper-case alphanumerics, ISO 10383): " + code);
        }
    }

    /**
     * @return {@code true} if this MIC identifies a US trading venue. Today this
     *     recognises the {@link #US_DEFAULT} sentinel and the principal US MICs.
     */
    public boolean isUS() {
        return switch (code) {
            // Nasdaq (and the US-default sentinel), NYSE, NYSE American/Arca, IEX, CBOE, OTC
            case "XNAS", "XNYS", "XASE", "ARCX", "BATS", "IEXG", "XCBO", "OOTC" -> true;
            default -> false;
        };
    }
}
