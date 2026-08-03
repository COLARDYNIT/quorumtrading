package com.quorumtrading.application.service;

import com.quorumtrading.application.port.out.BrokerPort;
import com.quorumtrading.application.port.out.LLMPort;
import com.quorumtrading.application.port.out.SimulationRepository;
import com.quorumtrading.application.port.out.StrategyRepository;
import com.quorumtrading.application.usecase.command.UpdateStrategyCommand;
import com.quorumtrading.model.entity.Simulation;
import com.quorumtrading.model.entity.Strategy;
import com.quorumtrading.model.valueobject.AssetInfo;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UpdateStrategyServiceTest {

    @Mock
    private StrategyRepository strategyRepository;

    @Mock
    private SimulationRepository simulationRepository;

    @Mock
    private LLMPort llmPort;

    @Mock
    private BrokerPort brokerPort;

    private UpdateStrategyService service;

    @BeforeEach
    void setUp() {
        service = new UpdateStrategyService(strategyRepository, simulationRepository, llmPort, brokerPort, new AuthorizationService());
    }

    private static final String STRATEGY_WITH_TICKERS = """
        ---
        version: v1.0
        consensus_method: MAJORITY
        consensus_threshold: 0.66
        stock_tickers:
          - AAPL
          - MSFT
        live_schedule:
          timezone: "America/New_York"
          trading_days: [MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY]
          evaluation_times: ["10:00", "14:00"]
          market_open: "09:30"
          market_close: "16:00"
        position_limits:
          AAPL: 0.3
          MSFT: 0.3
        ---
        # Test Strategy
        Buy when RSI < 30.
        """;

    private static final String STRATEGY_WITH_DIFFERENT_TICKERS = """
        ---
        version: v1.0
        consensus_method: MAJORITY
        consensus_threshold: 0.66
        stock_tickers:
          - GOOGL
          - MSFT
        live_schedule:
          timezone: "America/New_York"
          trading_days: [MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY]
          evaluation_times: ["10:00", "14:00"]
          market_open: "09:30"
          market_close: "16:00"
        position_limits:
          GOOGL: 0.3
          MSFT: 0.3
        ---
        # Test Strategy
        Buy when RSI < 30.
        """;

    private static final String STRATEGY_WITH_ADDED_TICKER = """
        ---
        version: v1.0
        consensus_method: MAJORITY
        consensus_threshold: 0.66
        stock_tickers:
          - AAPL
          - MSFT
          - NVDA
        live_schedule:
          timezone: "America/New_York"
          trading_days: [MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY]
          evaluation_times: ["10:00", "14:00"]
          market_open: "09:30"
          market_close: "16:00"
        position_limits:
          AAPL: 0.3
          MSFT: 0.3
          NVDA: 0.2
        ---
        # Test Strategy
        Buy when RSI < 30.
        """;

    private static final String STRATEGY_WITH_SAME_TICKERS_DIFFERENT_LIMITS = """
        ---
        version: v1.0
        consensus_method: MAJORITY
        consensus_threshold: 0.66
        stock_tickers:
          - AAPL
          - MSFT
        live_schedule:
          timezone: "America/New_York"
          trading_days: [MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY]
          evaluation_times: ["10:00", "14:00"]
          market_open: "09:30"
          market_close: "16:00"
        position_limits:
          AAPL: 0.0
          MSFT: 0.5
        ---
        # Test Strategy - Updated
        Buy when RSI < 25.
        """;

    @Test
    void shouldUpdateStrategyWithoutSimulations() {
        // Given
        StrategyId strategyId = new StrategyId(UUID.randomUUID());
        UserId ownerId = new UserId(UUID.randomUUID());
        Strategy strategy = new Strategy(strategyId, "Test", STRATEGY_WITH_TICKERS, ownerId);

        when(strategyRepository.findById(strategyId)).thenReturn(Optional.of(strategy));
        when(simulationRepository.findByStrategyId(strategyId)).thenReturn(List.of());
        when(brokerPort.getAssetInfo("GOOGL"))
            .thenReturn(Optional.of(AssetInfo.tradable("GOOGL", "Alphabet Inc.", "NASDAQ", "us_equity")));
        when(brokerPort.getAssetInfo("MSFT"))
            .thenReturn(Optional.of(AssetInfo.tradable("MSFT", "Microsoft Corp.", "NASDAQ", "us_equity")));
        when(strategyRepository.save(any(Strategy.class))).thenAnswer(i -> i.getArgument(0));

        UpdateStrategyCommand command = new UpdateStrategyCommand(strategyId, STRATEGY_WITH_DIFFERENT_TICKERS, ownerId);

        // When
        service.execute(command);

        // Then - should allow ticker changes when no simulations exist
        verify(strategyRepository).save(any(Strategy.class));
    }

    @Test
    void shouldRejectTickerRemovalWhenSimulationsExist() {
        // Given
        StrategyId strategyId = new StrategyId(UUID.randomUUID());
        UserId ownerId = new UserId(UUID.randomUUID());
        Strategy strategy = new Strategy(strategyId, "Test", STRATEGY_WITH_TICKERS, ownerId);
        Simulation simulation = createSimulation(strategyId);

        when(strategyRepository.findById(strategyId)).thenReturn(Optional.of(strategy));
        when(simulationRepository.findByStrategyId(strategyId)).thenReturn(List.of(simulation));

        // Trying to replace AAPL with GOOGL
        UpdateStrategyCommand command = new UpdateStrategyCommand(strategyId, STRATEGY_WITH_DIFFERENT_TICKERS, ownerId);

        // When/Then
        assertThatThrownBy(() -> service.execute(command))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Cannot modify tickers")
            .hasMessageContaining("1 simulation(s)")
            .hasMessageContaining("Removed stocks: [AAPL]")
            .hasMessageContaining("Added stocks: [GOOGL]");

        verify(strategyRepository, never()).save(any());
    }

    @Test
    void shouldRejectTickerAdditionWhenSimulationsExist() {
        // Given
        StrategyId strategyId = new StrategyId(UUID.randomUUID());
        UserId ownerId = new UserId(UUID.randomUUID());
        Strategy strategy = new Strategy(strategyId, "Test", STRATEGY_WITH_TICKERS, ownerId);
        Simulation simulation = createSimulation(strategyId);

        when(strategyRepository.findById(strategyId)).thenReturn(Optional.of(strategy));
        when(simulationRepository.findByStrategyId(strategyId)).thenReturn(List.of(simulation));

        // Trying to add NVDA
        UpdateStrategyCommand command = new UpdateStrategyCommand(strategyId, STRATEGY_WITH_ADDED_TICKER, ownerId);

        // When/Then
        assertThatThrownBy(() -> service.execute(command))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Cannot modify tickers")
            .hasMessageContaining("Added stocks: [NVDA]");

        verify(strategyRepository, never()).save(any());
    }

    @Test
    void shouldAllowPositionLimitChangesWhenSimulationsExist() {
        // Given
        StrategyId strategyId = new StrategyId(UUID.randomUUID());
        UserId ownerId = new UserId(UUID.randomUUID());
        Strategy strategy = new Strategy(strategyId, "Test", STRATEGY_WITH_TICKERS, ownerId);
        Simulation simulation = createSimulation(strategyId);

        when(strategyRepository.findById(strategyId)).thenReturn(Optional.of(strategy));
        when(simulationRepository.findByStrategyId(strategyId)).thenReturn(List.of(simulation));
        when(strategyRepository.save(any(Strategy.class))).thenAnswer(i -> i.getArgument(0));

        // Same tickers, but AAPL position_limit set to 0.0 and MSFT changed to 0.5
        UpdateStrategyCommand command = new UpdateStrategyCommand(strategyId, STRATEGY_WITH_SAME_TICKERS_DIFFERENT_LIMITS, ownerId);

        // When
        service.execute(command);

        // Then - should allow position limit changes
        verify(strategyRepository).save(any(Strategy.class));
    }

    /** Same tickers and limits as STRATEGY_WITH_TICKERS — only the session is moved to Tokyo. */
    private static final String STRATEGY_WITH_TOKYO_SESSION = """
        ---
        version: v1.0
        consensus_method: MAJORITY
        consensus_threshold: 0.66
        stock_tickers:
          - AAPL
          - MSFT
        live_schedule:
          timezone: "Asia/Tokyo"
          trading_days: [MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY]
          evaluation_times: ["10:00"]
          market_open: "09:00"
          market_close: "15:30"
        position_limits:
          AAPL: 0.3
          MSFT: 0.3
        ---
        # Test Strategy
        Buy when RSI < 30.
        """;

    @Test
    void shouldRejectAScheduleThatCannotExistOnTheVenueEvenWhenSimulationsFreezeTheTickers() {
        // Tickers are frozen once simulations exist, so that branch skips broker validation —
        // but the schedule is still editable, and moving a US-listed strategy onto a Tokyo
        // session is exactly the silent acceptance this check exists to stop.
        StrategyId strategyId = new StrategyId(UUID.randomUUID());
        UserId ownerId = new UserId(UUID.randomUUID());
        Strategy strategy = new Strategy(strategyId, "Test", STRATEGY_WITH_TICKERS, ownerId);

        when(strategyRepository.findById(strategyId)).thenReturn(Optional.of(strategy));
        when(simulationRepository.findByStrategyId(strategyId)).thenReturn(List.of(createSimulation(strategyId)));

        UpdateStrategyCommand command =
            new UpdateStrategyCommand(strategyId, STRATEGY_WITH_TOKYO_SESSION, ownerId);

        assertThatThrownBy(() -> service.execute(command))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Asia/Tokyo");

        verify(strategyRepository, never()).save(any(Strategy.class));
    }

    @Test
    void shouldRejectWhenStrategyNotFound() {
        // Given
        StrategyId strategyId = new StrategyId(UUID.randomUUID());
        when(strategyRepository.findById(strategyId)).thenReturn(Optional.empty());

        UpdateStrategyCommand command = new UpdateStrategyCommand(
            strategyId, STRATEGY_WITH_TICKERS, new UserId(UUID.randomUUID()));

        // When/Then
        assertThatThrownBy(() -> service.execute(command))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Strategy not found");
    }

    @Test
    void shouldShowCorrectCountWhenMultipleSimulationsExist() {
        // Given
        StrategyId strategyId = new StrategyId(UUID.randomUUID());
        UserId ownerId = new UserId(UUID.randomUUID());
        Strategy strategy = new Strategy(strategyId, "Test", STRATEGY_WITH_TICKERS, ownerId);
        Simulation sim1 = createSimulation(strategyId);
        Simulation sim2 = createSimulation(strategyId);
        Simulation sim3 = createSimulation(strategyId);

        when(strategyRepository.findById(strategyId)).thenReturn(Optional.of(strategy));
        when(simulationRepository.findByStrategyId(strategyId)).thenReturn(List.of(sim1, sim2, sim3));

        UpdateStrategyCommand command = new UpdateStrategyCommand(strategyId, STRATEGY_WITH_DIFFERENT_TICKERS, ownerId);

        // When/Then
        assertThatThrownBy(() -> service.execute(command))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("3 simulation(s)");
    }

    @Test
    void shouldRejectUpdateWithUntradableTickerWhenNoSimulations() {
        // Given
        StrategyId strategyId = new StrategyId(UUID.randomUUID());
        UserId ownerId = new UserId(UUID.randomUUID());
        Strategy strategy = new Strategy(strategyId, "Test", STRATEGY_WITH_TICKERS, ownerId);

        when(strategyRepository.findById(strategyId)).thenReturn(Optional.of(strategy));
        when(simulationRepository.findByStrategyId(strategyId)).thenReturn(List.of());
        when(brokerPort.getAssetInfo("GOOGL"))
            .thenReturn(Optional.of(AssetInfo.notFound("GOOGL")));
        when(brokerPort.getAssetInfo("MSFT"))
            .thenReturn(Optional.of(AssetInfo.tradable("MSFT", "Microsoft", "NASDAQ", "us_equity")));

        UpdateStrategyCommand command = new UpdateStrategyCommand(strategyId, STRATEGY_WITH_DIFFERENT_TICKERS, ownerId);

        // When/Then
        assertThatThrownBy(() -> service.execute(command))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("non-tradable tickers")
            .hasMessageContaining("GOOGL");

        verify(strategyRepository, never()).save(any());
    }

    @Test
    void shouldAllowUpdateWhenBrokerUnavailableAndNoSimulations() {
        // Given — broker returns empty for all tickers (unavailable)
        StrategyId strategyId = new StrategyId(UUID.randomUUID());
        UserId ownerId = new UserId(UUID.randomUUID());
        Strategy strategy = new Strategy(strategyId, "Test", STRATEGY_WITH_TICKERS, ownerId);

        when(strategyRepository.findById(strategyId)).thenReturn(Optional.of(strategy));
        when(simulationRepository.findByStrategyId(strategyId)).thenReturn(List.of());
        // Empty getAssetInfo for every ticker = broker API unreachable → soft-pass (no fallback).
        when(brokerPort.getAssetInfo(anyString())).thenReturn(Optional.empty());
        when(strategyRepository.save(any(Strategy.class))).thenAnswer(i -> i.getArgument(0));

        UpdateStrategyCommand command = new UpdateStrategyCommand(strategyId, STRATEGY_WITH_DIFFERENT_TICKERS, ownerId);

        // When
        service.execute(command);

        // Then — should succeed (soft validation)
        verify(strategyRepository).save(any(Strategy.class));
    }

    @Test
    void shouldUpdateNameOnly() {
        // Given
        StrategyId strategyId = new StrategyId(UUID.randomUUID());
        UserId ownerId = new UserId(UUID.randomUUID());
        Strategy strategy = new Strategy(strategyId, "Old Name", STRATEGY_WITH_TICKERS, ownerId);

        when(strategyRepository.findById(strategyId)).thenReturn(Optional.of(strategy));
        when(strategyRepository.save(any(Strategy.class))).thenAnswer(i -> i.getArgument(0));

        UpdateStrategyCommand command = new UpdateStrategyCommand(strategyId, "New Name", null, ownerId);

        // When
        service.execute(command);

        // Then - should update name without ticker/LLM validation
        verify(strategyRepository).save(any(Strategy.class));
        verify(simulationRepository, never()).findByStrategyId(any());
        assertThat(strategy.getName()).isEqualTo("New Name");
    }

    @Test
    void shouldUpdateNameAndContent() {
        // Given
        StrategyId strategyId = new StrategyId(UUID.randomUUID());
        UserId ownerId = new UserId(UUID.randomUUID());
        Strategy strategy = new Strategy(strategyId, "Old Name", STRATEGY_WITH_TICKERS, ownerId);

        when(strategyRepository.findById(strategyId)).thenReturn(Optional.of(strategy));
        when(simulationRepository.findByStrategyId(strategyId)).thenReturn(List.of());
        when(brokerPort.getAssetInfo("AAPL"))
            .thenReturn(Optional.of(AssetInfo.tradable("AAPL", "Apple Inc.", "NASDAQ", "us_equity")));
        when(brokerPort.getAssetInfo("MSFT"))
            .thenReturn(Optional.of(AssetInfo.tradable("MSFT", "Microsoft Corp.", "NASDAQ", "us_equity")));
        when(strategyRepository.save(any(Strategy.class))).thenAnswer(i -> i.getArgument(0));

        UpdateStrategyCommand command = new UpdateStrategyCommand(
            strategyId, "New Name", STRATEGY_WITH_SAME_TICKERS_DIFFERENT_LIMITS, ownerId);

        // When
        service.execute(command);

        // Then
        verify(strategyRepository).save(any(Strategy.class));
        assertThat(strategy.getName()).isEqualTo("New Name");
    }

    @Test
    void shouldRejectWhenNeitherNameNorContentProvided() {
        // Given
        StrategyId strategyId = new StrategyId(UUID.randomUUID());
        UpdateStrategyCommand command = new UpdateStrategyCommand(strategyId, null, null, null, null);

        // When/Then
        assertThatThrownBy(() -> service.execute(command))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("At least one of 'name', 'description', or 'content' must be provided");
    }

    private Simulation createSimulation(StrategyId strategyId) {
        return new Simulation(
            SimulationId.generate(),
            "Test Simulation",
            strategyId,
            Instant.now(),
            Instant.now().plusSeconds(3600),
            Money.usd(new BigDecimal("100000"))
        );
    }
}
