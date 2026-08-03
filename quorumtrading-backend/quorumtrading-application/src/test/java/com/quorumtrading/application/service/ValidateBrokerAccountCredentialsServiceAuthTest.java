package com.quorumtrading.application.service;

import com.quorumtrading.application.exception.AccessDeniedException;
import com.quorumtrading.application.port.out.BrokerAccountRepository;
import com.quorumtrading.application.port.out.BrokerCredentialValidationPort;
import com.quorumtrading.application.port.out.SimulationRepository;
import com.quorumtrading.application.usecase.command.ValidateBrokerAccountCredentialsCommand;
import com.quorumtrading.model.entity.BrokerAccount;
import com.quorumtrading.model.valueobject.BrokerAccountId;
import com.quorumtrading.model.valueobject.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Validating credentials reaches the broker's API using the account's stored credentials and
 * mutates the account status. The command carried only the account id, so any caller could
 * trigger that against any account by UUID — and it was reachable without a token over MCP.
 */
@ExtendWith(MockitoExtension.class)
class ValidateBrokerAccountCredentialsServiceAuthTest {

    @Mock private BrokerAccountRepository brokerAccountRepository;
    @Mock private BrokerCredentialValidationPort credentialValidationPort;
    @Mock private SimulationRepository simulationRepository;

    private ValidateBrokerAccountCredentialsService service;

    private static final UserId OWNER = new UserId(UUID.randomUUID());
    private static final UserId OTHER = new UserId(UUID.randomUUID());
    private static final BrokerAccountId ACCOUNT_ID = new BrokerAccountId(UUID.randomUUID());

    @BeforeEach
    void setUp() {
        service = new ValidateBrokerAccountCredentialsService(
            brokerAccountRepository, credentialValidationPort, simulationRepository,
            new AuthorizationService());
    }

    private void stubOwnedAccount() {
        BrokerAccount account = mock(BrokerAccount.class);
        lenient().when(account.getId()).thenReturn(ACCOUNT_ID);
        lenient().when(account.getUserId()).thenReturn(OWNER);
        when(brokerAccountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));
    }

    @Test
    void rejectsValidationByNonOwner() {
        stubOwnedAccount();

        assertThatThrownBy(() -> service.execute(
                new ValidateBrokerAccountCredentialsCommand(ACCOUNT_ID, OTHER)))
            .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(credentialValidationPort);
        verify(brokerAccountRepository, never()).save(any());
    }

    @Test
    void rejectsValidationWithNoUser() {
        stubOwnedAccount();

        assertThatThrownBy(() -> service.execute(
                new ValidateBrokerAccountCredentialsCommand(ACCOUNT_ID, null)))
            .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(credentialValidationPort);
        verify(brokerAccountRepository, never()).save(any());
    }
}
