package com.quorumtrading.application.service;

import com.quorumtrading.application.port.out.BrokerAccountRepository;
import com.quorumtrading.application.port.out.SimulationRepository;
import com.quorumtrading.application.port.out.StrategyRepository;
import com.quorumtrading.application.usecase.command.DeleteStrategyCommand;
import com.quorumtrading.model.entity.Simulation;
import com.quorumtrading.model.entity.Strategy;
import com.quorumtrading.model.enums.SimulationStatus;
import com.quorumtrading.model.valueobject.SimulationId;
import com.quorumtrading.model.valueobject.StrategyId;
import com.quorumtrading.model.valueobject.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Deleting a strategy must not pull a running simulation out from under its executor.
 *
 * <p>The guard used to test for {@code LIVE} alone, so a {@code SIMULATING} backtest was cascade
 * deleted mid-run. The executor holds its own {@link Simulation} instance and is never signalled,
 * so it kept going: a full consensus round was paid for after the delete, every insert then failed
 * with a foreign-key violation against the vanished simulation row, and the run finally died on a
 * "Data inconsistency between domain and database" error. From the caller's side the tool reported
 * success while claiming, in its own description, that running simulations block deletion.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DeleteStrategyService — running-simulation guard")
class DeleteStrategyServiceTest {

    @Mock private StrategyRepository strategyRepository;
    @Mock private SimulationRepository simulationRepository;
    @Mock private BrokerAccountRepository brokerAccountRepository;
    @Mock private AuthorizationService authorizationService;

    private DeleteStrategyService service;

    private StrategyId strategyId;
    private UserId userId;

    @BeforeEach
    void setUp() {
        service = new DeleteStrategyService(
                strategyRepository, simulationRepository, brokerAccountRepository, authorizationService);
        strategyId = StrategyId.generate();
        userId = UserId.generate();

        Strategy strategy = org.mockito.Mockito.mock(Strategy.class);
        lenient().when(strategyRepository.findById(strategyId)).thenReturn(Optional.of(strategy));
        lenient().when(strategyRepository.deleteById(strategyId)).thenReturn(true);
    }

    private DeleteStrategyCommand command() {
        return new DeleteStrategyCommand(strategyId, userId);
    }

    private Simulation simulation(SimulationStatus status, boolean paused) {
        Simulation sim = org.mockito.Mockito.mock(Simulation.class);
        lenient().when(sim.getStatus()).thenReturn(status);
        lenient().when(sim.isPaused()).thenReturn(paused);
        lenient().when(sim.isTerminal()).thenReturn(
                status == SimulationStatus.COMPLETED || status == SimulationStatus.FAILED);
        lenient().when(sim.getSimulationId()).thenReturn(new SimulationId(UUID.randomUUID()));
        lenient().when(sim.getBrokerAccountId()).thenReturn(null);
        return sim;
    }

    @Test
    @DisplayName("a running backtest blocks the delete")
    void shouldRefuseWhileABacktestIsSimulating() {
        Simulation running = simulation(SimulationStatus.SIMULATING, false);
        when(simulationRepository.findByStrategyId(strategyId)).thenReturn(List.of(running));

        assertThatThrownBy(() -> service.execute(command()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("still running")
                .hasMessageContaining("SIMULATING");

        verify(strategyRepository, never()).deleteById(any());
    }

    @Test
    @DisplayName("a running LIVE simulation still blocks the delete")
    void shouldRefuseWhileLiveIsRunning() {
        Simulation running = simulation(SimulationStatus.LIVE, false);
        when(simulationRepository.findByStrategyId(strategyId)).thenReturn(List.of(running));

        assertThatThrownBy(() -> service.execute(command()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("still running");

        verify(strategyRepository, never()).deleteById(any());
    }

    @Test
    @DisplayName("a paused backtest does not block — nothing is executing it")
    void shouldAllowDeleteWhenSimulatingButPaused() {
        Simulation paused = simulation(SimulationStatus.SIMULATING, true);
        when(simulationRepository.findByStrategyId(strategyId)).thenReturn(List.of(paused));

        assertThatCode(() -> service.execute(command())).doesNotThrowAnyException();

        verify(strategyRepository).deleteById(strategyId);
    }

    @Test
    @DisplayName("finished simulations do not block")
    void shouldAllowDeleteWhenAllSimulationsAreTerminal() {
        Simulation completed = simulation(SimulationStatus.COMPLETED, false);
        Simulation failed = simulation(SimulationStatus.FAILED, false);
        when(simulationRepository.findByStrategyId(strategyId)).thenReturn(List.of(completed, failed));

        assertThatCode(() -> service.execute(command())).doesNotThrowAnyException();

        verify(strategyRepository).deleteById(strategyId);
    }

    @Test
    @DisplayName("the refusal counts every running simulation, not just the first")
    void shouldReportHowManySimulationsAreRunning() {
        Simulation backtest = simulation(SimulationStatus.SIMULATING, false);
        Simulation live = simulation(SimulationStatus.LIVE, false);
        Simulation done = simulation(SimulationStatus.COMPLETED, false);
        when(simulationRepository.findByStrategyId(strategyId))
                .thenReturn(List.of(backtest, live, done));

        assertThatThrownBy(() -> service.execute(command()))
                .hasMessageContaining("2 simulation(s) are still running");
    }

    @Test
    @DisplayName("a strategy with no simulations deletes cleanly")
    void shouldDeleteWhenNoSimulationsExist() {
        when(simulationRepository.findByStrategyId(strategyId)).thenReturn(List.of());

        assertThatCode(() -> service.execute(command())).doesNotThrowAnyException();

        verify(strategyRepository).deleteById(strategyId);
    }
}
