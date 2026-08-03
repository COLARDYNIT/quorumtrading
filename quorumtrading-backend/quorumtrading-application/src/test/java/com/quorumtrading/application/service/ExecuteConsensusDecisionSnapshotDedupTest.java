package com.quorumtrading.application.service;

import com.quorumtrading.application.config.TradingLimitsConfig;
import com.quorumtrading.application.event.PortfolioChangeEvent;
import com.quorumtrading.application.event.TradeExecutionEvent;
import com.quorumtrading.application.port.out.BrokerPort;
import com.quorumtrading.application.port.out.ConsensusDecisionRepository;
import com.quorumtrading.application.port.out.EventPublisher;
import com.quorumtrading.application.port.out.MarketEventRepository;
import com.quorumtrading.application.port.out.PortfolioRepository;
import com.quorumtrading.application.port.out.SimulationRepository;
import com.quorumtrading.application.port.out.StrategyRepository;
import com.quorumtrading.model.entity.Portfolio;
import com.quorumtrading.model.entity.PortfolioSnapshot;
import com.quorumtrading.model.entity.Position;
import com.quorumtrading.model.enums.InvestmentType;
import com.quorumtrading.model.orchestration.StrategyConfigParser;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.SimulationId;
import com.quorumtrading.model.valueobject.Symbol;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A quiet evaluation must not write the same portfolio twice at the same instant.
 *
 * <p>One snapshot is written per decision, and a multi-ticker evaluation produces one decision per
 * symbol at the same simulated timestamp. When those decisions are all HOLD the portfolio does not
 * move between them, so each wrote a byte-identical row — a BTC+ETH backtest accumulated two rows
 * per quiet evaluation, and 36 such duplicate groups across 32 simulations were found in a running
 * instance. Same-instant rows that genuinely differ must survive: they are the intermediate states
 * after each leg of a multi-symbol trade, and the portfolio chart steps through them.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ExecuteConsensusDecisionService — same-instant snapshot dedup")
class ExecuteConsensusDecisionSnapshotDedupTest {

    @Mock private BrokerPort brokerPort;
    @Mock private PortfolioRepository portfolioRepository;
    @Mock private ConsensusDecisionRepository consensusDecisionRepository;
    @Mock private MarketEventRepository marketEventRepository;
    @Mock private EventPublisher<TradeExecutionEvent> tradeExecutionEventPublisher;
    @Mock private EventPublisher<PortfolioChangeEvent> portfolioChangeEventPublisher;
    @Mock private TradingLimitsConfig tradingLimitsConfig;
    @Mock private SimulationRepository simulationRepository;
    @Mock private StrategyRepository strategyRepository;

    private ExecuteConsensusDecisionService service;
    private SimulationId simulationId;
    private Instant evaluationTime;

    private static final Symbol BTC = new Symbol("BTC", InvestmentType.CRYPTO);

    @BeforeEach
    void setUp() {
        service = new ExecuteConsensusDecisionService(
                brokerPort, portfolioRepository, consensusDecisionRepository, marketEventRepository,
                tradeExecutionEventPublisher, portfolioChangeEventPublisher, tradingLimitsConfig,
                simulationRepository, strategyRepository, new StrategyConfigParser());

        simulationId = new SimulationId(UUID.randomUUID());
        evaluationTime = Instant.parse("2026-07-21T00:00:00Z");

        // No simulation row: skips the end-date guard and leaves the rest of the path intact.
        lenient().when(simulationRepository.findById(simulationId)).thenReturn(java.util.Optional.empty());
        lenient().when(portfolioRepository.save(any(PortfolioSnapshot.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    private PortfolioSnapshot snapshot(Instant at, String totalValue, String cash, Map<Symbol, Position> positions) {
        return new PortfolioSnapshot(
                simulationId, at, positions,
                Money.usd(new BigDecimal(totalValue)), Money.usd(new BigDecimal(cash)));
    }

    private void brokerReportsCashOnly(String cash) {
        when(brokerPort.getCurrentPortfolio(simulationId))
                .thenReturn(new Portfolio(simulationId, Map.of(), Money.usd(new BigDecimal(cash))));
    }

    @Test
    @DisplayName("an identical cash-only snapshot at the same instant is reused, not rewritten")
    void shouldReuseIdenticalCashOnlySnapshot() {
        brokerReportsCashOnly("10000.00");
        PortfolioSnapshot alreadyThere = snapshot(evaluationTime, "10000.00", "10000.00", Map.of());
        when(portfolioRepository.findBySimulationIdAndTimeRange(simulationId, evaluationTime, evaluationTime))
                .thenReturn(List.of(alreadyThere));

        PortfolioSnapshot result = service.updatePortfolioWithMarketValue(simulationId, evaluationTime);

        assertThat(result).isSameAs(alreadyThere);
        verify(portfolioRepository, never()).save(any(PortfolioSnapshot.class));
        verify(portfolioChangeEventPublisher, never()).publish(any());
    }

    @Test
    @DisplayName("differing scale is still the same portfolio — 10000.0 does not justify a new row")
    void shouldTreatDifferentBigDecimalScaleAsIdentical() {
        brokerReportsCashOnly("10000.00");
        PortfolioSnapshot alreadyThere = snapshot(evaluationTime, "10000.0", "10000.0", Map.of());
        when(portfolioRepository.findBySimulationIdAndTimeRange(simulationId, evaluationTime, evaluationTime))
                .thenReturn(List.of(alreadyThere));

        PortfolioSnapshot result = service.updatePortfolioWithMarketValue(simulationId, evaluationTime);

        assertThat(result).isSameAs(alreadyThere);
        verify(portfolioRepository, never()).save(any(PortfolioSnapshot.class));
    }

    @Test
    @DisplayName("nothing at this instant yet — the snapshot is written")
    void shouldWriteWhenNoSnapshotExistsAtThatInstant() {
        brokerReportsCashOnly("10000.00");
        when(portfolioRepository.findBySimulationIdAndTimeRange(simulationId, evaluationTime, evaluationTime))
                .thenReturn(List.of());

        service.updatePortfolioWithMarketValue(simulationId, evaluationTime);

        verify(portfolioRepository).save(any(PortfolioSnapshot.class));
    }

    @Test
    @DisplayName("a different portfolio at the same instant is still written — a trade happened between legs")
    void shouldWriteWhenSameInstantSnapshotDiffers() {
        brokerReportsCashOnly("6008.19");
        PortfolioSnapshot beforeTheTrade = snapshot(evaluationTime, "10000.00", "10000.00", Map.of());
        when(portfolioRepository.findBySimulationIdAndTimeRange(simulationId, evaluationTime, evaluationTime))
                .thenReturn(List.of(beforeTheTrade));

        service.updatePortfolioWithMarketValue(simulationId, evaluationTime);

        verify(portfolioRepository).save(any(PortfolioSnapshot.class));
    }

    @Test
    @DisplayName("same cash and value but a different position set is a different portfolio")
    void shouldWriteWhenPositionsDifferAtSameInstant() {
        brokerReportsCashOnly("10000.00");
        PortfolioSnapshot holdingBtc = snapshot(evaluationTime, "10000.00", "10000.00",
                Map.of(BTC, new Position(BTC, new BigDecimal("0.5"),
                        Money.usd(new BigDecimal("60000")), Money.usd(new BigDecimal("60000")))));
        when(portfolioRepository.findBySimulationIdAndTimeRange(simulationId, evaluationTime, evaluationTime))
                .thenReturn(List.of(holdingBtc));

        service.updatePortfolioWithMarketValue(simulationId, evaluationTime);

        verify(portfolioRepository).save(any(PortfolioSnapshot.class));
    }

    @Test
    @DisplayName("the dedup is scoped to the instant, not the simulation")
    void shouldOnlyLookAtTheInstantBeingWritten() {
        brokerReportsCashOnly("10000.00");
        when(portfolioRepository.findBySimulationIdAndTimeRange(simulationId, evaluationTime, evaluationTime))
                .thenReturn(List.of());

        service.updatePortfolioWithMarketValue(simulationId, evaluationTime);

        verify(portfolioRepository)
                .findBySimulationIdAndTimeRange(eq(simulationId), eq(evaluationTime), eq(evaluationTime));
    }
}
