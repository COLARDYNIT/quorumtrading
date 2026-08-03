package com.quorumtrading.application.service;

import com.quorumtrading.application.exception.RefreshChainBrokenException;
import com.quorumtrading.application.port.out.BrokerAccountRepository;
import com.quorumtrading.application.port.out.BrokerTokenLeasePort;
import com.quorumtrading.application.port.out.BrokerTokenLeasePort.RefreshLease;
import com.quorumtrading.application.port.out.OAuthTokens;
import com.quorumtrading.application.port.out.SaxoOAuthClientPort;
import com.quorumtrading.application.usecase.command.DispatchNotificationCommand;
import com.quorumtrading.application.usecase.notification.DispatchNotificationUseCase;
import com.quorumtrading.model.entity.BrokerAccount;
import com.quorumtrading.model.enums.AccountStatus;
import com.quorumtrading.model.enums.BrokerType;
import com.quorumtrading.model.enums.NotificationType;
import com.quorumtrading.model.enums.SaxoEnvironment;
import com.quorumtrading.model.valueobject.BrokerAccountId;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.SaxoOAuthCredentials;
import com.quorumtrading.model.valueobject.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for {@link BrokerTokenRefreshService} — the lease-protected, proactive Saxo OAuth
 * token provider.
 *
 * <p>Covers the five WP cases: fresh→cached (no network), near-expiry→lease+refresh+persist+release,
 * lease-held→no refresh, broken-chain→markNeedsReauth + BROKER_REAUTH_REQUIRED notification +
 * {@link RefreshChainBrokenException}, and batch isolation (one broken chain does not abort the
 * batch).</p>
 */
class BrokerTokenRefreshServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-04T12:00:00Z");
    private static final Duration SKEW = Duration.ofMinutes(5);
    private static final Duration LEASE = Duration.ofSeconds(30);
    private static final String HOLDER = "qt-test-instance";

    private BrokerAccountRepository repo;
    private SaxoOAuthClientPort oauthClient;
    private BrokerTokenLeasePort leasePort;
    private DispatchNotificationUseCase notifications;
    private BrokerTokenRefreshService service;

    @BeforeEach
    void setUp() {
        repo = mock(BrokerAccountRepository.class);
        oauthClient = mock(SaxoOAuthClientPort.class);
        leasePort = mock(BrokerTokenLeasePort.class);
        notifications = mock(DispatchNotificationUseCase.class);
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        service = new BrokerTokenRefreshService(
                repo, oauthClient, leasePort, notifications, HOLDER, SKEW, LEASE, clock);
    }

    // ---- Test fixtures ----

    private SaxoOAuthCredentials credentials(String access, Instant expiry, String refresh) {
        return new SaxoOAuthCredentials(
                "app-key", "app-secret", refresh, access, expiry, SaxoEnvironment.SIM);
    }

    private BrokerAccount saxoAccount(SaxoOAuthCredentials creds) {
        return BrokerAccount.createReal(
                UserId.generate(),
                "My Saxo",
                BrokerType.SAXO_SIM,
                creds,
                Money.of(java.math.BigDecimal.valueOf(10_000), Money.Currency.EUR),
                Money.of(java.math.BigDecimal.valueOf(10_000), Money.Currency.EUR));
    }

    // ---- 1. Fresh: cached token returned, no lease, no refresh ----

    @Test
    void freshTokenIsReturnedFromCacheWithoutRefreshing() {
        SaxoOAuthCredentials fresh = credentials(
                "fresh-access", NOW.plus(Duration.ofMinutes(19)), "refresh-1");
        BrokerAccount account = saxoAccount(fresh);
        when(repo.findById(account.getId())).thenReturn(Optional.of(account));

        String token = service.getValidAccessToken(account.getId());

        assertThat(token).isEqualTo("fresh-access");
        verifyNoInteractions(oauthClient);
        verifyNoInteractions(leasePort);
        verifyNoInteractions(notifications);
        verify(repo, never()).save(any());
    }

    // ---- 2. Near-expiry: lease acquired, refresh called, rotated tokens persisted, lease released ----

    @Test
    void nearExpiryAcquiresLeaseRefreshesPersistsRotatedTokensAndReleases() {
        // Access token expires within the 5-min skew window -> must refresh.
        SaxoOAuthCredentials stale = credentials(
                "old-access", NOW.plus(Duration.ofMinutes(2)), "refresh-old");
        BrokerAccount account = saxoAccount(stale);
        BrokerAccountId id = account.getId();
        when(repo.findById(id)).thenReturn(Optional.of(account));

        RefreshLease lease = new RefreshLease(id, HOLDER);
        when(leasePort.acquire(eq(id), eq(HOLDER), eq(LEASE))).thenReturn(Optional.of(lease));

        Instant newExpiry = NOW.plus(Duration.ofMinutes(20));
        when(oauthClient.refreshAccessToken("refresh-old", SaxoEnvironment.SIM))
                .thenReturn(new OAuthTokens("new-access", "refresh-new", newExpiry, SaxoEnvironment.SIM));

        String token = service.getValidAccessToken(id);

        // New access token returned.
        assertThat(token).isEqualTo("new-access");

        // Rotated tokens were applied to the account and persisted in the same save.
        ArgumentCaptor<BrokerAccount> saved = ArgumentCaptor.forClass(BrokerAccount.class);
        verify(repo, times(1)).save(saved.capture());
        SaxoOAuthCredentials persisted = (SaxoOAuthCredentials) saved.getValue().getCredentials();
        assertThat(persisted.accessToken()).isEqualTo("new-access");
        assertThat(persisted.refreshToken()).isEqualTo("refresh-new");
        assertThat(persisted.accessTokenExpiry()).isEqualTo(newExpiry);

        // Lease acquired then released; no reauth notification on the happy path.
        verify(leasePort).acquire(id, HOLDER, LEASE);
        verify(leasePort).release(lease);
        verifyNoInteractions(notifications);
    }

    // ---- 3. Lease held by another instance: do not refresh ----

    @Test
    void leaseHeldByPeerDoesNotRefreshAndSignalsRetryWhenCachedTokenUnusable() {
        SaxoOAuthCredentials stale = credentials(
                "old-access", NOW.plus(Duration.ofMinutes(1)), "refresh-old");
        BrokerAccount account = saxoAccount(stale);
        BrokerAccountId id = account.getId();
        when(repo.findById(id)).thenReturn(Optional.of(account));

        // Peer owns the lease — acquire fails.
        when(leasePort.acquire(eq(id), eq(HOLDER), eq(LEASE))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getValidAccessToken(id))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("in progress");

        // We must NOT have refreshed or persisted anything.
        verifyNoInteractions(oauthClient);
        verify(repo, never()).save(any());
        verify(leasePort, never()).release(any());
        verifyNoInteractions(notifications);
    }

    @Test
    void leaseHeldByPeerReturnsPeerRefreshedTokenWhenAlreadyFresh() {
        // First read: stale (triggers refresh attempt). Second read (after lease miss): a peer has
        // already rotated to a fresh token, so we reuse it instead of failing.
        SaxoOAuthCredentials stale = credentials(
                "old-access", NOW.plus(Duration.ofMinutes(1)), "refresh-old");
        BrokerAccount staleAccount = saxoAccount(stale);
        BrokerAccountId id = staleAccount.getId();

        SaxoOAuthCredentials peerFresh = credentials(
                "peer-access", NOW.plus(Duration.ofMinutes(19)), "refresh-peer");
        BrokerAccount freshAccount = mock(BrokerAccount.class);
        when(freshAccount.getId()).thenReturn(id);
        when(freshAccount.getBrokerType()).thenReturn(BrokerType.SAXO_SIM);
        when(freshAccount.getCredentials()).thenReturn(peerFresh);

        when(repo.findById(id))
                .thenReturn(Optional.of(staleAccount))   // initial read -> stale
                .thenReturn(Optional.of(freshAccount));   // re-read after lease miss -> peer-fresh

        when(leasePort.acquire(eq(id), eq(HOLDER), eq(LEASE))).thenReturn(Optional.empty());

        String token = service.getValidAccessToken(id);

        assertThat(token).isEqualTo("peer-access");
        verifyNoInteractions(oauthClient);
        verify(repo, never()).save(any());
    }

    // ---- 4. Broken chain: markNeedsReauth + BROKER_REAUTH_REQUIRED + RefreshChainBrokenException ----

    @Test
    void brokenChainMarksReauthNotifiesAndThrows() {
        SaxoOAuthCredentials stale = credentials(
                "old-access", NOW.plus(Duration.ofMinutes(1)), "refresh-dead");
        BrokerAccount account = saxoAccount(stale);
        BrokerAccountId id = account.getId();
        when(repo.findById(id)).thenReturn(Optional.of(account));

        RefreshLease lease = new RefreshLease(id, HOLDER);
        when(leasePort.acquire(eq(id), eq(HOLDER), eq(LEASE))).thenReturn(Optional.of(lease));

        // Saxo rejects the refresh token (already rotated / expired / revoked).
        when(oauthClient.refreshAccessToken("refresh-dead", SaxoEnvironment.SIM))
                .thenThrow(new IllegalArgumentException("Saxo refresh-token grant rejected (status=401)"));

        assertThatThrownBy(() -> service.getValidAccessToken(id))
                .isInstanceOf(RefreshChainBrokenException.class)
                .hasMessageContaining(id.toString());

        // Account flipped to NEEDS_REAUTH and persisted.
        assertThat(account.getStatus()).isEqualTo(AccountStatus.NEEDS_REAUTH);
        verify(repo, times(1)).save(account);

        // BROKER_REAUTH_REQUIRED notification dispatched to the owner.
        ArgumentCaptor<DispatchNotificationCommand> cmd =
                ArgumentCaptor.forClass(DispatchNotificationCommand.class);
        verify(notifications).execute(cmd.capture());
        assertThat(cmd.getValue().type()).isEqualTo(NotificationType.BROKER_REAUTH_REQUIRED);
        assertThat(cmd.getValue().userId()).isEqualTo(account.getUserId());
        assertThat(cmd.getValue().relatedEntityId()).isEqualTo(id.value());

        // Lease still released even on the failure path.
        verify(leasePort).release(lease);
    }

    // ---- 5. Batch isolation: one broken chain does not abort the batch ----

    @Test
    void refreshDueAccountsIsolatesFailuresAcrossAccounts() {
        // Account A: near-expiry, refresh succeeds.
        SaxoOAuthCredentials aStale = credentials(
                "a-old", NOW.plus(Duration.ofMinutes(2)), "a-refresh");
        BrokerAccount a = saxoAccount(aStale);

        // Account B: near-expiry, refresh fails (broken chain) — must NOT abort the batch.
        SaxoOAuthCredentials bStale = credentials(
                "b-old", NOW.plus(Duration.ofMinutes(2)), "b-refresh-dead");
        BrokerAccount b = saxoAccount(bStale);

        // Account C: still fresh — skipped, never touched.
        SaxoOAuthCredentials cFresh = credentials(
                "c-fresh", NOW.plus(Duration.ofMinutes(18)), "c-refresh");
        BrokerAccount c = saxoAccount(cFresh);

        when(repo.findAllByStatus(AccountStatus.ACTIVE)).thenReturn(List.of(a, b, c));
        when(repo.findById(a.getId())).thenReturn(Optional.of(a));
        when(repo.findById(b.getId())).thenReturn(Optional.of(b));

        when(leasePort.acquire(eq(a.getId()), eq(HOLDER), eq(LEASE)))
                .thenReturn(Optional.of(new RefreshLease(a.getId(), HOLDER)));
        when(leasePort.acquire(eq(b.getId()), eq(HOLDER), eq(LEASE)))
                .thenReturn(Optional.of(new RefreshLease(b.getId(), HOLDER)));

        when(oauthClient.refreshAccessToken("a-refresh", SaxoEnvironment.SIM))
                .thenReturn(new OAuthTokens(
                        "a-new", "a-refresh-new", NOW.plus(Duration.ofMinutes(20)), SaxoEnvironment.SIM));
        when(oauthClient.refreshAccessToken("b-refresh-dead", SaxoEnvironment.SIM))
                .thenThrow(new IllegalArgumentException("rejected"));

        BrokerTokenRefreshService.RefreshBatchResult result = service.refreshDueAccounts();

        // A refreshed, B broke (reauth), C skipped-fresh — batch completed all three.
        assertThat(result.refreshed()).isEqualTo(1);
        assertThat(result.reauth()).isEqualTo(1);
        assertThat(result.skippedFresh()).isEqualTo(1);
        assertThat(result.failed()).isEqualTo(0);
        assertThat(result.total()).isEqualTo(3);

        // A got a fresh token persisted; B was flipped to NEEDS_REAUTH and notified; C untouched.
        assertThat(a.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(((SaxoOAuthCredentials) a.getCredentials()).accessToken()).isEqualTo("a-new");
        assertThat(b.getStatus()).isEqualTo(AccountStatus.NEEDS_REAUTH);
        assertThat(c.getStatus()).isEqualTo(AccountStatus.ACTIVE);

        verify(notifications, times(1)).execute(any());
        verify(repo, never()).findById(c.getId());
    }
}
