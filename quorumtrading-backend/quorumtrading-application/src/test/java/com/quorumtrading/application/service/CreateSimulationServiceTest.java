package com.quorumtrading.application.service;

import com.quorumtrading.application.event.SimulationCreatedEvent;
import com.quorumtrading.application.port.out.BrokerAccountRepository;
import com.quorumtrading.application.port.out.EventPublisher;
import com.quorumtrading.application.port.out.PortfolioRepository;
import com.quorumtrading.application.port.out.SimulationRepository;
import com.quorumtrading.application.port.out.StrategyRepository;
import com.quorumtrading.application.usecase.command.CreateSimulationCommand;
import com.quorumtrading.model.entity.BrokerAccount;
import com.quorumtrading.model.entity.PortfolioSnapshot;
import com.quorumtrading.model.entity.Simulation;
import com.quorumtrading.model.entity.Strategy;
import com.quorumtrading.model.enums.AccountStatus;
import com.quorumtrading.model.enums.BrokerType;
import com.quorumtrading.model.valueobject.BrokerAccountId;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.SimulationId;
import com.quorumtrading.model.valueobject.StrategyId;
import com.quorumtrading.model.valueobject.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreateSimulationServiceTest {

    @Mock private SimulationRepository simulationRepository;
    @Mock private StrategyRepository strategyRepository;
    @Mock private PortfolioRepository portfolioRepository;
    @Mock private BrokerAccountRepository brokerAccountRepository;
    @Mock private EventPublisher<SimulationCreatedEvent> eventPublisher;

    private CreateSimulationService service;

    private static final UserId OWNER = new UserId(UUID.randomUUID());
    private static final StrategyId STRATEGY_ID = StrategyId.generate();
    private static final BrokerAccountId BROKER_ACCOUNT_ID = BrokerAccountId.generate();

    @BeforeEach
    void setUp() {
        AuthorizationService authorizationService = new AuthorizationService();
        service = new CreateSimulationService(
            simulationRepository, strategyRepository, portfolioRepository,
            brokerAccountRepository, eventPublisher, authorizationService
        );
    }

    @Test
    void shouldUseRequestedInitialCashForLiveSimulation() {
        // Given: User requests $10,000 for a live simulation with a broker account
        Money requestedCash = Money.usd(new BigDecimal("10000.00"));
        Money brokerBalance = Money.usd(new BigDecimal("26821.30"));

        Strategy strategy = mock(Strategy.class);
        when(strategy.getOwnerId()).thenReturn(OWNER);
        when(strategyRepository.findById(STRATEGY_ID)).thenReturn(Optional.of(strategy));

        BrokerAccount account = new BrokerAccount(
            BROKER_ACCOUNT_ID, OWNER, "Paper Account", BrokerType.ALPACA_PAPER,
            null, brokerBalance, null, Money.usd(BigDecimal.ZERO),
            AccountStatus.ACTIVE, null, Instant.now(), Instant.now()
        );
        when(brokerAccountRepository.findById(BROKER_ACCOUNT_ID)).thenReturn(Optional.of(account));
        when(simulationRepository.save(any(Simulation.class))).thenAnswer(inv -> inv.getArgument(0));

        Instant now = Instant.now();
        CreateSimulationCommand command = new CreateSimulationCommand(
            "Live Test",
            STRATEGY_ID,
            now,
            now.plus(30, ChronoUnit.DAYS), // Future end date = LIVE simulation
            requestedCash,
            BROKER_ACCOUNT_ID,
            requestedCash,  // tradingLimit
            null,  // mockBrokerConfig
            OWNER
        );

        // When: Creating the simulation
        service.execute(command);

        // Then: Simulation should use the user's requested $10,000, NOT the broker balance
        ArgumentCaptor<Simulation> simCaptor = ArgumentCaptor.forClass(Simulation.class);
        verify(simulationRepository).save(simCaptor.capture());
        assertThat(simCaptor.getValue().getInitialCash().amount())
            .isEqualByComparingTo(new BigDecimal("10000.00"));

        // And: Portfolio snapshot should also use $10,000
        ArgumentCaptor<PortfolioSnapshot> snapCaptor = ArgumentCaptor.forClass(PortfolioSnapshot.class);
        verify(portfolioRepository).save(snapCaptor.capture());
        assertThat(snapCaptor.getValue().getCashAvailable().amount())
            .isEqualByComparingTo(new BigDecimal("10000.00"));
    }

    @Test
    void shouldCreateLiveSimulationWithNullEndDate() {
        // Reproduces the DCA failure: ExecuteInvestmentPlanService passes endDate=null
        // to signal "LIVE forever" per the Simulation entity contract. Before the fix,
        // CreateSimulationService validated `command.endDate().isAfter(...)` unconditionally
        // and NPE'd on null — which bubbled up as a generic FAILED_BROKER_ERROR with no
        // failureReason on the allocation row.
        Money requestedCash = Money.usd(new BigDecimal("200.00"));

        com.quorumtrading.model.valueobject.UserId owner =
            new com.quorumtrading.model.valueobject.UserId(java.util.UUID.randomUUID());
        Strategy strategy = mock(Strategy.class);
        when(strategy.getOwnerId()).thenReturn(owner);
        when(strategyRepository.findById(STRATEGY_ID)).thenReturn(Optional.of(strategy));
        when(simulationRepository.save(any(Simulation.class))).thenAnswer(inv -> inv.getArgument(0));

        Instant now = Instant.now();
        // The owner must be passed explicitly: a null user is denied rather than skipping the
        // check. The bug this test guards is purely the endDate validation NPE.
        CreateSimulationCommand command = new CreateSimulationCommand(
            "Plan-spawned live sim",
            STRATEGY_ID,
            now,
            null, // null endDate → LIVE forever (DCA path)
            requestedCash,
            null,
            null,
            null,
            owner
        );

        service.execute(command);

        ArgumentCaptor<Simulation> captor = ArgumentCaptor.forClass(Simulation.class);
        verify(simulationRepository).save(captor.capture());
        assertThat(captor.getValue().getEndDate()).isNull();
        assertThat(captor.getValue().getInitialCash().amount())
            .isEqualByComparingTo(new BigDecimal("200.00"));
    }

    @Test
    void shouldUseRequestedInitialCashForBacktestSimulation() {
        // Given: User requests $10,000 for a backtest simulation
        Money requestedCash = Money.usd(new BigDecimal("10000.00"));

        Strategy strategy = mock(Strategy.class);
        when(strategy.getOwnerId()).thenReturn(OWNER);
        when(strategyRepository.findById(STRATEGY_ID)).thenReturn(Optional.of(strategy));
        when(simulationRepository.save(any(Simulation.class))).thenAnswer(inv -> inv.getArgument(0));

        Instant now = Instant.now();
        CreateSimulationCommand command = new CreateSimulationCommand(
            "Backtest",
            STRATEGY_ID,
            now.minus(60, ChronoUnit.DAYS),
            now.minus(1, ChronoUnit.DAYS), // Past end date = BACKTEST
            requestedCash,
            null, null, null, OWNER
        );

        // When: Creating the simulation
        service.execute(command);

        // Then: Simulation should use the requested cash
        ArgumentCaptor<Simulation> captor = ArgumentCaptor.forClass(Simulation.class);
        verify(simulationRepository).save(captor.capture());
        assertThat(captor.getValue().getInitialCash().amount())
            .isEqualByComparingTo(new BigDecimal("10000.00"));

    }

    // --- Schedule/venue validation on the way into a run ---

    /** A US-listed ticker on a Tokyo session — accepted before the venue validator existed. */
    private static final String GRANDFATHERED_TOKYO_STRATEGY = """
        ---
        version: v1.0
        consensus_method: MAJORITY
        consensus_threshold: 0.66
        stock_tickers:
          - TM
        live_schedule:
          timezone: "Asia/Tokyo"
          trading_days: [MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY]
          evaluation_times: ["10:00"]
          market_open: "09:00"
          market_close: "15:30"
        ---
        # Grandfathered
        Buy the dip.
        """;

    private static final String VALID_US_STRATEGY = """
        ---
        version: v1.0
        consensus_method: MAJORITY
        consensus_threshold: 0.66
        stock_tickers:
          - AAPL
        live_schedule:
          timezone: "America/New_York"
          trading_days: [MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY]
          evaluation_times: ["10:00"]
          market_open: "09:30"
          market_close: "16:00"
        ---
        # Valid
        Buy the dip.
        """;

    private CreateSimulationCommand backtestCommandFor(StrategyId id) {
        Instant start = Instant.parse("2025-03-03T00:00:00Z");
        return new CreateSimulationCommand(
            "Backtest", id, start, start.plus(10, ChronoUnit.DAYS),
            Money.usd(new BigDecimal("10000.00")), null, null, null, OWNER);
    }

    @Test
    void shouldRejectARunWhoseStoredStrategyCannotFireOnItsVenue() {
        // Round 2 found the hole: the venue check ran only at strategy create/update, so a
        // strategy stored BEFORE that check could still start a fresh simulation in silence —
        // and did, firing every decision at 21:00 ET with NYSE shut, one of them on a Sunday.
        Strategy grandfathered = new Strategy(
            STRATEGY_ID, "Toyota Tokyo Clock", GRANDFATHERED_TOKYO_STRATEGY, OWNER);
        when(strategyRepository.findById(STRATEGY_ID)).thenReturn(Optional.of(grandfathered));

        assertThatThrownBy(() -> service.execute(backtestCommandFor(STRATEGY_ID)))
            .isInstanceOf(IllegalArgumentException.class)
            // Names the strategy: the user did not author this schedule just now, and may
            // have several stored — "fix your schedule" is useless without saying which.
            .hasMessageContaining("Toyota Tokyo Clock")
            .hasMessageContaining("Asia/Tokyo")
            .hasMessageContaining("21:00");

        verify(simulationRepository, never()).save(any(Simulation.class));
    }

    @Test
    void shouldStillCreateARunForAStrategyWhoseScheduleIsFine() {
        Strategy valid = new Strategy(STRATEGY_ID, "Apple US", VALID_US_STRATEGY, OWNER);
        when(strategyRepository.findById(STRATEGY_ID)).thenReturn(Optional.of(valid));
        when(simulationRepository.save(any(Simulation.class))).thenAnswer(inv -> inv.getArgument(0));

        service.execute(backtestCommandFor(STRATEGY_ID));

        verify(simulationRepository).save(any(Simulation.class));
    }

    @Test
    void shouldNotTurnAnUnparseableStrategyIntoAScheduleComplaint() {
        // A strategy that no longer parses has a different problem, and the existing
        // parse-failure path reports it accurately. Describing it as a schedule fault would
        // send the user to fix something that is not wrong.
        Strategy unparseable = new Strategy(
            STRATEGY_ID, "Broken", "this is not a strategy document", OWNER);
        when(strategyRepository.findById(STRATEGY_ID)).thenReturn(Optional.of(unparseable));
        when(simulationRepository.save(any(Simulation.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThatCode(() -> service.execute(backtestCommandFor(STRATEGY_ID)))
            .doesNotThrowAnyException();
    }
}
