package com.quorumtrading.model.valueobject;

import com.quorumtrading.model.enums.InvestmentType;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link SymbolParser} — the shared "request ticker string → Symbol"
 * helper used by the REST and MCP adapters. The load-bearing guarantees:
 * <ul>
 *   <li>US payloads behave <b>identically</b> to the legacy 2-arg path
 *       ({@code canonical() == ticker}, USD, equal to {@code new Symbol(t, type)}).</li>
 *   <li>Exchange-qualified ({@code ROOT:MIC}) and dotted EU suffix ({@code RHM.DE})
 *       tokens enrich to the right MIC + settlement currency.</li>
 *   <li>A US dotted ticker ({@code BRK.B}) is left untouched as a US ticker.</li>
 * </ul>
 */
class SymbolParserTest {

    @Nested
    class UsBackCompat {

        @ParameterizedTest
        @ValueSource(strings = {"AAPL", "GOOGL", "SPY", "QQQ"})
        void bareUsTickerIsByteIdenticalToLegacyTwoArg(String ticker) {
            Symbol parsed = SymbolParser.parse(ticker, InvestmentType.STOCK);

            assertThat(parsed).isEqualTo(new Symbol(ticker, InvestmentType.STOCK));
            assertThat(parsed.canonical()).isEqualTo(ticker);
            assertThat(parsed.currency()).isEqualTo(Money.Currency.USD);
            assertThat(parsed.isEuropean()).isFalse();
        }

        @Test
        void lowercaseInputIsUpperCasedAndTrimmed() {
            Symbol parsed = SymbolParser.parse("  aapl  ", InvestmentType.STOCK);

            assertThat(parsed.ticker()).isEqualTo("AAPL");
            assertThat(parsed.canonical()).isEqualTo("AAPL");
        }

        @Test
        void usDottedTickerStaysUsAndIsNotTreatedAsEuVenue() {
            // "B" is not a known EU venue suffix → BRK.B stays a US ticker.
            Symbol parsed = SymbolParser.parse("BRK.B", InvestmentType.STOCK);

            assertThat(parsed.isEuropean()).isFalse();
            assertThat(parsed.canonical()).isEqualTo("BRK.B");
            assertThat(parsed.currency()).isEqualTo(Money.Currency.USD);
        }

        @Test
        void cryptoTickerKeepsItsType() {
            Symbol parsed = SymbolParser.parse("BTC", InvestmentType.CRYPTO);

            assertThat(parsed.type()).isEqualTo(InvestmentType.CRYPTO);
            assertThat(parsed.canonical()).isEqualTo("BTC");
        }
    }

    @Nested
    class EuropeanEnrichment {

        @Test
        void colonQualifiedResolvesMicAndEuroCurrency() {
            Symbol parsed = SymbolParser.parse("RHM:XETR", InvestmentType.STOCK);

            assertThat(parsed.ticker()).isEqualTo("RHM");
            assertThat(parsed.exchange()).isEqualTo(new MIC("XETR"));
            assertThat(parsed.currency()).isEqualTo(Money.Currency.EUR);
            assertThat(parsed.isEuropean()).isTrue();
            assertThat(parsed.canonical()).isEqualTo("RHM:XETR");
        }

        @Test
        void dottedEuSuffixResolvesMicAndEuroCurrency() {
            Symbol parsed = SymbolParser.parse("RHM.DE", InvestmentType.STOCK);

            assertThat(parsed.exchange()).isEqualTo(new MIC("XETR"));
            assertThat(parsed.currency()).isEqualTo(Money.Currency.EUR);
            assertThat(parsed.isEuropean()).isTrue();
            assertThat(parsed.canonical()).isEqualTo("RHM:XETR");
        }

        @Test
        void euVenueKeepsCallerAssignedInvestmentType() {
            Symbol parsed = SymbolParser.parse("ASML:XAMS", InvestmentType.STOCK);

            assertThat(parsed.type()).isEqualTo(InvestmentType.STOCK);
            assertThat(parsed.exchange()).isEqualTo(new MIC("XAMS"));
            assertThat(parsed.currency()).isEqualTo(Money.Currency.EUR);
        }

        @Test
        void explicitCurrencyOverridesVenueDefault() {
            // LSE (XLON) is not in the EUR table → defaults to USD; an explicit GBP
            // is unsupported by Money.Currency, but USD override must still win here.
            Symbol parsed = SymbolParser.parse("VOD:XLON", InvestmentType.STOCK, Money.Currency.USD);

            assertThat(parsed.exchange()).isEqualTo(new MIC("XLON"));
            assertThat(parsed.currency()).isEqualTo(Money.Currency.USD);
        }

        @Test
        void unknownDottedSuffixIsNotTreatedAsEuVenue() {
            // ".XYZ" is not a known suffix → left as a (US-default) ticker.
            Symbol parsed = SymbolParser.parse("FOO.XYZ", InvestmentType.STOCK);

            assertThat(parsed.isEuropean()).isFalse();
            assertThat(parsed.canonical()).isEqualTo("FOO.XYZ");
        }
    }

    @Nested
    class AutoType {

        @Test
        void inferStockForUnknownRoot() {
            assertThat(SymbolParser.parseAutoType("AAPL").type()).isEqualTo(InvestmentType.STOCK);
        }

        @Test
        void inferCryptoForKnownCryptoRoot() {
            assertThat(SymbolParser.parseAutoType("BTC").type()).isEqualTo(InvestmentType.CRYPTO);
        }

        @Test
        void inferTypeFromRootStrippingEuSuffix() {
            // The suffix must not derail type inference: classify on the root.
            Symbol parsed = SymbolParser.parseAutoType("RHM.DE");

            assertThat(parsed.type()).isEqualTo(InvestmentType.STOCK);
            assertThat(parsed.exchange()).isEqualTo(new MIC("XETR"));
        }
    }

    @Nested
    class Validation {

        @Test
        void rejectsNullToken() {
            assertThatThrownBy(() -> SymbolParser.parse(null, InvestmentType.STOCK))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void rejectsBlankToken() {
            assertThatThrownBy(() -> SymbolParser.parse("   ", InvestmentType.STOCK))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void rejectsNullType() {
            assertThatThrownBy(() -> SymbolParser.parse("AAPL", null))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }

    /**
     * The reusable MIC → settlement-currency hook the currency-thread relies on when a
     * price's currency must be inferred from where the instrument trades.
     */
    @Nested
    class CurrencyResolution {

        @Test
        void usVenuesResolveToUsd() {
            assertThat(SymbolParser.currencyFor(MIC.US_DEFAULT)).isEqualTo(Money.Currency.USD);
            assertThat(SymbolParser.currencyFor(new MIC("XNYS"))).isEqualTo(Money.Currency.USD);
        }

        @Test
        void nullMicFallsBackToUsd() {
            assertThat(SymbolParser.currencyFor((MIC) null)).isEqualTo(Money.Currency.USD);
        }

        @ParameterizedTest
        @ValueSource(strings = {"XETR", "XPAR", "XAMS", "XBRU", "XMIL", "XMAD", "XLIS"})
        void europeanVenuesResolveToEur(String mic) {
            assertThat(SymbolParser.currencyFor(new MIC(mic))).isEqualTo(Money.Currency.EUR);
        }

        @Test
        void usSymbolResolvesToUsd() {
            Symbol aapl = new Symbol("AAPL", InvestmentType.STOCK);
            assertThat(SymbolParser.currencyFor(aapl)).isEqualTo(Money.Currency.USD);
        }

        @Test
        void europeanSymbolResolvesToEur() {
            Symbol rhm = SymbolParser.parse("RHM:XETR", InvestmentType.STOCK);
            assertThat(rhm.currency()).isEqualTo(Money.Currency.EUR);
            assertThat(SymbolParser.currencyFor(rhm)).isEqualTo(Money.Currency.EUR);
        }

        @Test
        void explicitSymbolCurrencyWinsOverVenueFallback() {
            // Symbol normalises a null currency to USD in its constructor, so an explicit
            // currency is always present; verify it is preferred over the MIC fallback.
            Symbol eurOnUsLikeVenue = new Symbol(
                "X", InvestmentType.STOCK, MIC.US_DEFAULT, Money.Currency.EUR, null, null);
            assertThat(SymbolParser.currencyFor(eurOnUsLikeVenue)).isEqualTo(Money.Currency.EUR);
        }
    }

    /**
     * The "TICKER[:TYPE]" target-asset convention shared by the market-research
     * REST endpoint and MCP tool: the colon delimits an optional asset TYPE
     * ({@code "BTC:CRYPTO"}), NOT a MIC, and a bare ticker defaults to STOCK.
     */
    @Nested
    class TypeSuffixConvention {

        @Test
        void bareTickerDefaultsToStock() {
            Symbol parsed = SymbolParser.parseWithTypeSuffix("AAPL");

            assertThat(parsed.type()).isEqualTo(InvestmentType.STOCK);
            assertThat(parsed.canonical()).isEqualTo("AAPL");
            assertThat(parsed.currency()).isEqualTo(Money.Currency.USD);
        }

        @Test
        void bareCryptoLookingTickerAlsoDefaultsToStock() {
            // Deliberate: the type must be stated explicitly ("BTC:CRYPTO") —
            // no heuristic classification on this path.
            assertThat(SymbolParser.parseWithTypeSuffix("BTC").type())
                .isEqualTo(InvestmentType.STOCK);
        }

        @Test
        void explicitCryptoSuffixIsHonoured() {
            Symbol parsed = SymbolParser.parseWithTypeSuffix("BTC:CRYPTO");

            assertThat(parsed.type()).isEqualTo(InvestmentType.CRYPTO);
            assertThat(parsed.ticker()).isEqualTo("BTC");
        }

        @Test
        void explicitStockSuffixIsHonoured() {
            Symbol parsed = SymbolParser.parseWithTypeSuffix("AAPL:STOCK");

            assertThat(parsed.type()).isEqualTo(InvestmentType.STOCK);
            assertThat(parsed.ticker()).isEqualTo("AAPL");
        }

        @Test
        void typeSuffixIsCaseInsensitiveAndTokenIsTrimmed() {
            Symbol parsed = SymbolParser.parseWithTypeSuffix("  btc : crypto  ");

            assertThat(parsed.type()).isEqualTo(InvestmentType.CRYPTO);
            assertThat(parsed.ticker()).isEqualTo("BTC");
        }

        @Test
        void dottedEuAliasStillEnrichesBeforeTypeSuffix() {
            Symbol parsed = SymbolParser.parseWithTypeSuffix("RHM.DE:STOCK");

            assertThat(parsed.exchange()).isEqualTo(new MIC("XETR"));
            assertThat(parsed.currency()).isEqualTo(Money.Currency.EUR);
            assertThat(parsed.type()).isEqualTo(InvestmentType.STOCK);
        }

        @Test
        void invalidTypeSuffixThrowsWithValidValuesListed() {
            assertThatThrownBy(() -> SymbolParser.parseWithTypeSuffix("AAPL:EQUITY"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("EQUITY")
                .hasMessageContaining("CRYPTO")
                .hasMessageContaining("STOCK");
        }

        @Test
        void nullAndBlankTokensAreRejected() {
            assertThatThrownBy(() -> SymbolParser.parseWithTypeSuffix(null))
                .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> SymbolParser.parseWithTypeSuffix("   "))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void formatEmitsBareTickerForStock() {
            assertThat(SymbolParser.formatWithTypeSuffix(new Symbol("AAPL", InvestmentType.STOCK)))
                .isEqualTo("AAPL");
        }

        @Test
        void formatEmitsExplicitSuffixForCrypto() {
            assertThat(SymbolParser.formatWithTypeSuffix(new Symbol("BTC", InvestmentType.CRYPTO)))
                .isEqualTo("BTC:CRYPTO");
        }

        @Test
        void formatRoundTripsThroughParse() {
            Symbol crypto = new Symbol("ETH", InvestmentType.CRYPTO);
            Symbol stock = new Symbol("MSFT", InvestmentType.STOCK);

            assertThat(SymbolParser.parseWithTypeSuffix(SymbolParser.formatWithTypeSuffix(crypto)))
                .isEqualTo(crypto);
            assertThat(SymbolParser.parseWithTypeSuffix(SymbolParser.formatWithTypeSuffix(stock)))
                .isEqualTo(stock);
        }
    }
}
