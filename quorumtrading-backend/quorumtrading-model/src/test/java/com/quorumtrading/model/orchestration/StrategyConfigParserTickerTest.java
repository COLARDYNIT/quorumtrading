package com.quorumtrading.model.orchestration;

import com.quorumtrading.model.enums.InvestmentType;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.Symbol;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that {@link StrategyConfigParser} turns the ticker frontmatter lists
 * into {@link Symbol} objects correctly for both the legacy US bare-ticker form
 * and the EU exchange-qualified forms introduced for Saxo support.
 *
 * <p>Two non-negotiable invariants:</p>
 * <ul>
 *   <li>A US bare ticker ({@code AAPL}) parses <b>exactly</b> as before — null
 *       exchange, USD currency, canonical form byte-identical to the ticker.</li>
 *   <li>An EU exchange-qualified ticker resolves to its MIC + settlement currency.</li>
 * </ul>
 */
class StrategyConfigParserTickerTest {

    private final StrategyConfigParser parser = new StrategyConfigParser();

    private static final String STOCK_TEMPLATE = """
            ---
            stock_tickers:
            %s
            live_schedule:
              evaluation_times: ["10:00"]
            ---
            Buy when RSI is low.
            """;

    private static final String CRYPTO_TEMPLATE = """
            ---
            crypto_tickers:
            %s
            live_schedule:
              interval: 300s
            ---
            Buy the dip.
            """;

    private StrategyConfig parseStock(String tickerYaml) {
        return parser.parse(STOCK_TEMPLATE.formatted(tickerYaml));
    }

    private StrategyConfig parseCrypto(String tickerYaml) {
        return parser.parse(CRYPTO_TEMPLATE.formatted(tickerYaml));
    }

    private Symbol findByTicker(Set<Symbol> symbols, String ticker) {
        return symbols.stream()
                .filter(s -> s.ticker().equals(ticker))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "No symbol with ticker " + ticker + " in "
                                + symbols.stream().map(Symbol::canonical).collect(Collectors.toSet())));
    }

    // ---------------------------------------------------------------------
    // US bare tickers — must parse EXACTLY as today.
    // ---------------------------------------------------------------------

    @Test
    void usBareTickerIsByteIdenticalToLegacyTwoArgSymbol() {
        StrategyConfig config = parseStock("  - AAPL");

        Set<Symbol> symbols = config.getStockTickers();
        assertThat(symbols).hasSize(1);

        Symbol aapl = symbols.iterator().next();
        // Identical to the legacy 2-arg construction path.
        assertThat(aapl).isEqualTo(new Symbol("AAPL", InvestmentType.STOCK));
        assertThat(aapl.ticker()).isEqualTo("AAPL");
        assertThat(aapl.type()).isEqualTo(InvestmentType.STOCK);
        assertThat(aapl.exchange()).isNull();
        assertThat(aapl.currency()).isEqualTo(Money.Currency.USD);
        assertThat(aapl.uic()).isNull();
        assertThat(aapl.assetType()).isNull();
        // canonical() must be byte-identical to the bare ticker.
        assertThat(aapl.canonical()).isEqualTo("AAPL");
        assertThat(aapl.isEuropean()).isFalse();
    }

    @Test
    void usBareTickerIsLowercasedInputStillUppercases() {
        StrategyConfig config = parseStock("  - aapl");

        Symbol aapl = config.getStockTickers().iterator().next();
        assertThat(aapl.ticker()).isEqualTo("AAPL");
        assertThat(aapl.canonical()).isEqualTo("AAPL");
    }

    @Test
    void usDottedTickerWithUnknownSuffixStaysUs() {
        // BRK.B is a US share class, NOT an exchange suffix — must remain a US ticker.
        StrategyConfig config = parseStock("  - BRK.B");

        Symbol brk = config.getStockTickers().iterator().next();
        assertThat(brk.ticker()).isEqualTo("BRK.B");
        assertThat(brk.exchange()).isNull();
        assertThat(brk.currency()).isEqualTo(Money.Currency.USD);
        assertThat(brk.canonical()).isEqualTo("BRK.B");
        assertThat(brk.isEuropean()).isFalse();
    }

    @Test
    void multipleUsTickersAllParseAsBareUs() {
        StrategyConfig config = parseStock("""
                  - AAPL
                  - MSFT
                  - NVDA""");

        Set<Symbol> symbols = config.getStockTickers();
        assertThat(symbols).hasSize(3);
        assertThat(symbols).allSatisfy(s -> {
            assertThat(s.exchange()).isNull();
            assertThat(s.currency()).isEqualTo(Money.Currency.USD);
            assertThat(s.canonical()).isEqualTo(s.ticker());
        });
    }

    // ---------------------------------------------------------------------
    // EU exchange-qualified — colon canonical form "ROOT:MIC".
    // ---------------------------------------------------------------------

    @Test
    void euColonQualifiedTickerResolvesToMicAndCurrency() {
        StrategyConfig config = parseStock("  - RHM:XETR");

        Symbol rhm = config.getStockTickers().iterator().next();
        assertThat(rhm.ticker()).isEqualTo("RHM");
        assertThat(rhm.type()).isEqualTo(InvestmentType.STOCK);
        assertThat(rhm.exchange()).isNotNull();
        assertThat(rhm.exchange().code()).isEqualTo("XETR");
        assertThat(rhm.currency()).isEqualTo(Money.Currency.EUR);
        assertThat(rhm.canonical()).isEqualTo("RHM:XETR");
        assertThat(rhm.isEuropean()).isTrue();
    }

    // ---------------------------------------------------------------------
    // EU exchange-qualified — dotted suffix form "ROOT.SUFFIX".
    // ---------------------------------------------------------------------

    @Test
    void euDottedSuffixTickerResolvesToMicAndCurrency() {
        StrategyConfig config = parseStock("  - RHM.DE");

        Symbol rhm = config.getStockTickers().iterator().next();
        assertThat(rhm.ticker()).isEqualTo("RHM");
        assertThat(rhm.exchange()).isNotNull();
        assertThat(rhm.exchange().code()).isEqualTo("XETR");
        assertThat(rhm.currency()).isEqualTo(Money.Currency.EUR);
        // Suffix form round-trips into the canonical colon form.
        assertThat(rhm.canonical()).isEqualTo("RHM:XETR");
        assertThat(rhm.isEuropean()).isTrue();
    }

    @Test
    void euDottedSuffixAmsterdamResolvesToEuronextAmsterdam() {
        StrategyConfig config = parseStock("  - ASML.AS");

        Symbol asml = config.getStockTickers().iterator().next();
        assertThat(asml.ticker()).isEqualTo("ASML");
        assertThat(asml.exchange().code()).isEqualTo("XAMS");
        assertThat(asml.currency()).isEqualTo(Money.Currency.EUR);
        assertThat(asml.canonical()).isEqualTo("ASML:XAMS");
    }

    // ---------------------------------------------------------------------
    // Explicit currency via the map entry form.
    // ---------------------------------------------------------------------

    @Test
    void mapEntryWithExplicitCurrencyOverridesVenueDefault() {
        // London defaults to USD in the model (no GBP in Money.Currency yet);
        // an explicit currency on the entry is honoured.
        StrategyConfig config = parseStock("""
                  - ticker: RHM:XETR
                    currency: EUR""");

        Symbol rhm = config.getStockTickers().iterator().next();
        assertThat(rhm.exchange().code()).isEqualTo("XETR");
        assertThat(rhm.currency()).isEqualTo(Money.Currency.EUR);
        assertThat(rhm.canonical()).isEqualTo("RHM:XETR");
    }

    // ---------------------------------------------------------------------
    // Mixed US + EU in the same list.
    // ---------------------------------------------------------------------

    @Test
    void mixedUsAndEuTickersCoexist() {
        StrategyConfig config = parseStock("""
                  - AAPL
                  - RHM:XETR
                  - ASML.AS""");

        Set<Symbol> symbols = config.getStockTickers();
        assertThat(symbols).hasSize(3);

        Symbol aapl = findByTicker(symbols, "AAPL");
        assertThat(aapl.exchange()).isNull();
        assertThat(aapl.canonical()).isEqualTo("AAPL");

        Symbol rhm = findByTicker(symbols, "RHM");
        assertThat(rhm.exchange().code()).isEqualTo("XETR");
        assertThat(rhm.canonical()).isEqualTo("RHM:XETR");

        Symbol asml = findByTicker(symbols, "ASML");
        assertThat(asml.exchange().code()).isEqualTo("XAMS");
        assertThat(asml.canonical()).isEqualTo("ASML:XAMS");
    }

    // ---------------------------------------------------------------------
    // Crypto list still parses as bare crypto tickers (type preserved).
    // ---------------------------------------------------------------------

    @Test
    void cryptoTickersKeepCryptoTypeAndBareCanonical() {
        StrategyConfig config = parseCrypto("""
                  - BTC
                  - ETH""");

        Set<Symbol> symbols = config.getCryptoTickers();
        assertThat(symbols).hasSize(2);
        assertThat(symbols).allSatisfy(s -> {
            assertThat(s.type()).isEqualTo(InvestmentType.CRYPTO);
            assertThat(s.exchange()).isNull();
            assertThat(s.currency()).isEqualTo(Money.Currency.USD);
            assertThat(s.canonical()).isEqualTo(s.ticker());
        });
    }
}
