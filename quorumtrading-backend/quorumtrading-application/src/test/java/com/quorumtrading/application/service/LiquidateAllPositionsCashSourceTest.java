package com.quorumtrading.application.service;

import com.quorumtrading.application.port.out.SimulationRepository;
import com.quorumtrading.application.port.out.StrategyRepository;
import com.quorumtrading.application.usecase.UpdatePortfolioWithMarketValueUseCase;
import com.quorumtrading.application.usecase.result.LiquidateAllResult;
import com.quorumtrading.model.entity.Portfolio;
import com.quorumtrading.model.entity.PortfolioSnapshot;
import com.quorumtrading.model.entity.Simulation;
import com.quorumtrading.model.entity.Strategy;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.SimulationId;
import com.quorumtrading.model.valueobject.StrategyId;
import com.quorumtrading.model.valueobject.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@code cashAfterLiquidation} must be this simulation's cash, not the broker account's.
 *
 * <p>A MOCK broker account is shared by every simulation that uses it, so asking the broker returns
 * the ACCOUNT balance. Measured live: a simulation whose tracked cash was $1,000 reported
 * $12,000 — the account balance, twelve times the truth — to a caller who had just liquidated it.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LiquidateAllPositionsCashSourceTest {

    @Mock private SimulationRepository simulationRepository;
    @Mock private StrategyRepository strategyRepository;
    @Mock private BrokerRouter brokerRouter;
    @Mock private UpdatePortfolioWithMarketValueUseCase updatePortfolioUseCase;

    private LiquidateAllPositionsService service;

    private static final UserId OWNER = new UserId(UUID.randomUUID());
    private static final SimulationId SIM_ID = SimulationId.generate();
    private static final StrategyId STRATEGY_ID = StrategyId.generate();

    private static final Money SIMULATION_CASH = new Money(new BigDecimal("1000.00"), Money.Currency.USD);
    private static final Money BROKER_ACCOUNT_CASH = new Money(new BigDecimal("12000.00"), Money.Currency.USD);

    @BeforeEach
    void setUp() {
        service = new LiquidateAllPositionsService(simulationRepository, strategyRepository,
            brokerRouter, updatePortfolioUseCase, new AuthorizationService());

        Simulation simulation = mock(Simulation.class);
        when(simulation.getSimulationId()).thenReturn(SIM_ID);
        when(simulation.getStrategyId()).thenReturn(STRATEGY_ID);
        when(simulationRepository.findById(SIM_ID)).thenReturn(Optional.of(simulation));

        Strategy strategy = mock(Strategy.class);
        when(strategy.getOwnerId()).thenReturn(OWNER);
        when(strategyRepository.findById(STRATEGY_ID)).thenReturn(Optional.of(strategy));

        // No positions: the liquidation loop is not what is under test here, the reported cash is.
        Portfolio brokerView = new Portfolio(SIM_ID, Map.of(), BROKER_ACCOUNT_CASH);
        when(brokerRouter.getCurrentPortfolio(SIM_ID)).thenReturn(brokerView);

        PortfolioSnapshot snapshot = mock(PortfolioSnapshot.class);
        when(snapshot.getCashAvailable()).thenReturn(SIMULATION_CASH);
        when(updatePortfolioUseCase.updatePortfolioWithMarketValue(org.mockito.ArgumentMatchers.eq(SIM_ID),
            org.mockito.ArgumentMatchers.any())).thenReturn(snapshot);
    }

    @Test
    void reportsTheSimulationsOwnCashNotTheSharedBrokerBalance() {
        LiquidateAllResult result = service.execute(SIM_ID, OWNER);

        assertThat(result.cashAfterLiquidation())
            .as("the caller liquidated one simulation, so the figure must be that simulation's "
                + "cash — the broker account balance belongs to every simulation sharing it")
            .isEqualByComparingTo(SIMULATION_CASH.amount());
        assertThat(result.cashAfterLiquidation())
            .isNotEqualByComparingTo(BROKER_ACCOUNT_CASH.amount());
    }

    @Test
    void reportsNoCashRatherThanAWrongOneWhenTheSnapshotCouldNotBeWritten() {
        when(updatePortfolioUseCase.updatePortfolioWithMarketValue(org.mockito.ArgumentMatchers.eq(SIM_ID),
            org.mockito.ArgumentMatchers.any())).thenReturn(null);

        LiquidateAllResult result = service.execute(SIM_ID, OWNER);

        assertThat(result.cashAfterLiquidation())
            .as("with no snapshot there is no trustworthy figure; null says so, and the existing "
                + "stale-value error is still reported alongside")
            .isNull();
        assertThat(result.errors()).isNotEmpty();
    }
}
