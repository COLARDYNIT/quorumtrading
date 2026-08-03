package com.quorumtrading.model.valueobject;

import com.quorumtrading.model.enums.AssetType;
import com.quorumtrading.model.enums.ConfidenceLevel;
import com.quorumtrading.model.enums.InvestmentType;
import com.quorumtrading.model.enums.ListingType;
import com.quorumtrading.model.enums.TickerResolution;

import java.util.List;

/**
 * The result of resolving a company name to a tradable instrument: which
 * listing was selected, how far it was corroborated, and what else was
 * considered.
 *
 * <p><b>{@code resolution} is the field to branch on.</b> The older
 * {@code tradableOnBroker} boolean is retained for compatibility, but a boolean
 * cannot express the distinctions that matter here — "not tradable" once
 * covered a company that is genuinely private, one nobody could identify, one
 * listed on a venue this broker does not reach, and one where the broker was
 * simply unreachable. Those four call for four different next actions.
 * {@link TickerResolution} states which case applies; {@code tradableOnBroker}
 * remains true only for {@link TickerResolution#RESOLVED}.</p>
 *
 * <p><b>Ambiguity fails closed.</b> When verification finds that the probed
 * ticker belongs to a different company than the one asked about, {@code ticker}
 * is deliberately left null and the rejected candidate is moved into
 * {@link #alternatives()} with an explanation. A caller that reads only
 * {@code ticker()} — as the chat and MCP surfaces do — then cannot act on the
 * wrong instrument by default. Withholding a symbol is recoverable; trading the
 * wrong company is not.</p>
 *
 * <p>The {@code mic}/{@code currency}/{@code uic}/{@code assetType} fields are
 * additive and nullable, carrying venue / settlement currency / numeric
 * instrument identity for brokers (e.g. Saxo) that expose them.</p>
 *
 * @param ticker            resolved ticker, or null when nothing safe to act on was found
 * @param companyName       the company as identified
 * @param exchange          venue the resolved ticker would actually trade on, nullable
 * @param investmentType    STOCK or CRYPTO, null when unresolved
 * @param tradableOnBroker  true only for {@link TickerResolution#RESOLVED}
 * @param message           human-readable explanation, always populated
 * @param dividendInfo      dividend information for the ticker, nullable
 * @param mic               ISO 10383 market identifier code of the listing venue, nullable
 * @param currency          settlement currency, nullable (null ⇒ USD by convention)
 * @param uic               broker-specific numeric instrument id (Saxo Uic), nullable
 * @param assetType         broker-neutral instrument asset type, nullable
 * @param resolution        what kind of answer this is — the field callers should branch on
 * @param confidence        how much independent corroboration stands behind it
 * @param listingType       how the resolved ticker relates to the company (primary line, ADR, …)
 * @param alternatives      other listings considered, including rejected and unusable ones; never null
 * @param warnings          caveats a caller should surface before acting; never null
 */
public record TickerSearchResult(
    String ticker,
    String companyName,
    String exchange,
    InvestmentType investmentType,
    boolean tradableOnBroker,
    String message,
    DividendInfo dividendInfo,
    MIC mic,
    Money.Currency currency,
    Long uic,
    AssetType assetType,
    TickerResolution resolution,
    ConfidenceLevel confidence,
    ListingType listingType,
    List<InstrumentListing> alternatives,
    List<String> warnings
) {

    public TickerSearchResult {
        alternatives = alternatives == null ? List.of() : List.copyOf(alternatives);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        if (resolution == null) {
            resolution = tradableOnBroker ? TickerResolution.RESOLVED : TickerResolution.UNVERIFIED;
        }
        if (confidence == null) {
            confidence = tradableOnBroker ? ConfidenceLevel.MEDIUM : ConfidenceLevel.LOW;
        }
        if (listingType == null) {
            listingType = ListingType.UNKNOWN;
        }
    }

    /**
     * Legacy 11-arg constructor, preserved so existing construction sites compile
     * unchanged. Resolution, confidence and listing type are inferred from
     * {@code tradableOnBroker}; alternatives and warnings default to empty.
     */
    public TickerSearchResult(String ticker, String companyName, String exchange,
                              InvestmentType investmentType, boolean tradableOnBroker, String message,
                              DividendInfo dividendInfo, MIC mic, Money.Currency currency,
                              Long uic, AssetType assetType) {
        this(ticker, companyName, exchange, investmentType, tradableOnBroker, message, dividendInfo,
            mic, currency, uic, assetType, null, null, null, List.of(), List.of());
    }

    public static TickerSearchResult found(String ticker, String companyName, String exchange,
                                           InvestmentType investmentType) {
        return new TickerSearchResult(ticker, companyName, exchange, investmentType, true,
            String.format("%s (%s) trades on %s and is available for trading.", companyName, ticker, exchange),
            null, null, null, null, null,
            TickerResolution.RESOLVED, ConfidenceLevel.MEDIUM, ListingType.PRIMARY,
            List.of(), List.of());
    }

    /**
     * Exchange-qualified tradable result, carrying the listing venue / settlement currency /
     * broker-specific instrument identity resolved against a specific broker account (e.g. a Saxo
     * EU account: {@code mic=XETR}, {@code currency=EUR}, {@code uic=...}). The {@code mic}/
     * {@code currency}/{@code uic}/{@code assetType} fields are additive and nullable — passing all
     * {@code null} is equivalent to {@link #found(String, String, String, InvestmentType)}.
     */
    public static TickerSearchResult foundOnAccount(String ticker, String companyName, String exchange,
                                                    InvestmentType investmentType, MIC mic,
                                                    Money.Currency currency, Long uic, AssetType assetType) {
        return new TickerSearchResult(ticker, companyName, exchange, investmentType, true,
            String.format("%s (%s) trades on %s and is available for trading.", companyName, ticker, exchange),
            null, mic, currency, uic, assetType,
            TickerResolution.RESOLVED, ConfidenceLevel.MEDIUM, ListingType.PRIMARY,
            List.of(), List.of());
    }

    /**
     * The company exists but has no public listing, and the claim is trustworthy —
     * the answering engine could actually check.
     */
    public static TickerSearchResult notPubliclyTraded(String companyName, String reason) {
        return new TickerSearchResult(null, companyName, null, null, false,
            String.format("%s is not publicly traded. %s", companyName, reason),
            null, null, null, null, null,
            TickerResolution.NOT_LISTED, ConfidenceLevel.MEDIUM, ListingType.UNKNOWN,
            List.of(), List.of());
    }

    /**
     * Nothing could be established either way.
     *
     * <p>Distinct from {@link #notPubliclyTraded} in the one way that matters to a
     * caller: this is an absence of evidence, not evidence of absence. An engine
     * without web access cannot see a listing that happened after its training
     * cutoff, and reporting that as "not publicly traded" turns a gap in
     * knowledge into a confident false denial — which is how a user gets told
     * that a company the platform holds live prices for does not exist.</p>
     */
    public static TickerSearchResult unverified(String companyName, String reason,
                                                List<String> warnings) {
        return new TickerSearchResult(null, companyName, null, null, false, reason,
            null, null, null, null, null,
            TickerResolution.UNVERIFIED, ConfidenceLevel.LOW, ListingType.UNKNOWN,
            List.of(), warnings);
    }

    public static TickerSearchResult notTradableOnBroker(String ticker, String companyName,
                                                         String exchange, String reason) {
        return new TickerSearchResult(ticker, companyName, exchange, InvestmentType.STOCK, false,
            String.format("%s (%s) trades on %s but is not available for trading. %s",
                companyName, ticker, exchange != null ? exchange : "unknown exchange", reason),
            null, null, null, null, null,
            TickerResolution.TRADABLE_ELSEWHERE, ConfidenceLevel.MEDIUM, ListingType.UNKNOWN,
            List.of(), List.of());
    }

    /**
     * Verification contradicted the candidate: the broker's record for the probed
     * ticker names a different company than the one asked about.
     *
     * <p>The ticker is withheld — see the class note on failing closed — and the
     * rejected candidate is carried in {@code alternatives} so the caller can see
     * precisely what was refused and why.</p>
     */
    public static TickerSearchResult ambiguous(String companyName, String message,
                                               List<InstrumentListing> alternatives,
                                               List<String> warnings) {
        return new TickerSearchResult(null, companyName, null, null, false, message,
            null, null, null, null, null,
            TickerResolution.AMBIGUOUS, ConfidenceLevel.LOW, ListingType.UNKNOWN,
            alternatives, warnings);
    }

    /**
     * Returns a copy of this result with dividend info attached.
     */
    public TickerSearchResult withDividendInfo(DividendInfo dividendInfo) {
        return new TickerSearchResult(ticker, companyName, exchange, investmentType, tradableOnBroker, message,
            dividendInfo, mic, currency, uic, assetType, resolution, confidence, listingType,
            alternatives, warnings);
    }

    /** Returns a copy carrying the supplied alternatives and warnings. */
    public TickerSearchResult withContext(List<InstrumentListing> alternatives, List<String> warnings) {
        return new TickerSearchResult(ticker, companyName, exchange, investmentType, tradableOnBroker, message,
            dividendInfo, mic, currency, uic, assetType, resolution, confidence, listingType,
            alternatives, warnings);
    }

    /** Returns a copy with the message, listing type and confidence replaced. */
    public TickerSearchResult withListingContext(ListingType listingType, ConfidenceLevel confidence,
                                                 String message) {
        return new TickerSearchResult(ticker, companyName, exchange, investmentType, tradableOnBroker, message,
            dividendInfo, mic, currency, uic, assetType, resolution, confidence, listingType,
            alternatives, warnings);
    }
}
