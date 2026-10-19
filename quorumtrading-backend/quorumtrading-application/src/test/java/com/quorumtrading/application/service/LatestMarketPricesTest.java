package com.quorumtrading.application.service;

import com.quorumtrading.application.port.out.MarketEventRepository;
import com.quorumtrading.model.enums.EventType;
import com.quorumtrading.model.enums.InvestmentType;
import com.quorumtrading.model.marketdata.MarketEvent;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.Symbol;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A position must be priced in its OWN asset universe.
 *
 * <p>This resolver asked for every ticker as both crypto and equity at once, which says "either
 * kind will do". {@code MockBrokerAdapter.fetchMarketPrice} deliberately does not — an equity
 * bar for a same-named ticker would otherwise be used as a crypto's price — and the resolver has
 * to hold the same line now that it feeds {@code BrokerRouter.getPortfolioValue}, and therefore
 * {@code portfolio_snapshots.total_value_usd} and every return figure derived from it. Real
 * collisions exist: {@code BTC} is both a crypto pair and a listed Bitcoin trust, roughly three
 * orders of magnitude apart.</p>
 */
@DisplayName("LatestMarketPrices — a position is priced in its own asset universe")
class LatestMarketPricesTest {

    private static final Instant AS_OF = Instant.parse("2026-07-30T12:00:00Z");
    private static final Symbol CRYPTO_BTC = new Symbol("BTC", InvestmentType.CRYPTO);
    private static final Symbol STOCK_NVDA = new Symbol("NVDA", InvestmentType.STOCK);

    private MarketEventRepository repository;

    @BeforeEach
    void setUp() {
        repository = mock(MarketEventRepository.class);
        when(repository.findByTickersAndTimeRange(any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of());
    }

    @SuppressWarnings("unchecked")
    private Set<String>[] capturedUniverses() {
        ArgumentCaptor<Set<String>> crypto = ArgumentCaptor.forClass(Set.class);
        ArgumentCaptor<Set<String>> stock = ArgumentCaptor.forClass(Set.class);
        verify(repository).findByTickersAndTimeRange(
                crypto.capture(), stock.capture(), any(), eq(AS_OF), eq(EventType.PRICE), any());
        return new Set[] {crypto.getValue(), stock.getValue()};
    }

    @Test
    @DisplayName("a crypto holding is asked for as crypto only")
    void aCryptoHoldingIsNotAskedForAsAnEquity() {
        LatestMarketPrices.resolve(repository, Set.of(CRYPTO_BTC), AS_OF);

        Set<String>[] universes = capturedUniverses();
        assertThat(universes[0]).containsExactly("BTC");
        assertThat(universes[1])
                .as("asking for BTC as an equity too invites the Bitcoin trust's price")
                .isEmpty();
    }

    @Test
    @DisplayName("an equity holding is asked for as an equity only")
    void anEquityHoldingIsNotAskedForAsCrypto() {
        LatestMarketPrices.resolve(repository, Set.of(STOCK_NVDA), AS_OF);

        Set<String>[] universes = capturedUniverses();
        assertThat(universes[0]).isEmpty();
        assertThat(universes[1]).containsExactly("NVDA");
    }

    @Test
    @DisplayName("a mixed portfolio splits its tickers between the two universes")
    void aMixedPortfolioIsSplitByType() {
        LatestMarketPrices.resolve(repository, Set.of(CRYPTO_BTC, STOCK_NVDA), AS_OF);

        Set<String>[] universes = capturedUniverses();
        assertThat(universes[0]).containsExactly("BTC");
        assertThat(universes[1]).containsExactly("NVDA");
    }

    @Test
    @DisplayName("the resolved price still comes back keyed by the caller's own Symbol")
    void resolvesThePriceForTheRequestedSymbol() {
        when(repository.findByTickersAndTimeRange(any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(new MarketEvent(
                        AS_OF.minusSeconds(60), EventType.PRICE, "test", "BTC",
                        Map.of("price", "63828.87"))));

        Map<Symbol, Money> prices = LatestMarketPrices.resolve(repository, Set.of(CRYPTO_BTC), AS_OF);

        assertThat(prices.get(CRYPTO_BTC).amount()).isEqualByComparingTo("63828.87");
    }

    // --- A closed market is not missing data (prod: QQQ, every weekend, 2026-09/10) ---

    private static final Symbol STOCK_QQQ = new Symbol("QQQ", InvestmentType.STOCK);
    private static final Instant FRIDAY_LAST_TRADE = Instant.parse("2026-10-16T20:37:00Z");

    /** A repository that honours the time window it is asked for, as the real one does. */
    private void storedPrices(MarketEvent... events) {
        when(repository.findByTickersAndTimeRange(any(), any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    Instant from = invocation.getArgument(2);
                    Instant to = invocation.getArgument(3);
                    return java.util.Arrays.stream(events)
                            .filter(e -> !e.timestamp().isBefore(from) && !e.timestamp().isAfter(to))
                            .toList();
                });
    }

    private static MarketEvent price(String ticker, Instant at, String price) {
        return new MarketEvent(at, EventType.PRICE, "test", ticker, Map.of("price", price));
    }

    @Test
    @DisplayName("Monday pre-market still values an equity at Friday's last price (the prod case)")
    void mondayPreMarketUsesFridaysPrice() {
        storedPrices(price("QQQ", FRIDAY_LAST_TRADE, "710.2"));

        Map<Symbol, Money> prices = LatestMarketPrices.resolve(
                repository, Set.of(STOCK_QQQ), Instant.parse("2026-10-19T11:27:54Z"));

        assertThat(prices.get(STOCK_QQQ).amount()).isEqualByComparingTo("710.2");
    }

    @Test
    @DisplayName("Saturday evening and Sunday still value an equity at Friday's last price")
    void weekendUsesFridaysPrice() {
        storedPrices(price("QQQ", FRIDAY_LAST_TRADE, "710.2"));

        assertThat(LatestMarketPrices.resolve(repository, Set.of(STOCK_QQQ),
                Instant.parse("2026-10-17T21:00:00Z"))).containsKey(STOCK_QQQ);
        assertThat(LatestMarketPrices.resolve(repository, Set.of(STOCK_QQQ),
                Instant.parse("2026-10-18T23:00:00Z"))).containsKey(STOCK_QQQ);
    }

    @Test
    @DisplayName("mid-week, an equity price older than 24h is still missing — a stopped feed is not hidden")
    void midWeekGapIsStillMissing() {
        storedPrices(price("QQQ", Instant.parse("2026-10-12T20:00:00Z"), "745.64"));

        assertThat(LatestMarketPrices.resolve(repository, Set.of(STOCK_QQQ),
                Instant.parse("2026-10-14T18:00:00Z"))).doesNotContainKey(STOCK_QQQ);
    }

    @Test
    @DisplayName("on a weekend a crypto price older than 24h is still missing, even next to an equity")
    void cryptoKeepsItsOwnWindowInAMixedPortfolio() {
        storedPrices(
                price("QQQ", FRIDAY_LAST_TRADE, "710.2"),
                price("BTC", Instant.parse("2026-10-17T05:00:00Z"), "63828.87"));

        Map<Symbol, Money> prices = LatestMarketPrices.resolve(
                repository, Set.of(STOCK_QQQ, CRYPTO_BTC), Instant.parse("2026-10-18T10:00:00Z"));

        assertThat(prices).containsKey(STOCK_QQQ);
        assertThat(prices)
                .as("BTC trades all weekend; a 29h-old BTC price is a real gap")
                .doesNotContainKey(CRYPTO_BTC);
    }

    @Test
    @DisplayName("no symbols means no query at all")
    void noSymbolsMeansNoQuery() {
        assertThat(LatestMarketPrices.resolve(repository, Set.of(), AS_OF)).isEmpty();
        verify(repository, org.mockito.Mockito.never())
                .findByTickersAndTimeRange(any(), any(), any(), any(), any(), any());
    }
}
