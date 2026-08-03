package com.quorumtrading.application.usecase;

import com.quorumtrading.application.exception.AccessDeniedException;
import com.quorumtrading.application.port.out.ConsensusDecisionRepository;
import com.quorumtrading.application.port.out.LLMDecisionRepository;
import com.quorumtrading.application.port.out.SimulationRepository;
import com.quorumtrading.application.port.out.StrategyRepository;
import com.quorumtrading.application.service.AuthorizationService;
import com.quorumtrading.application.usecase.command.GetConsensusWithAgentsCommand;
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
class GetConsensusWithAgentsUseCaseAuthTest {

    @Mock private ConsensusDecisionRepository consensusRepo;
    @Mock private LLMDecisionRepository llmDecisionRepository;
    @Mock private SimulationRepository simulationRepository;
    @Mock private StrategyRepository strategyRepository;

    private GetConsensusWithAgentsUseCase useCase;

    private static final UserId OWNER = new UserId(UUID.randomUUID());
    private static final UserId OTHER = new UserId(UUID.randomUUID());
    private static final SimulationId SIM_ID = SimulationId.generate();
    private static final StrategyId STRATEGY_ID = StrategyId.generate();

    @BeforeEach
    void setUp() {
        AuthorizationService authorizationService = new AuthorizationService();
        useCase = new GetConsensusWithAgentsUseCase(consensusRepo, llmDecisionRepository, simulationRepository, strategyRepository, authorizationService);
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

        GetConsensusWithAgentsCommand cmd = new GetConsensusWithAgentsCommand(
            SIM_ID.value(), null, null, 10, null, null, OTHER);

        assertThatThrownBy(() -> useCase.execute(cmd))
            .isInstanceOf(AccessDeniedException.class);

        verify(consensusRepo, never()).findBySimulationIdAndTicker(any(), any(), any(), any(), any(), any());
    }

    @Test
    void allowsQueryByOwner() {
        Simulation simulation = mock(Simulation.class);
        when(simulation.getStrategyId()).thenReturn(STRATEGY_ID);
        when(simulationRepository.findById(SIM_ID)).thenReturn(Optional.of(simulation));

        Strategy strategy = mock(Strategy.class);
        when(strategy.getOwnerId()).thenReturn(OWNER);
        when(strategyRepository.findById(STRATEGY_ID)).thenReturn(Optional.of(strategy));

        when(consensusRepo.findBySimulationIdAndTicker(any(), any(), any(), any(), any(), any()))
            .thenReturn(List.of());

        GetConsensusWithAgentsCommand cmd = new GetConsensusWithAgentsCommand(
            SIM_ID.value(), null, null, 10, null, null, OWNER);

        useCase.execute(cmd);

        verify(consensusRepo).findBySimulationIdAndTicker(any(), any(), any(), any(), any(), any());
    }

    @Test
    void nullUserIdSkipsAuth() {
        when(consensusRepo.findBySimulationIdAndTicker(any(), any(), any(), any(), any(), any()))
            .thenReturn(List.of());

        GetConsensusWithAgentsCommand cmd = new GetConsensusWithAgentsCommand(
            SIM_ID.value(), null, null, 10, null, null);

        useCase.execute(cmd);

        verify(simulationRepository, never()).findById(any());
        verify(consensusRepo).findBySimulationIdAndTicker(any(), any(), any(), any(), any(), any());
    }
}
