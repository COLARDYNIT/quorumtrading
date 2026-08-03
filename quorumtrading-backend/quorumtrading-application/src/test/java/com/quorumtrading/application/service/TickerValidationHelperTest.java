package com.quorumtrading.application.service;

import com.quorumtrading.application.port.out.BrokerPort;
import com.quorumtrading.model.enums.InvestmentType;
import com.quorumtrading.model.orchestration.StrategyConfig;
import com.quorumtrading.model.valueobject.AssetInfo;
import com.quorumtrading.model.valueobject.Symbol;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Broker ticker validation resolves a bare ticker against the broker's US equity universe, so it
 * must only ever be handed equities. Several crypto symbols collide with listed stocks — SOL is
 * Emeren Group Ltd on the NYSE — and looking one up as a stock rejected a perfectly valid crypto
 * strategy.
 */
class TickerValidationHelperTest {

    private BrokerPort brokerPort;
    private TickerValidationHelper helper;

    @BeforeEach
    void setUp() {
        brokerPort = mock(BrokerPort.class);
        helper = new TickerValidationHelper(brokerPort);
    }

    private static StrategyConfig configWith(Set<Symbol> stocks, Set<Symbol> crypto) {
        StrategyConfig config = new StrategyConfig();
        if (stocks != null) {
            config.setStockTickers(stocks);
        }
        if (crypto != null) {
            config.setCryptoTickers(crypto);
        }
        return config;
    }

    private static Set<Symbol> crypto(String... tickers) {
        Set<Symbol> set = new LinkedHashSet<>();
        for (String t : tickers) {
            set.add(new Symbol(t, InvestmentType.CRYPTO));
        }
        return set;
    }

    private static Set<Symbol> stocks(String... tickers) {
        Set<Symbol> set = new LinkedHashSet<>();
        for (String t : tickers) {
            set.add(new Symbol(t, InvestmentType.STOCK));
        }
        return set;
    }

    @Nested
    @DisplayName("Crypto tickers are not validated against the equity universe")
    class CryptoIsNotAnEquity {

        @Test
        @DisplayName("SOL is accepted even though it resolves to an inactive NYSE listing")
        void solIsAcceptedDespiteCollidingWithAnInactiveEquity() {
            // Exactly what the broker returns for "SOL": a real but untradable US equity.
            when(brokerPort.getAssetInfo("SOL")).thenReturn(
                Optional.of(AssetInfo.notTradable(
                    "SOL", "Emeren Group Ltd American Depositary Shares", "NYSE", "inactive", "us_equity")));

            assertThatCode(() -> helper.validateTickers(configWith(null, crypto("SOL"))))
                .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("the broker is not consulted at all for a crypto-only strategy")
        void brokerIsNeverAskedAboutCryptoSymbols() {
            helper.validateTickers(configWith(null, crypto("BTC", "ETH", "SOL", "DOGE")));

            verify(brokerPort, never()).getAssetInfo(anyString());
        }

        @Test
        @DisplayName("a mixed strategy still rejects the bad stock without blaming the crypto")
        void mixedStrategyValidatesOnlyTheEquityLeg() {
            when(brokerPort.getAssetInfo("XYZF")).thenReturn(Optional.of(AssetInfo.notFound("XYZF")));

            assertThatThrownBy(() ->
                helper.validateTickers(configWith(stocks("XYZF"), crypto("SOL"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("XYZF")
                .satisfies(e -> assertThatCode(() -> {
                    if (e.getMessage().contains("SOL")) {
                        throw new AssertionError("crypto ticker must not appear in an equity "
                            + "tradability error: " + e.getMessage());
                    }
                }).doesNotThrowAnyException());

            verify(brokerPort, never()).getAssetInfo("SOL");
        }
    }

    @Nested
    @DisplayName("Exchange-qualified symbols are reported as written")
    class ExchangeQualifiedSymbols {

        /**
         * The lookup takes a bare root, so RHM:XETR is asked about Alpaca's US universe as plain
         * "RHM" — and the rejection then quoted "RHM" back. The user is told to verify a symbol
         * they never typed, about a market the broker was never asked about. RHM is the correct
         * XETRA ticker; no amount of verifying it will ever help.
         */
        @Test
        @DisplayName("the error quotes the symbol the user wrote, not the stripped root")
        void errorNamesTheQualifiedSymbol() {
            Symbol rheinmetall = Symbol.parse("RHM:XETR");
            when(brokerPort.getAssetInfo("RHM"))
                .thenReturn(Optional.of(AssetInfo.notFound("RHM")));

            assertThatThrownBy(() -> helper.validateTickers(
                    configWith(Set.of(rheinmetall), null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("RHM:XETR");
        }

        @Test
        @DisplayName("a venue-qualified ticker is refused even when its root IS a US listing")
        void refusesVenueQualifiedTickerWhoseRootCollidesWithAUsListing() {
            // The root is not an identity. getAssetInfo takes a bare ticker, so AZN:XLON
            // was asked about as "AZN" — AstraZeneca's NEW YORK receipt — which the broker
            // happily confirmed as tradable. Validation passed on that confirmation and the
            // strategy then backtested against the US listing in USD while still displaying
            // AZN:XLON, so a user who asked for London silently received New York.
            //
            // A broker answer about the root says nothing about the venue, so it must not
            // be accepted as one.
            Symbol londonLine = Symbol.parse("AZN:XLON");
            when(brokerPort.getAssetInfo("AZN"))
                .thenReturn(Optional.of(AssetInfo.tradable("AZN", "AstraZeneca PLC", "NYSE", "us_equity")));

            assertThatThrownBy(() -> helper.validateTickers(configWith(Set.of(londonLine), null)))
                .as("a tradable US root must not be mistaken for confirmation of the London line")
                .hasMessageContaining("AZN:XLON")
                .hasMessageContaining("not supported yet");

            // The root lookup must not even be consulted: its answer is about a different
            // security, so treating it as evidence either way is the original mistake.
            verify(brokerPort, never()).getAssetInfo("AZN");
        }

        @Test
        @DisplayName("it points at the broker, not at the market — Saxo will trade these")
        void namesTheBrokerAsTheConstraint() {
            Symbol rheinmetall = Symbol.parse("RHM:XETR");
            when(brokerPort.getAssetInfo("RHM"))
                .thenReturn(Optional.of(AssetInfo.notFound("RHM")));

            assertThatThrownBy(() -> helper.validateTickers(
                    configWith(Set.of(rheinmetall), null)))
                .as("EU venues are a broker capability, not a permanent platform limit — the "
                    + "message must not read as 'this market is impossible here'")
                .hasMessageContaining("XETR")
                .hasMessageContaining("broker");
        }

        @Test
        @DisplayName("it does not tell the user to verify a symbol that was already correct")
        void doesNotBlameTheSymbol() {
            Symbol rheinmetall = Symbol.parse("RHM:XETR");
            when(brokerPort.getAssetInfo("RHM"))
                .thenReturn(Optional.of(AssetInfo.notFound("RHM")));

            assertThatThrownBy(() -> helper.validateTickers(
                    configWith(Set.of(rheinmetall), null)))
                .as("the ticker is right; the venue is what is unsupported")
                .hasMessageNotContaining("verify the ticker symbol");
        }

        @Test
        @DisplayName("a bare US ticker still reads exactly as before")
        void usTickersAreUnchanged() {
            Symbol aapl = new Symbol("NOPE", InvestmentType.STOCK);
            when(brokerPort.getAssetInfo("NOPE"))
                .thenReturn(Optional.of(AssetInfo.notFound("NOPE")));

            assertThatThrownBy(() -> helper.validateTickers(configWith(Set.of(aapl), null)))
                .hasMessageContaining("NOPE");
        }
    }

    @Nested
    @DisplayName("Equity validation is unchanged")
    class EquityValidationStillApplies {

        @Test
        @DisplayName("an untradable stock is still rejected with a usable message")
        void untradableStockIsRejected() {
            when(brokerPort.getAssetInfo("XYZF")).thenReturn(
                Optional.of(AssetInfo.notTradable("XYZF", "Fake Corp", "NYSE", "inactive", "us_equity")));

            assertThatThrownBy(() -> helper.validateTickers(configWith(stocks("XYZF"), null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("non-tradable tickers")
                .hasMessageContaining("XYZF");
        }

        @Test
        @DisplayName("a tradable stock passes")
        void tradableStockPasses() {
            when(brokerPort.getAssetInfo("AAPL")).thenReturn(
                Optional.of(AssetInfo.tradable("AAPL", "Apple Inc.", "NASDAQ", "us_equity")));

            assertThatCode(() -> helper.validateTickers(configWith(stocks("AAPL"), null)))
                .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("an unreachable broker soft-passes rather than blocking creation")
        void unreachableBrokerSoftPasses() {
            when(brokerPort.getAssetInfo(anyString())).thenReturn(Optional.empty());

            assertThatCode(() -> helper.validateTickers(configWith(stocks("AAPL"), null)))
                .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("crypto symbol form")
    class CryptoSymbolForm {

        @Test
        void rejectsAPairFormWrittenWithAHyphen() {
            // Skipping the broker lookup for crypto left it with NO symbol check, and
            // Symbol.TICKER_PATTERN permits '-' for US share classes — so BTC-USD created an
            // ACTIVE strategy that no provider prices and no broker trades. Its backtest
            // reported 0 trades / 0.00%, indistinguishable from a run that never fired.
            // Reported by three consecutive new-user audits.
            assertThatThrownBy(() -> helper.validateTickers(configWith(null, crypto("BTC-USD"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("BTC-USD")
                .hasMessageContaining("write the bare asset \"BTC\"")
                .hasMessageContaining("without ever trading");
        }

        @Test
        void aSlashPairIsRefusedEarlierStillWithCryptoShapedAdvice() {
            // A slash never reaches this helper — Symbol's own format check rejects it first.
            // That check used to answer a crypto user with equity advice ("use find_ticker with
            // the company name to find a US-tradable listing"), which cannot help anyone
            // holding ETH/USD. Asserted here because this is where a user meets it.
            assertThatThrownBy(() -> new Symbol("ETH/USD", InvestmentType.CRYPTO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("that is a currency pair")
                .hasMessageContaining("write \"ETH\"")
                .hasMessageNotContaining("company name");
        }

        @Test
        void rejectsTheConcatenatedPairForm() {
            // The spelling that survived three rounds of fixes, because each round only closed
            // the form it had been shown. By round 4 the market-data guide was printing BTCUSD
            // in its own "do NOT write" column while the validator accepted it.
            assertThatThrownBy(() -> helper.validateTickers(configWith(null, crypto("BTCUSD"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("BTCUSD")
                .hasMessageContaining("write the bare asset \"BTC\"");
        }

        @Test
        void rejectsAConcatenatedTetherPair() {
            assertThatThrownBy(() -> helper.validateTickers(configWith(null, crypto("BTCUSDT"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("write the bare asset \"BTC\"");
        }

        @Test
        void acceptsAssetsWhoseOwnNamesEndInAQuoteName() {
            // Recognised by name, not by length. The old rule used "base of 3+ chars means pair",
            // which refused axlUSDC and crvUSD — both real — while accepting OPUSD and SUSD,
            // which are not.
            assertThatCode(() -> helper.validateTickers(configWith(null,
                    crypto("USDT", "USDC", "TUSD", "PYUSD", "FDUSD", "CRVUSD", "AXLUSDC", "WBTC"))))
                .doesNotThrowAnyException();
        }

        @Test
        void rejectsNonDollarPairForms() {
            // The family the old dollar-only rule could not see at all: nothing was stripped, so
            // every one of these created an ACTIVE, permanently untradable strategy. The old
            // message even asserted "the quote currency is always USD".
            for (String pair : new String[]{"ETHBTC", "BTCEUR", "SOLETH", "ETHGBP"}) {
                assertThatThrownBy(() -> helper.validateTickers(configWith(null, crypto(pair))))
                    .as("%s is a pair, not an asset", pair)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("never a pair");
            }
        }

        @Test
        void rejectsPairsOnShortAssetNames() {
            // OPUSD (Optimism), ARUSD (Arweave), IDUSD (Space ID) all leave a base under three
            // characters, so the length rule waved them through. An audit created an ACTIVE
            // strategy holding OPUSD and then watched backfill fail on all three providers.
            for (String pair : new String[]{"OPUSD", "ARUSD", "IDUSD"}) {
                assertThatThrownBy(() -> helper.validateTickers(configWith(null, crypto(pair))))
                    .as("%s is a pair on a short asset name", pair)
                    .isInstanceOf(IllegalArgumentException.class);
            }
        }

        @Test
        void doesNotClaimQuotesAreAlwaysDollars() {
            assertThatThrownBy(() -> helper.validateTickers(configWith(null, crypto("BTCEUR"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageNotContaining("always USD");
        }

        @Test
        void stillAcceptsBareCryptoAssets() {
            assertThatCode(() -> helper.validateTickers(configWith(null, crypto("BTC", "ETH", "SOL", "DOGE"))))
                .doesNotThrowAnyException();
            verify(brokerPort, never()).getAssetInfo(anyString());
        }

        @Test
        void doesNotConstrainStockTickersThatLegitimatelyContainAHyphen() {
            // US share classes are written BRK-B; the pair rule is crypto-only.
            when(brokerPort.getAssetInfo(anyString()))
                .thenReturn(Optional.of(AssetInfo.tradable("BRK-B", "Berkshire Hathaway B", "NYSE", "us_equity")));

            assertThatCode(() -> helper.validateTickers(configWith(stocks("BRK-B"), null)))
                .doesNotThrowAnyException();
        }
    }
}
