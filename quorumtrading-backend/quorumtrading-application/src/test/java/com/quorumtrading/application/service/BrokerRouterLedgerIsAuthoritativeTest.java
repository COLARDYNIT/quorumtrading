package com.quorumtrading.application.service;

import com.quorumtrading.application.port.out.BrokerAccountRepository;
import com.quorumtrading.application.port.out.BrokerPort;
import com.quorumtrading.application.port.out.MarketEventRepository;
import com.quorumtrading.application.port.out.SimulationPortfolioRepository;
import com.quorumtrading.application.port.out.SimulationRepository;
import com.quorumtrading.model.entity.Portfolio;
import com.quorumtrading.model.entity.Position;
import com.quorumtrading.model.entity.Simulation;
import com.quorumtrading.model.entity.SimulationPortfolio;
import com.quorumtrading.model.enums.EventType;
import com.quorumtrading.model.enums.InvestmentType;
import com.quorumtrading.model.marketdata.MarketEvent;
import com.quorumtrading.model.valueobject.BrokerAccountId;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.SimulationId;
import com.quorumtrading.model.valueobject.Symbol;
import jakarta.enterprise.inject.Instance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A simulation's own capital ledger — not the broker account it borrows capital from — is the
 * authoritative record of its cash and positions.
 *
 * <p>One broker account backs many simulations: a mock account states a starting capital, and per
 * {@code docs/configuration/BROKER_CONFIGURATION.md} an Alpaca account may host several concurrent
 * LIVE runs. An account-level balance is therefore never a valid figure for one simulation, yet it
 * was what {@code getCurrentPortfolio} returned: the mock adapter seeds a per-simulation placeholder
 * from the account's configured capital at link time, and nothing had to replace it before the
 * 5-minute LIVE valuation read it back and wrote it into {@code portfolio_snapshots}. Three
 * plan-funded simulations reported their account's capital instead of their funding — $12,000 on
 * $1,000 (+1100%), $100,000 on $1,000 (+9900%) — and a fourth reported $495 on $12,000 (-95.9%)
 * because the broker's placeholder never learned about the contributions that followed it.</p>
 *
 * <p>The ledger sees both kinds of cash movement (every fill via
 * {@code BrokerRouter.recordFillInSimulationPortfolio}, every top-up via
 * {@code SimulationContributionService}) and is durable, whereas the mock adapter's map is
 * in-memory and re-seeded from the account after a restart. The broker's job is prices and fills.
 * A broker-less backtest has no ledger and keeps reading the broker's own state.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("BrokerRouter — the simulation's capital ledger outranks the broker account")
class BrokerRouterLedgerIsAuthoritativeTest {

    private static final SimulationId SIM_ID = SimulationId.generate();
    private static final Symbol BTC = new Symbol("BTC", InvestmentType.CRYPTO);
    private static final Instant AT = Instant.parse("2026-07-30T10:50:26Z");

    private BrokerRouter router;
    private BrokerPort brokerBean;
    private SimulationPortfolioRepository ledgerRepository;
    private MarketEventRepository marketEvents;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        router = new BrokerRouter();
        brokerBean = mock(BrokerPort.class);
        router.mockBroker = satisfied(brokerBean, BrokerPort.class);
        router.alpacaBroker = (Instance<BrokerPort>) mock(Instance.class);
        router.saxoBroker = (Instance<BrokerPort>) mock(Instance.class);
        when(router.alpacaBroker.isUnsatisfied()).thenReturn(true);
        when(router.saxoBroker.isUnsatisfied()).thenReturn(true);

        SimulationRepository simulationRepository = mock(SimulationRepository.class);
        Simulation simulation = mock(Simulation.class);
        when(simulation.getBrokerAccountId()).thenReturn(null);
        when(simulationRepository.findById(any())).thenReturn(Optional.of(simulation));
        router.simulationRepository = simulationRepository;

        Instance<BrokerAccountRepository> accountRepo = (Instance<BrokerAccountRepository>) mock(Instance.class);
        when(accountRepo.isUnsatisfied()).thenReturn(true);
        router.brokerAccountRepository = accountRepo;

        ledgerRepository = mock(SimulationPortfolioRepository.class);
        when(ledgerRepository.findById(any())).thenReturn(Optional.empty());
        router.simulationPortfolioRepository = satisfied(ledgerRepository, SimulationPortfolioRepository.class);

        marketEvents = mock(MarketEventRepository.class);
        when(marketEvents.findByTickersAndTimeRange(any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of());
        router.marketEventRepository = satisfied(marketEvents, MarketEventRepository.class);
    }

    @SuppressWarnings("unchecked")
    private <T> Instance<T> satisfied(T bean, Class<T> type) {
        Instance<T> instance = (Instance<T>) mock(Instance.class);
        when(instance.isUnsatisfied()).thenReturn(false);
        when(instance.isResolvable()).thenReturn(true);
        when(instance.get()).thenReturn(bean);
        return instance;
    }

    /** A ledger holding {@code cash} plus the given positions. */
    private SimulationPortfolio ledger(String cash, Map<Symbol, Position> positions) {
        SimulationPortfolio portfolio = new SimulationPortfolio(
                SIM_ID, BrokerAccountId.generate(), positions,
                Money.usd(new BigDecimal(cash)), Money.usd(new BigDecimal(cash)),
                Money.usd(BigDecimal.ZERO));
        when(ledgerRepository.findById(SIM_ID)).thenReturn(Optional.of(portfolio));
        return portfolio;
    }

    private Map<Symbol, Position> btc(String quantity, String averagePrice) {
        Map<Symbol, Position> positions = new HashMap<>();
        positions.put(BTC, new Position(BTC, new BigDecimal(quantity), Money.usd(new BigDecimal(averagePrice))));
        return positions;
    }

    /** The broker reports the ACCOUNT's figure — the number that must never reach a snapshot. */
    private void brokerReportsAccountCapital(String cash) {
        when(brokerBean.getCurrentPortfolio(SIM_ID))
                .thenReturn(new Portfolio(SIM_ID, Map.of(), Money.usd(new BigDecimal(cash))));
        when(brokerBean.getAvailableBalance(SIM_ID)).thenReturn(Money.usd(new BigDecimal(cash)));
        when(brokerBean.getPortfolioValue(eq(SIM_ID), any())).thenReturn(Money.usd(new BigDecimal(cash)));
    }

    private void priceIs(String price) {
        when(marketEvents.findByTickersAndTimeRange(any(), any(), any(), any(), eq(EventType.PRICE), any()))
                .thenReturn(List.of(new MarketEvent(
                        AT.minusSeconds(30), EventType.PRICE, "test", "BTC",
                        Map.of("price", price))));
    }

    // ---------------------------------------------------------------- a81d46f1 / dfcb0883 shape

    @Test
    @DisplayName("current portfolio reports the simulation's own cash, not the account's capital")
    void currentPortfolioComesFromTheLedger() {
        ledger("1000.00", Map.of());
        brokerReportsAccountCapital("12000");

        Portfolio result = router.getCurrentPortfolio(SIM_ID);

        assertThat(result.cashAvailable().amount()).isEqualByComparingTo(new BigDecimal("1000.00"));
    }

    @Test
    @DisplayName("available balance comes from the ledger, so sizing cannot spend account capital")
    void availableBalanceComesFromTheLedger() {
        ledger("1000.00", Map.of());
        brokerReportsAccountCapital("12000");

        assertThat(router.getAvailableBalance(SIM_ID).amount())
                .isEqualByComparingTo(new BigDecimal("1000.00"));
    }

    @Test
    @DisplayName("a cash-only ledger is valued at its own cash, not the account's")
    void portfolioValueOfACashOnlyLedgerIsItsOwnCash() {
        ledger("1000.00", Map.of());
        brokerReportsAccountCapital("12000");

        assertThat(router.getPortfolioValue(SIM_ID, AT).amount())
                .isEqualByComparingTo(new BigDecimal("1000.00"));
    }

    // ------------------------------------------------------------------------- 854f1f75 shape
    // Here the account's figure came out far too LOW, not too high: contributions had been
    // reserved, so the broker's placeholder held only the pre-contribution cash. A fix that
    // merely clamped implausibly high values would have left this one reporting -95.9%.

    /**
     * The market price deliberately differs from the position's average price, so passing this
     * requires the position to be valued at market and not at cost basis — {@code
     * SimulationPortfolio.calculateTotalValue} silently falls back to the average price for an
     * unpriced symbol, and a test priced at cost could not tell the two apart.
     */
    @Test
    @DisplayName("positions are priced at market and added to the ledger's cash")
    void portfolioValueAddsMarketPricedPositionsToLedgerCash() {
        ledger("11552.03", btc("0.5", "64000"));
        brokerReportsAccountCapital("495.04");
        priceIs("70000");

        // 11552.03 + 0.5 x 70000 (market), not 0.5 x 64000 (cost)
        assertThat(router.getPortfolioValue(SIM_ID, AT).amount())
                .isEqualByComparingTo(new BigDecimal("46552.03"));
    }

    @Test
    @DisplayName("a too-low broker figure is rejected as firmly as a too-high one")
    void currentPortfolioIgnoresATooLowBrokerFigure() {
        ledger("11552.03", btc("0.5", "64000"));
        brokerReportsAccountCapital("50.91");

        assertThat(router.getCurrentPortfolio(SIM_ID).cashAvailable().amount())
                .isEqualByComparingTo(new BigDecimal("11552.03"));
        assertThat(router.getCurrentPortfolio(SIM_ID).positions()).containsKey(BTC);
    }

    // ------------------------------------------------------------------------ integrity guards

    @Test
    @DisplayName("an unpriceable position withholds the value rather than under-reporting it")
    void portfolioValueIsWithheldWhenAPositionCannotBePriced() {
        ledger("11552.03", btc("0.5", "64000"));
        brokerReportsAccountCapital("495.04");
        // No PRICE events: LatestMarketPrices returns nothing for BTC.

        assertThat(router.getPortfolioValue(SIM_ID, AT)).isNull();
    }

    @Test
    @DisplayName("with no price source at all the value is withheld, never borrowed from the broker")
    @SuppressWarnings("unchecked")
    void portfolioValueIsWithheldWhenNoPriceSourceIsWired() {
        ledger("11552.03", btc("0.5", "64000"));
        brokerReportsAccountCapital("495.04");
        Instance<MarketEventRepository> absent = (Instance<MarketEventRepository>) mock(Instance.class);
        when(absent.isUnsatisfied()).thenReturn(true);
        router.marketEventRepository = absent;

        assertThat(router.getPortfolioValue(SIM_ID, AT)).isNull();
    }

    // -------------------------------------------------------------------- broker-less backtests

    @Test
    @DisplayName("a broker-less backtest has no ledger and still reads the broker's own state")
    void aBrokerLessBacktestStillReadsTheBroker() {
        // No ledger stubbed: findById returns empty.
        brokerReportsAccountCapital("10000");

        assertThat(router.getCurrentPortfolio(SIM_ID).cashAvailable().amount())
                .isEqualByComparingTo(new BigDecimal("10000"));
        assertThat(router.getAvailableBalance(SIM_ID).amount())
                .isEqualByComparingTo(new BigDecimal("10000"));
        assertThat(router.getPortfolioValue(SIM_ID, AT).amount())
                .isEqualByComparingTo(new BigDecimal("10000"));
    }

    /**
     * Documented fallback, not an aspiration: if the ledger cannot be read at all the broker's
     * figure is used rather than the call failing. That figure is account-level, so this is the one
     * path on which a wrong number can still surface — see the warning it logs.
     */
    @Test
    @DisplayName("a ledger read that fails falls back to the broker rather than throwing")
    void aFailedLedgerReadFallsBackToTheBroker() {
        when(ledgerRepository.findById(SIM_ID)).thenThrow(new RuntimeException("connection reset"));
        brokerReportsAccountCapital("10000");

        assertThat(router.getCurrentPortfolio(SIM_ID).cashAvailable().amount())
                .isEqualByComparingTo(new BigDecimal("10000"));
        assertThat(router.getAvailableBalance(SIM_ID).amount())
                .isEqualByComparingTo(new BigDecimal("10000"));
    }

    @Test
    @DisplayName("an isolated context with no persistence falls back to the broker")
    @SuppressWarnings("unchecked")
    void anIsolatedContextFallsBackToTheBroker() {
        brokerReportsAccountCapital("10000");
        Instance<SimulationPortfolioRepository> absent =
                (Instance<SimulationPortfolioRepository>) mock(Instance.class);
        when(absent.isUnsatisfied()).thenReturn(true);
        router.simulationPortfolioRepository = absent;

        assertThat(router.getCurrentPortfolio(SIM_ID).cashAvailable().amount())
                .isEqualByComparingTo(new BigDecimal("10000"));
        verify(brokerBean).getCurrentPortfolio(SIM_ID);
    }

    // --------------------------------------------------------------- the provisional account seed

    /**
     * The adapter seeds a placeholder from the account's capital because it cannot know this
     * simulation's share of it. The router can: it holds the ledger, so it replaces the
     * placeholder immediately rather than leaving a $12,000 figure sitting behind an
     * affordability check on a $1,000 simulation.
     */
    @Test
    @DisplayName("linking replaces the account-seeded placeholder with the simulation's own figure")
    @SuppressWarnings("unchecked")
    void linkingReplacesTheAccountSeedWithTheLedger() {
        ledger("1000.00", Map.of());
        com.quorumtrading.application.port.out.ConfigurableBrokerPort configurableBroker =
                mock(com.quorumtrading.application.port.out.ConfigurableBrokerPort.class);
        router.mockBroker = satisfied(configurableBroker, BrokerPort.class);

        com.quorumtrading.model.entity.BrokerAccount account =
                mock(com.quorumtrading.model.entity.BrokerAccount.class);
        when(account.getId()).thenReturn(ACCOUNT_ID_FOR_LINK);
        when(account.getBrokerType()).thenReturn(com.quorumtrading.model.enums.BrokerType.MOCK);
        BrokerAccountRepository accounts = mock(BrokerAccountRepository.class);
        when(accounts.findById(ACCOUNT_ID_FOR_LINK)).thenReturn(Optional.of(account));
        router.brokerAccountRepository = satisfied(accounts, BrokerAccountRepository.class);

        router.linkSimulationToAccount(SIM_ID, ACCOUNT_ID_FOR_LINK);

        verify(configurableBroker).linkSimulationToAccount(SIM_ID, ACCOUNT_ID_FOR_LINK);
        verify(configurableBroker).initializePortfolio(
                eq(SIM_ID), eq(Money.usd(new BigDecimal("1000.00"))), any());
    }

    private static final BrokerAccountId ACCOUNT_ID_FOR_LINK = BrokerAccountId.generate();

    @Test
    @DisplayName("the broker is never consulted for a ledger-backed simulation's cash")
    void theBrokerIsNotAskedForCashWhenALedgerExists() {
        ledger("1000.00", Map.of());
        brokerReportsAccountCapital("12000");

        router.getCurrentPortfolio(SIM_ID);
        router.getAvailableBalance(SIM_ID);

        verify(brokerBean, never()).getCurrentPortfolio(any());
        verify(brokerBean, never()).getAvailableBalance(any());
    }
}
