package com.quorumtrading.application.service;

import com.quorumtrading.application.exception.AccessDeniedException;
import com.quorumtrading.application.port.out.BrokerAccountRepository;
import com.quorumtrading.application.port.out.SimulationContributionRepository;
import com.quorumtrading.application.port.out.SimulationPortfolioRepository;
import com.quorumtrading.application.port.out.SimulationRepository;
import com.quorumtrading.application.port.out.StrategyRepository;
import com.quorumtrading.application.usecase.command.RecordSimulationContributionCommand;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Contributions move real money: they top up the simulation portfolio's cash and the parent
 * broker account's reserved balance. {@code RecordSimulationContributionCommand} carried no
 * user at all, so any caller could fund any LIVE simulation by UUID.
 */
@ExtendWith(MockitoExtension.class)
class SimulationContributionServiceAuthTest {

    @Mock private SimulationRepository simulationRepository;
    @Mock private SimulationPortfolioRepository simulationPortfolioRepository;
    @Mock private BrokerAccountRepository brokerAccountRepository;
    @Mock private SimulationContributionRepository contributionRepository;
    @Mock private StrategyRepository strategyRepository;
    @Mock private com.quorumtrading.application.port.out.PortfolioRepository portfolioRepository;

    private SimulationContributionService service;

    private static final UserId OWNER = new UserId(UUID.randomUUID());
    private static final UserId OTHER = new UserId(UUID.randomUUID());
    private static final SimulationId SIM_ID = SimulationId.generate();
    private static final StrategyId STRATEGY_ID = StrategyId.generate();

    @BeforeEach
    void setUp() {
        service = new SimulationContributionService(
            simulationRepository, simulationPortfolioRepository, brokerAccountRepository,
            contributionRepository, strategyRepository, new AuthorizationService(), portfolioRepository);
    }

    private void stubOwnedSimulation() {
        Simulation simulation = mock(Simulation.class);
        lenient().when(simulation.getSimulationId()).thenReturn(SIM_ID);
        lenient().when(simulation.getStrategyId()).thenReturn(STRATEGY_ID);
        when(simulationRepository.findById(SIM_ID)).thenReturn(Optional.of(simulation));

        Strategy strategy = mock(Strategy.class);
        lenient().when(strategy.getOwnerId()).thenReturn(OWNER);
        when(strategyRepository.findById(STRATEGY_ID)).thenReturn(Optional.of(strategy));
    }

    private RecordSimulationContributionCommand contributionBy(UserId user) {
        return new RecordSimulationContributionCommand(
            SIM_ID,
            new Money(new BigDecimal("500.00"), Money.Currency.USD),
            Instant.parse("2026-01-15T00:00:00Z"),
            null,
            null,
            user);
    }

    @Test
    void rejectsContributionByNonOwner() {
        stubOwnedSimulation();

        assertThatThrownBy(() -> service.execute(contributionBy(OTHER)))
            .isInstanceOf(AccessDeniedException.class);

        verify(simulationPortfolioRepository, never()).save(any());
        verify(brokerAccountRepository, never()).save(any());
    }

    @Test
    void rejectsContributionWithNoUser() {
        stubOwnedSimulation();

        assertThatThrownBy(() -> service.execute(contributionBy(null)))
            .isInstanceOf(AccessDeniedException.class);

        verify(simulationPortfolioRepository, never()).save(any());
        verify(brokerAccountRepository, never()).save(any());
    }

    @Test
    void rejectsReadingAnotherUsersContributionHistory() {
        stubOwnedSimulation();

        assertThatThrownBy(() -> service.execute(SIM_ID, OTHER))
            .isInstanceOf(AccessDeniedException.class);

        verify(contributionRepository, never()).findBySimulationId(any());
    }
}
