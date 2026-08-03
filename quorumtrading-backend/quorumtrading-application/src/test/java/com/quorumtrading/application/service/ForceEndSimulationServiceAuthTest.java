package com.quorumtrading.application.service;

import com.quorumtrading.application.event.SimulationCompletedEvent;
import com.quorumtrading.application.exception.AccessDeniedException;
import com.quorumtrading.application.port.out.BrokerAccountRepository;
import com.quorumtrading.application.port.out.EventPublisher;
import com.quorumtrading.application.port.out.PortfolioRepository;
import com.quorumtrading.application.port.out.SimulationRepository;
import com.quorumtrading.application.port.out.StrategyRepository;
import com.quorumtrading.application.usecase.LiquidateAllPositionsUseCase;
import com.quorumtrading.application.usecase.command.ForceEndSimulationCommand;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ForceEndSimulationServiceAuthTest {

    @Mock private SimulationRepository simulationRepository;
    @Mock private StrategyRepository strategyRepository;
    @Mock private LiquidateAllPositionsUseCase liquidateUseCase;
    @Mock private PortfolioRepository portfolioRepository;
    @Mock private EventPublisher<SimulationCompletedEvent> eventPublisher;
    @Mock private BrokerAccountRepository brokerAccountRepository;
    @Mock private com.quorumtrading.application.port.out.SimulationContributionRepository contributionRepository;

    private ForceEndSimulationService service;

    private static final UserId OWNER = new UserId(UUID.randomUUID());
    private static final UserId OTHER = new UserId(UUID.randomUUID());
    private static final SimulationId SIM_ID = SimulationId.generate();
    private static final StrategyId STRATEGY_ID = StrategyId.generate();

    @BeforeEach
    void setUp() {
        AuthorizationService authorizationService = new AuthorizationService();
        service = new ForceEndSimulationService(simulationRepository, strategyRepository, liquidateUseCase, portfolioRepository, eventPublisher, brokerAccountRepository, authorizationService, contributionRepository);
    }

    @Test
    void rejectsForceEndByNonOwner() {
        Simulation simulation = mock(Simulation.class);
        when(simulation.getSimulationId()).thenReturn(SIM_ID);
        when(simulation.getStrategyId()).thenReturn(STRATEGY_ID);
        when(simulationRepository.findById(SIM_ID)).thenReturn(Optional.of(simulation));

        Strategy strategy = mock(Strategy.class);
        when(strategy.getOwnerId()).thenReturn(OWNER);
        when(strategyRepository.findById(STRATEGY_ID)).thenReturn(Optional.of(strategy));

        ForceEndSimulationCommand command = new ForceEndSimulationCommand(SIM_ID, true, OTHER);

        assertThatThrownBy(() -> service.execute(command))
            .isInstanceOf(AccessDeniedException.class);

        verify(simulationRepository, never()).save(any());
    }

    @Test
    void allowsForceEndByOwner() {
        Simulation simulation = mock(Simulation.class);
        when(simulation.getStrategyId()).thenReturn(STRATEGY_ID);
        when(simulation.getSimulationId()).thenReturn(SIM_ID);
        when(simulation.isTerminal()).thenReturn(false);
        when(simulation.getBrokerAccountId()).thenReturn(null);
        when(simulationRepository.findById(SIM_ID)).thenReturn(Optional.of(simulation));

        Strategy strategy = mock(Strategy.class);
        when(strategy.getOwnerId()).thenReturn(OWNER);
        when(strategyRepository.findById(STRATEGY_ID)).thenReturn(Optional.of(strategy));

        ForceEndSimulationCommand command = new ForceEndSimulationCommand(SIM_ID, false, OWNER);

        service.execute(command);

        verify(simulation).forceComplete();
        // Compare-and-set, not save(). While save() still copied lifecycle columns, the user's
        // force-end was reverted by the executor's next progress write and the run carried on.
        verify(simulationRepository).updateStatus(any(), any(), any(), any(), any());
        verify(simulationRepository, never()).save(any());
    }

    @Test
    void forwardsTheCallerToLiquidationSoItIsNotSeenAsAnonymous() {
        Simulation simulation = mock(Simulation.class);
        when(simulation.getStrategyId()).thenReturn(STRATEGY_ID);
        when(simulation.getSimulationId()).thenReturn(SIM_ID);
        when(simulation.isTerminal()).thenReturn(false);
        when(simulation.getBrokerAccountId()).thenReturn(null);
        when(simulationRepository.findById(SIM_ID)).thenReturn(Optional.of(simulation));

        Strategy strategy = mock(Strategy.class);
        when(strategy.getOwnerId()).thenReturn(OWNER);
        when(strategyRepository.findById(STRATEGY_ID)).thenReturn(Optional.of(strategy));

        // Liquidation runs its own ownership check. Calling the single-argument overload here
        // passed a null user, so every force-end that asked to liquidate failed with
        // "User anonymous does not have access" — on both MCP and REST.
        service.execute(new ForceEndSimulationCommand(SIM_ID, true, OWNER));

        verify(liquidateUseCase).execute(SIM_ID, OWNER);
        verify(simulation).forceComplete();
    }

    @Test
    void nullUserIdIsDenied() {
        Simulation simulation = mock(Simulation.class);
        when(simulation.getSimulationId()).thenReturn(SIM_ID);
        when(simulation.getStrategyId()).thenReturn(STRATEGY_ID);
        when(simulationRepository.findById(SIM_ID)).thenReturn(Optional.of(simulation));

        when(strategyRepository.findById(STRATEGY_ID)).thenReturn(Optional.of(mock(Strategy.class)));

        // A null user used to skip the ownership check, so an unresolved caller could
        // force-end (and optionally liquidate) another user's simulation.
        ForceEndSimulationCommand command = new ForceEndSimulationCommand(SIM_ID, false);

        assertThatThrownBy(() -> service.execute(command))
            .isInstanceOf(AccessDeniedException.class);

        verify(simulation, never()).forceComplete();
    }
}
