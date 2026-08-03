package com.quorumtrading.integration;

import com.quorumtrading.application.port.out.BrokerAccountRepository;
import com.quorumtrading.application.usecase.CreateSimulationUseCase;
import com.quorumtrading.application.usecase.brokeraccount.CreateBrokerAccountUseCase;
import com.quorumtrading.application.usecase.command.CreateBrokerAccountCommand;
import com.quorumtrading.application.usecase.command.CreateSimulationCommand;
import com.quorumtrading.model.entity.BrokerAccount;
import com.quorumtrading.model.entity.Strategy;
import com.quorumtrading.model.entity.User;
import com.quorumtrading.model.enums.AccountStatus;
import com.quorumtrading.model.enums.BrokerType;
import com.quorumtrading.model.enums.CostPreset;
import com.quorumtrading.model.valueobject.*;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for broker account creation, persistence, and simulation wiring.
 *
 * Domain invariants (reserve/release) are covered by unit tests —
 * these integration tests verify the persistence layer and use case orchestration.
 */
@QuarkusTest
class BrokerAccountIntegrationTest {

    @Inject
    CreateBrokerAccountUseCase createBrokerAccountUseCase;

    @Inject
    BrokerAccountRepository brokerAccountRepository;

    @Inject
    CreateSimulationUseCase createSimulationUseCase;

    @Inject
    IntegrationTestHelper helper;

    @Test
    @Transactional
    @DisplayName("should create mock broker account via use case")
    void shouldCreateMockBrokerAccountViaUseCase() {
        // Arrange
        User user = helper.createUser();
        MockBrokerConfig config = MockBrokerConfig.fromPreset(Money.usd(100_000), CostPreset.TYPICAL_RETAIL);
        CreateBrokerAccountCommand command = CreateBrokerAccountCommand.forMock(
                user.getId(), "Test Mock Account", config
        );

        // Act
        BrokerAccount account = createBrokerAccountUseCase.execute(command);

        // Assert
        assertThat(account).isNotNull();
        assertThat(account.getId()).isNotNull();
        assertThat(account.getBrokerType()).isEqualTo(BrokerType.MOCK);
        assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(account.getTotalBalance().amount()).isEqualByComparingTo(BigDecimal.valueOf(100_000));
        assertThat(account.getReservedBalance().amount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(account.isMock()).isTrue();

        // Verify persistence
        BrokerAccount reloaded = brokerAccountRepository.findById(account.getId()).orElseThrow();
        assertThat(reloaded.getTotalBalance().amount()).isEqualByComparingTo(BigDecimal.valueOf(100_000));
    }

    @Test
    @Transactional
    @DisplayName("should persist reservation state")
    void shouldPersistReservationState() {
        // Arrange
        User user = helper.createUser();
        BrokerAccount account = helper.createMockBrokerAccount(user.getId(), Money.usd(100_000));

        // Act — reserve $50k
        account.reserve(Money.usd(50_000));
        brokerAccountRepository.save(account);

        // Assert — reload and verify
        BrokerAccount reloaded = brokerAccountRepository.findById(account.getId()).orElseThrow();
        assertThat(reloaded.getReservedBalance().amount()).isEqualByComparingTo(BigDecimal.valueOf(50_000));
        assertThat(reloaded.getAvailableBalance().amount()).isEqualByComparingTo(BigDecimal.valueOf(50_000));
    }

    @Test
    @Transactional
    @DisplayName("should create simulation with inline mock broker config")
    void shouldCreateSimulationWithInlineMockBroker() {
        // Arrange — inline mock broker creation uses system user 00000000-...-000000000001
        // which must exist in the users table due to FK constraint
        UserId systemUserId = new UserId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        helper.createUserWithId(systemUserId, "system-inline-broker@quorumtrading.com");

        Strategy strategy = helper.createStrategy(
                "Inline Broker Strategy-" + UUID.randomUUID().toString().substring(0, 8),
                IntegrationTestHelper.VALID_CRYPTO_STRATEGY
        );
        MockBrokerConfig config = MockBrokerConfig.fromPreset(Money.usd(100_000), CostPreset.TYPICAL_RETAIL);

        CreateSimulationCommand command = new CreateSimulationCommand(
                "Inline Mock Sim",
                strategy.getId(),
                Instant.now().minus(7, ChronoUnit.DAYS),
                Instant.now(),
                Money.usd(100_000),
                null, // brokerAccountId
                null, // tradingLimit
                config, // mockBrokerConfig
                strategy.getOwnerId() // requestingUserId — a null user is denied, not exempted
        );

        // Act
        SimulationId simulationId = createSimulationUseCase.execute(command);

        // Assert
        assertThat(simulationId).isNotNull();
    }

    @Test
    @Transactional
    @DisplayName("should create simulation with existing broker account")
    void shouldCreateSimulationWithExistingBrokerAccount() {
        // Arrange
        User user = helper.createUser();
        BrokerAccount account = helper.createMockBrokerAccount(user.getId(), Money.usd(100_000));
        // Same owner for the account and the strategy. The two-arg createStrategy mints a fresh
        // user, so this fixture used to hand one user's broker account to another user's request —
        // which the service now refuses, and which no real user can do.
        Strategy strategy = helper.createStrategy(
                "Existing Broker Strategy-" + UUID.randomUUID().toString().substring(0, 8),
                IntegrationTestHelper.VALID_CRYPTO_STRATEGY,
                user.getId()
        );

        CreateSimulationCommand command = new CreateSimulationCommand(
                "Existing Broker Sim",
                strategy.getId(),
                Instant.now().minus(7, ChronoUnit.DAYS),
                Instant.now(),
                Money.usd(50_000),
                account.getId(),
                Money.usd(50_000),
                null,
                strategy.getOwnerId());

        // Act
        SimulationId simulationId = createSimulationUseCase.execute(command);

        // Assert
        assertThat(simulationId).isNotNull();
    }

    @Test
    @Transactional
    @DisplayName("should reject simulation exceeding available balance")
    void shouldRejectSimulationExceedingAvailableBalance() {
        // Arrange — create account with $100k, reserve $80k so only $20k available
        User user = helper.createUser();
        BrokerAccount account = helper.createMockBrokerAccount(user.getId(), Money.usd(100_000));
        account.reserve(Money.usd(80_000));
        brokerAccountRepository.save(account);

        Strategy strategy = helper.createStrategy(
                "Exceed Balance Strategy-" + UUID.randomUUID().toString().substring(0, 8),
                IntegrationTestHelper.VALID_CRYPTO_STRATEGY,
                user.getId()
        );

        // Try to create a simulation requesting $50k from account with only $20k available
        CreateSimulationCommand command = new CreateSimulationCommand(
                "Exceeding Sim",
                strategy.getId(),
                Instant.now().minus(7, ChronoUnit.DAYS),
                Instant.now(),
                Money.usd(50_000),
                account.getId(),
                Money.usd(50_000),
                null,
                strategy.getOwnerId());

        // Act & Assert
        assertThatThrownBy(() -> createSimulationUseCase.execute(command))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Insufficient available balance");
    }

    /**
     * The strategy was authorization-checked; the broker account was not. Anyone who learned
     * another user's account UUID could ring-fence that account's capital behind their own
     * simulation — and on a live account, trade with it.
     *
     * <p>Two fixtures in this very file used to pair one user's account with another user's
     * request and pass, which is how reachable the gap was.</p>
     */
    @Test
    @Transactional
    @DisplayName("should refuse a broker account belonging to another user")
    void shouldRefuseAnotherUsersBrokerAccount() {
        User accountOwner = helper.createUser();
        BrokerAccount theirAccount =
                helper.createMockBrokerAccount(accountOwner.getId(), Money.usd(100_000));

        User attacker = helper.createUser();
        Strategy myStrategy = helper.createStrategy(
                "Borrowed Broker Strategy-" + UUID.randomUUID().toString().substring(0, 8),
                IntegrationTestHelper.VALID_CRYPTO_STRATEGY,
                attacker.getId()
        );

        CreateSimulationCommand command = new CreateSimulationCommand(
                "Borrowed Broker Sim",
                myStrategy.getId(),
                Instant.now().minus(7, ChronoUnit.DAYS),
                Instant.now(),
                Money.usd(50_000),
                theirAccount.getId(),
                Money.usd(50_000),
                null,
                attacker.getId());

        assertThatThrownBy(() -> createSimulationUseCase.execute(command))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("does not belong");
    }
}
