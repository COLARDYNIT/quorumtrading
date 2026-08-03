package com.quorumtrading.application.service;

import com.quorumtrading.application.exception.AccessDeniedException;
import com.quorumtrading.application.exception.BrokerCredentialValidationException;
import com.quorumtrading.application.exception.EntityNotFoundException;
import com.quorumtrading.application.port.out.BrokerAccountRepository;
import com.quorumtrading.application.port.out.BrokerCredentialValidationPort;
import com.quorumtrading.application.port.out.BrokerCredentialValidationPort.CredentialValidationResult;
import com.quorumtrading.application.port.out.SimulationRepository;
import com.quorumtrading.application.usecase.command.UpdateBrokerAccountCredentialsCommand;
import com.quorumtrading.model.entity.BrokerAccount;
import com.quorumtrading.model.enums.AccountStatus;
import com.quorumtrading.model.enums.BrokerType;
import com.quorumtrading.model.valueobject.ApiKeyCredentials;
import com.quorumtrading.model.valueobject.BrokerAccountId;
import com.quorumtrading.model.valueobject.BrokerCredentials;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UpdateBrokerAccountCredentialsServiceTest {

    @Mock private BrokerAccountRepository brokerAccountRepository;
    @Mock private BrokerCredentialValidationPort credentialValidationPort;
    @Mock private SimulationRepository simulationRepository;

    private UpdateBrokerAccountCredentialsService service;

    private static final UserId OWNER = new UserId(UUID.randomUUID());
    private static final UserId OTHER_USER = new UserId(UUID.randomUUID());
    private static final BrokerAccountId ACCOUNT_ID = BrokerAccountId.generate();
    private static final BrokerCredentials OLD_CREDENTIALS = new ApiKeyCredentials("old-key", "old-secret");
    private static final BrokerCredentials NEW_CREDENTIALS = new ApiKeyCredentials("new-key", "new-secret");
    private static final Money BALANCE = Money.usd(new BigDecimal("50000.00"));
    private static final Money EQUITY = Money.usd(new BigDecimal("55000.00"));

    @BeforeEach
    void setUp() {
        AuthorizationService authorizationService = new AuthorizationService();
        service = new UpdateBrokerAccountCredentialsService(
            brokerAccountRepository,
            credentialValidationPort,
            simulationRepository,
            authorizationService
        );
    }

    @Test
    void shouldUpdateCredentialsAndNameSuccessfully() {
        // Given
        BrokerAccount account = createAlpacaPaperAccount();
        when(brokerAccountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));
        when(credentialValidationPort.validate(BrokerType.ALPACA_PAPER, NEW_CREDENTIALS))
            .thenReturn(CredentialValidationResult.success(BALANCE, EQUITY));
        when(simulationRepository.findActiveByBrokerAccountId(ACCOUNT_ID)).thenReturn(Collections.emptyList());
        when(brokerAccountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateBrokerAccountCredentialsCommand command = new UpdateBrokerAccountCredentialsCommand(
            ACCOUNT_ID, OWNER, "Updated Name", NEW_CREDENTIALS
        );

        // When
        BrokerAccount result = service.execute(command);

        // Then
        assertThat(result.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(result.getName()).isEqualTo("Updated Name");
        assertThat(result.getCredentials()).isEqualTo(NEW_CREDENTIALS);
        assertThat(result.getTotalBalance()).isEqualTo(BALANCE);
        assertThat(result.getEquity()).isEqualTo(EQUITY);
        verify(brokerAccountRepository).save(account);
    }

    @Test
    void shouldUpdateCredentialsWithoutChangingName() {
        // Given
        BrokerAccount account = createAlpacaPaperAccount();
        when(brokerAccountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));
        when(credentialValidationPort.validate(BrokerType.ALPACA_PAPER, NEW_CREDENTIALS))
            .thenReturn(CredentialValidationResult.success(BALANCE, EQUITY));
        when(simulationRepository.findActiveByBrokerAccountId(ACCOUNT_ID)).thenReturn(Collections.emptyList());
        when(brokerAccountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateBrokerAccountCredentialsCommand command = new UpdateBrokerAccountCredentialsCommand(
            ACCOUNT_ID, OWNER, null, NEW_CREDENTIALS
        );

        // When
        BrokerAccount result = service.execute(command);

        // Then
        assertThat(result.getName()).isEqualTo("Test Account");
        assertThat(result.getCredentials()).isEqualTo(NEW_CREDENTIALS);
    }

    @Test
    void shouldMarkInvalidCredentialsWhenValidationFails() {
        // Given
        BrokerAccount account = createAlpacaPaperAccount();
        when(brokerAccountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));
        when(credentialValidationPort.validate(BrokerType.ALPACA_PAPER, NEW_CREDENTIALS))
            .thenReturn(CredentialValidationResult.failure("Invalid API key"));

        UpdateBrokerAccountCredentialsCommand command = new UpdateBrokerAccountCredentialsCommand(
            ACCOUNT_ID, OWNER, null, NEW_CREDENTIALS
        );

        // When / Then
        assertThatThrownBy(() -> service.execute(command))
            .isInstanceOf(BrokerCredentialValidationException.class);

        assertThat(account.getStatus()).isEqualTo(AccountStatus.INVALID_CREDENTIALS);
        verify(brokerAccountRepository, never()).save(any());
    }

    @Test
    void shouldThrowWhenAccountNotFound() {
        // Given
        when(brokerAccountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.empty());

        UpdateBrokerAccountCredentialsCommand command = new UpdateBrokerAccountCredentialsCommand(
            ACCOUNT_ID, OWNER, null, NEW_CREDENTIALS
        );

        // When / Then
        assertThatThrownBy(() -> service.execute(command))
            .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void shouldThrowWhenCalledOnMockAccount() {
        // Given
        BrokerAccount mockAccount = createMockAccount();
        when(brokerAccountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(mockAccount));

        UpdateBrokerAccountCredentialsCommand command = new UpdateBrokerAccountCredentialsCommand(
            ACCOUNT_ID, OWNER, null, NEW_CREDENTIALS
        );

        // When / Then
        assertThatThrownBy(() -> service.execute(command))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("mock");
    }

    @Test
    void shouldThrowWhenUserDoesNotOwnAccount() {
        // Given
        BrokerAccount account = createAlpacaPaperAccount();
        when(brokerAccountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));

        UpdateBrokerAccountCredentialsCommand command = new UpdateBrokerAccountCredentialsCommand(
            ACCOUNT_ID, OTHER_USER, null, NEW_CREDENTIALS
        );

        // When / Then
        assertThatThrownBy(() -> service.execute(command))
            .isInstanceOf(AccessDeniedException.class);
    }

    private BrokerAccount createAlpacaPaperAccount() {
        return new BrokerAccount(
            ACCOUNT_ID,
            OWNER,
            "Test Account",
            BrokerType.ALPACA_PAPER,
            OLD_CREDENTIALS,
            Money.usd(new BigDecimal("10000.00")),
            Money.usd(new BigDecimal("12000.00")),
            Money.usd(BigDecimal.ZERO),
            AccountStatus.ACTIVE,
            null,
            Instant.now(),
            Instant.now()
        );
    }

    private BrokerAccount createMockAccount() {
        return new BrokerAccount(
            ACCOUNT_ID,
            OWNER,
            "Mock Account",
            BrokerType.MOCK,
            null,
            Money.usd(new BigDecimal("100000.00")),
            null,
            Money.usd(BigDecimal.ZERO),
            AccountStatus.ACTIVE,
            null,
            Instant.now(),
            Instant.now()
        );
    }
}
