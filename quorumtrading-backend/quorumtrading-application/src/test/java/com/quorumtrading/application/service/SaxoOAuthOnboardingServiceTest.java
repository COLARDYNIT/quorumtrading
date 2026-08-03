package com.quorumtrading.application.service;

import com.quorumtrading.application.port.out.OAuthStateStorePort;
import com.quorumtrading.application.port.out.OAuthTokens;
import com.quorumtrading.application.port.out.SaxoOAuthClientPort;
import com.quorumtrading.application.usecase.brokeraccount.CreateBrokerAccountUseCase;
import com.quorumtrading.application.usecase.command.CreateBrokerAccountCommand;
import com.quorumtrading.application.usecase.saxo.CompleteSaxoOnboardingUseCase;
import com.quorumtrading.application.usecase.saxo.OnboardingStart;
import com.quorumtrading.application.usecase.saxo.StartSaxoOnboardingUseCase;
import com.quorumtrading.model.entity.BrokerAccount;
import com.quorumtrading.model.enums.BrokerType;
import com.quorumtrading.model.enums.SaxoEnvironment;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.SaxoOAuthCredentials;
import com.quorumtrading.model.valueobject.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SaxoOAuthOnboardingService} (WP {@code OA1-onboarding-service}).
 *
 * <p>Named acceptance cases:</p>
 * <ul>
 *   <li><b>confidential start round-trip</b> — {@code start} mints a single-use state, persists it
 *       with a TTL, and returns an authorize URL carrying {@code response_type=code} +
 *       {@code client_id} + {@code redirect_uri} + {@code state} and <b>no</b> PKCE
 *       {@code code_challenge} (confidential server flow, SAXO_ANSWERS §1.5); the matching
 *       {@code complete} consumes that state and creates the account.</li>
 *   <li><b>single-use state</b> — the stored state is consumed exactly once; a second/forged callback
 *       is rejected and never reaches the token exchange or account creation.</li>
 *   <li><b>SAXO_LIVE confirmLive guard</b> — onboarding a {@code SAXO_LIVE} account without an
 *       acknowledged {@code confirmLive} propagates the D8 rejection.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class SaxoOAuthOnboardingServiceTest {

    @Mock private SaxoOAuthClientPort oauthClient;
    @Mock private CreateBrokerAccountUseCase createBrokerAccount;

    /** Single-use, in-memory state store mirroring the Redis GETDEL semantics. */
    private InMemoryStateStore stateStore;
    private SaxoOAuthOnboardingService service;

    private static final UserId OWNER = new UserId(UUID.randomUUID());
    private static final String APP_KEY = "test-app-key";
    private static final String APP_SECRET = "test-app-secret";
    private static final String AUTHORIZE_URL = "https://sim.logonvalidation.net/authorize";
    private static final String REDIRECT_URI = "https://eu.quorumtrading.com/api/v1/saxo/callback";
    private static final Duration TTL = Duration.ofMinutes(10);

    @BeforeEach
    void setUp() {
        stateStore = new InMemoryStateStore();
        // Deterministic SecureRandom seed so PKCE/state values are reproducible across the run.
        SecureRandom seeded = new SecureRandom(new byte[]{1, 2, 3, 4});
        service = new SaxoOAuthOnboardingService(
                oauthClient, stateStore, createBrokerAccount,
                APP_KEY, APP_SECRET, AUTHORIZE_URL, TTL, seeded);
    }

    // --- confidential start round-trip --------------------------------------------------------

    @Test
    void startMintsSingleUseStateAndConfidentialAuthorizeUrl() {
        OnboardingStart result = service.start(simStartCommand());

        // State persisted exactly once with the configured TTL.
        assertThat(stateStore.puts).isEqualTo(1);
        assertThat(stateStore.lastTtl).isEqualTo(TTL);
        assertThat(stateStore.store).containsKey(result.state());

        String url = result.authorizationUrl().toString();
        assertThat(url).startsWith(AUTHORIZE_URL + "?");
        assertThat(url)
                .contains("response_type=code")
                .contains("client_id=" + APP_KEY)
                .contains("state=" + result.state());
        // The redirect URI is URL-encoded into the query.
        assertThat(url).contains("redirect_uri=https%3A%2F%2Feu.quorumtrading.com");
        // Confidential server flow: NO PKCE params and no verifier leak (SAXO_ANSWERS §1.5).
        assertThat(url)
                .doesNotContain("code_challenge")
                .doesNotContain("code_challenge_method")
                .doesNotContain("code_verifier");
    }

    @Test
    void completeConsumesStartedStateAndCreatesAccount() {
        OnboardingStart started = service.start(simStartCommand());

        OAuthTokens tokens = new OAuthTokens(
                "access-tok", "refresh-tok", Instant.now().plusSeconds(1200), SaxoEnvironment.SIM);
        when(oauthClient.exchangeAuthorizationCode("auth-code", REDIRECT_URI, SaxoEnvironment.SIM))
                .thenReturn(tokens);
        when(createBrokerAccount.createFromOAuth(any())).thenAnswer(inv -> {
            CreateBrokerAccountCommand.OAuthAccountCreation req = inv.getArgument(0);
            return BrokerAccount.createReal(
                    req.userId(), req.name(), req.brokerType(), req.credentials(),
                    req.balance(), req.equity());
        });

        BrokerAccount account = service.complete(
                new CompleteSaxoOnboardingUseCase.Command(started.state(), "auth-code"));

        assertThat(account.getBrokerType()).isEqualTo(BrokerType.SAXO_SIM);

        ArgumentCaptor<CreateBrokerAccountCommand.OAuthAccountCreation> captor =
                ArgumentCaptor.forClass(CreateBrokerAccountCommand.OAuthAccountCreation.class);
        verify(createBrokerAccount).createFromOAuth(captor.capture());
        CreateBrokerAccountCommand.OAuthAccountCreation req = captor.getValue();
        assertThat(req.userId()).isEqualTo(OWNER);
        assertThat(req.brokerType()).isEqualTo(BrokerType.SAXO_SIM);
        assertThat(req.confirmLive()).isFalse();
        // Credentials carry the rotated token pair plus the app credentials from config.
        SaxoOAuthCredentials creds = req.credentials();
        assertThat(creds.appKey()).isEqualTo(APP_KEY);
        assertThat(creds.appSecret()).isEqualTo(APP_SECRET);
        assertThat(creds.refreshToken()).isEqualTo("refresh-tok");
        assertThat(creds.accessToken()).isEqualTo("access-tok");
        assertThat(creds.environment()).isEqualTo(SaxoEnvironment.SIM);
        // SB5-deferred: seeded zero EUR balance.
        assertThat(req.balance()).isEqualTo(Money.zero(Money.Currency.EUR));

        // State was consumed by the round-trip.
        assertThat(stateStore.store).doesNotContainKey(started.state());
    }

    // --- single-use state ---------------------------------------------------------------------

    @Test
    void completeRejectsReusedState() {
        OnboardingStart started = service.start(simStartCommand());

        OAuthTokens tokens = new OAuthTokens(
                "access-tok", "refresh-tok", Instant.now().plusSeconds(1200), SaxoEnvironment.SIM);
        when(oauthClient.exchangeAuthorizationCode(any(), any(), any())).thenReturn(tokens);
        when(createBrokerAccount.createFromOAuth(any())).thenAnswer(inv -> {
            CreateBrokerAccountCommand.OAuthAccountCreation req = inv.getArgument(0);
            return BrokerAccount.createReal(
                    req.userId(), req.name(), req.brokerType(), req.credentials(),
                    req.balance(), req.equity());
        });

        // First callback succeeds and consumes the state.
        service.complete(new CompleteSaxoOnboardingUseCase.Command(started.state(), "auth-code"));

        // Replaying the same state is rejected — single-use invariant.
        assertThatThrownBy(() ->
                service.complete(new CompleteSaxoOnboardingUseCase.Command(started.state(), "auth-code")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("consumed");

        // The token exchange / account creation ran only for the first (valid) callback.
        verify(oauthClient).exchangeAuthorizationCode(any(), any(), any());
        verify(createBrokerAccount).createFromOAuth(any());
    }

    @Test
    void completeRejectsForgedState() {
        assertThatThrownBy(() ->
                service.complete(new CompleteSaxoOnboardingUseCase.Command("never-issued-state", "auth-code")))
                .isInstanceOf(IllegalArgumentException.class);

        // A forged/unknown state never reaches the token exchange or account creation.
        verifyNoInteractions(oauthClient);
        verifyNoInteractions(createBrokerAccount);
    }

    // --- SAXO_LIVE confirmLive guard ----------------------------------------------------------

    @Test
    void completeSaxoLiveWithoutConfirmationIsRejected() {
        StartSaxoOnboardingUseCase.Command liveUnconfirmed = new StartSaxoOnboardingUseCase.Command(
                OWNER, BrokerType.SAXO_LIVE, SaxoEnvironment.LIVE, REDIRECT_URI, false);
        OnboardingStart started = service.start(liveUnconfirmed);

        OAuthTokens tokens = new OAuthTokens(
                "access-tok", "refresh-tok", Instant.now().plusSeconds(1200), SaxoEnvironment.LIVE);
        when(oauthClient.exchangeAuthorizationCode("auth-code", REDIRECT_URI, SaxoEnvironment.LIVE))
                .thenReturn(tokens);
        // createFromOAuth applies the D8 guard server-side; emulate its rejection.
        when(createBrokerAccount.createFromOAuth(any()))
                .thenThrow(new IllegalArgumentException(
                        "Creating a live (real-money) SAXO_LIVE account requires confirmLive=true"));

        assertThatThrownBy(() ->
                service.complete(new CompleteSaxoOnboardingUseCase.Command(started.state(), "auth-code")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("confirmLive");

        // The unconfirmed flag was faithfully threaded through to createFromOAuth.
        ArgumentCaptor<CreateBrokerAccountCommand.OAuthAccountCreation> captor =
                ArgumentCaptor.forClass(CreateBrokerAccountCommand.OAuthAccountCreation.class);
        verify(createBrokerAccount).createFromOAuth(captor.capture());
        assertThat(captor.getValue().brokerType()).isEqualTo(BrokerType.SAXO_LIVE);
        assertThat(captor.getValue().confirmLive()).isFalse();
    }

    @Test
    void completeSaxoLiveWithConfirmationCreatesAccount() {
        StartSaxoOnboardingUseCase.Command liveConfirmed = new StartSaxoOnboardingUseCase.Command(
                OWNER, BrokerType.SAXO_LIVE, SaxoEnvironment.LIVE, REDIRECT_URI, true);
        OnboardingStart started = service.start(liveConfirmed);

        OAuthTokens tokens = new OAuthTokens(
                "access-tok", "refresh-tok", Instant.now().plusSeconds(1200), SaxoEnvironment.LIVE);
        when(oauthClient.exchangeAuthorizationCode("auth-code", REDIRECT_URI, SaxoEnvironment.LIVE))
                .thenReturn(tokens);
        when(createBrokerAccount.createFromOAuth(any())).thenAnswer(inv -> {
            CreateBrokerAccountCommand.OAuthAccountCreation req = inv.getArgument(0);
            return BrokerAccount.createReal(
                    req.userId(), req.name(), req.brokerType(), req.credentials(),
                    req.balance(), req.equity());
        });

        BrokerAccount account = service.complete(
                new CompleteSaxoOnboardingUseCase.Command(started.state(), "auth-code"));

        assertThat(account.getBrokerType()).isEqualTo(BrokerType.SAXO_LIVE);
        ArgumentCaptor<CreateBrokerAccountCommand.OAuthAccountCreation> captor =
                ArgumentCaptor.forClass(CreateBrokerAccountCommand.OAuthAccountCreation.class);
        verify(createBrokerAccount).createFromOAuth(captor.capture());
        assertThat(captor.getValue().confirmLive()).isTrue();
    }

    // --- token-exchange failure ---------------------------------------------------------------

    @Test
    void completePropagatesTokenExchangeRejectionAndDoesNotCreateAccount() {
        OnboardingStart started = service.start(simStartCommand());

        when(oauthClient.exchangeAuthorizationCode(eq("bad-code"), eq(REDIRECT_URI), eq(SaxoEnvironment.SIM)))
                .thenThrow(new IllegalArgumentException("Saxo authorization-code exchange rejected (status=400)"));

        assertThatThrownBy(() ->
                service.complete(new CompleteSaxoOnboardingUseCase.Command(started.state(), "bad-code")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("rejected");

        verify(createBrokerAccount, never()).createFromOAuth(any());
        // The state is still single-use: it was consumed even though the exchange failed.
        assertThat(stateStore.store).doesNotContainKey(started.state());
    }

    // --- start input validation ---------------------------------------------------------------

    @Test
    void startRejectsNonSaxoBrokerType() {
        assertThatThrownBy(() -> new StartSaxoOnboardingUseCase.Command(
                OWNER, BrokerType.ALPACA_PAPER, SaxoEnvironment.SIM, REDIRECT_URI, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Saxo");
    }

    // --- helpers ------------------------------------------------------------------------------

    private static StartSaxoOnboardingUseCase.Command simStartCommand() {
        return new StartSaxoOnboardingUseCase.Command(
                OWNER, BrokerType.SAXO_SIM, SaxoEnvironment.SIM, REDIRECT_URI, false);
    }

    /** In-memory single-use state store: {@link #takeOnce} removes the entry (GETDEL semantics). */
    private static final class InMemoryStateStore implements OAuthStateStorePort {
        private final Map<String, String> store = new HashMap<>();
        private int puts;
        private Duration lastTtl;

        @Override
        public void put(String state, String value, Duration ttl) {
            store.put(state, value);
            puts++;
            lastTtl = ttl;
        }

        @Override
        public Optional<String> takeOnce(String state) {
            return Optional.ofNullable(store.remove(state));
        }
    }
}
