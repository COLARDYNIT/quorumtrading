package com.quorumtrading.application.service;

import com.quorumtrading.application.exception.AccessDeniedException;
import com.quorumtrading.application.port.out.SimulationRepository;
import com.quorumtrading.application.port.out.StrategyRepository;
import com.quorumtrading.application.usecase.UpdatePortfolioWithMarketValueUseCase;
import com.quorumtrading.model.entity.Simulation;
import com.quorumtrading.model.entity.Strategy;
import com.quorumtrading.model.valueobject.SimulationId;
import com.quorumtrading.model.valueobject.StrategyId;
import com.quorumtrading.model.valueobject.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LiquidateAllPositionsServiceAuthTest {

    @Mock private SimulationRepository simulationRepository;
    @Mock private StrategyRepository strategyRepository;
    @Mock private BrokerRouter brokerRouter;
    @Mock private UpdatePortfolioWithMarketValueUseCase updatePortfolioUseCase;

    private LiquidateAllPositionsService service;

    private static final UserId OWNER = new UserId(UUID.randomUUID());
    private static final UserId OTHER = new UserId(UUID.randomUUID());
    private static final SimulationId SIM_ID = SimulationId.generate();
    private static final StrategyId STRATEGY_ID = StrategyId.generate();

    @BeforeEach
    void setUp() {
        AuthorizationService authorizationService = new AuthorizationService();
        service = new LiquidateAllPositionsService(simulationRepository, strategyRepository, brokerRouter, updatePortfolioUseCase, authorizationService);
    }

    @Test
    void rejectsLiquidateByNonOwner() {
        Simulation simulation = mock(Simulation.class);
        when(simulation.getSimulationId()).thenReturn(SIM_ID);
        when(simulation.getStrategyId()).thenReturn(STRATEGY_ID);
        when(simulationRepository.findById(SIM_ID)).thenReturn(Optional.of(simulation));

        Strategy strategy = mock(Strategy.class);
        when(strategy.getOwnerId()).thenReturn(OWNER);
        when(strategyRepository.findById(STRATEGY_ID)).thenReturn(Optional.of(strategy));

        assertThatThrownBy(() -> service.execute(SIM_ID, OTHER))
            .isInstanceOf(AccessDeniedException.class);

        verify(brokerRouter, never()).executeTrade(any());
    }

    @Test
    void nullUserIdIsDenied() {
        Simulation simulation = mock(Simulation.class);
        when(simulation.getSimulationId()).thenReturn(SIM_ID);
        when(simulation.getStrategyId()).thenReturn(STRATEGY_ID);
        when(simulationRepository.findById(SIM_ID)).thenReturn(Optional.of(simulation));

        when(strategyRepository.findById(STRATEGY_ID)).thenReturn(Optional.of(mock(Strategy.class)));

        // A null user used to skip the ownership check, so an unresolved caller could
        // liquidate every position in another user's simulation.
        assertThatThrownBy(() -> service.execute(SIM_ID, null))
            .isInstanceOf(AccessDeniedException.class);
    }
}
