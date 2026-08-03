package com.quorumtrading.application.service;

import com.quorumtrading.application.port.out.BrokerAccountRepository;
import com.quorumtrading.application.port.out.SimulationRepository;
import com.quorumtrading.application.port.out.StrategyRepository;
import com.quorumtrading.application.usecase.command.DeleteSimulationCommand;
import com.quorumtrading.model.entity.BrokerAccount;
import com.quorumtrading.model.entity.Simulation;
import com.quorumtrading.model.entity.Strategy;
import com.quorumtrading.model.enums.SimulationStatus;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeleteSimulationServiceTest {

    @Mock private SimulationRepository simulationRepository;
    @Mock private StrategyRepository strategyRepository;
    @Mock private BrokerAccountRepository brokerAccountRepository;

    private DeleteSimulationService service;

    private static final UserId OWNER = new UserId(UUID.randomUUID());
    private static final StrategyId STRATEGY_ID = StrategyId.generate();
    private static final BrokerAccountId BROKER_ACCOUNT_ID = BrokerAccountId.generate();
    private static final Money TRADING_LIMIT = Money.usd(new BigDecimal("9600.00"));

    @BeforeEach
    void setUp() {
        AuthorizationService authorizationService = new AuthorizationService();
        service = new DeleteSimulationService(simulationRepository, strategyRepository, authorizationService, brokerAccountRepository);
    }

    @Test
    void shouldReleaseCapitalWhenDeletingTerminalSimulation() {
        // Given: A COMPLETED simulation with a broker account reservation
        Simulation simulation = createSimulationWithBroker(SimulationStatus.COMPLETED);
        BrokerAccount account = createBrokerAccountWithReservation(TRADING_LIMIT);

        when(simulationRepository.findById(simulation.getSimulationId())).thenReturn(Optional.of(simulation));
        Strategy strategy = createStrategy();
        when(strategyRepository.findById(STRATEGY_ID)).thenReturn(Optional.of(strategy));
        when(brokerAccountRepository.findById(BROKER_ACCOUNT_ID)).thenReturn(Optional.of(account));
        when(simulationRepository.deleteById(simulation.getSimulationId())).thenReturn(true);

        // When: Deleting the completed simulation
        service.execute(new DeleteSimulationCommand(simulation.getSimulationId(), OWNER));

        // Then: Capital should still be released even though simulation is terminal
        ArgumentCaptor<BrokerAccount> captor = ArgumentCaptor.forClass(BrokerAccount.class);
        verify(brokerAccountRepository).save(captor.capture());
        assertThat(captor.getValue().getReservedBalance().amount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void shouldReleaseCapitalWhenDeletingLiveSimulation() {
        // Given: A LIVE simulation with a broker account reservation
        Simulation simulation = createSimulationWithBroker(SimulationStatus.LIVE);
        BrokerAccount account = createBrokerAccountWithReservation(TRADING_LIMIT);

        when(simulationRepository.findById(simulation.getSimulationId())).thenReturn(Optional.of(simulation));
        Strategy strategy = createStrategy();
        when(strategyRepository.findById(STRATEGY_ID)).thenReturn(Optional.of(strategy));
        when(brokerAccountRepository.findById(BROKER_ACCOUNT_ID)).thenReturn(Optional.of(account));
        when(simulationRepository.deleteById(simulation.getSimulationId())).thenReturn(true);

        // When: Deleting the live simulation
        service.execute(new DeleteSimulationCommand(simulation.getSimulationId(), OWNER));

        // Then: Capital should be released
        ArgumentCaptor<BrokerAccount> captor = ArgumentCaptor.forClass(BrokerAccount.class);
        verify(brokerAccountRepository).save(captor.capture());
        assertThat(captor.getValue().getReservedBalance().amount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void shouldHandleAlreadyReleasedCapitalGracefully() {
        // Given: A COMPLETED simulation whose capital was already released (reserved = 0)
        Simulation simulation = createSimulationWithBroker(SimulationStatus.COMPLETED);
        BrokerAccount account = createBrokerAccountWithReservation(Money.usd(BigDecimal.ZERO));

        when(simulationRepository.findById(simulation.getSimulationId())).thenReturn(Optional.of(simulation));
        Strategy strategy = createStrategy();
        when(strategyRepository.findById(STRATEGY_ID)).thenReturn(Optional.of(strategy));
        when(brokerAccountRepository.findById(BROKER_ACCOUNT_ID)).thenReturn(Optional.of(account));
        when(simulationRepository.deleteById(simulation.getSimulationId())).thenReturn(true);

        // When: Deleting the simulation (capital already released)
        service.execute(new DeleteSimulationCommand(simulation.getSimulationId(), OWNER));

        // Then: Should not attempt to save broker account (nothing to release)
        verify(brokerAccountRepository, never()).save(any());
    }

    @Test
    void shouldCapReleaseAtReservedBalanceWhenTradingLimitExceedsReserved() {
        // Given: A simulation with trading limit $9600 but only $5000 reserved
        // (partial release from previous end-sim that partially succeeded)
        Simulation simulation = createSimulationWithBroker(SimulationStatus.COMPLETED);
        Money partialReserved = Money.usd(new BigDecimal("5000.00"));
        BrokerAccount account = createBrokerAccountWithReservation(partialReserved);

        when(simulationRepository.findById(simulation.getSimulationId())).thenReturn(Optional.of(simulation));
        Strategy strategy = createStrategy();
        when(strategyRepository.findById(STRATEGY_ID)).thenReturn(Optional.of(strategy));
        when(brokerAccountRepository.findById(BROKER_ACCOUNT_ID)).thenReturn(Optional.of(account));
        when(simulationRepository.deleteById(simulation.getSimulationId())).thenReturn(true);

        // When: Deleting the simulation
        service.execute(new DeleteSimulationCommand(simulation.getSimulationId(), OWNER));

        // Then: Should release only the reserved amount, not the full trading limit
        ArgumentCaptor<BrokerAccount> captor = ArgumentCaptor.forClass(BrokerAccount.class);
        verify(brokerAccountRepository).save(captor.capture());
        assertThat(captor.getValue().getReservedBalance().amount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void shouldDeleteSimulationWithoutBrokerAccount() {
        // Given: A simulation without a broker account (legacy/backtest)
        Simulation simulation = createSimulationWithoutBroker(SimulationStatus.COMPLETED);

        when(simulationRepository.findById(simulation.getSimulationId())).thenReturn(Optional.of(simulation));
        Strategy strategy = createStrategy();
        when(strategyRepository.findById(STRATEGY_ID)).thenReturn(Optional.of(strategy));
        when(simulationRepository.deleteById(simulation.getSimulationId())).thenReturn(true);

        // When: Deleting the simulation
        service.execute(new DeleteSimulationCommand(simulation.getSimulationId(), OWNER));

        // Then: Should not touch broker account at all
        verify(brokerAccountRepository, never()).findById(any());
        verify(brokerAccountRepository, never()).save(any());
    }

    // --- Test helpers ---

    private Simulation createSimulationWithBroker(SimulationStatus status) {
        Instant startDate = Instant.now().minus(30, ChronoUnit.DAYS);
        Instant endDate = Instant.now().minus(1, ChronoUnit.HOURS);

        Simulation simulation = Simulation.reconstitute(
            SimulationId.generate(),
            "Test Simulation",
            STRATEGY_ID,
            startDate,
            endDate,
            TRADING_LIMIT,
            null,
            BROKER_ACCOUNT_ID,
            TRADING_LIMIT
        );

        if (status == SimulationStatus.COMPLETED) {
            simulation.setStatus(SimulationStatus.COMPLETED);
            simulation.setCompletedAt(Instant.now());
        } else if (status == SimulationStatus.FAILED) {
            simulation.fail("test failure");
        } else {
            simulation.setStatus(status);
        }

        return simulation;
    }

    private Simulation createSimulationWithoutBroker(SimulationStatus status) {
        Instant startDate = Instant.now().minus(30, ChronoUnit.DAYS);
        Instant endDate = Instant.now().minus(1, ChronoUnit.HOURS);

        Simulation simulation = Simulation.reconstitute(
            SimulationId.generate(),
            "Test Simulation",
            STRATEGY_ID,
            startDate,
            endDate,
            Money.usd(new BigDecimal("10000.00")),
            null,
            null,
            null
        );

        simulation.setStatus(status);
        if (status == SimulationStatus.COMPLETED) {
            simulation.setCompletedAt(Instant.now());
        }

        return simulation;
    }

    private BrokerAccount createBrokerAccountWithReservation(Money reserved) {
        BrokerAccount account = new BrokerAccount(
            BROKER_ACCOUNT_ID,
            OWNER,
            "Test Paper Account",
            com.quorumtrading.model.enums.BrokerType.ALPACA_PAPER,
            null,
            Money.usd(new BigDecimal("26821.30")),
            null,
            reserved,
            com.quorumtrading.model.enums.AccountStatus.ACTIVE,
            null,
            Instant.now(),
            Instant.now()
        );
        return account;
    }

    private Strategy createStrategy() {
        Strategy strategy = mock(Strategy.class);
        when(strategy.getOwnerId()).thenReturn(OWNER);
        return strategy;
    }
}
