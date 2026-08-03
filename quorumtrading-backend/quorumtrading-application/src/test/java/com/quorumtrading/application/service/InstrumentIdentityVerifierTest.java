package com.quorumtrading.application.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static com.quorumtrading.application.service.InstrumentIdentityVerifier.NameMatch;
import static com.quorumtrading.application.service.InstrumentIdentityVerifier.VenueAgreement;
import static com.quorumtrading.application.service.InstrumentIdentityVerifier.compareNames;
import static com.quorumtrading.application.service.InstrumentIdentityVerifier.compareVenues;
import static com.quorumtrading.application.service.InstrumentIdentityVerifier.indicatesForeignListing;
import static com.quorumtrading.application.service.InstrumentIdentityVerifier.isIdentityConflict;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The verifier is the whole basis on which resolution decides whether a ticker
 * belongs to the company that was asked for, so its two failure directions are
 * tested separately and deliberately:
 *
 * <ul>
 *   <li><b>False negatives</b> (calling a real match a conflict) would break the
 *       common path — every backtest against a nameless mock broker, every
 *       company whose broker name carries share-class decoration.</li>
 *   <li><b>False positives</b> (missing a genuine conflict) are the bug this
 *       exists to fix: a user trading an investment bank because they asked for
 *       a luxury-goods conglomerate.</li>
 * </ul>
 */
class InstrumentIdentityVerifierTest {

    @Nested
    @DisplayName("catching the wrong company")
    class Conflicts {

        @Test
        @DisplayName("LVMH must not match Moelis & Company — the bug that started this")
        void lvmhIsNotMoelis() {
            // Both are quoted "MC": LVMH on Euronext Paris, Moelis on NYSE. The broker
            // confirms "MC" is tradable, but it is confirming the wrong company.
            NameMatch match = compareNames("LVMH Moët Hennessy Louis Vuitton", "Moelis & Company");

            assertThat(match)
                .as("nothing but the shared ticker string connects these two companies")
                .isEqualTo(NameMatch.CONFLICT);
            assertThat(isIdentityConflict(match)).isTrue();
        }

        @Test
        @DisplayName("Airbus must not match AAR Corp — same collision, second instance")
        void airbusIsNotAarCorp() {
            assertThat(compareNames("Airbus SE", "AAR Corp")).isEqualTo(NameMatch.CONFLICT);
        }

        @Test
        @DisplayName("a shared corporate suffix alone is not a match")
        void sharedLegalFormIsNotIdentity() {
            // Both end in "Company"/"Corporation"; stripping those leaves nothing in common.
            assertThat(compareNames("Moelis & Company", "Ford Motor Company"))
                .isEqualTo(NameMatch.CONFLICT);
        }
    }

    @Nested
    @DisplayName("not breaking genuine matches")
    class Matches {

        @Test
        @DisplayName("identical names ignoring legal form and punctuation")
        void exactAfterNormalisation() {
            assertThat(compareNames("Apple Inc.", "Apple Inc")).isEqualTo(NameMatch.EXACT);
            assertThat(compareNames("Toyota Motor Corporation", "Toyota Motor Corp"))
                .isEqualTo(NameMatch.EXACT);
        }

        @Test
        @DisplayName("accents are folded, so Moët matches Moet")
        void diacriticsAreFolded() {
            assertThat(compareNames("LVMH Moët Hennessy", "LVMH Moet Hennessy"))
                .isEqualTo(NameMatch.EXACT);
        }

        @Test
        @DisplayName("ampersands survive as part of the token, so AT&T matches itself")
        void ampersandIsNotASeparator() {
            assertThat(compareNames("AT&T Inc.", "AT&T Inc")).isEqualTo(NameMatch.EXACT);
        }

        @Test
        @DisplayName("broker share-class decoration does not break the match")
        void shareClassDecorationIsIgnored() {
            // Alpaca names carry "Class B Common Stock" and similar trailing decoration.
            assertThat(compareNames("Berkshire Hathaway Inc.",
                "Berkshire Hathaway Inc. Class B Common Stock"))
                .isIn(NameMatch.EXACT, NameMatch.STRONG);
            assertThat(compareNames("Alphabet Inc.", "Alphabet Inc. Class A Common Stock"))
                .isIn(NameMatch.EXACT, NameMatch.STRONG);
        }

        @Test
        @DisplayName("a depositary-receipt name still matches its company")
        void adrDecorationIsIgnored() {
            assertThat(compareNames("Toyota Motor Corporation",
                "Toyota Motor Corp American Depositary Shares"))
                .isIn(NameMatch.EXACT, NameMatch.STRONG);
        }

        @Test
        @DisplayName("one name containing the other is a strong match")
        void containmentIsStrong() {
            assertThat(compareNames("Rheinmetall", "Rheinmetall AG Vorzugsaktien"))
                .isIn(NameMatch.EXACT, NameMatch.STRONG);
        }
    }

    @Nested
    @DisplayName("refusing to guess without evidence")
    class MissingEvidence {

        @Test
        @DisplayName("a broker that reports no name yields UNKNOWN, never a conflict")
        void nullBrokerNameIsUnknown() {
            // The mock broker backing every backtest exposes no company name. Treating
            // that as a conflict would break the common path to fix the rare one.
            assertThat(compareNames("Apple Inc.", null)).isEqualTo(NameMatch.UNKNOWN);
            assertThat(compareNames("Apple Inc.", "  ")).isEqualTo(NameMatch.UNKNOWN);
            assertThat(isIdentityConflict(compareNames("Apple Inc.", null))).isFalse();
        }

        @Test
        @DisplayName("a name made entirely of corporate noise yields UNKNOWN")
        void contentFreeNameIsUnknown() {
            assertThat(compareNames("Apple Inc.", "Holdings Group Ltd")).isEqualTo(NameMatch.UNKNOWN);
        }
    }

    @Nested
    @DisplayName("placing venues")
    class Venues {

        @Test
        @DisplayName("Paris versus New York is a regional disagreement")
        void europeVersusUs() {
            assertThat(compareVenues("Euronext Paris", "NYSE"))
                .isEqualTo(VenueAgreement.DIFFERENT_REGION);
            assertThat(compareVenues("Tokyo Stock Exchange", "NYSE"))
                .isEqualTo(VenueAgreement.DIFFERENT_REGION);
        }

        @Test
        @DisplayName("two US venues agree even when named differently")
        void usVenuesAgree() {
            assertThat(compareVenues("NASDAQ", "NASDAQ")).isEqualTo(VenueAgreement.AGREE);
            assertThat(compareVenues("NYSE", "XNYS")).isEqualTo(VenueAgreement.AGREE);
            assertThat(compareVenues("NASDAQ", "NYSE Arca")).isEqualTo(VenueAgreement.AGREE);
        }

        @Test
        @DisplayName("MICs and human names are placed the same way")
        void micsAndNamesInterchange() {
            assertThat(compareVenues("XETR", "Frankfurt Stock Exchange"))
                .isEqualTo(VenueAgreement.AGREE);
            assertThat(compareVenues("XPAR", "NASDAQ")).isEqualTo(VenueAgreement.DIFFERENT_REGION);
        }

        @Test
        @DisplayName("Nasdaq's European venues are European, not US")
        void nasdaqNordicIsNotUs() {
            // "NASDAQ Stockholm" contains "NASDAQ"; a naive US match would misplace it
            // and turn a genuine ADR signal into a false agreement.
            assertThat(compareVenues("NASDAQ Stockholm", "NYSE"))
                .isEqualTo(VenueAgreement.DIFFERENT_REGION);
            assertThat(compareVenues("NASDAQ Copenhagen", "XETR"))
                .isEqualTo(VenueAgreement.AGREE);
        }

        @Test
        @DisplayName("unplaceable and synthetic venues yield UNKNOWN, not a contradiction")
        void syntheticVenuesAreUnknown() {
            assertThat(compareVenues("MOCK", "NASDAQ")).isEqualTo(VenueAgreement.UNKNOWN);
            assertThat(compareVenues(null, "NASDAQ")).isEqualTo(VenueAgreement.UNKNOWN);
            assertThat(compareVenues("NASDAQ", "")).isEqualTo(VenueAgreement.UNKNOWN);
        }
    }

    @Nested
    @DisplayName("distinguishing a depositary receipt from a wrong company")
    class ForeignListing {

        @Test
        @DisplayName("same company on a different continent is a foreign listing")
        void nameAgreesVenueDiffers() {
            // Toyota: the search says Tokyo, the broker says NYSE, and the names agree.
            // That is an ADR — tradable, but a USD proxy for the Tokyo line.
            NameMatch name = compareNames("Toyota Motor Corporation", "Toyota Motor Corp");
            VenueAgreement venue = compareVenues("Tokyo Stock Exchange", "NYSE");

            assertThat(indicatesForeignListing(name, venue)).isTrue();
            assertThat(isIdentityConflict(name)).isFalse();
        }

        @Test
        @DisplayName("a different company on a different continent is NOT a foreign listing")
        void nameDiffersVenueDiffers() {
            // Same venue disagreement, opposite conclusion — this is the LVMH case, and
            // it must never be dressed up as an ADR.
            NameMatch name = compareNames("LVMH Moët Hennessy Louis Vuitton", "Moelis & Company");
            VenueAgreement venue = compareVenues("Euronext Paris", "NYSE");

            assertThat(indicatesForeignListing(name, venue)).isFalse();
            assertThat(isIdentityConflict(name)).isTrue();
        }

        @Test
        @DisplayName("agreeing venues are never a foreign listing")
        void sameRegionIsNotForeign() {
            assertThat(indicatesForeignListing(NameMatch.EXACT, VenueAgreement.AGREE)).isFalse();
        }

        @Test
        @DisplayName("an unverifiable name is not enough to claim a foreign listing")
        void unknownNameDoesNotImplyAdr() {
            // Without a name to corroborate, a venue disagreement is equally consistent
            // with an ADR and with the wrong company. Claiming ADR would be a guess.
            assertThat(indicatesForeignListing(NameMatch.UNKNOWN, VenueAgreement.DIFFERENT_REGION))
                .isFalse();
            assertThat(indicatesForeignListing(NameMatch.WEAK, VenueAgreement.DIFFERENT_REGION))
                .isFalse();
        }
    }
}
