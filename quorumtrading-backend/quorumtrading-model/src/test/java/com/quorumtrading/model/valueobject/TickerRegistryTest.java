package com.quorumtrading.model.valueobject;

import com.quorumtrading.model.enums.InvestmentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TickerRegistry")
class TickerRegistryTest {

    private static Symbol crypto(String ticker) {
        return new Symbol(ticker, InvestmentType.CRYPTO);
    }

    private static Symbol stock(String ticker) {
        return new Symbol(ticker, InvestmentType.STOCK);
    }

    @Nested
    @DisplayName("asset-class collisions")
    class AssetClassCollisions {

        @Test
        @DisplayName("drops a stock ticker that is a known crypto symbol")
        void dropsCryptoSymbolFromStockUniverse() {
            // A strategy declaring `stock_tickers: [BTC]` put BTC into the stock universe
            // for the whole instance. The equity feed then answered with the NYSE-listed
            // spot-bitcoin ETF that also trades as "BTC" (~$28), and because a price bar's
            // asset class is re-derived from the ticker string on write, that quote landed
            // in the *crypto* BTC series — revaluing live BTC positions from ~$64,000 to
            // ~$28 a unit.
            TickerRegistry registry = new TickerRegistry(Set.of(), Set.of(stock("BTC")));

            assertThat(registry.getStockTickerStrings()).isEmpty();
            assertThat(registry.isEmpty()).isTrue();
        }

        @Test
        @DisplayName("keeps genuine equities alongside a rejected crypto symbol")
        void keepsGenuineEquities() {
            TickerRegistry registry = new TickerRegistry(
                Set.of(),
                Set.of(stock("BTC"), stock("AAPL"), stock("MSFT"))
            );

            assertThat(registry.getStockTickerStrings()).containsExactlyInAnyOrder("AAPL", "MSFT");
        }

        @Test
        @DisplayName("leaves the crypto universe untouched")
        void leavesCryptoUniverseUntouched() {
            TickerRegistry registry = new TickerRegistry(
                Set.of(crypto("BTC"), crypto("ETH")),
                Set.of(stock("AAPL"))
            );

            assertThat(registry.getCryptoTickerStrings()).containsExactlyInAnyOrder("BTC", "ETH");
            assertThat(registry.getStockTickerStrings()).containsExactly("AAPL");
        }

        @Test
        @DisplayName("resolves a symbol declared under both asset classes in favour of crypto")
        void resolvesOverlapInFavourOfCrypto() {
            TickerRegistry registry = new TickerRegistry(Set.of(crypto("ETH")), Set.of(stock("ETH")));

            assertThat(registry.getCryptoTickerStrings()).containsExactly("ETH");
            assertThat(registry.getStockTickerStrings()).isEmpty();
            assertThat(registry.getTotalCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("reports which stock tickers would be rejected")
        void reportsRejectedTickers() {
            Set<Symbol> requested = Set.of(stock("BTC"), stock("DOGE"), stock("AAPL"));

            assertThat(TickerRegistry.rejectedStockTickers(requested))
                .extracting(Symbol::ticker)
                .containsExactlyInAnyOrder("BTC", "DOGE");
        }

        @Test
        @DisplayName("reports nothing to reject for an all-equity set")
        void reportsNothingForEquities() {
            assertThat(TickerRegistry.rejectedStockTickers(Set.of(stock("AAPL"), stock("QQQ")))).isEmpty();
            assertThat(TickerRegistry.rejectedStockTickers(null)).isEmpty();
        }
    }

    @Nested
    @DisplayName("basics")
    class Basics {

        @Test
        @DisplayName("empty() has no tickers")
        void emptyRegistry() {
            assertThat(TickerRegistry.empty().isEmpty()).isTrue();
            assertThat(TickerRegistry.empty().getTotalCount()).isZero();
        }

        @Test
        @DisplayName("null ticker sets are treated as empty")
        void nullSetsAreEmpty() {
            TickerRegistry registry = new TickerRegistry(null, null);

            assertThat(registry.cryptoTickers()).isEmpty();
            assertThat(registry.stockTickers()).isEmpty();
        }

        @Test
        @DisplayName("getAllSymbols combines both asset classes")
        void combinesAssetClasses() {
            TickerRegistry registry = new TickerRegistry(Set.of(crypto("BTC")), Set.of(stock("AAPL")));

            assertThat(registry.getAllSymbols())
                .extracting(Symbol::ticker)
                .containsExactlyInAnyOrder("BTC", "AAPL");
        }
    }
}
