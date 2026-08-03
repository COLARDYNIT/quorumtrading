package com.quorumtrading.application.service;

import com.quorumtrading.application.port.out.BrokerPort;
import com.quorumtrading.model.orchestration.StrategyConfig;
import com.quorumtrading.model.valueobject.AssetInfo;
import com.quorumtrading.model.valueobject.Symbol;
import com.quorumtrading.model.valueobject.TickerForm;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Shared helper for validating tickers with the broker.
 * Used by both CreateStrategyService and UpdateStrategyService.
 *
 * Provides rich error messages using AssetInfo when available,
 * falling back to basic validation otherwise.
 */
public class TickerValidationHelper {

    private static final Logger LOG = Logger.getLogger(TickerValidationHelper.class.getName());

    private final BrokerPort brokerPort;

    public TickerValidationHelper(BrokerPort brokerPort) {
        this.brokerPort = brokerPort;
    }

    /**
     * Rejects a crypto ticker written as a trading pair rather than a bare asset.
     *
     * <p>Skipping the broker lookup for crypto (see {@link #validateTickers}) left crypto with
     * <b>no</b> symbol check at all, and {@code Symbol.TICKER_PATTERN} permits {@code -} for US
     * share classes — so {@code BTC-USD} was a syntactically valid symbol that no provider will
     * ever price and no broker will ever trade. The strategy was created ACTIVE and its backtest
     * reported 0 trades and 0.00%, indistinguishable from a run that simply never fired.</p>
     *
     * <p>This is a form check, not an existence check: the platform deliberately does not resolve
     * crypto symbols against the broker's equity universe, because that is what caused the
     * SOL/Emeren collision described in {@link #validateTickers}. A resolution check against the
     * discovered <i>price sources</i> would be strictly better — it is the right next step, not
     * something this form check can do.</p>
     *
     * <p>The rule itself now lives in {@link TickerForm}, so strategy creation and the market-data
     * tools cannot disagree about what a pair form is. It used to live here alone, reachable only
     * from strategy creation, which is why {@code get_price_coverage} happily accepted
     * {@code BTC-USD} and told the user to go and backfill it.</p>
     */
    private void validateCryptoSymbolForm(StrategyConfig config) {
        if (config.getCryptoTickers() == null) {
            return;
        }
        List<String> malformed = new ArrayList<>();
        for (Symbol symbol : config.getCryptoTickers()) {
            String base = TickerForm.cryptoPairBaseOrNull(symbol.ticker());
            if (base != null) {
                malformed.add(String.format("%s (write the bare asset \"%s\" — crypto tickers are"
                    + " the asset alone, never a pair. Quotes are not tradable here in any"
                    + " currency)", symbol.ticker(), base));
            }
        }
        if (!malformed.isEmpty()) {
            throw new IllegalArgumentException(
                "Strategy contains crypto tickers written as trading pairs: "
                    + String.join(", ", malformed)
                    + ". A pair form is accepted by no price source and no broker, so the strategy"
                    + " would run without ever trading. If one of these is genuinely an asset name"
                    + " rather than a pair, say so — it needs adding to the known-asset list.");
        }
    }

    public void validateTickers(StrategyConfig config) {
        // Equities only. getAssetInfo takes a bare ticker string and resolves it against the
        // broker's US equity universe, so a crypto symbol was being looked up as a stock: SOL —
        // the very symbol the strategy-format guide gives as its crypto example — resolved to
        // Emeren Group Ltd on the NYSE, came back inactive, and the strategy was rejected as
        // "non-tradable". Only symbols that collide with a listed equity failed, so LTC and DOGE
        // passed while SOL and other colliding names did not. Crypto symbols are validated where
        // it is meaningful: backfill_price_data fails loudly when no provider serves the pair.
        validateCryptoSymbolForm(config);

        Set<Symbol> allSymbols = new HashSet<>();
        if (config.getStockTickers() != null) {
            allSymbols.addAll(config.getStockTickers());
        }

        if (allSymbols.isEmpty()) {
            return;
        }

        try {
            // First, try to get detailed asset info for rich error messages
            List<String> errorDetails = new ArrayList<>();
            boolean hasDetailedInfo = false;

            for (Symbol symbol : allSymbols) {
                // An exchange-qualified symbol cannot be validated by this lookup at all.
                // getAssetInfo takes a bare root, so RHM:XETR is asked about as "RHM" and
                // AZN:XLON as "AZN" — and where that root also exists as a US listing the
                // broker answers "yes, tradable" about a completely different instrument.
                // The strategy was then accepted and backtested against the US listing in
                // USD while still displaying the venue-qualified symbol, so a user who
                // asked for London silently received New York and was told it worked.
                //
                // Nothing downstream can price a non-US venue either, so there is no
                // answer here other than refusal. Reject before the collision can be
                // mistaken for confirmation.
                if (symbol.isEuropean()) {
                    errorDetails.add(String.format(
                        "%s (exchange-qualified tickers are not supported yet — no configured"
                            + " broker trades %s and no price source covers it. Use find_ticker"
                            + " with the company name to find a US-tradable listing)",
                        symbol.canonical(), symbol.exchange().code()));
                    hasDetailedInfo = true;
                    continue;
                }

                Optional<AssetInfo> assetInfo = brokerPort.getAssetInfo(symbol.ticker());
                if (assetInfo.isPresent()) {
                    hasDetailedInfo = true;
                    AssetInfo info = assetInfo.get();
                    if (!info.tradable()) {
                        if (!info.isFound()) {
                            // Report the symbol AS WRITTEN. The lookup takes a bare root, so
                            // RHM:XETR is asked about as plain "RHM" — and quoting "RHM" back
                            // describes a call the user never made, about a market the broker was
                            // never asked about. Telling them to "verify the ticker symbol" is
                            // worse still when the ticker is correct and the venue is the problem.
                            // Name the BROKER as the constraint, not the market. Exchange support
                            // is a broker capability — Saxo trades European venues — so a message
                            // reading "this market is not supported here" would be wrong the day
                            // an EU-capable broker is attached. Say what is actually true: the
                            // broker currently in use does not carry this venue.
                            //
                            // Do NOT name a broker class as the remedy. This branch fires for ANY
                            // exchange-qualified symbol, not only European ones, and an earlier
                            // wording promising that "an EU-capable broker is needed" told users
                            // asking for Hong Kong to go and obtain a European broker — advice
                            // that cannot work, since the platform has no route to that venue at
                            // all. Describe the obstacle and let the user find the remedy.
                            errorDetails.add(symbol.exchange() != null
                                ? String.format(
                                    "%s (listed on %s — the broker currently configured does not"
                                        + " trade that venue. Use find_ticker to look for a"
                                        + " US-tradable listing of the same company)",
                                    symbol.canonical(), symbol.exchange().code())
                                : String.format(
                                    "%s (not found in the broker's US equity universe — verify the"
                                        + " ticker symbol)",
                                    symbol.canonical()));
                        } else {
                            // Report WHY it cannot be traded without quoting a status that
                            // contradicts the sentence around it: an instrument can be listed
                            // as "active" and still be closed to trading through this broker,
                            // and "not available for trading — status: active" reads as a bug
                            // rather than as the two different facts it actually is.
                            errorDetails.add(String.format(
                                "%s (found on %s as \"%s\", but this broker does not permit"
                                    + " trading in it%s)",
                                symbol.canonical(),
                                info.exchange() != null ? info.exchange() : "unknown exchange",
                                info.name() != null ? info.name() : symbol.ticker(),
                                "active".equalsIgnoreCase(info.status())
                                    ? " — the listing is active but not offered here"
                                    : " — the listing is " + info.status()));
                        }
                    }
                }
            }

            if (hasDetailedInfo && !errorDetails.isEmpty()) {
                throw new IllegalArgumentException(
                    "Strategy contains non-tradable tickers: " + String.join(", ", errorDetails) +
                    ". Use valid US-listed stock tickers or supported crypto symbols."
                );
            }

            // getAssetInfo came back empty for EVERY ticker. A working broker API always answers
            // (tradable / notTradable / notFound) — empty across the board means the broker could
            // not be consulted at all (unreachable, unauthenticated, or no credentials configured).
            // Soft validation by design: warn and allow rather than hard-fail. The old fallback
            // called validateSymbols here, which hit the same broken API, got an empty set back,
            // and rejected every ticker — turning "broker down" into a 400.
            if (!hasDetailedInfo) {
                LOG.log(Level.WARNING,
                    "Broker returned no asset info for any of {0} ticker(s) — broker appears "
                    + "unavailable or unconfigured; skipping ticker validation (soft-pass).",
                    allSymbols.size());
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Throwable e) {
            // Broker service unavailable or infrastructure error — log but allow creation
            LOG.log(Level.WARNING, "Could not validate tickers with broker: {0}", e.getMessage());
        }
    }
}
