package com.quorumtrading.application.service;

import com.quorumtrading.application.port.out.StrategyRepository;
import com.quorumtrading.model.entity.Strategy;
import com.quorumtrading.model.valueobject.StrategyId;
import com.quorumtrading.model.valueobject.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("TickerRegistryService")
class TickerRegistryServiceTest {

    @Mock
    private StrategyRepository strategyRepository;

    private TickerRegistryService service;

    @BeforeEach
    void setUp() {
        service = new TickerRegistryService(strategyRepository);
    }

    /** Stock mode requires an explicit evaluation schedule; crypto mode does not. */
    private static final String STOCK_SCHEDULE =
        "live_schedule:\n  evaluation_times: [\"10:00\"]\n  timezone: America/New_York";

    private static Strategy strategy(String name, String frontmatter) {
        return new Strategy(
            StrategyId.generate(),
            name,
            "---\n" + frontmatter + "\n---\n\n# " + name + "\n\nRules.\n",
            UserId.generate()
        );
    }

    @Test
    @DisplayName("collects tickers from every strategy")
    void collectsTickersFromEveryStrategy() {
        when(strategyRepository.findAll()).thenReturn(List.of(
            strategy("Crypto accumulation", "crypto_tickers: [BTC]"),
            strategy("Equity momentum", "stock_tickers: [AAPL, MSFT]\n" + STOCK_SCHEDULE)
        ));

        var registry = service.getAllRequiredTickers();

        assertThat(registry.getCryptoTickerStrings()).containsExactly("BTC");
        assertThat(registry.getStockTickerStrings()).containsExactlyInAnyOrder("AAPL", "MSFT");
    }

    @Test
    @DisplayName("does not let one strategy's stock_tickers put a crypto symbol in the equity universe")
    void doesNotLeakCryptoSymbolIntoEquityUniverse() {
        // The registry is global: every strategy on the instance contributes to the one
        // set the market-data adapters fetch. A single strategy declaring BTC as an
        // equity therefore sent BTC to the stock feed for everyone, which answered with
        // the similarly-ticked spot-bitcoin ETF (~$28) and overwrote the crypto BTC
        // price series — marking live BTC positions down by ~99.96%.
        when(strategyRepository.findAll()).thenReturn(List.of(
            strategy("BTC accumulation", "crypto_tickers: [BTC]"),
            strategy("RSI Reversal", "stock_tickers: [BTC]\ncrypto_tickers: []\n" + STOCK_SCHEDULE)
        ));

        var registry = service.getAllRequiredTickers();

        assertThat(registry.getStockTickerStrings()).isEmpty();
        assertThat(registry.getCryptoTickerStrings()).containsExactly("BTC");
    }

    @Test
    @DisplayName("keeps real equities when a sibling strategy names a crypto symbol as stock")
    void keepsRealEquitiesAlongsideRejectedSymbol() {
        when(strategyRepository.findAll()).thenReturn(List.of(
            strategy("Mixed", "stock_tickers: [BTC, AAPL]\n" + STOCK_SCHEDULE)
        ));

        var registry = service.getAllRequiredTickers();

        assertThat(registry.getStockTickerStrings()).containsExactly("AAPL");
    }

    @Test
    @DisplayName("returns an empty registry when there are no strategies")
    void emptyWhenNoStrategies() {
        when(strategyRepository.findAll()).thenReturn(List.of());

        assertThat(service.getAllRequiredTickers().isEmpty()).isTrue();
    }

    @Test
    @DisplayName("skips a strategy whose frontmatter cannot be parsed")
    void skipsUnparseableStrategy() {
        when(strategyRepository.findAll()).thenReturn(List.of(
            strategy("Good", "crypto_tickers: [ETH]"),
            new Strategy(
                StrategyId.generate(),
                "No frontmatter",
                "# Just prose, no YAML block\n",
                UserId.generate()
            )
        ));

        assertThat(service.getAllRequiredTickers().getCryptoTickerStrings()).containsExactly("ETH");
    }

    @Test
    @DisplayName("getSymbolsByAssetType filters by requested class")
    void getSymbolsByAssetType() {
        when(strategyRepository.findAll()).thenReturn(List.of(
            strategy("Mixed", "crypto_tickers: [BTC]\nstock_tickers: [AAPL]\n" + STOCK_SCHEDULE)
        ));

        assertThat(service.getSymbolsByAssetType("CRYPTO")).hasSize(1);
        assertThat(service.getSymbolsByAssetType("stock")).hasSize(1);
    }
}
