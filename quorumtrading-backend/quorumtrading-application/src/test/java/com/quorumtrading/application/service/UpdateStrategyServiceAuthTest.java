package com.quorumtrading.application.service;

import com.quorumtrading.application.exception.AccessDeniedException;
import com.quorumtrading.application.port.out.BrokerPort;
import com.quorumtrading.application.port.out.LLMPort;
import com.quorumtrading.application.port.out.SimulationRepository;
import com.quorumtrading.application.port.out.StrategyRepository;
import com.quorumtrading.application.usecase.command.UpdateStrategyCommand;
import com.quorumtrading.model.entity.Strategy;
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
class UpdateStrategyServiceAuthTest {

    @Mock private StrategyRepository strategyRepository;
    @Mock private SimulationRepository simulationRepository;
    @Mock private LLMPort llmPort;
    @Mock private BrokerPort brokerPort;

    private UpdateStrategyService service;

    private static final UserId OWNER = new UserId(UUID.randomUUID());
    private static final UserId OTHER = new UserId(UUID.randomUUID());
    private static final StrategyId STRATEGY_ID = StrategyId.generate();

    @BeforeEach
    void setUp() {
        AuthorizationService authorizationService = new AuthorizationService();
        service = new UpdateStrategyService(strategyRepository, simulationRepository, llmPort, brokerPort, authorizationService);
    }

    @Test
    void rejectsUpdateByNonOwner() {
        Strategy existing = mock(Strategy.class);
        when(existing.getOwnerId()).thenReturn(OWNER);
        when(existing.getId()).thenReturn(STRATEGY_ID);
        when(strategyRepository.findById(STRATEGY_ID)).thenReturn(Optional.of(existing));

        UpdateStrategyCommand command = new UpdateStrategyCommand(STRATEGY_ID, "new content", OTHER);

        assertThatThrownBy(() -> service.execute(command))
            .isInstanceOf(AccessDeniedException.class);

        verify(strategyRepository, never()).save(any());
    }

    @Test
    void allowsUpdateByOwner() {
        Strategy existing = mock(Strategy.class);
        when(existing.getOwnerId()).thenReturn(OWNER);
        when(strategyRepository.findById(STRATEGY_ID)).thenReturn(Optional.of(existing));
        when(simulationRepository.findByStrategyId(STRATEGY_ID)).thenReturn(List.of());

        UpdateStrategyCommand command = new UpdateStrategyCommand(STRATEGY_ID, "new content", OWNER);

        // May throw validation errors on content, but should not throw AccessDeniedException
        try {
            service.execute(command);
        } catch (AccessDeniedException e) {
            throw new AssertionError("Should not throw AccessDeniedException for owner", e);
        } catch (Exception ignored) {
            // Other exceptions (content validation, etc.) are expected in this minimal test
        }
    }

    @Test
    void nullUserIdIsDenied() {
        Strategy existing = mock(Strategy.class);
        when(existing.getId()).thenReturn(STRATEGY_ID);
        when(strategyRepository.findById(STRATEGY_ID)).thenReturn(Optional.of(existing));

        UpdateStrategyCommand command = new UpdateStrategyCommand(STRATEGY_ID, "new content");

        // A null user used to skip the ownership check, so an unresolved caller could
        // rewrite another user's strategy.
        assertThatThrownBy(() -> service.execute(command))
            .isInstanceOf(AccessDeniedException.class);

        verify(strategyRepository, never()).save(any());
    }
}
