package com.quorumtrading.application.service;

import com.quorumtrading.application.port.out.MarketEventRepository;
import com.quorumtrading.model.enums.EventType;
import com.quorumtrading.model.enums.InvestmentType;
import com.quorumtrading.model.service.PriceLookbackWindow;
import com.quorumtrading.model.marketdata.MarketEvent;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.Symbol;
import com.quorumtrading.model.valueobject.SymbolParser;
import org.jboss.logging.Logger;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Resolves the most recent market price per symbol from the PRICE event stream.
 *
 * <p>Exists so that a portfolio snapshot's per-position {@code currentPrice} is priced at the
 * same moment as its {@code totalValue}. A snapshot whose positions carry prices from hours
 * earlier does not reconcile as {@code cash + sum(qty x currentPrice)}, which makes every
 * downstream consumer of the row — charts, benchmarks, performance maths — disagree with the
 * total it sits next to.</p>
 *
 * <p>No fallbacks: a symbol with no price event in the look-back window is simply absent from
 * the returned map, rather than being priced at cost basis and presented as a market value.</p>
 *
 * <p>The look-back window is per asset universe ({@link PriceLookbackWindow}): 24 hours for crypto
 * and on an ordinary trading day, stretched over weekends and US market holidays for exchange-listed
 * instruments. A flat 24 hours treated every closed weekend as missing data, so LIVE simulations
 * holding equities skipped every snapshot from Saturday evening to Monday pre-market.</p>
 */
final class LatestMarketPrices {

    private static final Logger LOG = Logger.getLogger(LatestMarketPrices.class);

    /** How far back a price may lie per symbol before the symbol is treated as unpriced. */
    private static final PriceLookbackWindow LOOKBACK = new PriceLookbackWindow();

    private LatestMarketPrices() {
    }

    /** The tickers whose type either matches {@code type} ({@code matching}) or does not. */
    private static Set<String> tickersOfType(Set<Symbol> symbols, InvestmentType type, boolean matching) {
        return symbols.stream()
                .filter(symbol -> (symbol.type() == type) == matching)
                .map(Symbol::ticker)
                .collect(Collectors.toSet());
    }

    /**
     * Resolve the latest price at or before {@code asOf} for each of the given symbols.
     *
     * @param marketEventRepository source of PRICE events
     * @param symbols               the symbols to price (typically the portfolio's positions)
     * @param asOf                  upper bound — prices after this instant are rejected
     * @return symbol to latest price; symbols without a usable price are absent
     */
    static Map<Symbol, Money> resolve(
            MarketEventRepository marketEventRepository,
            Set<Symbol> symbols,
            Instant asOf) {

        Map<Symbol, Money> prices = new HashMap<>();
        if (symbols == null || symbols.isEmpty()) {
            return prices;
        }

        // Ask for each holding in its OWN universe. Passing one ticker set as both the crypto and
        // the equity argument says "either kind will do", and the two can be orders of magnitude
        // apart for the same string: BTC is a crypto pair and also a listed Bitcoin trust.
        // MockBrokerAdapter.fetchMarketPrice already splits for exactly this reason, and this
        // resolver has to as well now that it prices BrokerRouter.getPortfolioValue — a crypto
        // position picking up an equity's price lands straight in total_value_usd and every
        // return figure derived from it.
        Set<String> cryptoTickers = tickersOfType(symbols, InvestmentType.CRYPTO, true);
        Set<String> stockTickers = tickersOfType(symbols, InvestmentType.CRYPTO, false);

        // A price event carries only a bare ticker — no venue, no currency. Index the caller's
        // full Symbols by ticker so the resolved price is keyed by the SAME Symbol the caller
        // holds (EU symbols canonicalize to "ROOT:MIC", not the bare ticker) and is stamped in
        // the instrument's own currency.
        Map<String, Symbol> symbolByTicker = new HashMap<>();
        for (Symbol symbol : symbols) {
            symbolByTicker.putIfAbsent(symbol.ticker(), symbol);
        }

        // One query wide enough for the most generous window; each event is then held to its own
        // symbol's window below, so a crypto price never borrows an equity's weekend allowance.
        Map<Symbol, Instant> earliestBySymbol = new HashMap<>();
        for (Symbol symbol : symbolByTicker.values()) {
            earliestBySymbol.put(symbol, LOOKBACK.earliestAcceptable(symbol.type(), asOf));
        }
        Instant from = earliestBySymbol.values().stream().min(Instant::compareTo).orElse(asOf);

        List<MarketEvent> priceEvents = marketEventRepository.findByTickersAndTimeRange(
                cryptoTickers, stockTickers, from, asOf, EventType.PRICE, null);

        // Track which event each price came from so a later event never loses to an earlier
        // one. The repository returns rows ORDER BY timestamp DESC, so a plain put-per-event
        // would leave the OLDEST price in the window as the winner.
        Map<Symbol, Instant> priceTimestamps = new HashMap<>();

        for (MarketEvent event : priceEvents) {
            if (event.timestamp().isAfter(asOf)) {
                LOG.warnf("Rejecting future price for %s at %s (as-of %s)",
                        event.symbol(), event.timestamp(), asOf);
                continue;
            }

            Object priceObj = event.rawData().get("price");
            if (priceObj == null) {
                continue;
            }

            Symbol symbol = symbolByTicker.get(event.symbol());
            if (symbol == null || event.timestamp().isBefore(earliestBySymbol.get(symbol))) {
                continue;
            }

            Instant chosen = priceTimestamps.get(symbol);
            if (chosen != null && !event.timestamp().isAfter(chosen)) {
                continue;
            }

            try {
                prices.put(symbol, Money.of(
                        new BigDecimal(priceObj.toString()), SymbolParser.currencyFor(symbol)));
                priceTimestamps.put(symbol, event.timestamp());
            } catch (Exception e) {
                LOG.warnf("Failed to parse price for %s: %s", event.symbol(), e.getMessage());
            }
        }

        for (Symbol symbol : symbols) {
            if (!prices.containsKey(symbol)) {
                LOG.debugf("No price for %s since %s (as-of %s)",
                        symbol.ticker(), LOOKBACK.earliestAcceptable(symbol.type(), asOf), asOf);
            }
        }

        return prices;
    }
}
