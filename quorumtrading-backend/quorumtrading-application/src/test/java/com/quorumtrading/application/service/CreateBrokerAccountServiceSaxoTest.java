package com.quorumtrading.application.service;

import com.quorumtrading.application.port.out.BrokerAccountRepository;
import com.quorumtrading.application.port.out.BrokerCredentialValidationPort;
import com.quorumtrading.application.usecase.command.CreateBrokerAccountCommand;
import com.quorumtrading.model.entity.BrokerAccount;
import com.quorumtrading.model.enums.AccountStatus;
import com.quorumtrading.model.enums.BrokerType;
import com.quorumtrading.model.enums.SaxoEnvironment;
import com.quorumtrading.model.valueobject.ApiKeyCredentials;
import com.quorumtrading.model.valueobject.BrokerCredentials;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.SaxoOAuthCredentials;
import com.quorumtrading.model.valueobject.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the Saxo (OAuth) paths of {@link CreateBrokerAccountService} and the Saxo
 * command factories on {@link CreateBrokerAccountCommand}.
 *
 * <p>Covers (WP PB4-command-factories):</p>
 * <ul>
 *   <li>{@code forSaxoSim} / {@code forSaxoLive} build correctly-shaped commands</li>
 *   <li>{@code createFromOAuth} skips the paste-key validation port entirely</li>
 *   <li>the D8 {@code confirmLive} guard (keyed off {@link BrokerType#isLive()}) rejects an
 *       unconfirmed live broker on both the OAuth path and the paste-key {@code execute} path</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class CreateBrokerAccountServiceSaxoTest {

    @Mock private BrokerAccountRepository brokerAccountRepository;
    @Mock private BrokerCredentialValidationPort credentialValidationPort;

    private CreateBrokerAccountService service;

    private static final UserId OWNER = new UserId(UUID.randomUUID());
    private static final Money EUR_BALANCE = new Money(new BigDecimal("25000.00"), Money.Currency.EUR);
    private static final Money EUR_EQUITY = new Money(new BigDecimal("26500.00"), Money.Currency.EUR);

    @BeforeEach
    void setUp() {
        service = new CreateBrokerAccountService(brokerAccountRepository, credentialValidationPort);
    }

    // --- Command factories ----------------------------------------------------------------

    @Test
    void forSaxoSimBuildsCorrectCommand() {
        SaxoOAuthCredentials creds = simCredentials();

        CreateBrokerAccountCommand command = CreateBrokerAccountCommand.forSaxoSim(OWNER, "My Saxo SIM", creds);

        assertThat(command.userId()).isEqualTo(OWNER);
        assertThat(command.name()).isEqualTo("My Saxo SIM");
        assertThat(command.brokerType()).isEqualTo(BrokerType.SAXO_SIM);
        assertThat(command.credentials()).isSameAs(creds);
        assertThat(command.mockConfig()).isNull();
        assertThat(command.confirmLive()).isFalse();
        assertThat(command.targetsLiveBroker()).isFalse();
    }

    @Test
    void forSaxoLiveBuildsCorrectCommandWithConfirmFlag() {
        SaxoOAuthCredentials creds = liveCredentials();

        CreateBrokerAccountCommand confirmed =
            CreateBrokerAccountCommand.forSaxoLive(OWNER, "My Saxo LIVE", creds, true);
        CreateBrokerAccountCommand unconfirmed =
            CreateBrokerAccountCommand.forSaxoLive(OWNER, "My Saxo LIVE", creds, false);

        assertThat(confirmed.brokerType()).isEqualTo(BrokerType.SAXO_LIVE);
        assertThat(confirmed.credentials()).isSameAs(creds);
        assertThat(confirmed.mockConfig()).isNull();
        assertThat(confirmed.confirmLive()).isTrue();
        assertThat(confirmed.targetsLiveBroker()).isTrue();

        assertThat(unconfirmed.confirmLive()).isFalse();
        assertThat(unconfirmed.targetsLiveBroker()).isTrue();
    }

    // --- createFromOAuth: happy paths -----------------------------------------------------

    @Test
    void createFromOAuthSimSkipsPasteKeyValidationAndPersists() {
        when(brokerAccountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CreateBrokerAccountCommand.OAuthAccountCreation request =
            new CreateBrokerAccountCommand.OAuthAccountCreation(
                OWNER, "Saxo SIM", BrokerType.SAXO_SIM, simCredentials(), EUR_BALANCE, EUR_EQUITY, false);

        BrokerAccount account = service.createFromOAuth(request);

        // The paste-key validation port is never consulted for OAuth accounts.
        verifyNoInteractions(credentialValidationPort);
        assertThat(account.getBrokerType()).isEqualTo(BrokerType.SAXO_SIM);
        assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(account.getTotalBalance()).isEqualTo(EUR_BALANCE);
        assertThat(account.getEquity()).isEqualTo(EUR_EQUITY);
        assertThat(account.getCredentials()).isInstanceOf(SaxoOAuthCredentials.class);
        verify(brokerAccountRepository).save(any(BrokerAccount.class));
    }

    @Test
    void createFromOAuthLiveWithConfirmationPersists() {
        when(brokerAccountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CreateBrokerAccountCommand.OAuthAccountCreation request =
            new CreateBrokerAccountCommand.OAuthAccountCreation(
                OWNER, "Saxo LIVE", BrokerType.SAXO_LIVE, liveCredentials(), EUR_BALANCE, null, true);

        BrokerAccount account = service.createFromOAuth(request);

        verifyNoInteractions(credentialValidationPort);
        assertThat(account.getBrokerType()).isEqualTo(BrokerType.SAXO_LIVE);
        verify(brokerAccountRepository).save(any(BrokerAccount.class));
    }

    // --- createFromOAuth: D8 confirmLive guard + input validation -------------------------

    @Test
    void createFromOAuthLiveWithoutConfirmationRejected() {
        CreateBrokerAccountCommand.OAuthAccountCreation request =
            new CreateBrokerAccountCommand.OAuthAccountCreation(
                OWNER, "Saxo LIVE", BrokerType.SAXO_LIVE, liveCredentials(), EUR_BALANCE, EUR_EQUITY, false);

        assertThatThrownBy(() -> service.createFromOAuth(request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("confirmLive");

        verify(brokerAccountRepository, never()).save(any());
        verifyNoInteractions(credentialValidationPort);
    }

    @Test
    void createFromOAuthRejectsNonSaxoBrokerType() {
        CreateBrokerAccountCommand.OAuthAccountCreation request =
            new CreateBrokerAccountCommand.OAuthAccountCreation(
                OWNER, "Wrong", BrokerType.ALPACA_PAPER, simCredentials(), EUR_BALANCE, EUR_EQUITY, false);

        assertThatThrownBy(() -> service.createFromOAuth(request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Saxo");

        verify(brokerAccountRepository, never()).save(any());
    }

    @Test
    void createFromOAuthRejectsMissingBalance() {
        CreateBrokerAccountCommand.OAuthAccountCreation request =
            new CreateBrokerAccountCommand.OAuthAccountCreation(
                OWNER, "Saxo SIM", BrokerType.SAXO_SIM, simCredentials(), null, null, false);

        assertThatThrownBy(() -> service.createFromOAuth(request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Balance");

        verify(brokerAccountRepository, never()).save(any());
    }

    // --- execute() (paste-key path) D8 guard + Saxo rejection -----------------------------

    @Test
    void executeRejectsAlpacaLiveWithoutConfirmation() {
        // forAlpacaLive defaults confirmLive=true; construct an unconfirmed command directly to
        // prove the server-side D8 guard fires for any isLive() broker on the paste-key path.
        BrokerCredentials creds = new ApiKeyCredentials("key", "secret");
        CreateBrokerAccountCommand command = new CreateBrokerAccountCommand(
            OWNER, "Alpaca Live", BrokerType.ALPACA_LIVE, creds, null, false);

        assertThatThrownBy(() -> service.execute(command))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("confirmLive");

        verify(brokerAccountRepository, never()).save(any());
        verifyNoInteractions(credentialValidationPort);
    }

    @Test
    void executeRejectsSaxoTypeDirectingToOAuthFlow() {
        CreateBrokerAccountCommand command =
            CreateBrokerAccountCommand.forSaxoSim(OWNER, "Saxo SIM", simCredentials());

        assertThatThrownBy(() -> service.execute(command))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("createFromOAuth");

        verify(brokerAccountRepository, never()).save(any());
        verifyNoInteractions(credentialValidationPort);
    }

    // --- helpers --------------------------------------------------------------------------

    private static SaxoOAuthCredentials simCredentials() {
        return new SaxoOAuthCredentials(
            "app-key", "app-secret", "refresh-token", null, null, SaxoEnvironment.SIM);
    }

    private static SaxoOAuthCredentials liveCredentials() {
        return new SaxoOAuthCredentials(
            "app-key", "app-secret", "refresh-token", null, null, SaxoEnvironment.LIVE);
    }
}
