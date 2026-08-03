package com.quorumtrading.application.service;

import com.quorumtrading.application.port.out.BrokerAccountRepository;
import com.quorumtrading.application.port.out.PortfolioRepository;
import com.quorumtrading.application.port.out.SimulationContributionRepository;
import com.quorumtrading.application.port.out.SimulationPortfolioRepository;
import com.quorumtrading.application.port.out.SimulationRepository;
import com.quorumtrading.application.port.out.StrategyRepository;
import com.quorumtrading.application.usecase.command.RecordSimulationContributionCommand;
import com.quorumtrading.model.entity.Contribution;
import com.quorumtrading.model.entity.PortfolioSnapshot;
import com.quorumtrading.model.entity.Simulation;
import com.quorumtrading.model.entity.SimulationPortfolio;
import com.quorumtrading.model.entity.Strategy;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.SimulationId;
import com.quorumtrading.model.valueobject.StrategyId;
import com.quorumtrading.model.valueobject.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Contributions must reach the snapshot lineage, not only the portfolio aggregate.
 *
 * <p>Every user-facing performance figure is derived from {@code portfolio_snapshots}, and each
 * snapshot carries cash forward from the previous one. Crediting only {@code SimulationPortfolio}
 * left a $9,500 contribution invisible: {@code totalInvested} grew to $10,000 while the timeline
 * still showed ~$500, and the simulation reported -99.49% having lost nothing.
 */
@ExtendWith(MockitoExtension.class)
class SimulationContributionSnapshotTest {

    @Mock private SimulationRepository simulationRepository;
    @Mock private SimulationPortfolioRepository simulationPortfolioRepository;
    @Mock private BrokerAccountRepository brokerAccountRepository;
    @Mock private SimulationContributionRepository contributionRepository;
    @Mock private StrategyRepository strategyRepository;
    @Mock private PortfolioRepository portfolioRepository;

    private SimulationContributionService service;

    private static final UserId OWNER = new UserId(UUID.randomUUID());
    private static final SimulationId SIM_ID = SimulationId.generate();
    private static final StrategyId STRATEGY_ID = StrategyId.generate();

    private static Money usd(String amount) {
        return new Money(new BigDecimal(amount), Money.Currency.USD);
    }

    @BeforeEach
    void setUp() {
        service = new SimulationContributionService(
            simulationRepository, simulationPortfolioRepository, brokerAccountRepository,
            contributionRepository, strategyRepository, new AuthorizationService(), portfolioRepository);

        Simulation simulation = mock(Simulation.class);
        lenient().when(simulation.getSimulationId()).thenReturn(SIM_ID);
        lenient().when(simulation.getStrategyId()).thenReturn(STRATEGY_ID);
        Contribution built = mock(Contribution.class);
        // The snapshot is ordered just after this instant so TWR attributes the cash to the
        // period it lands in rather than reading the value step as growth.
        lenient().when(built.createdAt()).thenReturn(Instant.parse("2026-07-27T19:39:51Z"));
        lenient().when(simulation.buildContribution(any(), any(), any(), any()))
            .thenReturn(built);
        when(simulationRepository.findById(SIM_ID)).thenReturn(Optional.of(simulation));

        Strategy strategy = mock(Strategy.class);
        lenient().when(strategy.getOwnerId()).thenReturn(OWNER);
        when(strategyRepository.findById(STRATEGY_ID)).thenReturn(Optional.of(strategy));

        SimulationPortfolio portfolio = mock(SimulationPortfolio.class);
        lenient().when(portfolio.getBrokerAccountId()).thenReturn(null);
        when(simulationPortfolioRepository.findById(SIM_ID)).thenReturn(Optional.of(portfolio));

        lenient().when(contributionRepository.save(any())).thenReturn(mock(Contribution.class));
    }

    private RecordSimulationContributionCommand contribution(String amount) {
        return new RecordSimulationContributionCommand(
            SIM_ID, usd(amount), Instant.parse("2026-07-27T00:00:00Z"), null, null, OWNER);
    }

    @Test
    void appendsASnapshotCarryingTheInjectedCash() {
        when(portfolioRepository.findLatestBySimulationId(SIM_ID)).thenReturn(Optional.of(
            new PortfolioSnapshot(SIM_ID, Instant.parse("2026-07-27T14:41:05Z"),
                Map.of(), usd("498.65"), usd("50.89"))));

        service.execute(contribution("9500.00"));

        ArgumentCaptor<PortfolioSnapshot> saved = ArgumentCaptor.forClass(PortfolioSnapshot.class);
        verify(portfolioRepository).save(saved.capture());

        assertThat(saved.getValue().getCashAvailable().amount())
            .isEqualByComparingTo(new BigDecimal("9550.89"));
        assertThat(saved.getValue().getTotalValue().amount())
            .isEqualByComparingTo(new BigDecimal("9998.65"));
    }

    @Test
    void leavesPositionsUntouchedBecauseAContributionIsNotATrade() {
        PortfolioSnapshot previous = new PortfolioSnapshot(SIM_ID, Instant.now(),
            Map.of(), usd("1000.00"), usd("1000.00"));
        when(portfolioRepository.findLatestBySimulationId(SIM_ID)).thenReturn(Optional.of(previous));

        service.execute(contribution("250.00"));

        ArgumentCaptor<PortfolioSnapshot> saved = ArgumentCaptor.forClass(PortfolioSnapshot.class);
        verify(portfolioRepository).save(saved.capture());
        assertThat(saved.getValue().getPositions()).isEqualTo(previous.getPositions());
    }

    @Test
    void stillCreditsThePortfolioAggregate() {
        when(portfolioRepository.findLatestBySimulationId(SIM_ID)).thenReturn(Optional.of(
            new PortfolioSnapshot(SIM_ID, Instant.now(), Map.of(), usd("100.00"), usd("100.00"))));

        service.execute(contribution("400.00"));

        verify(simulationPortfolioRepository).save(any(SimulationPortfolio.class));
    }

    /** No timeline yet is not a reason to reject the money — the aggregate still takes it. */
    @Test
    void doesNotFailWhenThereIsNoSnapshotYet() {
        when(portfolioRepository.findLatestBySimulationId(SIM_ID)).thenReturn(Optional.empty());

        service.execute(contribution("400.00"));

        verify(portfolioRepository, never()).save(any());
        verify(simulationPortfolioRepository).save(any(SimulationPortfolio.class));
    }
}
