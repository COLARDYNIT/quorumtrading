package com.quorumtrading.application.service;

import com.quorumtrading.application.port.out.SimulationRepository;
import com.quorumtrading.application.usecase.command.CalculateSimulationOverlaysCommand;
import com.quorumtrading.model.entity.Simulation;
import com.quorumtrading.model.enums.InvestmentType;
import com.quorumtrading.model.valueobject.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static com.quorumtrading.application.service.TestConstants.TEST_USER_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CalculateSimulationOverlaysServiceTest {

    @Mock
    private SimulationRepository simulationRepository;

    @Mock
    private BuyAndHoldOverlayCalculator calculator;

    private CalculateSimulationOverlaysService service;

    private static final SimulationId SIM_ID = new SimulationId(java.util.UUID.randomUUID());
    private static final Instant START = Instant.parse("2024-01-01T00:00:00Z");
    private static final Instant END = Instant.parse("2024-06-30T23:59:59Z");

    @org.mockito.Mock
    private com.quorumtrading.application.port.out.StrategyRepository strategyRepository;

    private static final com.quorumtrading.model.valueobject.StrategyId STRATEGY_ID =
        com.quorumtrading.model.valueobject.StrategyId.generate();

    /** Overlays are authorized through the simulation's strategy, which is now always loaded. */
    private void stubOwnedStrategyFor(Simulation sim) {
        lenient().when(sim.getSimulationId()).thenReturn(SIM_ID);
        lenient().when(sim.getStrategyId()).thenReturn(STRATEGY_ID);
        com.quorumtrading.model.entity.Strategy strategy =
            mock(com.quorumtrading.model.entity.Strategy.class);
        lenient().when(strategy.getOwnerId()).thenReturn(TEST_USER_ID);
        lenient().when(strategyRepository.findById(STRATEGY_ID)).thenReturn(Optional.of(strategy));
    }

    @BeforeEach
    void setUp() {
        service = new CalculateSimulationOverlaysService(
            simulationRepository, strategyRepository, new AuthorizationService(), calculator);
    }

    @Test
    void shouldComputeOverlaysForCompletedSimulation() {
        Simulation sim = mock(Simulation.class);
        stubOwnedStrategyFor(sim);
        when(sim.getStartDate()).thenReturn(START);
        when(sim.getEndDate()).thenReturn(END);
        when(sim.getInitialCash()).thenReturn(Money.usd(100_000));
        when(simulationRepository.findById(SIM_ID)).thenReturn(Optional.of(sim));

        Symbol spy = new Symbol("SPY", InvestmentType.STOCK);
        OverlayResult expected = OverlayResult.ready("SPY", "STOCK",
            List.of(new BuyAndHoldDataPoint(START, new BigDecimal("100000.00"))),
            new BigDecimal("10.0000"));
        when(calculator.computeOverlay(eq(spy), eq(START), eq(END), any(BigDecimal.class), eq("1d")))
            .thenReturn(expected);

        CalculateSimulationOverlaysCommand cmd = new CalculateSimulationOverlaysCommand(
            SIM_ID, List.of(spy), TEST_USER_ID, null);

        List<OverlayResult> results = service.execute(cmd);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).symbol()).isEqualTo("SPY");
        assertThat(results.get(0).status()).isEqualTo(OverlayResult.OverlayStatus.READY);
    }

    @Test
    void shouldUseInstantNowForLiveSimulationWithNullEndDate() {
        Simulation sim = mock(Simulation.class);
        stubOwnedStrategyFor(sim);
        when(sim.getStartDate()).thenReturn(START);
        when(sim.getEndDate()).thenReturn(null);
        when(sim.getInitialCash()).thenReturn(Money.usd(50_000));
        when(simulationRepository.findById(SIM_ID)).thenReturn(Optional.of(sim));

        Symbol spy = new Symbol("SPY", InvestmentType.STOCK);
        when(calculator.computeOverlay(eq(spy), eq(START), any(Instant.class), any(BigDecimal.class), eq("1d")))
            .thenReturn(OverlayResult.ready("SPY", "STOCK", List.of(), BigDecimal.ZERO));

        CalculateSimulationOverlaysCommand cmd = new CalculateSimulationOverlaysCommand(
            SIM_ID, List.of(spy), TEST_USER_ID, null);

        List<OverlayResult> results = service.execute(cmd);

        assertThat(results).hasSize(1);
        // Verify endDate passed to calculator is close to now (not null)
        verify(calculator).computeOverlay(eq(spy), eq(START), any(Instant.class), any(BigDecimal.class), eq("1d"));
    }

    @Test
    void shouldComputeMultipleSymbols() {
        Simulation sim = mock(Simulation.class);
        stubOwnedStrategyFor(sim);
        when(sim.getStartDate()).thenReturn(START);
        when(sim.getEndDate()).thenReturn(END);
        when(sim.getInitialCash()).thenReturn(Money.usd(100_000));
        when(simulationRepository.findById(SIM_ID)).thenReturn(Optional.of(sim));

        Symbol spy = new Symbol("SPY", InvestmentType.STOCK);
        Symbol btc = new Symbol("BTC", InvestmentType.CRYPTO);

        when(calculator.computeOverlay(eq(spy), any(), any(), any(), any()))
            .thenReturn(OverlayResult.ready("SPY", "STOCK", List.of(), BigDecimal.ZERO));
        when(calculator.computeOverlay(eq(btc), any(), any(), any(), any()))
            .thenReturn(OverlayResult.ready("BTC", "CRYPTO", List.of(), BigDecimal.ZERO));

        CalculateSimulationOverlaysCommand cmd = new CalculateSimulationOverlaysCommand(
            SIM_ID, List.of(spy, btc), TEST_USER_ID, null);

        List<OverlayResult> results = service.execute(cmd);

        assertThat(results).hasSize(2);
        assertThat(results.get(0).symbol()).isEqualTo("SPY");
        assertThat(results.get(1).symbol()).isEqualTo("BTC");
    }

    @Test
    void shouldThrowWhenSimulationNotFound() {
        when(simulationRepository.findById(SIM_ID)).thenReturn(Optional.empty());

        Symbol spy = new Symbol("SPY", InvestmentType.STOCK);
        CalculateSimulationOverlaysCommand cmd = new CalculateSimulationOverlaysCommand(
            SIM_ID, List.of(spy), TEST_USER_ID, null);

        assertThatThrownBy(() -> service.execute(cmd))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Simulation not found");
    }

    @Test
    void shouldPassResolutionParameter() {
        Simulation sim = mock(Simulation.class);
        stubOwnedStrategyFor(sim);
        when(sim.getStartDate()).thenReturn(START);
        when(sim.getEndDate()).thenReturn(END);
        when(sim.getInitialCash()).thenReturn(Money.usd(100_000));
        when(simulationRepository.findById(SIM_ID)).thenReturn(Optional.of(sim));

        Symbol spy = new Symbol("SPY", InvestmentType.STOCK);
        when(calculator.computeOverlay(eq(spy), any(), any(), any(), eq("1h")))
            .thenReturn(OverlayResult.ready("SPY", "STOCK", List.of(), BigDecimal.ZERO));

        CalculateSimulationOverlaysCommand cmd = new CalculateSimulationOverlaysCommand(
            SIM_ID, List.of(spy), TEST_USER_ID, "1h");

        service.execute(cmd);

        verify(calculator).computeOverlay(eq(spy), eq(START), eq(END), any(BigDecimal.class), eq("1h"));
    }
}
