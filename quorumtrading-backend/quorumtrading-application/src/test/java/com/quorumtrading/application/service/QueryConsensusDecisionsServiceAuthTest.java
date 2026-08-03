package com.quorumtrading.application.service;

import com.quorumtrading.application.exception.AccessDeniedException;
import com.quorumtrading.application.port.out.ConsensusDecisionRepository;
import com.quorumtrading.application.port.out.SimulationRepository;
import com.quorumtrading.application.port.out.StrategyRepository;
import com.quorumtrading.application.usecase.query.QueryConsensusDecisionsQuery;
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

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QueryConsensusDecisionsServiceAuthTest {

    @Mock private ConsensusDecisionRepository consensusRepo;
    @Mock private SimulationRepository simulationRepository;
    @Mock private StrategyRepository strategyRepository;

    private QueryConsensusDecisionsService service;

    private static final UserId OWNER = new UserId(UUID.randomUUID());
    private static final UserId OTHER = new UserId(UUID.randomUUID());
    private static final SimulationId SIM_ID = SimulationId.generate();
    private static final StrategyId STRATEGY_ID = StrategyId.generate();

    @BeforeEach
    void setUp() {
        AuthorizationService authorizationService = new AuthorizationService();
        service = new QueryConsensusDecisionsService(consensusRepo, simulationRepository, strategyRepository, authorizationService);
    }

    @Test
    void rejectsQueryByNonOwner() {
        Simulation simulation = mock(Simulation.class);
        when(simulation.getSimulationId()).thenReturn(SIM_ID);
        when(simulation.getStrategyId()).thenReturn(STRATEGY_ID);
        when(simulationRepository.findById(SIM_ID)).thenReturn(Optional.of(simulation));

        Strategy strategy = mock(Strategy.class);
        when(strategy.getOwnerId()).thenReturn(OWNER);
        when(strategyRepository.findById(STRATEGY_ID)).thenReturn(Optional.of(strategy));

        QueryConsensusDecisionsQuery query = new QueryConsensusDecisionsQuery(
            SIM_ID.value(), null, null, null, null, null, null, OTHER);

        assertThatThrownBy(() -> service.execute(query))
            .isInstanceOf(AccessDeniedException.class);

        verify(consensusRepo, never()).findBySimulationId(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void allowsQueryByOwner() {
        Simulation simulation = mock(Simulation.class);
        when(simulation.getStrategyId()).thenReturn(STRATEGY_ID);
        when(simulationRepository.findById(SIM_ID)).thenReturn(Optional.of(simulation));

        Strategy strategy = mock(Strategy.class);
        when(strategy.getOwnerId()).thenReturn(OWNER);
        when(strategyRepository.findById(STRATEGY_ID)).thenReturn(Optional.of(strategy));

        when(consensusRepo.findBySimulationId(any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(List.of());

        QueryConsensusDecisionsQuery query = new QueryConsensusDecisionsQuery(
            SIM_ID.value(), null, null, null, null, null, null, OWNER);

        service.execute(query);

        verify(consensusRepo).findBySimulationId(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void nullUserIdIsDenied() {
        Simulation simulation = mock(Simulation.class);
        when(simulation.getSimulationId()).thenReturn(SIM_ID);
        when(simulation.getStrategyId()).thenReturn(STRATEGY_ID);
        when(simulationRepository.findById(SIM_ID)).thenReturn(Optional.of(simulation));

        when(strategyRepository.findById(STRATEGY_ID)).thenReturn(Optional.of(mock(Strategy.class)));

        QueryConsensusDecisionsQuery query = new QueryConsensusDecisionsQuery(
            SIM_ID.value(), null, null, null, null, null, null);

        // A null user used to skip the check entirely and return the decisions anyway.
        assertThatThrownBy(() -> service.execute(query))
            .isInstanceOf(AccessDeniedException.class);

        verify(consensusRepo, never()).findBySimulationId(any(), any(), any(), any(), any(), any(), any());
    }
}
