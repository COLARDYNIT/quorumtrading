package com.quorumtrading.model.valueobject;

import com.quorumtrading.model.enums.AssetType;
import com.quorumtrading.model.enums.InvestmentType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class SymbolTest {

    @ParameterizedTest
    @ValueSource(strings = {"BTC", "ETH", "AAPL", "GOOGL"})
    void shouldCreateValidSymbol(String ticker) {
        Symbol symbol = new Symbol(ticker, InvestmentType.STOCK);

        assertThat(symbol.ticker()).isEqualTo(ticker);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "btc", "123", "1ABC", ".AB", "-X"})
    void shouldRejectInvalidSymbol(String invalidTicker) {
        assertThatThrownBy(() -> new Symbol(invalidTicker, InvestmentType.STOCK))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid symbol format");
    }

    @Test
    void shouldRejectNullType() {
        assertThatThrownBy(() -> new Symbol("BTC", null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("InvestmentType cannot be null");
    }

    @Test
    void shouldPreserveExplicitCryptoType() {
        Symbol symbol = new Symbol("BTC", InvestmentType.CRYPTO);

        assertThat(symbol.type()).isEqualTo(InvestmentType.CRYPTO);
    }

    @Test
    void shouldPreserveExplicitStockType() {
        Symbol symbol = new Symbol("AAPL", InvestmentType.STOCK);

        assertThat(symbol.type()).isEqualTo(InvestmentType.STOCK);
    }

    @Test
    void shouldAllowMarketSpecialTicker() {
        Symbol marketSymbol = new Symbol("MARKET", InvestmentType.STOCK);

        assertThat(marketSymbol.ticker()).isEqualTo("MARKET");
        assertThat(marketSymbol.type()).isEqualTo(InvestmentType.STOCK);
    }

    @Test
    void shouldSupportRecordEquality() {
        Symbol a = new Symbol("BTC", InvestmentType.CRYPTO);
        Symbol b = new Symbol("BTC", InvestmentType.CRYPTO);

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    void shouldNotBeEqualWithDifferentType() {
        Symbol crypto = new Symbol("BTC", InvestmentType.CRYPTO);
        Symbol stock = new Symbol("BTC", InvestmentType.STOCK);

        assertThat(crypto).isNotEqualTo(stock);
    }

    // ---- Saxo/EU keystone (K1) invariants ----------------------------------

    @Nested
    class TwoArgConstructorPreserved {

        @Test
        void delegatesToUsDefaults() {
            Symbol s = new Symbol("AAPL", InvestmentType.STOCK);

            assertThat(s.ticker()).isEqualTo("AAPL");
            assertThat(s.type()).isEqualTo(InvestmentType.STOCK);
            assertThat(s.exchange()).isNull();                 // null ⇒ US default
            assertThat(s.currency()).isEqualTo(Money.Currency.USD);
            assertThat(s.uic()).isNull();
            assertThat(s.assetType()).isNull();
            assertThat(s.isEuropean()).isFalse();
        }
    }

    @Nested
    class CanonicalForm {

        @Test
        void usBareTickerCanonicalIsByteIdenticalToTicker() {
            Symbol us = new Symbol("AAPL", InvestmentType.STOCK);

            assertThat(us.canonical()).isEqualTo("AAPL");
        }

        @Test
        void usDefaultMicStillCanonicalisesToBareTicker() {
            Symbol us = new Symbol("AAPL", InvestmentType.STOCK,
                MIC.US_DEFAULT, Money.Currency.USD, null, null);

            assertThat(us.canonical()).isEqualTo("AAPL");
            assertThat(us.isEuropean()).isFalse();
        }

        @Test
        void europeanSymbolCanonicalIsExchangeQualified() {
            Symbol rhm = new Symbol("RHM", InvestmentType.STOCK,
                new MIC("XETR"), Money.Currency.EUR, 12345L, AssetType.STOCK);

            assertThat(rhm.canonical()).isEqualTo("RHM:XETR");
            assertThat(rhm.isEuropean()).isTrue();
        }
    }

    @Nested
    class EqualsStability {

        @Test
        void equalsIsStableUnderUicEnrichment() {
            // Same canonical+type; one bare, one enriched with US MIC + Uic
            Symbol bare = new Symbol("AAPL", InvestmentType.STOCK);
            Symbol enriched = new Symbol("AAPL", InvestmentType.STOCK,
                MIC.US_DEFAULT, Money.Currency.USD, 99L, AssetType.STOCK);

            assertThat(bare).isEqualTo(enriched);
            assertThat(bare.hashCode()).isEqualTo(enriched.hashCode());
        }

        @Test
        void equalsIgnoresCurrencyAndAssetTypeDifferencesForSameCanonical() {
            Symbol a = new Symbol("RHM", InvestmentType.STOCK,
                new MIC("XETR"), Money.Currency.EUR, 12345L, AssetType.STOCK);
            Symbol b = new Symbol("RHM", InvestmentType.STOCK,
                new MIC("XETR"), Money.Currency.USD, 67890L, AssetType.ETF);

            assertThat(a).isEqualTo(b);
            assertThat(a.hashCode()).isEqualTo(b.hashCode());
        }

        @Test
        void differentExchangesAreNotEqual() {
            Symbol xetr = new Symbol("RHM", InvestmentType.STOCK,
                new MIC("XETR"), Money.Currency.EUR, 1L, AssetType.STOCK);
            Symbol xbru = new Symbol("RHM", InvestmentType.STOCK,
                new MIC("XBRU"), Money.Currency.EUR, 2L, AssetType.STOCK);

            assertThat(xetr).isNotEqualTo(xbru);
        }

        @Test
        void usAndEuListingsOfSameRootAreDistinct() {
            Symbol us = new Symbol("RHM", InvestmentType.STOCK);
            Symbol eu = new Symbol("RHM", InvestmentType.STOCK,
                new MIC("XETR"), Money.Currency.EUR, 1L, AssetType.STOCK);

            assertThat(us).isNotEqualTo(eu);
        }
    }

    @Nested
    class ParseRoundTrip {

        @Test
        void usBareTickerRoundTrips() {
            Symbol parsed = Symbol.parse("AAPL");

            assertThat(parsed.canonical()).isEqualTo("AAPL");
            assertThat(parsed.isEuropean()).isFalse();
        }

        @Test
        void europeanSymbolRoundTripsThroughParseAndCanonical() {
            Symbol original = new Symbol("RHM", InvestmentType.STOCK,
                new MIC("XETR"), Money.Currency.EUR, 12345L, AssetType.STOCK);

            String canonical = original.canonical();        // "RHM:XETR"
            Symbol reparsed = Symbol.parse(canonical);

            assertThat(reparsed.canonical()).isEqualTo(canonical);
            assertThat(reparsed.isEuropean()).isTrue();
            assertThat(reparsed.exchange()).isEqualTo(new MIC("XETR"));
            // identity (canonical+type) preserved across the round-trip
            assertThat(reparsed).isEqualTo(original);
        }

        @Test
        void rejectsBlankParse() {
            assertThatThrownBy(() -> Symbol.parse("  "))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void shouldAcceptSuffixedEuropeanTicker() {
        Symbol s = new Symbol("RHM.DE", InvestmentType.STOCK);

        assertThat(s.ticker()).isEqualTo("RHM.DE");
    }
}
