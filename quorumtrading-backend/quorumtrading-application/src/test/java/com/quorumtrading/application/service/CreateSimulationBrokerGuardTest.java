package com.quorumtrading.application.service;

import com.quorumtrading.application.event.SimulationCreatedEvent;
import com.quorumtrading.application.port.out.BrokerAccountRepository;
import com.quorumtrading.application.port.out.EventPublisher;
import com.quorumtrading.application.port.out.PortfolioRepository;
import com.quorumtrading.application.port.out.SimulationRepository;
import com.quorumtrading.application.port.out.StrategyRepository;
import com.quorumtrading.application.usecase.command.CreateSimulationCommand;
import com.quorumtrading.model.entity.BrokerAccount;
import com.quorumtrading.model.entity.Simulation;
import com.quorumtrading.model.entity.Strategy;
import com.quorumtrading.model.enums.AccountStatus;
import com.quorumtrading.model.enums.BrokerType;
import com.quorumtrading.model.valueobject.BrokerAccountId;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.StrategyId;
import com.quorumtrading.model.valueobject.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Attaching a broker account to a simulation is the single act that turns a harmless backtest into
 * a real-money position, and this path guarded neither half of it.
 *
 * <p><b>No ownership check.</b> The strategy is authorization-checked
 * ({@code authorizationService.requireEdit}); the broker account was not. Any user who learned
 * another user's account UUID could ring-fence that account's capital behind their own simulation.
 * {@code InvestmentPlanService.requireUsableBroker} has always checked it — the two paths simply
 * disagreed.</p>
 *
 * <p><b>No live-money acknowledgement.</b> {@code create_investment_plan} demands
 * {@code confirmLive=true} before a plan may name a live account. {@code create_simulation} — which
 * opens the position directly and immediately — asked for nothing but prose in a tool description
 * telling the agent to please check with the user. The rails were inverted relative to risk: the
 * slower, smaller, monthly path was gated in code and the instant one was gated by politeness.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CreateSimulationBrokerGuardTest {

    @Mock private SimulationRepository simulationRepository;
    @Mock private StrategyRepository strategyRepository;
    @Mock private PortfolioRepository portfolioRepository;
    @Mock private BrokerAccountRepository brokerAccountRepository;
    @Mock private EventPublisher<SimulationCreatedEvent> eventPublisher;

    private CreateSimulationService service;

    private static final UserId OWNER = new UserId(UUID.randomUUID());
    private static final UserId SOMEONE_ELSE = new UserId(UUID.randomUUID());
    private static final StrategyId STRATEGY_ID = StrategyId.generate();
    private static final Money CASH = Money.usd(new BigDecimal("10000.00"));

    @BeforeEach
    void setUp() {
        service = new CreateSimulationService(
            simulationRepository, strategyRepository, portfolioRepository,
            brokerAccountRepository, eventPublisher, new AuthorizationService());

        Strategy strategy = mock(Strategy.class);
        when(strategy.getOwnerId()).thenReturn(OWNER);
        when(strategyRepository.findById(STRATEGY_ID)).thenReturn(Optional.of(strategy));
        when(simulationRepository.save(any(Simulation.class))).thenAnswer(i -> i.getArgument(0));
    }

    private BrokerAccountId account(BrokerType type, UserId owner) {
        BrokerAccountId id = BrokerAccountId.generate();
        BrokerAccount acc = new BrokerAccount(
            id, owner, type.name() + " account", type, null,
            Money.usd(new BigDecimal("50000.00")), null, Money.usd(BigDecimal.ZERO),
            AccountStatus.ACTIVE, null, Instant.now(), Instant.now());
        when(brokerAccountRepository.findById(id)).thenReturn(Optional.of(acc));
        return id;
    }

    private CreateSimulationCommand commandFor(BrokerAccountId broker, boolean confirmLive) {
        Instant now = Instant.now();
        return new CreateSimulationCommand(
            "guard probe", STRATEGY_ID, now, now.plus(30, ChronoUnit.DAYS),
            CASH, broker, CASH, null, OWNER, confirmLive);
    }

    @Nested
    @DisplayName("a broker account must belong to the caller")
    class Ownership {

        @Test
        @DisplayName("another user's account is refused")
        void refusesAnotherUsersAccount() {
            BrokerAccountId theirs = account(BrokerType.ALPACA_PAPER, SOMEONE_ELSE);

            assertThatThrownBy(() -> service.execute(commandFor(theirs, false)))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("does not belong");

            verify(simulationRepository, never()).save(any());
        }

        @Test
        @DisplayName("the caller's own account is accepted")
        void acceptsOwnAccount() {
            BrokerAccountId mine = account(BrokerType.ALPACA_PAPER, OWNER);

            assertThatCode(() -> service.execute(commandFor(mine, false))).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("a live account requires the same acknowledgement a live investment plan does")
    class LiveMoneyConsent {

        @Test
        @DisplayName("ALPACA_LIVE without confirmLive is refused")
        void refusesAlpacaLiveWithoutConsent() {
            BrokerAccountId live = account(BrokerType.ALPACA_LIVE, OWNER);

            assertThatThrownBy(() -> service.execute(commandFor(live, false)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("confirmLive")
                .hasMessageContaining("REAL money");

            verify(simulationRepository, never()).save(any());
        }

        @Test
        @DisplayName("SAXO_LIVE is covered too — the guard is isLive(), not an ALPACA_LIVE equality check")
        void refusesSaxoLiveWithoutConsent() {
            BrokerAccountId live = account(BrokerType.SAXO_LIVE, OWNER);

            assertThatThrownBy(() -> service.execute(commandFor(live, false)))
                .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("ALPACA_LIVE with confirmLive proceeds")
        void allowsLiveWithConsent() {
            BrokerAccountId live = account(BrokerType.ALPACA_LIVE, OWNER);

            assertThatCode(() -> service.execute(commandFor(live, true))).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("paper and mock accounts need no acknowledgement — the gate is real money, not brokers")
        void paperAndMockNeedNoConsent() {
            assertThatCode(() -> service.execute(commandFor(account(BrokerType.ALPACA_PAPER, OWNER), false)))
                .doesNotThrowAnyException();
            assertThatCode(() -> service.execute(commandFor(account(BrokerType.MOCK, OWNER), false)))
                .doesNotThrowAnyException();
            assertThatCode(() -> service.execute(commandFor(account(BrokerType.SAXO_SIM, OWNER), false)))
                .doesNotThrowAnyException();
        }
    }

    @Test
    @DisplayName("a backtest with no broker account is unaffected")
    void backtestNeedsNoBrokerGuard() {
        Instant now = Instant.now();
        CreateSimulationCommand backtest = new CreateSimulationCommand(
            "backtest", STRATEGY_ID, now.minus(60, ChronoUnit.DAYS), now.minus(30, ChronoUnit.DAYS),
            CASH, null, null, null, OWNER, false);

        assertThatCode(() -> service.execute(backtest)).doesNotThrowAnyException();
    }
}
