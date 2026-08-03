package com.quorumtrading.application.service;

import com.quorumtrading.application.port.out.BrokerAccountRepository;
import com.quorumtrading.application.port.out.BrokerPort;
import com.quorumtrading.application.port.out.SimulationPortfolioRepository;
import com.quorumtrading.application.port.out.SimulationRepository;
import com.quorumtrading.model.broker.ExecuteTradeCommand;
import com.quorumtrading.model.broker.TradeResult;
import com.quorumtrading.model.entity.Simulation;
import com.quorumtrading.model.entity.SimulationPortfolio;
import com.quorumtrading.model.enums.InvestmentType;
import com.quorumtrading.model.enums.TradeAction;
import com.quorumtrading.model.enums.TradeStatus;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.OrderId;
import com.quorumtrading.model.valueobject.SimulationId;
import com.quorumtrading.model.valueobject.Symbol;
import jakarta.enterprise.inject.Instance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Capital-ledger accounting belongs to {@link BrokerRouter}, the one choke point every trade
 * passes through.
 *
 * <p>It previously lived inside the Alpaca and Saxo adapters and nowhere else, so the mock broker
 * — and therefore every backtest — never maintained {@code SimulationPortfolio}. Its cash stayed
 * frozen at the seed value while {@code portfolio_snapshots} tracked reality, leaving two ledgers
 * disagreeing behind a single port. These tests pin both halves of the fix: every broker updates
 * the ledger, and it is updated exactly once (the adapters must not also do it, or Alpaca fills
 * would be counted twice).</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BrokerRouterCapitalLedgerTest {

    private BrokerRouter router;
    private BrokerPort mockBrokerBean;
    private SimulationPortfolioRepository portfolioRepository;
    private SimulationPortfolio portfolio;

    private static final SimulationId SIM_ID = SimulationId.generate();
    private static final Symbol BTC = new Symbol("BTC", InvestmentType.CRYPTO);

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        router = new BrokerRouter();
        mockBrokerBean = mock(BrokerPort.class);
        router.mockBroker = satisfied(mockBrokerBean, BrokerPort.class);
        router.alpacaBroker = (Instance<BrokerPort>) mock(Instance.class);
        router.saxoBroker = (Instance<BrokerPort>) mock(Instance.class);
        when(router.alpacaBroker.isUnsatisfied()).thenReturn(true);
        when(router.saxoBroker.isUnsatisfied()).thenReturn(true);

        SimulationRepository simulationRepository = mock(SimulationRepository.class);
        Simulation simulation = mock(Simulation.class);
        when(simulation.getBrokerAccountId()).thenReturn(null);
        when(simulationRepository.findById(any())).thenReturn(Optional.of(simulation));
        router.simulationRepository = simulationRepository;

        Instance<BrokerAccountRepository> accountRepoInstance = (Instance<BrokerAccountRepository>) mock(Instance.class);
        when(accountRepoInstance.isUnsatisfied()).thenReturn(true);
        router.brokerAccountRepository = accountRepoInstance;

        portfolio = mock(SimulationPortfolio.class);
        when(portfolio.getCashAvailable()).thenReturn(Money.usd(new BigDecimal("500.00")));
        portfolioRepository = mock(SimulationPortfolioRepository.class);
        when(portfolioRepository.findById(SIM_ID)).thenReturn(Optional.of(portfolio));
        router.simulationPortfolioRepository = satisfied(portfolioRepository, SimulationPortfolioRepository.class);
    }

    @SuppressWarnings("unchecked")
    private <T> Instance<T> satisfied(T bean, Class<T> type) {
        Instance<T> instance = (Instance<T>) mock(Instance.class);
        when(instance.isUnsatisfied()).thenReturn(false);
        when(instance.isResolvable()).thenReturn(true);
        when(instance.get()).thenReturn(bean);
        return instance;
    }

    private ExecuteTradeCommand command(TradeAction action, String qty) {
        return new ExecuteTradeCommand(SIM_ID, action, BTC, new BigDecimal(qty),
            com.quorumtrading.model.enums.OrderType.MARKET, null, Instant.now());
    }

    private void brokerReturns(TradeStatus status, String qty, String price) {
        when(mockBrokerBean.executeTrade(any())).thenReturn(new TradeResult(
            status, OrderId.generate(),
            status == TradeStatus.REJECTED ? null : Money.usd(new BigDecimal(price)),
            status == TradeStatus.REJECTED ? null : new BigDecimal(qty),
            status == TradeStatus.REJECTED ? "rejected" : null,
            Instant.now()));
    }

    @Test
    void recordsABuyOnTheLedgerForTheMockBrokerToo() {
        brokerReturns(TradeStatus.EXECUTED, "0.5", "60000");

        router.executeTrade(command(TradeAction.BUY, "0.5"));

        verify(portfolio).recordBuy(eq(BTC), eq(new BigDecimal("0.5")),
            eq(Money.usd(new BigDecimal("60000"))), any(Money.class));
        verify(portfolioRepository).save(portfolio);
    }

    @Test
    void recordsASellOnTheLedger() {
        brokerReturns(TradeStatus.EXECUTED, "0.25", "61000");

        router.executeTrade(command(TradeAction.SELL, "0.25"));

        verify(portfolio).recordSell(eq(BTC), eq(new BigDecimal("0.25")),
            eq(Money.usd(new BigDecimal("61000"))), any(Money.class));
        verify(portfolioRepository).save(portfolio);
    }

    /** Exactly once — the adapters must no longer do their own accounting. */
    @Test
    void appliesTheFillOnlyOnce() {
        brokerReturns(TradeStatus.EXECUTED, "1", "60000");

        router.executeTrade(command(TradeAction.BUY, "1"));

        verify(portfolio, times(1)).recordBuy(any(), any(), any(), any());
        verify(portfolioRepository, times(1)).save(any(SimulationPortfolio.class));
    }

    @Test
    void aPartialFillIsStillRecorded() {
        brokerReturns(TradeStatus.PARTIAL_FILL, "0.3", "60000");

        router.executeTrade(command(TradeAction.BUY, "1"));

        verify(portfolio).recordBuy(eq(BTC), eq(new BigDecimal("0.3")), any(), any());
    }

    @Test
    void aRejectedTradeNeverTouchesTheLedger() {
        brokerReturns(TradeStatus.REJECTED, "0", "0");

        router.executeTrade(command(TradeAction.BUY, "1"));

        verify(portfolio, never()).recordBuy(any(), any(), any(), any());
        verify(portfolioRepository, never()).save(any());
    }

    /** A domain-rule violation must not undo a trade the broker already executed. */
    @Test
    void aLedgerFailureDoesNotFailTheTrade() {
        brokerReturns(TradeStatus.EXECUTED, "1", "60000");
        doThrow(new IllegalStateException("insufficient cash"))
            .when(portfolio).recordBuy(any(), any(), any(), any());

        router.executeTrade(command(TradeAction.BUY, "1"));

        verify(portfolioRepository, never()).save(any());
    }

    @Test
    void survivesAnIsolatedContextWithNoPersistence() {
        brokerReturns(TradeStatus.EXECUTED, "1", "60000");
        @SuppressWarnings("unchecked")
        Instance<SimulationPortfolioRepository> absent = (Instance<SimulationPortfolioRepository>) mock(Instance.class);
        when(absent.isUnsatisfied()).thenReturn(true);
        router.simulationPortfolioRepository = absent;

        router.executeTrade(command(TradeAction.BUY, "1"));

        verify(portfolioRepository, never()).save(any());
    }
}
