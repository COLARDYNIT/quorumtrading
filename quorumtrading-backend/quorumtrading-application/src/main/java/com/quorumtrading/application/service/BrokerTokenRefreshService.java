package com.quorumtrading.application.service;

import com.quorumtrading.application.exception.RefreshChainBrokenException;
import com.quorumtrading.application.port.out.BrokerAccountRepository;
import com.quorumtrading.application.port.out.BrokerTokenLeasePort;
import com.quorumtrading.application.port.out.BrokerTokenLeasePort.RefreshLease;
import com.quorumtrading.application.port.out.BrokerTokenProviderPort;
import com.quorumtrading.application.port.out.OAuthTokens;
import com.quorumtrading.application.port.out.SaxoOAuthClientPort;
import com.quorumtrading.application.usecase.command.DispatchNotificationCommand;
import com.quorumtrading.application.usecase.notification.DispatchNotificationUseCase;
import com.quorumtrading.model.entity.BrokerAccount;
import com.quorumtrading.model.enums.AccountStatus;
import com.quorumtrading.model.enums.NotificationType;
import com.quorumtrading.model.valueobject.BrokerAccountId;
import com.quorumtrading.model.valueobject.SaxoOAuthCredentials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Lease-protected, proactive Saxo OAuth token provider.
 *
 * <p>Implements {@link BrokerTokenProviderPort}: the single seam every Saxo adapter calls before
 * an authenticated request. It returns a cached access token while it is still fresh, and when the
 * token is near expiry it performs a <b>single-flight, lease-protected</b> refresh so two ECS tasks
 * never rotate the same account's refresh token concurrently (Saxo rotates the refresh token on
 * every use — a double-refresh breaks the chain).</p>
 *
 * <p>The refresh flow is:</p>
 * <ol>
 *   <li>token still fresh → return the cached access token (no I/O);</li>
 *   <li>token near expiry → acquire the per-account refresh lease ({@link BrokerTokenLeasePort});</li>
 *   <li>lease acquired → re-read the account (a peer may have just refreshed it), then call
 *       {@link SaxoOAuthClientPort#refreshAccessToken}, apply the <em>rotated</em> tokens to the
 *       account ({@link BrokerAccount#applyRefreshedTokens}) and persist them <b>in the same save</b>
 *       as the new access-token expiry, then release the lease and return the new token;</li>
 *   <li>lease held by another instance → do <b>not</b> refresh; if the cached token is still usable
 *       return it, otherwise signal a transient "refresh in progress" so the caller can retry;</li>
 *   <li>refresh rejected by Saxo (the stored refresh token was already rotated / expired / revoked)
 *       → {@link BrokerAccount#markNeedsReauth}, persist, dispatch a {@code BROKER_REAUTH_REQUIRED}
 *       notification, and throw {@link RefreshChainBrokenException}.</li>
 * </ol>
 *
 * <p>Pure Java application service — wired as a POJO in {@code ApplicationConfig}, not
 * {@code @ApplicationScoped} (ArchUnit enforces this). All persistence transactionality is owned by
 * the repository/lease adapters.</p>
 */
public class BrokerTokenRefreshService implements BrokerTokenProviderPort {

    private static final Logger logger = LoggerFactory.getLogger(BrokerTokenRefreshService.class);

    /** Default safety margin: refresh once the access token is within this window of expiry. */
    static final Duration DEFAULT_REFRESH_SKEW = Duration.ofMinutes(5);
    /** Default lease validity; a crashed holder's lease may be force-released after this. */
    static final Duration DEFAULT_LEASE_DURATION = Duration.ofSeconds(30);

    private final BrokerAccountRepository brokerAccountRepository;
    private final SaxoOAuthClientPort oauthClient;
    private final BrokerTokenLeasePort leasePort;
    private final DispatchNotificationUseCase notificationDispatcher;
    private final Duration refreshSkew;
    private final Duration leaseDuration;
    private final String holderId;
    private final Clock clock;

    public BrokerTokenRefreshService(
            BrokerAccountRepository brokerAccountRepository,
            SaxoOAuthClientPort oauthClient,
            BrokerTokenLeasePort leasePort,
            DispatchNotificationUseCase notificationDispatcher,
            String holderId) {
        this(brokerAccountRepository, oauthClient, leasePort, notificationDispatcher,
                holderId, DEFAULT_REFRESH_SKEW, DEFAULT_LEASE_DURATION, Clock.systemUTC());
    }

    /** Full constructor — visible for tests to pin the skew, lease window, holder, and clock. */
    BrokerTokenRefreshService(
            BrokerAccountRepository brokerAccountRepository,
            SaxoOAuthClientPort oauthClient,
            BrokerTokenLeasePort leasePort,
            DispatchNotificationUseCase notificationDispatcher,
            String holderId,
            Duration refreshSkew,
            Duration leaseDuration,
            Clock clock) {
        this.brokerAccountRepository = Objects.requireNonNull(brokerAccountRepository, "brokerAccountRepository");
        this.oauthClient = Objects.requireNonNull(oauthClient, "oauthClient");
        this.leasePort = Objects.requireNonNull(leasePort, "leasePort");
        this.notificationDispatcher = Objects.requireNonNull(notificationDispatcher, "notificationDispatcher");
        if (holderId == null || holderId.isBlank()) {
            throw new IllegalArgumentException("holderId cannot be null or blank");
        }
        this.holderId = holderId;
        this.refreshSkew = Objects.requireNonNull(refreshSkew, "refreshSkew");
        this.leaseDuration = Objects.requireNonNull(leaseDuration, "leaseDuration");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public String getValidAccessToken(BrokerAccountId brokerAccountId) {
        Objects.requireNonNull(brokerAccountId, "brokerAccountId cannot be null");

        BrokerAccount account = brokerAccountRepository.findById(brokerAccountId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Broker account not found: " + brokerAccountId));

        SaxoOAuthCredentials credentials = oauthCredentialsOf(account);

        Instant now = clock.instant();
        if (!credentials.isAccessTokenExpired(now, refreshSkew)) {
            // Still fresh — return the cached access token, no network call.
            return credentials.accessToken();
        }

        return refreshAndReturn(account, credentials);
    }

    /**
     * Single-flight refresh: acquire the lease, refresh under it, persist the rotated tokens, and
     * return the fresh access token. If the lease is held by a peer we do not refresh.
     */
    private String refreshAndReturn(BrokerAccount account, SaxoOAuthCredentials credentials) {
        BrokerAccountId accountId = account.getId();

        Optional<RefreshLease> lease = leasePort.acquire(accountId, holderId, leaseDuration);
        if (lease.isEmpty()) {
            // Another instance owns the lease and is refreshing. Do NOT refresh.
            // Re-read in case the peer already completed and persisted a fresh token.
            BrokerAccount reread = brokerAccountRepository.findById(accountId).orElse(account);
            SaxoOAuthCredentials latest = oauthCredentialsOf(reread);
            if (!latest.isAccessTokenExpired(clock.instant(), refreshSkew)) {
                logger.debug("Refresh lease held by peer for account {}; using token a peer already rotated",
                        accountId);
                return latest.accessToken();
            }
            // Peer still in flight and our cached token is unusable — signal a transient retry.
            logger.info("Refresh lease held by another instance for account {}; refresh in progress, retry shortly",
                    accountId);
            throw new IllegalStateException(
                    "Token refresh for account " + accountId + " is in progress on another instance; retry shortly");
        }

        RefreshLease held = lease.get();
        try {
            // Re-read under the lease: a peer may have refreshed in the window between our expiry
            // check and acquiring the lease. If so, we must spend the LATEST refresh token, not a
            // stale one, and we can skip the call entirely if it is already fresh.
            BrokerAccount current = brokerAccountRepository.findById(accountId).orElse(account);
            SaxoOAuthCredentials latest = oauthCredentialsOf(current);
            if (!latest.isAccessTokenExpired(clock.instant(), refreshSkew)) {
                logger.debug("Account {} was refreshed by a peer before we held the lease; reusing fresh token",
                        accountId);
                return latest.accessToken();
            }
            return doRefresh(current, latest);
        } finally {
            leasePort.release(held);
        }
    }

    /**
     * Performs the actual token-endpoint refresh and persists the rotated tokens. On a rejected
     * refresh (broken chain) marks the account for re-auth, notifies the owner, and rethrows as
     * {@link RefreshChainBrokenException}.
     */
    private String doRefresh(BrokerAccount account, SaxoOAuthCredentials credentials) {
        BrokerAccountId accountId = account.getId();
        try {
            OAuthTokens rotated = oauthClient.refreshAccessToken(
                    credentials.refreshToken(), credentials.environment());

            SaxoOAuthCredentials rotatedCredentials = credentials.withRotatedTokens(
                    rotated.refreshToken(), rotated.accessToken(), rotated.expiry());

            // Apply + persist the rotated refresh token IN THE SAME SAVE as the new expiry, so a
            // crash between the network call and the save can never leave a half-rotated chain.
            account.applyRefreshedTokens(rotatedCredentials);
            brokerAccountRepository.save(account);

            logger.info("Refreshed Saxo access token for account {} (new expiry={})",
                    accountId, rotated.expiry());
            return rotated.accessToken();

        } catch (IllegalArgumentException e) {
            // Saxo rejected the refresh token: it was already rotated, expired, or revoked. The
            // chain is irrecoverable unattended — flip to NEEDS_REAUTH, persist, notify, and signal.
            handleBrokenChain(account, e);
            throw new RefreshChainBrokenException(accountId,
                    "Saxo rejected the refresh token; re-authorization required", e);
        }
    }

    private void handleBrokenChain(BrokerAccount account, Exception cause) {
        BrokerAccountId accountId = account.getId();
        logger.warn("Refresh chain broken for account {} (status was {}): {}",
                accountId, account.getStatus(), cause.getMessage());

        // Avoid re-notifying if we already flagged this account; the markNeedsReauth + notify pair
        // should fire on the transition into NEEDS_REAUTH.
        boolean wasAlreadyNeedingReauth = account.getStatus() == AccountStatus.NEEDS_REAUTH;
        account.markNeedsReauth();
        brokerAccountRepository.save(account);

        if (wasAlreadyNeedingReauth) {
            logger.debug("Account {} already in NEEDS_REAUTH; skipping duplicate reauth notification",
                    accountId);
            return;
        }
        dispatchReauthNotification(account);
    }

    private void dispatchReauthNotification(BrokerAccount account) {
        try {
            notificationDispatcher.execute(new DispatchNotificationCommand(
                    account.getUserId(),
                    NotificationType.BROKER_REAUTH_REQUIRED,
                    "Broker re-authorization required",
                    "Your broker connection \"" + account.getName() + "\" needs to be re-authorized. "
                            + "Automatic token renewal failed and trading is paused for this account "
                            + "until you reconnect it.",
                    Map.of("brokerAccountId", account.getId().value().toString()),
                    account.getId().value()));
        } catch (RuntimeException notifyError) {
            // Never let a notification failure mask the broken-chain signal.
            logger.error("Failed to dispatch BROKER_REAUTH_REQUIRED notification for account {}",
                    account.getId(), notifyError);
        }
    }

    /**
     * Proactively refreshes every active Saxo account whose access token is near expiry, isolating
     * each account so one broken chain (or transient failure) does not abort the batch. Intended to
     * be driven by the disabled-by-default refresh scheduler ({@code OA5}).
     *
     * @return a summary of the batch outcome (counts of refreshed / skipped-fresh / reauth / failed)
     */
    public RefreshBatchResult refreshDueAccounts() {
        List<BrokerAccount> active = brokerAccountRepository.findAllByStatus(AccountStatus.ACTIVE);
        int refreshed = 0;
        int skippedFresh = 0;
        int reauth = 0;
        int failed = 0;

        for (BrokerAccount account : active) {
            if (!account.getBrokerType().usesOAuth()
                    || !(account.getCredentials() instanceof SaxoOAuthCredentials creds)) {
                continue; // not an OAuth account — nothing to refresh
            }
            if (!creds.isAccessTokenExpired(clock.instant(), refreshSkew)) {
                skippedFresh++;
                continue; // still fresh — leave it alone
            }
            try {
                getValidAccessToken(account.getId());
                refreshed++;
            } catch (RefreshChainBrokenException e) {
                // Already marked NEEDS_REAUTH + notified inside the refresh path; isolate and continue.
                reauth++;
            } catch (RuntimeException e) {
                // Transient failure (lease contention, network) for this account only — keep going.
                logger.warn("Token refresh failed for account {} during batch; continuing with others",
                        account.getId(), e);
                failed++;
            }
        }

        logger.info("DCA token-refresh batch complete: refreshed={}, skippedFresh={}, reauth={}, failed={}",
                refreshed, skippedFresh, reauth, failed);
        return new RefreshBatchResult(refreshed, skippedFresh, reauth, failed);
    }

    private SaxoOAuthCredentials oauthCredentialsOf(BrokerAccount account) {
        if (!account.getBrokerType().usesOAuth()
                || !(account.getCredentials() instanceof SaxoOAuthCredentials credentials)) {
            throw new IllegalArgumentException(
                    "Broker account " + account.getId() + " is not OAuth-backed (brokerType="
                            + account.getBrokerType() + ")");
        }
        return credentials;
    }

    /**
     * Outcome counts from a {@link #refreshDueAccounts()} batch run.
     *
     * @param refreshed    accounts whose token was successfully rotated
     * @param skippedFresh accounts whose token was still fresh (no refresh needed)
     * @param reauth       accounts whose refresh chain broke (now NEEDS_REAUTH, owner notified)
     * @param failed       accounts that failed transiently (lease contention / network)
     */
    public record RefreshBatchResult(int refreshed, int skippedFresh, int reauth, int failed) {
        public int total() {
            return refreshed + skippedFresh + reauth + failed;
        }
    }
}
