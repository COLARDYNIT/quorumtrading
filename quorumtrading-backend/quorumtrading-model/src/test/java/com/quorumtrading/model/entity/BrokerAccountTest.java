package com.quorumtrading.model.entity;

import com.quorumtrading.model.enums.AccountStatus;
import com.quorumtrading.model.enums.BrokerType;
import com.quorumtrading.model.enums.CostPreset;
import com.quorumtrading.model.enums.SaxoEnvironment;
import com.quorumtrading.model.valueobject.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.*;

class BrokerAccountTest {

    @Test
    void shouldCreateMockBrokerAccount() {
        UserId userId = UserId.generate();
        Money initialCapital = new Money(new BigDecimal("100000"), Money.Currency.USD);
        MockBrokerConfig config = MockBrokerConfig.fromPreset(initialCapital, CostPreset.TYPICAL_RETAIL);

        BrokerAccount account = BrokerAccount.createMock(userId, "Test Mock", config);

        assertThat(account.getBrokerType()).isEqualTo(BrokerType.MOCK);
        assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(account.getTotalBalance()).isEqualTo(initialCapital);
        assertThat(account.getReservedBalance()).isEqualTo(Money.zero(Money.Currency.USD));
        assertThat(account.getCredentials()).isNull();
        assertThat(account.getMockConfig()).isEqualTo(config);
        assertThat(account.getUserId()).isEqualTo(userId);
        assertThat(account.getName()).isEqualTo("Test Mock");
        assertThat(account.getId()).isNotNull();
        assertThat(account.getCreatedAt()).isNotNull();
        assertThat(account.getUpdatedAt()).isNotNull();
    }

    @Test
    void shouldCreateRealBrokerAccount() {
        UserId userId = UserId.generate();
        BrokerCredentials creds = new ApiKeyCredentials("key", "secret");
        Money balance = new Money(new BigDecimal("50000"), Money.Currency.USD);

        BrokerAccount account = BrokerAccount.createReal(
            userId, "My Alpaca", BrokerType.ALPACA_PAPER, creds, balance, null
        );

        assertThat(account.getBrokerType()).isEqualTo(BrokerType.ALPACA_PAPER);
        assertThat(account.getCredentials()).isEqualTo(creds);
        assertThat(account.getTotalBalance()).isEqualTo(balance);
        assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(account.getMockConfig()).isNull();
        assertThat(account.getUserId()).isEqualTo(userId);
        assertThat(account.getName()).isEqualTo("My Alpaca");
    }

    @Test
    void shouldCreateSaxoBrokerAccountWithOAuthCredentials() {
        UserId userId = UserId.generate();
        SaxoOAuthCredentials creds = saxoCreds(SaxoEnvironment.SIM);
        Money balance = new Money(new BigDecimal("50000"), Money.Currency.EUR);

        BrokerAccount account = BrokerAccount.createReal(
            userId, "My Saxo", BrokerType.SAXO_SIM, creds, balance, balance
        );

        assertThat(account.getBrokerType()).isEqualTo(BrokerType.SAXO_SIM);
        assertThat(account.getCredentials()).isEqualTo(creds);
        assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(account.getTotalBalance().currency()).isEqualTo(Money.Currency.EUR);
    }

    // --- Credential-family guard ---

    @Test
    void shouldRejectAlpacaPaperWithSaxoOAuthCredentials() {
        UserId userId = UserId.generate();
        Money balance = new Money(new BigDecimal("50000"), Money.Currency.USD);

        assertThatThrownBy(() -> BrokerAccount.createReal(
            userId, "Wrong", BrokerType.ALPACA_PAPER, saxoCreds(SaxoEnvironment.SIM), balance, null
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("ApiKeyCredentials");
    }

    @Test
    void shouldRejectAlpacaLiveWithSaxoOAuthCredentials() {
        UserId userId = UserId.generate();
        Money balance = new Money(new BigDecimal("50000"), Money.Currency.USD);

        assertThatThrownBy(() -> BrokerAccount.createReal(
            userId, "Wrong", BrokerType.ALPACA_LIVE, saxoCreds(SaxoEnvironment.LIVE), balance, null
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("ApiKeyCredentials");
    }

    @Test
    void shouldRejectSaxoSimWithApiKeyCredentials() {
        UserId userId = UserId.generate();
        Money balance = new Money(new BigDecimal("50000"), Money.Currency.EUR);

        assertThatThrownBy(() -> BrokerAccount.createReal(
            userId, "Wrong", BrokerType.SAXO_SIM, new ApiKeyCredentials("key", "secret"), balance, null
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("SaxoOAuthCredentials");
    }

    @Test
    void shouldRejectSaxoLiveWithApiKeyCredentials() {
        UserId userId = UserId.generate();
        Money balance = new Money(new BigDecimal("50000"), Money.Currency.EUR);

        assertThatThrownBy(() -> BrokerAccount.createReal(
            userId, "Wrong", BrokerType.SAXO_LIVE, new ApiKeyCredentials("key", "secret"), balance, null
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("SaxoOAuthCredentials");
    }

    @Test
    void shouldAcceptApiKeyCredentialsForBothAlpacaTypes() {
        UserId userId = UserId.generate();
        Money balance = new Money(new BigDecimal("50000"), Money.Currency.USD);
        ApiKeyCredentials creds = new ApiKeyCredentials("key", "secret");

        assertThat(BrokerAccount.createReal(userId, "Paper", BrokerType.ALPACA_PAPER, creds, balance, null)
            .getCredentials()).isEqualTo(creds);
        assertThat(BrokerAccount.createReal(userId, "Live", BrokerType.ALPACA_LIVE, creds, balance, null)
            .getCredentials()).isEqualTo(creds);
    }

    @Test
    void shouldAcceptSaxoOAuthCredentialsForBothSaxoTypes() {
        UserId userId = UserId.generate();
        Money balance = new Money(new BigDecimal("50000"), Money.Currency.EUR);

        assertThat(BrokerAccount.createReal(userId, "Sim", BrokerType.SAXO_SIM, saxoCreds(SaxoEnvironment.SIM), balance, balance)
            .getCredentials()).isInstanceOf(SaxoOAuthCredentials.class);
        assertThat(BrokerAccount.createReal(userId, "Live", BrokerType.SAXO_LIVE, saxoCreds(SaxoEnvironment.LIVE), balance, balance)
            .getCredentials()).isInstanceOf(SaxoOAuthCredentials.class);
    }

    @Test
    void shouldRejectCredentialFamilySwapOnUpdateForSaxoAccount() {
        UserId userId = UserId.generate();
        Money balance = new Money(new BigDecimal("50000"), Money.Currency.EUR);
        BrokerAccount account = BrokerAccount.createReal(
            userId, "Saxo", BrokerType.SAXO_SIM, saxoCreds(SaxoEnvironment.SIM), balance, balance);

        assertThatThrownBy(() -> account.updateCredentials(new ApiKeyCredentials("key", "secret")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("SaxoOAuthCredentials");
    }

    @Test
    void shouldRejectCredentialFamilySwapOnUpdateForAlpacaAccount() {
        UserId userId = UserId.generate();
        Money balance = new Money(new BigDecimal("50000"), Money.Currency.USD);
        BrokerAccount account = BrokerAccount.createReal(
            userId, "Alpaca", BrokerType.ALPACA_PAPER, new ApiKeyCredentials("k", "s"), balance, null);

        assertThatThrownBy(() -> account.updateCredentials(saxoCreds(SaxoEnvironment.SIM)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("ApiKeyCredentials");
    }

    @Test
    void shouldAllowSameFamilyCredentialUpdate() {
        UserId userId = UserId.generate();
        Money balance = new Money(new BigDecimal("50000"), Money.Currency.EUR);
        BrokerAccount account = BrokerAccount.createReal(
            userId, "Saxo", BrokerType.SAXO_SIM, saxoCreds(SaxoEnvironment.SIM), balance, balance);

        SaxoOAuthCredentials newCreds = saxoCreds(SaxoEnvironment.SIM).withRotatedTokens(
            "new-refresh", "new-access", Instant.now().plusSeconds(1200));
        account.updateCredentials(newCreds);

        assertThat(account.getCredentials()).isEqualTo(newCreds);
    }

    // --- Reauth lifecycle ---

    @Test
    void shouldApplyRefreshedTokensAndPreserveActiveStatus() {
        UserId userId = UserId.generate();
        Money balance = new Money(new BigDecimal("50000"), Money.Currency.EUR);
        BrokerAccount account = BrokerAccount.createReal(
            userId, "Saxo", BrokerType.SAXO_SIM, saxoCreds(SaxoEnvironment.SIM), balance, balance);

        Instant expiry = Instant.now().plusSeconds(1200);
        SaxoOAuthCredentials rotated = saxoCreds(SaxoEnvironment.SIM)
            .withRotatedTokens("rotated-refresh", "rotated-access", expiry);

        account.applyRefreshedTokens(rotated);

        assertThat(account.getCredentials()).isEqualTo(rotated);
        assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    void shouldRecoverToActiveWhenApplyingRefreshedTokensFromNeedsReauth() {
        UserId userId = UserId.generate();
        Money balance = new Money(new BigDecimal("50000"), Money.Currency.EUR);
        BrokerAccount account = BrokerAccount.createReal(
            userId, "Saxo", BrokerType.SAXO_SIM, saxoCreds(SaxoEnvironment.SIM), balance, balance);
        account.markNeedsReauth();
        assertThat(account.getStatus()).isEqualTo(AccountStatus.NEEDS_REAUTH);

        SaxoOAuthCredentials rotated = saxoCreds(SaxoEnvironment.SIM)
            .withRotatedTokens("rotated-refresh", "rotated-access", Instant.now().plusSeconds(1200));
        account.applyRefreshedTokens(rotated);

        assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(account.getCredentials()).isEqualTo(rotated);
    }

    @Test
    void shouldRejectApplyRefreshedTokensOnNonOAuthAccount() {
        UserId userId = UserId.generate();
        Money balance = new Money(new BigDecimal("50000"), Money.Currency.USD);
        BrokerAccount account = BrokerAccount.createReal(
            userId, "Alpaca", BrokerType.ALPACA_PAPER, new ApiKeyCredentials("k", "s"), balance, null);

        SaxoOAuthCredentials rotated = saxoCreds(SaxoEnvironment.SIM)
            .withRotatedTokens("rotated-refresh", "rotated-access", Instant.now().plusSeconds(1200));

        assertThatThrownBy(() -> account.applyRefreshedTokens(rotated))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("non-OAuth");
    }

    @Test
    void shouldMarkNeedsReauth() {
        UserId userId = UserId.generate();
        Money balance = new Money(new BigDecimal("50000"), Money.Currency.EUR);
        BrokerAccount account = BrokerAccount.createReal(
            userId, "Saxo", BrokerType.SAXO_LIVE, saxoCreds(SaxoEnvironment.LIVE), balance, balance);

        account.markNeedsReauth();

        assertThat(account.getStatus()).isEqualTo(AccountStatus.NEEDS_REAUTH);
    }

    private static SaxoOAuthCredentials saxoCreds(SaxoEnvironment env) {
        return new SaxoOAuthCredentials("app-key", "app-secret", "refresh-token", null, null, env);
    }

    @Test
    void shouldRejectRealBrokerAccountWithMockType() {
        UserId userId = UserId.generate();
        BrokerCredentials creds = new ApiKeyCredentials("key", "secret");
        Money balance = new Money(new BigDecimal("50000"), Money.Currency.USD);

        assertThatThrownBy(() -> BrokerAccount.createReal(
            userId, "Invalid", BrokerType.MOCK, creds, balance, null
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("MOCK broker type");
    }

    @Test
    void shouldRejectRealBrokerAccountWithNullCredentials() {
        UserId userId = UserId.generate();
        Money balance = new Money(new BigDecimal("50000"), Money.Currency.USD);

        assertThatThrownBy(() -> BrokerAccount.createReal(
            userId, "Invalid", BrokerType.ALPACA_PAPER, null, balance, null
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("credentials");
    }

    @Test
    void shouldReserveAndReleaseBalance() {
        UserId userId = UserId.generate();
        Money initialCapital = new Money(new BigDecimal("100000"), Money.Currency.USD);
        MockBrokerConfig config = MockBrokerConfig.fromPreset(initialCapital, CostPreset.ZERO_FEES);
        BrokerAccount account = BrokerAccount.createMock(userId, "Test", config);

        Money reserveAmount = new Money(new BigDecimal("30000"), Money.Currency.USD);
        account.reserve(reserveAmount);

        assertThat(account.getReservedBalance()).isEqualTo(reserveAmount);
        assertThat(account.getAvailableBalance()).isEqualTo(new Money(new BigDecimal("70000"), Money.Currency.USD));

        account.release(reserveAmount);
        assertThat(account.getReservedBalance()).isEqualTo(Money.zero(Money.Currency.USD));
        assertThat(account.getAvailableBalance()).isEqualTo(initialCapital);
    }

    @Test
    void shouldReserveMultipleTimes() {
        UserId userId = UserId.generate();
        Money initialCapital = new Money(new BigDecimal("100000"), Money.Currency.USD);
        MockBrokerConfig config = MockBrokerConfig.fromPreset(initialCapital, CostPreset.ZERO_FEES);
        BrokerAccount account = BrokerAccount.createMock(userId, "Test", config);

        Money firstReserve = new Money(new BigDecimal("20000"), Money.Currency.USD);
        Money secondReserve = new Money(new BigDecimal("30000"), Money.Currency.USD);

        account.reserve(firstReserve);
        account.reserve(secondReserve);

        assertThat(account.getReservedBalance()).isEqualTo(new Money(new BigDecimal("50000"), Money.Currency.USD));
        assertThat(account.getAvailableBalance()).isEqualTo(new Money(new BigDecimal("50000"), Money.Currency.USD));
    }

    @Test
    void shouldThrowWhenReservingMoreThanAvailable() {
        UserId userId = UserId.generate();
        Money initialCapital = new Money(new BigDecimal("10000"), Money.Currency.USD);
        MockBrokerConfig config = MockBrokerConfig.fromPreset(initialCapital, CostPreset.ZERO_FEES);
        BrokerAccount account = BrokerAccount.createMock(userId, "Test", config);

        Money tooMuch = new Money(new BigDecimal("20000"), Money.Currency.USD);

        assertThatThrownBy(() -> account.reserve(tooMuch))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Insufficient available balance");
    }

    @Test
    void shouldThrowWhenReleasingMoreThanReserved() {
        UserId userId = UserId.generate();
        Money initialCapital = new Money(new BigDecimal("100000"), Money.Currency.USD);
        MockBrokerConfig config = MockBrokerConfig.fromPreset(initialCapital, CostPreset.ZERO_FEES);
        BrokerAccount account = BrokerAccount.createMock(userId, "Test", config);

        Money reserveAmount = new Money(new BigDecimal("30000"), Money.Currency.USD);
        account.reserve(reserveAmount);

        Money tooMuch = new Money(new BigDecimal("50000"), Money.Currency.USD);

        assertThatThrownBy(() -> account.release(tooMuch))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Cannot release more than reserved");
    }

    @Test
    void shouldMarkInvalidCredentials() {
        UserId userId = UserId.generate();
        BrokerCredentials creds = new ApiKeyCredentials("key", "secret");
        Money balance = new Money(new BigDecimal("50000"), Money.Currency.USD);
        BrokerAccount account = BrokerAccount.createReal(userId, "Alpaca", BrokerType.ALPACA_PAPER, creds, balance, null);

        account.markInvalidCredentials();

        assertThat(account.getStatus()).isEqualTo(AccountStatus.INVALID_CREDENTIALS);
    }

    @Test
    void shouldMarkActive() {
        UserId userId = UserId.generate();
        BrokerCredentials creds = new ApiKeyCredentials("key", "secret");
        Money balance = new Money(new BigDecimal("50000"), Money.Currency.USD);
        BrokerAccount account = BrokerAccount.createReal(userId, "Alpaca", BrokerType.ALPACA_PAPER, creds, balance, null);
        account.markInvalidCredentials();

        account.markActive();

        assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    void shouldDisableAccount() {
        UserId userId = UserId.generate();
        Money initialCapital = new Money(new BigDecimal("100000"), Money.Currency.USD);
        MockBrokerConfig config = MockBrokerConfig.fromPreset(initialCapital, CostPreset.ZERO_FEES);
        BrokerAccount account = BrokerAccount.createMock(userId, "Test", config);

        account.disable();

        assertThat(account.getStatus()).isEqualTo(AccountStatus.DISABLED);
    }

    @Test
    void shouldIdentifyMockAccount() {
        UserId userId = UserId.generate();
        Money initialCapital = new Money(new BigDecimal("100000"), Money.Currency.USD);
        MockBrokerConfig config = MockBrokerConfig.fromPreset(initialCapital, CostPreset.ZERO_FEES);
        BrokerAccount mockAccount = BrokerAccount.createMock(userId, "Test", config);

        BrokerCredentials creds = new ApiKeyCredentials("key", "secret");
        Money balance = new Money(new BigDecimal("50000"), Money.Currency.USD);
        BrokerAccount realAccount = BrokerAccount.createReal(userId, "Alpaca", BrokerType.ALPACA_PAPER, creds, balance, null);

        assertThat(mockAccount.isMock()).isTrue();
        assertThat(realAccount.isMock()).isFalse();
    }

    @Test
    void shouldImplementAggregateRoot() {
        UserId userId = UserId.generate();
        Money initialCapital = new Money(new BigDecimal("100000"), Money.Currency.USD);
        MockBrokerConfig config = MockBrokerConfig.fromPreset(initialCapital, CostPreset.ZERO_FEES);
        BrokerAccount account = BrokerAccount.createMock(userId, "Test", config);

        assertThat(account).isInstanceOf(AggregateRoot.class);
    }

    @Test
    void shouldUpdateTimestampOnStateChange() throws InterruptedException {
        UserId userId = UserId.generate();
        Money initialCapital = new Money(new BigDecimal("100000"), Money.Currency.USD);
        MockBrokerConfig config = MockBrokerConfig.fromPreset(initialCapital, CostPreset.ZERO_FEES);
        BrokerAccount account = BrokerAccount.createMock(userId, "Test", config);
        Instant initialUpdatedAt = account.getUpdatedAt();

        // Small delay to ensure timestamp difference
        Thread.sleep(10);

        account.reserve(new Money(new BigDecimal("10000"), Money.Currency.USD));

        assertThat(account.getUpdatedAt()).isAfter(initialUpdatedAt);
    }

    @Test
    void shouldSupportPersistenceReconstruction() {
        BrokerAccountId id = BrokerAccountId.generate();
        UserId userId = UserId.generate();
        String name = "Reconstructed Account";
        BrokerType brokerType = BrokerType.ALPACA_LIVE;
        BrokerCredentials credentials = new ApiKeyCredentials("key", "secret");
        Money totalBalance = new Money(new BigDecimal("75000"), Money.Currency.USD);
        Money reservedBalance = new Money(new BigDecimal("25000"), Money.Currency.USD);
        AccountStatus status = AccountStatus.ACTIVE;
        Instant createdAt = Instant.now().minusSeconds(3600);
        Instant updatedAt = Instant.now().minusSeconds(60);

        BrokerAccount account = new BrokerAccount(
            id, userId, name, brokerType, credentials, totalBalance, null, reservedBalance,
            status, null, createdAt, updatedAt
        );

        assertThat(account.getId()).isEqualTo(id);
        assertThat(account.getUserId()).isEqualTo(userId);
        assertThat(account.getName()).isEqualTo(name);
        assertThat(account.getBrokerType()).isEqualTo(brokerType);
        assertThat(account.getCredentials()).isEqualTo(credentials);
        assertThat(account.getTotalBalance()).isEqualTo(totalBalance);
        assertThat(account.getReservedBalance()).isEqualTo(reservedBalance);
        assertThat(account.getStatus()).isEqualTo(status);
        assertThat(account.getMockConfig()).isNull();
        assertThat(account.getCreatedAt()).isEqualTo(createdAt);
        assertThat(account.getUpdatedAt()).isEqualTo(updatedAt);
        assertThat(account.getAvailableBalance()).isEqualTo(new Money(new BigDecimal("50000"), Money.Currency.USD));
    }
}
