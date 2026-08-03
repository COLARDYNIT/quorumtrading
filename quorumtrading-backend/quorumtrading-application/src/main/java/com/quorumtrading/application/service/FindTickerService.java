package com.quorumtrading.application.service;

import com.quorumtrading.application.orchestration.agents.LLMConfig;
import com.quorumtrading.application.orchestration.agents.LLMQueryResult;
import com.quorumtrading.application.port.out.BrokerPort;
import com.quorumtrading.application.port.out.DividendInfoPort;
import com.quorumtrading.application.port.out.LLMPort;
import com.quorumtrading.application.service.InstrumentIdentityVerifier.NameMatch;
import com.quorumtrading.application.service.InstrumentIdentityVerifier.VenueAgreement;
import com.quorumtrading.application.usecase.FindTickerUseCase;
import com.quorumtrading.application.usecase.budget.RecordLLMUsageUseCase;
import com.quorumtrading.application.usecase.query.FindTickerQuery;
import com.quorumtrading.model.enums.ConfidenceLevel;
import com.quorumtrading.model.enums.InvestmentType;
import com.quorumtrading.model.enums.ListingType;
import com.quorumtrading.model.enums.TickerResolution;
import com.quorumtrading.model.valueobject.AssetInfo;
import com.quorumtrading.model.valueobject.BrokerAccountId;
import com.quorumtrading.model.valueobject.DividendInfo;
import com.quorumtrading.model.valueobject.InstrumentListing;
import com.quorumtrading.model.valueobject.LLMModelInfo;
import com.quorumtrading.model.valueobject.LLMUsage;
import com.quorumtrading.model.valueobject.MIC;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.TickerSearchResult;
import com.quorumtrading.model.valueobject.UsageSource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Resolves a company name to a tradable instrument.
 *
 * <p>This is the first step of every trading journey, so its failure modes are
 * expensive: a user who is told their company does not exist stops there, and a
 * user handed the wrong company's ticker trades the wrong company. Both used to
 * happen, for the same underlying reason — the service made a single unchecked
 * hop from "a language model said this symbol" to "this symbol is the answer".</p>
 *
 * <p><b>The resolution pipeline.</b></p>
 * <ol>
 *   <li><b>Enumerate listings, not a ticker.</b> A company is not a symbol; it
 *       has listings, and they differ in venue, currency and instrument type.
 *       The model is asked for all of them — the primary line and any
 *       depositary receipt — so the service can choose the one this broker can
 *       actually reach instead of accepting the first guess.</li>
 *   <li><b>Discard what this platform cannot express.</b> A listing whose ticker
 *       fails the {@code Symbol} grammar (Tokyo's numeric codes) is kept as an
 *       informational alternative but never offered as the answer, because
 *       every downstream tool would reject it.</li>
 *   <li><b>Probe and verify each candidate.</b> The broker is asked about each
 *       usable candidate in turn. Its reply carries the company name and venue
 *       for that symbol, which {@link InstrumentIdentityVerifier} compares
 *       against what was asked for. A name conflict rejects the candidate; a
 *       name match with a venue mismatch identifies a depositary receipt.</li>
 *   <li><b>Report honestly.</b> The outcome is a {@link TickerResolution}, not a
 *       boolean, so "could not check" is distinguishable from "does not
 *       exist" — the distinction that decides whether a user gives up.</li>
 * </ol>
 *
 * <p><b>Contradiction beats assertion.</b> Where the model's claim and the
 * broker's records disagree, the broker wins: it is authoritative about what can
 * be traded, and it cannot be out of date about its own instruments. That is why
 * a model asserting a company is private does not end the search when it has
 * also named a symbol the broker lists as tradable.</p>
 */
public class FindTickerService implements FindTickerUseCase {

    private static final Logger LOG = Logger.getLogger(FindTickerService.class.getName());
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * How many candidate listings are probed against the broker before giving up.
     * Bounds latency for a pathological reply; real companies rarely have more
     * than a primary line, a depositary receipt and a cross-listing worth trying.
     */
    private static final int PROBE_LIMIT = 4;

    private final LLMPort llmPort;
    private final BrokerPort brokerPort;
    private final String defaultOnlineModel;
    private final RecordLLMUsageUseCase recordLLMUsageUseCase;
    private final DividendInfoPort dividendInfoPort;

    public FindTickerService(LLMPort llmPort, BrokerPort brokerPort, String defaultOnlineModel,
                             RecordLLMUsageUseCase recordLLMUsageUseCase, DividendInfoPort dividendInfoPort) {
        this.llmPort = llmPort;
        this.brokerPort = brokerPort;
        this.defaultOnlineModel = defaultOnlineModel;
        this.recordLLMUsageUseCase = recordLLMUsageUseCase;
        this.dividendInfoPort = dividendInfoPort;
    }

    /** Backwards-compatible constructor for existing call sites. */
    public FindTickerService(LLMPort llmPort, BrokerPort brokerPort, String defaultOnlineModel,
                             RecordLLMUsageUseCase recordLLMUsageUseCase) {
        this(llmPort, brokerPort, defaultOnlineModel, recordLLMUsageUseCase, null);
    }

    @Override
    public TickerSearchResult execute(FindTickerQuery query) {
        String companyName = query.companyName();
        LOG.log(Level.INFO, "Finding ticker for company: {0}", companyName);

        recordLLMUsageUseCase.requireAvailableBudget(query.userId());

        LLMQueryResult result = queryModel(companyName, query);
        JsonNode json = parseJsonResponse(result.content());

        // Judge web capability by the model that ACTUALLY served the call (the router may have
        // sent the query to a local engine regardless of the web-capable model we requested).
        boolean webSearchUsed = result.model() != null && llmPort.getModelInfo(result.model())
            .map(LLMModelInfo::supportsWebSearch)
            .orElse(false);

        String resolvedName = text(json, "companyName", companyName);
        String reasoning = text(json, "reasoning", "");
        boolean claimedPublic = json.has("publiclyTraded") && json.get("publiclyTraded").asBoolean();
        InvestmentType investmentType = parseInvestmentType(json);

        List<InstrumentListing> candidates = parseListings(json, investmentType);
        // A model that labels a listing CRYPTO while calling the instrument a STOCK
        // has contradicted itself, and this field is not inert: it becomes
        // backfill_price_data's assetType, where requesting a coin as an equity
        // fetches whichever share happens to collide with the symbol and writes it
        // into the same price series. The listing is the more specific statement,
        // so it wins rather than the contradiction being passed downstream.
        investmentType = reconcileInvestmentType(investmentType, candidates);

        // Nothing to probe: the model named no symbol at all. Whether that means
        // "private" or "I could not find out" depends entirely on whether the
        // engine was in a position to know.
        if (candidates.isEmpty()) {
            return unresolvable(resolvedName, reasoning, claimedPublic, webSearchUsed);
        }

        ProbeReport report = probeCandidates(candidates, resolvedName, investmentType, query.brokerAccountId());

        if (report.winner() != null) {
            return buildResolved(report, resolvedName, investmentType, claimedPublic, query.brokerAccountId());
        }
        return buildUnresolved(report, resolvedName, reasoning, claimedPublic, webSearchUsed,
            investmentType);
    }

    // ---------------------------------------------------------------- probing

    /**
     * Asks the broker about each usable candidate until one verifies.
     *
     * <p>Candidates are tried in the order the model listed them, with the
     * primary listing first by convention. That ordering matters less than it
     * appears: a candidate the broker cannot trade is skipped, so against a US
     * broker a European primary line falls through to the depositary receipt
     * behind it, which is the answer the user can actually act on.</p>
     */
    private ProbeReport probeCandidates(List<InstrumentListing> candidates, String claimedName,
                                        InvestmentType investmentType, BrokerAccountId accountId) {
        List<InstrumentListing> unusable = new ArrayList<>();
        List<InstrumentListing> rejected = new ArrayList<>();
        List<InstrumentListing> unanswered = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        InstrumentListing winner = null;
        AssetInfo winningInfo = null;
        NameMatch winningName = NameMatch.UNKNOWN;
        VenueAgreement winningVenue = VenueAgreement.UNKNOWN;
        boolean brokerUnavailable = false;
        boolean sawConflict = false;
        boolean sawUntradable = false;

        int probes = 0;
        for (InstrumentListing candidate : candidates) {
            if (!candidate.symbolSupported()) {
                unusable.add(candidate);
                continue;
            }
            if (probes >= PROBE_LIMIT) {
                break;
            }
            probes++;

            String ticker = candidate.ticker().toUpperCase();
            Optional<AssetInfo> lookup = accountId != null
                ? brokerPort.getAssetInfo(ticker, accountId)
                : brokerPort.getAssetInfo(ticker);

            if (lookup.isEmpty()) {
                // The broker could not answer at all — this says nothing about the
                // instrument, so it must not be recorded as a rejection. It is kept
                // separately so the candidate can still be surfaced to the user.
                brokerUnavailable = true;
                unanswered.add(candidate);
                continue;
            }

            AssetInfo info = lookup.get();

            // getAssetInfo resolves a bare string against the broker's US EQUITY universe, so a
            // crypto symbol comes back as whichever listed company owns those letters: SOL is
            // Emeren Group Ltd on the NYSE, LINK is Interlink Electronics on NASDAQ. Identity
            // comparison is disabled for crypto (below), so nothing caught it, and the equity's
            // tradability was reported as the coin's — in both directions. Emeren is delisted, so
            // "Solana is not tradable"; Interlink is active, so "your broker trades Chainlink".
            //
            // Only a record that positively identifies as a NON-crypto asset class is treated as
            // a collision. A broker that leaves assetClass unset tells us nothing either way, and
            // must keep its existing behaviour rather than lose every crypto resolution.
            if (investmentType == InvestmentType.CRYPTO && info.isFound()
                    && describesNonCryptoAsset(info)) {
                sawUntradable = true;
                rejected.add(rejectedListing(candidate, String.format(
                    "The broker's record for '%s' is '%s'%s — a listed equity that happens to share"
                        + " the ticker, not the coin. This broker's instrument lookup covers"
                        + " equities, so it cannot say whether '%s' itself is tradable there;"
                        + " backtesting is unaffected.",
                    ticker, info.name(),
                    info.exchange() != null ? " on " + info.exchange() : "",
                    claimedName)));
                LOG.log(Level.INFO,
                    "Crypto ticker {0} collides with equity ''{1}'' — broker verdict not applied",
                    new Object[]{ticker, info.name()});
                continue;
            }

            // Crypto naming is not standardised across venues ("Bitcoin" versus
            // "BTC/USD"), so identity comparison would manufacture conflicts that
            // do not exist. Tradability is the only meaningful check there.
            NameMatch nameMatch = investmentType == InvestmentType.CRYPTO
                ? NameMatch.UNKNOWN
                : InstrumentIdentityVerifier.compareNames(claimedName, info.name());
            VenueAgreement venue = InstrumentIdentityVerifier.compareVenues(
                candidate.exchangeName() != null ? candidate.exchangeName()
                    : (candidate.mic() != null ? candidate.mic().code() : null),
                info.exchange());

            if (InstrumentIdentityVerifier.isIdentityConflict(nameMatch)) {
                sawConflict = true;
                rejected.add(rejectedListing(candidate, String.format(
                    "Rejected: the broker's record for '%s' is '%s'%s, which is a different company"
                        + " than '%s'.",
                    ticker, info.name(), info.exchange() != null ? " on " + info.exchange() : "",
                    claimedName)));
                LOG.log(Level.INFO, "Ticker {0} rejected for ''{1}'': broker reports ''{2}''",
                    new Object[]{ticker, claimedName, info.name()});
                continue;
            }

            if (!info.isFound()) {
                sawUntradable = true;
                rejected.add(rejectedListing(candidate,
                    "Not present in this broker's instrument universe."));
                continue;
            }
            if (!info.tradable()) {
                sawUntradable = true;
                rejected.add(rejectedListing(candidate, String.format(
                    "Present on this broker but not tradable (status: %s).", info.status())));
                continue;
            }

            // A venue disagreement that survives identity verification is not an
            // error — it is the signal that this is a foreign listing of the same
            // company. Callers are told which, rather than left to assume.
            winner = candidate;
            winningInfo = info;
            winningName = nameMatch;
            winningVenue = venue;
            break;
        }

        return new ProbeReport(winner, winningInfo, winningName, winningVenue,
            unusable, rejected, unanswered, warnings, brokerUnavailable, sawConflict, sawUntradable);
    }

    // --------------------------------------------------------------- building

    private TickerSearchResult buildResolved(ProbeReport report, String resolvedName,
                                             InvestmentType investmentType, boolean claimedPublic,
                                             BrokerAccountId accountId) {
        InstrumentListing winner = report.winner();
        AssetInfo info = report.info();
        String ticker = info.ticker() != null ? info.ticker() : winner.ticker().toUpperCase();

        boolean foreignListing = InstrumentIdentityVerifier.indicatesForeignListing(
            report.nameMatch(), report.venue());
        ListingType listingType = resolveListingType(winner, investmentType, foreignListing);

        // A cryptocurrency has no listing venue in the equity sense, so passing the
        // broker's equity venue through produces claims like "Bitcoin trades on
        // ARCA" — NYSE Arca being an equities and ETF market. Reporting no venue is
        // both true and less misleading than reporting the wrong one.
        String exchange = listingType == ListingType.CRYPTO
            ? null
            : (info.exchange() != null ? info.exchange() : winner.exchangeName());

        // Venue and currency belong on the instrument the caller is being handed, not
        // only on the ones it cannot have. Where the broker does not supply them, the
        // resolved listing does — otherwise the single question this tool exists to
        // answer, "what am I actually buying and in what currency", comes back null
        // on every tradable result and populated only on rejected alternatives.
        //
        // But only when the two agree on the venue. For a foreign listing the candidate
        // describes the HOME line while the broker trades the local one, so borrowing
        // its MIC and currency labels a New York receipt as XLON/GBP — a more specific
        // falsehood than the null it replaced. There, the broker's answer stands alone.
        boolean candidateDescribesTradedVenue = !foreignListing;
        MIC mic = info.mic() != null ? info.mic()
            : (candidateDescribesTradedVenue ? winner.mic() : null);
        Money.Currency currency = info.currency() != null ? info.currency()
            : (candidateDescribesTradedVenue ? winner.currency() : null);

        List<String> warnings = new ArrayList<>(report.warnings());
        if (!brokerPort.providesInstrumentUniverse()) {
            // Saying "the broker confirmed this is tradable" when the broker confirms
            // everything is a false assurance, and the user finds out only when a real
            // account rejects the order.
            warnings.add("Tradability was not actually verified: the active broker is a"
                + " simulation broker that accepts every well-formed symbol. Before trading this"
                + " for real, re-check it against the broker account you intend to use.");
        }
        if (!claimedPublic) {
            // The model said private; the broker lists it as tradable. The broker is
            // authoritative about its own instruments, so the model was simply stale —
            // which is exactly how a recently-listed company gets wrongly denied.
            warnings.add("The search engine reported this company as not publicly traded, but the"
                + " broker lists " + ticker + " as tradable. The engine's knowledge appears to be"
                + " out of date; the broker's record was used instead.");
        }

        // A broker that rubber-stamps every symbol cannot corroborate anything, so the
        // result must not claim corroboration it did not get.
        ConfidenceLevel confidence = brokerPort.providesInstrumentUniverse()
            ? confidenceFor(report.nameMatch())
            : ConfidenceLevel.LOW;
        String message = resolvedMessage(resolvedName, ticker, exchange, listingType,
            report.primaryListing(), confidence);

        TickerSearchResult base = accountId != null
            ? TickerSearchResult.foundOnAccount(ticker, resolvedName, exchange, investmentType,
                mic, currency, info.uic(), info.assetType())
            : TickerSearchResult.foundOnAccount(ticker, resolvedName, exchange, investmentType,
                mic, currency, null, null);

        return enrichWithDividendInfo(
            base.withListingContext(listingType, confidence, message)
                .withContext(report.otherListings(), warnings),
            investmentType);
    }

    private TickerSearchResult buildUnresolved(ProbeReport report, String resolvedName, String reasoning,
                                               boolean claimedPublic, boolean webSearchUsed,
                                               InvestmentType investmentType) {
        List<InstrumentListing> context = report.otherListings();
        List<String> warnings = new ArrayList<>(report.warnings());

        // A verification conflict is the most important thing that can have happened:
        // a plausible-looking ticker was found and deliberately withheld.
        if (report.sawConflict()) {
            String message = String.format(
                "Could not confirm a ticker for '%s'. A candidate symbol was found but the broker's"
                    + " record for it names a different company, so it has been withheld rather than"
                    + " returned — acting on it would trade the wrong company. This usually means the"
                    + " company's home-market ticker collides with an unrelated listing elsewhere."
                    + " Try the full legal name, or pass the ticker directly if you know it.",
                resolvedName);
            return TickerSearchResult.ambiguous(resolvedName, message, context, warnings);
        }

        if (report.brokerUnavailable() && !report.sawUntradable()) {
            // Nothing was contradicted; the broker simply could not be reached. Keep the
            // candidate visible so the user can still act on it at their own risk.
            InstrumentListing first = report.firstUsable();
            String ticker = first != null ? first.ticker().toUpperCase() : null;
            String message = String.format(
                "%s (%s) — broker validation unavailable, tradability unknown.",
                resolvedName, ticker != null ? ticker : "no symbol");
            return new TickerSearchResult(ticker, resolvedName,
                first != null ? first.exchangeName() : null, investmentType, false, message,
                null, null, null, null, null,
                TickerResolution.UNVERIFIED, ConfidenceLevel.LOW,
                first != null ? first.type() : ListingType.UNKNOWN, context, warnings);
        }

        // The company and its listings are known; this broker just cannot reach them.
        InstrumentListing primary = report.primaryListing() != null
            ? report.primaryListing() : report.firstListing();
        if (primary != null) {
            // Prefer the concrete reason the broker gave for the specific symbol —
            // "present but not tradable (status: inactive)" tells the user something
            // a generic statement about venue coverage does not, and would be wrong
            // for a US listing that is merely suspended.
            StringBuilder reason = new StringBuilder(report.mostInformativeRejection()
                .map(InstrumentListing::note)
                .orElse("The broker currently configured does not trade that venue."));
            report.firstUnusable().ifPresent(u -> reason.append(' ').append(u.note()));
            String message = String.format("%s (%s) trades on %s but is not available for trading. %s",
                resolvedName, primary.ticker(),
                primary.exchangeName() != null ? primary.exchangeName() : "an unlisted venue",
                reason);
            return new TickerSearchResult(primary.symbolSupported() ? primary.ticker().toUpperCase() : null,
                resolvedName, primary.exchangeName(), investmentType, false, message,
                null, primary.mic(), primary.currency(), null, null,
                TickerResolution.TRADABLE_ELSEWHERE, ConfidenceLevel.MEDIUM, primary.type(),
                context, warnings);
        }

        return unresolvable(resolvedName, reasoning, claimedPublic, webSearchUsed);
    }

    /**
     * The outcome when no symbol was produced at all.
     *
     * <p>The whole point of this branch is that "not listed" and "could not find
     * out" are different answers. Only an engine that could actually check —
     * one with live web access — is allowed to assert the former. An engine
     * answering from training data cannot see a listing that happened after its
     * cutoff, and letting it say "is not publicly traded" turns a gap in its
     * knowledge into a confident denial the user has no reason to doubt.</p>
     */
    private TickerSearchResult unresolvable(String resolvedName, String reasoning,
                                            boolean claimedPublic, boolean webSearchUsed) {
        if (!claimedPublic && webSearchUsed) {
            return TickerSearchResult.notPubliclyTraded(resolvedName, reasoning);
        }

        String message;
        List<String> warnings = new ArrayList<>();
        if (!claimedPublic) {
            message = String.format(
                "Could not confirm whether %s is publicly traded. The engine that answered has no"
                    + " web access, so it answered from training data and a listing made after its"
                    + " cutoff would be invisible to it. %s",
                resolvedName, reasoning).trim();
            warnings.add("This is an unverified negative, not a confirmed absence. If you believe"
                + " the company is listed, pass the ticker directly to backfill_price_data or"
                + " get_price_coverage — those read live market data and will settle it.");
        } else {
            message = String.format(
                "Could not resolve a ticker symbol for %s. %s", resolvedName, reasoning).trim();
            warnings.add("The search identified the company but produced no usable symbol."
                + " Pass the ticker directly if you know it.");
        }
        return TickerSearchResult.unverified(resolvedName, message, warnings);
    }

    private ListingType resolveListingType(InstrumentListing winner, InvestmentType investmentType,
                                           boolean foreignListing) {
        if (investmentType == InvestmentType.CRYPTO) {
            return ListingType.CRYPTO;
        }
        if (winner.type() == ListingType.DEPOSITARY_RECEIPT
            || winner.type() == ListingType.CROSS_LISTING) {
            return winner.type();
        }
        // The model called it a primary listing, but the broker trades it in a
        // different market. The company checked out, so this is a foreign line —
        // most commonly a depositary receipt.
        return foreignListing ? ListingType.DEPOSITARY_RECEIPT : winner.type();
    }

    private ConfidenceLevel confidenceFor(NameMatch nameMatch) {
        return switch (nameMatch) {
            case EXACT, STRONG -> ConfidenceLevel.HIGH;
            case WEAK, UNKNOWN -> ConfidenceLevel.MEDIUM;
            case CONFLICT -> ConfidenceLevel.LOW;
        };
    }

    /**
     * Builds the human-facing sentence, which for a foreign listing must say
     * plainly that the instrument is a proxy — the user asked for a company and
     * is being given a different security that tracks it.
     */
    private String resolvedMessage(String companyName, String ticker, String exchange,
                                   ListingType listingType, InstrumentListing primary,
                                   ConfidenceLevel confidence) {
        if (listingType == ListingType.DEPOSITARY_RECEIPT || listingType == ListingType.CROSS_LISTING) {
            String kind = listingType == ListingType.DEPOSITARY_RECEIPT
                ? "a depositary receipt" : "a secondary listing";
            StringBuilder sb = new StringBuilder(String.format(
                "%s (%s) trades on %s and is available for trading. Note this is %s, not the"
                    + " company's home listing",
                companyName, ticker, exchange, kind));
            if (primary != null) {
                sb.append(" (").append(primary.describe()).append(')');
            }
            sb.append(". Its price tracks the home listing translated through the currency pair,"
                + " so returns include a foreign-exchange component");
            if (primary != null && !primary.symbolSupported()) {
                sb.append("; the home listing itself cannot be traded here");
            }
            sb.append('.');
            return sb.toString();
        }

        // Identity verification is deliberately skipped for crypto, so reporting that
        // the "company identity" behind Bitcoin went unchecked describes a check that
        // was never meaningful and reads as a defect in the answer. Nor does a coin
        // trade "on" a venue the way an equity does.
        if (listingType == ListingType.CRYPTO) {
            return String.format("%s (%s) is available for trading.", companyName, ticker);
        }

        String base = exchange != null
            ? String.format("%s (%s) trades on %s and is available for trading.",
                companyName, ticker, exchange)
            : String.format("%s (%s) is available for trading.", companyName, ticker);
        if (confidence == ConfidenceLevel.MEDIUM) {
            return base + " The broker confirmed the symbol is tradable but exposes no company name"
                + " for it, so the company identity behind this ticker could not be cross-checked.";
        }
        return base;
    }

    // ---------------------------------------------------------------- parsing

    /**
     * Reads the model's reply into candidate listings.
     *
     * <p>Accepts both the structured {@code listings} array and the older flat
     * {@code ticker}/{@code exchange} shape. The fallback is not legacy baggage:
     * the self-hosted default engine is a small local model that frequently
     * ignores the richer schema, and a resolution path that only worked against
     * frontier models would fail precisely the users least able to diagnose it.</p>
     */
    private List<InstrumentListing> parseListings(JsonNode json, InvestmentType investmentType) {
        List<InstrumentListing> listings = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();

        JsonNode array = json.get("listings");
        if (array != null && array.isArray()) {
            for (JsonNode node : array) {
                String ticker = text(node, "ticker", null);
                if (ticker == null || ticker.isBlank()) {
                    continue;
                }
                if (!seen.add(ticker.trim().toUpperCase())) {
                    continue;
                }
                listings.add(InstrumentListing.ofRaw(
                    ticker.trim(),
                    text(node, "exchange", null),
                    text(node, "mic", null),
                    text(node, "currency", null),
                    parseListingType(text(node, "type", null), investmentType)));
            }
        }

        // Flat shape, or a structured reply that also carried a top-level ticker.
        String flatTicker = text(json, "ticker", null);
        if (flatTicker != null && !flatTicker.isBlank()
            && seen.add(flatTicker.trim().toUpperCase())) {
            listings.add(InstrumentListing.ofRaw(
                flatTicker.trim(),
                text(json, "exchange", null),
                text(json, "mic", null),
                text(json, "currency", null),
                investmentType == InvestmentType.CRYPTO ? ListingType.CRYPTO : ListingType.PRIMARY));
        }

        return listings;
    }

    private ListingType parseListingType(String raw, InvestmentType investmentType) {
        if (investmentType == InvestmentType.CRYPTO) {
            return ListingType.CRYPTO;
        }
        if (raw == null || raw.isBlank()) {
            return ListingType.UNKNOWN;
        }
        String normalised = raw.trim().toUpperCase().replace('-', '_').replace(' ', '_');
        return switch (normalised) {
            case "PRIMARY", "PRIMARY_LISTING", "HOME", "ORDINARY" -> ListingType.PRIMARY;
            case "ADR", "ADS", "GDR", "DR", "DEPOSITARY_RECEIPT", "DEPOSITORY_RECEIPT" ->
                ListingType.DEPOSITARY_RECEIPT;
            case "CROSS_LISTING", "CROSSLISTING", "SECONDARY", "SECONDARY_LISTING" ->
                ListingType.CROSS_LISTING;
            case "CRYPTO", "CRYPTOCURRENCY" -> ListingType.CRYPTO;
            default -> ListingType.UNKNOWN;
        };
    }

    /**
     * Resolves a disagreement between the declared investment type and the
     * listings. A listing typed CRYPTO is a concrete claim about one venue entry,
     * where the top-level field is a broad label the model often leaves at its
     * default, so the listing is treated as the stronger evidence.
     */
    private InvestmentType reconcileInvestmentType(InvestmentType declared,
                                                   List<InstrumentListing> candidates) {
        if (declared == InvestmentType.CRYPTO) {
            return declared;
        }
        boolean anyCrypto = candidates.stream().anyMatch(l -> l.type() == ListingType.CRYPTO);
        if (anyCrypto) {
            LOG.log(Level.INFO,
                "Reply declared investmentType=STOCK but listed a CRYPTO listing; treating as CRYPTO");
            return InvestmentType.CRYPTO;
        }
        return declared;
    }

    private InvestmentType parseInvestmentType(JsonNode json) {
        String raw = text(json, "investmentType", "STOCK");
        try {
            return InvestmentType.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException e) {
            return InvestmentType.STOCK;
        }
    }

    private static String text(JsonNode node, String field, String fallback) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return fallback;
        }
        String value = node.get(field).asText();
        return value == null || value.isBlank() ? fallback : value;
    }

    /**
     * Records that probing rejected this listing. {@code tradableHere} is set to
     * false alongside the reason, so a consumer filtering on the field and a human
     * reading the note reach the same conclusion.
     */
    private static InstrumentListing rejectedListing(InstrumentListing listing, String note) {
        return listing.withProbeResult(false, note);
    }

    /**
     * Whether a broker's asset record positively describes something that is not a
     * cryptocurrency — an equity, an ETF, an option.
     *
     * <p>Deliberately conservative: an unset {@code assetClass} returns false. A broker that does
     * not classify its instruments is not asserting that the record is an equity, and treating
     * silence as a collision would strip tradability from every crypto answer such a broker gives.
     * Alpaca populates this faithfully ({@code us_equity} / {@code crypto}), which is where the
     * collision actually arises.</p>
     */
    private static boolean describesNonCryptoAsset(AssetInfo info) {
        String assetClass = info.assetClass();
        if (assetClass == null || assetClass.isBlank()) {
            return false;
        }
        return !assetClass.toLowerCase(java.util.Locale.ROOT).contains("crypto");
    }

    // ------------------------------------------------------------ LLM plumbing

    private LLMQueryResult queryModel(String companyName, FindTickerQuery query) {
        String model = selectWebSearchModel();
        // Enumerating a company's venues produces a longer reply than naming one
        // ticker did. A cap sized for the old single-symbol answer truncates a
        // multi-listing one mid-object, which reaches the parser as malformed JSON
        // and fails the whole search for a company that resolved perfectly well.
        LLMConfig config = new LLMConfig(model, 0.1, 2048);
        LLMQueryResult result;
        try {
            result = llmPort.queryWithUsage(buildPrompt(companyName), config);
        } catch (Exception e) {
            throw new IllegalStateException("LLM query failed for ticker search: " + e.getMessage(), e);
        }
        LLMUsage usage = result.usage();
        if (usage.totalTokens() > 0) {
            recordLLMUsageUseCase.recordUsage(
                query.userId(), usage.billedCost(), usage.rawCost(), usage.totalTokens(), UsageSource.CHAT);
        }
        return result;
    }

    private TickerSearchResult enrichWithDividendInfo(TickerSearchResult result, InvestmentType investmentType) {
        if (dividendInfoPort == null || result.ticker() == null) {
            return result;
        }
        try {
            Set<String> stocks = investmentType == InvestmentType.CRYPTO ? Set.of() : Set.of(result.ticker());
            Set<String> crypto = investmentType == InvestmentType.CRYPTO ? Set.of(result.ticker()) : Set.of();
            Map<String, DividendInfo> info = dividendInfoPort.getDividendInfo(stocks, crypto);
            DividendInfo dividendInfo = info.get(result.ticker());
            // Attach only what was actually established. An unknown lookup used to
            // arrive here as (paysDividends=false, count=0) and was reported as fact,
            // so well-known dividend payers came back declaring they pay none.
            if (dividendInfo != null && dividendInfo.isKnown()) {
                return result.withDividendInfo(dividendInfo);
            }
        } catch (Exception e) {
            LOG.log(Level.FINE, "Failed to enrich ticker search with dividend info: {0}", e.getMessage());
        }
        return result;
    }

    private String selectWebSearchModel() {
        if (llmPort.supportsModelListing()) {
            Optional<LLMModelInfo> webSearchModel = llmPort.getAvailableModels().stream()
                .filter(LLMModelInfo::supportsWebSearch)
                .findFirst();
            if (webSearchModel.isPresent()) {
                return webSearchModel.get().id();
            }
        }
        return defaultOnlineModel;
    }

    /**
     * The resolution prompt.
     *
     * <p>The instruction to enumerate listings rather than pick one is the
     * substantive change here. Asking for "the ticker" of a non-US company
     * forces a choice the model is in no position to make — it does not know
     * which broker the user has — and the old prompt resolved that by demanding
     * the primary-listing ticker, which is what produced a Paris root symbol
     * that collides with an unrelated New York listing. Enumerating instead
     * moves the choice to where the broker is actually known.</p>
     */
    private String buildPrompt(String companyName) {
        return """
            You are a financial reference-data assistant. Identify the company or asset below and \
            enumerate the venues it trades on. Search the web if you have that capability; \
            otherwise answer from your knowledge.

            Company/Asset name: "%s"

            Respond with ONLY a JSON object (no markdown, no explanation), in this exact shape:
            {
              "companyName": "Toyota Motor Corporation",
              "publiclyTraded": true,
              "investmentType": "STOCK",
              "listings": [
                {"ticker": "7203", "exchange": "Tokyo Stock Exchange", "mic": "XTKS", "currency": "JPY", "type": "PRIMARY"},
                {"ticker": "TM", "exchange": "NYSE", "mic": "XNYS", "currency": "USD", "type": "DEPOSITARY_RECEIPT"}
              ],
              "reasoning": "Toyota's home listing is in Tokyo; TM is its NYSE-listed ADR."
            }

            Rules:
            - List EVERY venue you are confident about. Put the company's home ("primary") \
            listing first, then any US depositary receipt (ADR/ADS), then other cross-listings. \
            Listing more is better than listing one: the caller picks whichever its broker can reach.
            - "type" must be one of: PRIMARY, DEPOSITARY_RECEIPT, CROSS_LISTING, CRYPTO.
            - "ticker" must be exactly as THAT venue quotes it. Tokyo quotes Toyota as "7203", \
            not "TM". Do not substitute one venue's symbol for another's.
            - "mic" is the venue's ISO 10383 code; "currency" its ISO 4217 settlement currency. \
            Omit either if you are unsure — do not guess.
            - "investmentType" must be "STOCK" or "CRYPTO". For a cryptocurrency use the base \
            ticker without suffixes ("BTC", not "BTC-USD") and a single listing of type CRYPTO.
            - If the company is NOT publicly traded, set "publiclyTraded" to false and use an \
            empty "listings" array.
            - Do NOT assert that a company is private merely because you do not recognise it, \
            and do not invent a ticker for a company you cannot identify. If you are unsure, say \
            so in "reasoning" — an honest "I am not certain" is more useful than a confident guess, \
            and a company may have listed after your training cutoff.
            - Always include "reasoning" explaining your answer.
            """.formatted(companyName);
    }

    private JsonNode parseJsonResponse(String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalStateException("LLM returned empty response for ticker search");
        }

        String cleaned = content.trim();

        // Remove markdown code block wrappers
        if (cleaned.startsWith("```")) {
            int firstNewline = cleaned.indexOf('\n');
            if (firstNewline > 0) {
                cleaned = cleaned.substring(firstNewline + 1);
            }
            if (cleaned.endsWith("```")) {
                cleaned = cleaned.substring(0, cleaned.length() - 3);
            }
            cleaned = cleaned.trim();
        }

        try {
            return OBJECT_MAPPER.readTree(cleaned);
        } catch (Exception e) {
            // Models without native JSON modes (local CLIs, small local models) often wrap the JSON
            // in prose. Salvage the first balanced top-level object before giving up.
            String embedded = extractFirstJsonObject(cleaned);
            if (embedded != null) {
                try {
                    return OBJECT_MAPPER.readTree(embedded);
                } catch (Exception ignored) {
                    // fall through to the repair attempt below
                }
            }
            // A reply cut off by the token limit is never balanced, so the extractor
            // above cannot find it. The listings that did arrive are still perfectly
            // good, and discarding them fails a search that had already succeeded —
            // so close the open brackets and keep the complete prefix.
            String repaired = repairTruncatedJsonObject(cleaned);
            if (repaired != null) {
                try {
                    JsonNode node = OBJECT_MAPPER.readTree(repaired);
                    // Closing the brackets on a reply that was severed before its first
                    // complete field yields "{}" — syntactically valid and semantically
                    // empty. Accepting that would turn unusable output into a confident
                    // "could not determine", hiding a broken engine behind a plausible
                    // answer. Recovery has to recover something.
                    if (node == null || node.isEmpty()) {
                        throw new IllegalStateException("repair produced no fields");
                    }
                    LOG.log(Level.INFO,
                        "Recovered a truncated ticker-search reply ({0} chars) by closing {1} open"
                            + " container(s); the model''s answer exceeded its output limit.",
                        new Object[]{cleaned.length(), repaired.length() - cleaned.length()});
                    return node;
                } catch (Exception ignored) {
                    // fall through to the error below
                }
            }
            String preview = cleaned.length() > 200 ? cleaned.substring(0, 200) + "..." : cleaned;
            throw new IllegalStateException(
                "Failed to parse LLM ticker search response as JSON: " + e.getMessage() +
                ". Response: " + preview, e);
        }
    }

    /** Returns the first balanced {...} block in the text (brace matching outside string literals), or null. */
    private static String extractFirstJsonObject(String text) {
        int start = text.indexOf('{');
        if (start < 0) {
            return null;
        }
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (escaped) {
                escaped = false;
            } else if (c == '\\') {
                escaped = inString;
            } else if (c == '"') {
                inString = !inString;
            } else if (!inString) {
                if (c == '{') {
                    depth++;
                } else if (c == '}' && --depth == 0) {
                    return text.substring(start, i + 1);
                }
            }
        }
        return null;
    }

    /**
     * Rebuilds parseable JSON from a reply that was cut off mid-structure.
     *
     * <p>Truncation is the characteristic failure of a small local model asked for
     * a list, and it is worth recovering from rather than reporting: the prefix
     * usually contains the primary listing, which is the answer the user wanted.
     * The prefix is trimmed back to the last point where a complete element had
     * been read at the innermost open container, then the open containers are
     * closed in order.</p>
     *
     * @return repaired JSON text, or null if nothing salvageable was found
     */
    private static String repairTruncatedJsonObject(String text) {
        int start = text.indexOf('{');
        if (start < 0) {
            return null;
        }

        java.util.Deque<Character> closers = new java.util.ArrayDeque<>();
        // safeCut[d] = index at which the container open at depth d can be cut and
        // still hold only complete elements.
        int[] safeCut = new int[128];
        boolean inString = false;
        boolean escaped = false;

        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (escaped) {
                escaped = false;
            } else if (c == '\\') {
                escaped = inString;
            } else if (c == '"') {
                inString = !inString;
            } else if (!inString) {
                if (c == '{' || c == '[') {
                    closers.push(c == '{' ? '}' : ']');
                    if (closers.size() < safeCut.length) {
                        safeCut[closers.size()] = i + 1;   // an empty container is valid
                    }
                } else if (c == '}' || c == ']') {
                    if (closers.isEmpty()) {
                        break;
                    }
                    closers.pop();
                    if (closers.isEmpty()) {
                        return text.substring(start, i + 1);   // already balanced
                    }
                    if (closers.size() < safeCut.length) {
                        safeCut[closers.size()] = i + 1;       // after a complete nested element
                    }
                } else if (c == ',' && closers.size() < safeCut.length) {
                    safeCut[closers.size()] = i;               // before the separator
                }
            }
        }

        if (closers.isEmpty() || closers.size() >= safeCut.length) {
            return null;
        }
        int cut = safeCut[closers.size()];
        if (cut <= start) {
            return null;
        }

        StringBuilder repaired = new StringBuilder(text.substring(start, cut));
        while (!closers.isEmpty()) {
            repaired.append(closers.pop());
        }
        return repaired.toString();
    }

    /**
     * What probing the candidate listings established. Carries enough detail for
     * the caller to explain itself: not only which listing won, but which were
     * rejected and for what reason.
     */
    private record ProbeReport(
        InstrumentListing winner,
        AssetInfo info,
        NameMatch nameMatch,
        VenueAgreement venue,
        List<InstrumentListing> unusable,
        List<InstrumentListing> rejected,
        List<InstrumentListing> unanswered,
        List<String> warnings,
        boolean brokerUnavailable,
        boolean sawConflict,
        boolean sawUntradable
    ) {
        /** Every listing except the winner, for reporting as alternatives. */
        List<InstrumentListing> otherListings() {
            List<InstrumentListing> all = new ArrayList<>(rejected);
            all.addAll(unusable);
            return all;
        }

        /**
         * The listing that best explains why nothing resolved — a rejection
         * carries the specific reason ("present but not tradable", "not in this
         * broker's universe"), which is far more useful than a generic venue
         * statement.
         */
        Optional<InstrumentListing> mostInformativeRejection() {
            return rejected.stream().filter(l -> l.note() != null).findFirst();
        }

        /** The home listing, if one was identified. */
        InstrumentListing primaryListing() {
            return allListings().stream()
                .filter(l -> l.type() == ListingType.PRIMARY)
                .findFirst()
                .orElse(null);
        }

        InstrumentListing firstListing() {
            return allListings().stream().findFirst().orElse(null);
        }

        InstrumentListing firstUsable() {
            return allListings().stream().filter(InstrumentListing::symbolSupported).findFirst().orElse(null);
        }

        Optional<InstrumentListing> firstUnusable() {
            return unusable.stream().filter(l -> l.note() != null).findFirst();
        }

        private List<InstrumentListing> allListings() {
            List<InstrumentListing> all = new ArrayList<>();
            if (winner != null) {
                all.add(winner);
            }
            all.addAll(unanswered);
            all.addAll(rejected);
            all.addAll(unusable);
            return all;
        }
    }
}
