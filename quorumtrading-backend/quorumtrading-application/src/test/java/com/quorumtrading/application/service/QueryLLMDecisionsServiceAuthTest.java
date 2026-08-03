package com.quorumtrading.application.service;

import com.quorumtrading.application.exception.AccessDeniedException;
import com.quorumtrading.application.port.out.LLMDecisionRepository;
import com.quorumtrading.application.port.out.SimulationRepository;
import com.quorumtrading.application.port.out.StrategyRepository;
import com.quorumtrading.application.usecase.query.QueryLLMDecisionsQuery;
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

/**
 * The sibling {@link QueryConsensusDecisionsService} was ownership-checked and had an auth test;
 * this one took no user at all, so the raw agent prompts and reasoning for any simulation UUID
 * were readable by anyone. That asymmetry was an oversight, not a design decision.
 */
@ExtendWith(MockitoExtension.class)
class QueryLLMDecisionsServiceAuthTest {

    @Mock private LLMDecisionRepository llmDecisionRepository;
    @Mock private SimulationRepository simulationRepository;
    @Mock private StrategyRepository strategyRepository;

    private QueryLLMDecisionsService service;

    private static final UserId OWNER = new UserId(UUID.randomUUID());
    private static final UserId OTHER = new UserId(UUID.randomUUID());
    private static final SimulationId SIM_ID = SimulationId.generate();
    private static final StrategyId STRATEGY_ID = StrategyId.generate();

    @BeforeEach
    void setUp() {
        service = new QueryLLMDecisionsService(
            llmDecisionRepository, simulationRepository, strategyRepository, new AuthorizationService());

        Simulation simulation = mock(Simulation.class);
        lenient().when(simulation.getSimulationId()).thenReturn(SIM_ID);
        lenient().when(simulation.getStrategyId()).thenReturn(STRATEGY_ID);
        lenient().when(simulationRepository.findById(SIM_ID)).thenReturn(Optional.of(simulation));

        Strategy strategy = mock(Strategy.class);
        lenient().when(strategy.getOwnerId()).thenReturn(OWNER);
        lenient().when(strategy.isPublicVisible()).thenReturn(false);
        lenient().when(strategyRepository.findById(STRATEGY_ID)).thenReturn(Optional.of(strategy));
    }

    private QueryLLMDecisionsQuery queryBy(UserId user) {
        return new QueryLLMDecisionsQuery(SIM_ID.value(), null, null, null, null, null, null, user);
    }

    @Test
    void rejectsQueryByNonOwner() {
        assertThatThrownBy(() -> service.execute(queryBy(OTHER)))
            .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(llmDecisionRepository);
    }

    @Test
    void rejectsQueryWithNoUser() {
        assertThatThrownBy(() -> service.execute(queryBy(null)))
            .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(llmDecisionRepository);
    }

    @Test
    void allowsQueryByOwner() {
        service.execute(queryBy(OWNER));

        verify(llmDecisionRepository).findBySimulationId(
            eq(SIM_ID.value()), any(), any(), any(), any(), any(), any());
    }
}
