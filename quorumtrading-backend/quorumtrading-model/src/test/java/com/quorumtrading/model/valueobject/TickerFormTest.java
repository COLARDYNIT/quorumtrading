package com.quorumtrading.model.valueobject;

import com.quorumtrading.model.enums.InvestmentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The point of {@link TickerForm} is that three market-data tools stop disagreeing about the same
 * string, so most of these tests are about <em>agreement</em>: one verdict, whatever the caller.
 */
class TickerFormTest {

    @Nested
    @DisplayName("pair forms")
    class PairForms {

        @ParameterizedTest
        @ValueSource(strings = {"BTC-USD", "BTC/USD", "BTCUSD", "BTCUSDT", "BTCEUR", "ETHBTC"})
        @DisplayName("are refused in every quote currency, as the guide promises")
        void pairFormsAreRefused(String pair) {
            TickerForm.Verdict verdict = TickerForm.check(pair, InvestmentType.CRYPTO);

            assertThat(verdict.acceptable()).isFalse();
            assertThat(verdict.problem()).isEqualTo(TickerForm.Problem.PAIR_FORM);
            assertThat(verdict.message()).contains("trading pair");
        }

        @Test
        @DisplayName("name the bare asset the user should have written")
        void namesTheCorrection() {
            TickerForm.Verdict verdict = TickerForm.check("BTC-USD", InvestmentType.CRYPTO);

            assertThat(verdict.correction()).isEqualTo("BTC");
            assertThat(verdict.message()).contains("\"BTC\"");
        }

        @Test
        @DisplayName("are caught without a declared asset class — get_price_coverage has none")
        void separatorPairsAreCaughtWithoutAType() {
            TickerForm.Verdict verdict = TickerForm.check("BTC-USD");

            assertThat(verdict.problem()).isEqualTo(TickerForm.Problem.PAIR_FORM);
            assertThat(verdict.correction()).isEqualTo("BTC");
        }

        @Test
        @DisplayName("are caught for coins the platform has never heard of")
        void unknownBasePairsAreStillPairs() {
            // InvestmentType.fromTicker knows a fixed list of coins, so FAKECOIN-USD classifies
            // as STOCK. Inferring the type first and only then testing for a pair let this form
            // through — the check has to come first.
            TickerForm.Verdict verdict = TickerForm.check("FAKECOIN-USD");

            assertThat(verdict.problem()).isEqualTo(TickerForm.Problem.PAIR_FORM);
            assertThat(verdict.correction()).isEqualTo("FAKECOIN");
        }

        @Test
        @DisplayName("do not swallow a US share class")
        void shareClassSuffixIsNotAPair() {
            assertThat(TickerForm.check("BRK-B").acceptable()).isTrue();
            assertThat(TickerForm.check("BRK.B").acceptable()).isTrue();
        }

        @ParameterizedTest
        @ValueSource(strings = {"USDT", "USDC", "AXLUSDC", "SUSD", "WBTC", "STETH"})
        @DisplayName("do not swallow assets whose own name ends in a quote currency")
        void assetsNamedAfterAQuoteAreNotPairs(String asset) {
            assertThat(TickerForm.check(asset, InvestmentType.CRYPTO).acceptable()).isTrue();
        }
    }

    @Nested
    @DisplayName("venue-qualified symbols")
    class VenueQualified {

        @Test
        @DisplayName("resolve identically however the caller arrives — the divergence this class exists to end")
        void colonFormResolvesRatherThanThrowing() {
            // get_price_coverage built `new Symbol(ticker, STOCK)` and threw "Invalid symbol
            // format" on this, while backfill_price_data parsed it, called three live providers
            // and blamed coverage. Same string, two answers.
            Symbol resolved = TickerForm.resolve("ASML:XAMS", InvestmentType.STOCK);

            assertThat(resolved.canonical()).isEqualTo("ASML:XAMS");
            assertThat(resolved.isEuropean()).isTrue();
        }

        @Test
        @DisplayName("accept the dotted alias and canonicalise it the same way")
        void dottedSuffixResolves() {
            assertThat(TickerForm.resolve("ASML.AS", InvestmentType.STOCK).canonical())
                .isEqualTo("ASML:XAMS");
        }
    }

    @Nested
    @DisplayName("malformed input")
    class Malformed {

        @ParameterizedTest
        @ValueSource(strings = {"", "   ", "1234", "!!", ":XAMS", "ASML:"})
        @DisplayName("is reported as a form problem, not as missing data")
        void malformedIsMalformed(String token) {
            assertThat(TickerForm.check(token).problem()).isEqualTo(TickerForm.Problem.MALFORMED);
        }

        @Test
        @DisplayName("null is a form problem rather than a NullPointerException")
        void nullIsHandled() {
            assertThat(TickerForm.check(null).problem()).isEqualTo(TickerForm.Problem.MALFORMED);
        }
    }

    @Nested
    @DisplayName("resolve")
    class Resolve {

        @Test
        @DisplayName("upper-cases and trims like every other entry point")
        void normalises() {
            assertThat(TickerForm.resolve(" aapl ", InvestmentType.STOCK).ticker()).isEqualTo("AAPL");
        }

        @Test
        @DisplayName("infers the asset class when the caller has none")
        void infersType() {
            assertThat(TickerForm.resolve("BTC").type()).isEqualTo(InvestmentType.CRYPTO);
            assertThat(TickerForm.resolve("AAPL").type()).isEqualTo(InvestmentType.STOCK);
        }

        @Test
        @DisplayName("refuses a pair before anything can dispatch on it")
        void refusesPairForms() {
            assertThatThrownBy(() -> TickerForm.resolve("BTC-USD"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("trading pair")
                .hasMessageContaining("BTC");
        }

        @Test
        @DisplayName("keeps the caller's asset class rather than re-guessing it")
        void honoursExplicitType() {
            assertThat(TickerForm.resolve("BTC", InvestmentType.CRYPTO).type())
                .isEqualTo(InvestmentType.CRYPTO);
        }
    }

    @Nested
    @DisplayName("edges")
    class Edges {

        @Test
        @DisplayName("a null asset class falls back to inference rather than throwing")
        void nullTypeInfers() {
            assertThat(TickerForm.check("BTC-USD", null).problem())
                .isEqualTo(TickerForm.Problem.PAIR_FORM);
            assertThat(TickerForm.check("AAPL", null).acceptable()).isTrue();
        }

        @Test
        @DisplayName("blank input is malformed whichever overload is used")
        void blankIsMalformedWithAType() {
            assertThat(TickerForm.check("  ", InvestmentType.STOCK).problem())
                .isEqualTo(TickerForm.Problem.MALFORMED);
            assertThat(TickerForm.check(null, InvestmentType.CRYPTO).problem())
                .isEqualTo(TickerForm.Problem.MALFORMED);
        }

        @Test
        @DisplayName("an equity written against a quote currency is a pair too")
        void stockPairForm() {
            TickerForm.Verdict verdict = TickerForm.check("AAPL-USD", InvestmentType.STOCK);

            assertThat(verdict.problem()).isEqualTo(TickerForm.Problem.PAIR_FORM);
            assertThat(verdict.correction()).isEqualTo("AAPL");
        }

        @Test
        @DisplayName("a crypto ticker with any separator is a pair, quote currency or not")
        void anySeparatorIsAPairForCrypto() {
            // Crypto assets carry no share-class suffix, so a hyphen can be nothing else.
            assertThat(TickerForm.cryptoPairBaseOrNull("BTC-XYZ")).isEqualTo("BTC");
            assertThat(TickerForm.cryptoPairBaseOrNull(null)).isNull();
            assertThat(TickerForm.cryptoPairBaseOrNull("BTC")).isNull();
        }

        @Test
        @DisplayName("a malformed ticker with an explicit asset class is still malformed")
        void malformedWithType() {
            assertThat(TickerForm.check("1234", InvestmentType.STOCK).problem())
                .isEqualTo(TickerForm.Problem.MALFORMED);
        }
    }
}
