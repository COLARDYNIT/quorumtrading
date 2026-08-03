package com.quorumtrading.adapter.persistence.repository;

import com.quorumtrading.adapter.persistence.entity.BrokerAccountJpaEntity;
import com.quorumtrading.application.port.out.BrokerAccountRepository;
import com.quorumtrading.application.port.out.UserRepository;
import com.quorumtrading.model.entity.BrokerAccount;
import com.quorumtrading.model.entity.User;
import com.quorumtrading.model.enums.AccountStatus;
import com.quorumtrading.model.enums.BrokerType;
import com.quorumtrading.model.enums.OAuthProvider;
import com.quorumtrading.model.enums.SaxoEnvironment;
import com.quorumtrading.model.valueobject.ApiKeyCredentials;
import com.quorumtrading.model.valueobject.BrokerAccountId;
import com.quorumtrading.model.valueobject.BrokerCredentials;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.SaxoOAuthCredentials;
import com.quorumtrading.model.valueobject.UserId;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Persistence round-trip for the V117 Saxo / OAuth columns mapped by PB3-jpa-columns.
 *
 * <p>Covers the end-to-end path BrokerAccount (with {@link SaxoOAuthCredentials}) →
 * {@code BrokerAccountMapper.toJpaEntity} → encrypt → store → reload → decrypt →
 * {@code toDomain}, asserting that:</p>
 * <ul>
 *   <li>the credential payload (app key/secret, rotating refresh token, access token,
 *       expiry, environment) survives encryption and decryption;</li>
 *   <li>the queryable projection columns {@code broker_environment} and
 *       {@code access_token_expires_at} are populated from the credentials so the refresh
 *       scheduler can probe them without decrypting;</li>
 *   <li>an update branch refreshes the projection columns from rotated credentials while
 *       <strong>preserving</strong> the lease columns ({@code refresh_generation},
 *       {@code refresh_locked_by}, {@code refresh_locked_at}) owned by OA3;</li>
 *   <li>a legacy / Alpaca {@link ApiKeyCredentials} account still round-trips with all the
 *       Saxo projection columns left NULL.</li>
 * </ul>
 */
@QuarkusTest
class BrokerAccountSaxoRoundTripIntegrationTest {

    @Inject
    BrokerAccountRepository repository;

    @Inject
    UserRepository userRepository;

    @Inject
    EntityManager entityManager;

    private UserId createUser() {
        UserId userId = UserId.generate();
        User user = User.create(
            userId,
            "saxo-roundtrip-" + UUID.randomUUID() + "@example.com",
            "Saxo Round Trip",
            null,
            OAuthProvider.GOOGLE);
        userRepository.save(user);
        return userId;
    }

    @Test
    void saxoOAuthAccountRoundTripsCredentialsAndProjectionColumns() {
        UserId userId = createUser();
        Instant expiry = Instant.parse("2026-06-04T12:34:56Z");
        SaxoOAuthCredentials creds = new SaxoOAuthCredentials(
            "saxo-app-key",
            "saxo-app-secret",
            "refresh-token-rotating-abc",
            "access-token-short-lived-xyz",
            expiry,
            SaxoEnvironment.LIVE);

        // EUR settlement balance — exercises the EUR currency that Saxo accounts settle in.
        Money balance = new Money(new BigDecimal("5000.00"), Money.Currency.EUR);
        BrokerAccount account = BrokerAccount.createReal(
            userId, "My Saxo EU", BrokerType.SAXO_LIVE, creds, balance, balance);

        BrokerAccount saved = repository.save(account);
        BrokerAccountId id = saved.getId();

        // Reload through the repository: credentials decrypt back to the same value object.
        BrokerAccount reloaded = repository.findById(id).orElseThrow();
        assertThat(reloaded.getBrokerType()).isEqualTo(BrokerType.SAXO_LIVE);
        assertThat(reloaded.getTotalBalance().currency()).isEqualTo(Money.Currency.EUR);
        assertThat(reloaded.getCredentials()).isInstanceOf(SaxoOAuthCredentials.class);
        SaxoOAuthCredentials reloadedCreds = (SaxoOAuthCredentials) reloaded.getCredentials();
        assertThat(reloadedCreds.appKey()).isEqualTo("saxo-app-key");
        assertThat(reloadedCreds.appSecret()).isEqualTo("saxo-app-secret");
        assertThat(reloadedCreds.refreshToken()).isEqualTo("refresh-token-rotating-abc");
        assertThat(reloadedCreds.accessToken()).isEqualTo("access-token-short-lived-xyz");
        assertThat(reloadedCreds.accessTokenExpiry()).isEqualTo(expiry);
        assertThat(reloadedCreds.environment()).isEqualTo(SaxoEnvironment.LIVE);

        // Inspect the raw JPA columns: environment + expiry are projected for queryability.
        BrokerAccountJpaEntity entity = loadEntity(id.value());
        assertThat(entity.getBrokerEnvironment()).isEqualTo("LIVE");
        assertThat(entity.getAccessTokenExpiresAt()).isEqualTo(expiry);
        // Lease columns are unwritten by the domain mapper — they default to NULL on insert.
        assertThat(entity.getRefreshGeneration()).isNull();
        assertThat(entity.getRefreshLockedBy()).isNull();
        assertThat(entity.getRefreshLockedAt()).isNull();
    }

    @Test
    void updateRefreshesProjectionColumnsAndPreservesLeaseColumns() {
        UserId userId = createUser();
        Instant firstExpiry = Instant.parse("2026-06-04T10:00:00Z");
        SaxoOAuthCredentials creds = new SaxoOAuthCredentials(
            "app-key", "app-secret", "refresh-1", "access-1", firstExpiry, SaxoEnvironment.SIM);
        Money balance = new Money(new BigDecimal("1000.00"), Money.Currency.EUR);
        BrokerAccount account = BrokerAccount.createReal(
            userId, "Saxo SIM", BrokerType.SAXO_SIM, creds, balance, balance);

        BrokerAccount saved = repository.save(account);
        BrokerAccountId id = saved.getId();

        // Simulate the lease adapter (OA3) writing a held lease + a bumped generation directly
        // on the row, as its atomic UPDATE-WHERE would.
        seedLease(id.value(), "ecs-task-7", 3L);

        // Rotate the OAuth tokens and persist through the domain repository update branch.
        Instant secondExpiry = Instant.parse("2026-06-04T10:20:00Z");
        SaxoOAuthCredentials rotated = ((SaxoOAuthCredentials) saved.getCredentials())
            .withRotatedTokens("refresh-2", "access-2", secondExpiry);
        BrokerAccount toUpdate = repository.findById(id).orElseThrow();
        toUpdate.applyRefreshedTokens(rotated);
        repository.save(toUpdate);

        // Projection columns track the rotation; the OA3-owned lease columns are untouched.
        BrokerAccountJpaEntity entity = loadEntity(id.value());
        assertThat(entity.getBrokerEnvironment()).isEqualTo("SIM");
        assertThat(entity.getAccessTokenExpiresAt()).isEqualTo(secondExpiry);
        assertThat(entity.getRefreshGeneration()).isEqualTo(3L);
        assertThat(entity.getRefreshLockedBy()).isEqualTo("ecs-task-7");
        assertThat(entity.getRefreshLockedAt()).isNotNull();

        // And the rotated refresh token survives reload + decrypt.
        BrokerAccount reloaded = repository.findById(id).orElseThrow();
        SaxoOAuthCredentials reloadedCreds = (SaxoOAuthCredentials) reloaded.getCredentials();
        assertThat(reloadedCreds.refreshToken()).isEqualTo("refresh-2");
        assertThat(reloadedCreds.accessToken()).isEqualTo("access-2");
        assertThat(reloadedCreds.accessTokenExpiry()).isEqualTo(secondExpiry);
        assertThat(reloaded.getStatus()).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    void alpacaApiKeyAccountRoundTripsWithNullSaxoColumns() {
        UserId userId = createUser();
        BrokerCredentials creds = new ApiKeyCredentials("PKTEST123", "alpacaSecret456");
        Money balance = Money.usd(new BigDecimal("25000.00"));
        BrokerAccount account = BrokerAccount.createReal(
            userId, "My Alpaca", BrokerType.ALPACA_PAPER, creds, balance, balance);

        BrokerAccount saved = repository.save(account);
        BrokerAccountId id = saved.getId();

        BrokerAccount reloaded = repository.findById(id).orElseThrow();
        assertThat(reloaded.getBrokerType()).isEqualTo(BrokerType.ALPACA_PAPER);
        assertThat(reloaded.getCredentials()).isInstanceOf(ApiKeyCredentials.class);
        ApiKeyCredentials reloadedCreds = (ApiKeyCredentials) reloaded.getCredentials();
        assertThat(reloadedCreds.apiKey()).isEqualTo("PKTEST123");
        assertThat(reloadedCreds.apiSecret()).isEqualTo("alpacaSecret456");

        // No Saxo OAuth projection: all five V117 columns stay NULL for an Alpaca row.
        BrokerAccountJpaEntity entity = loadEntity(id.value());
        assertThat(entity.getBrokerEnvironment()).isNull();
        assertThat(entity.getAccessTokenExpiresAt()).isNull();
        assertThat(entity.getRefreshGeneration()).isNull();
        assertThat(entity.getRefreshLockedBy()).isNull();
        assertThat(entity.getRefreshLockedAt()).isNull();
    }

    /**
     * Loads the raw JPA row in a fresh transaction so all eager scalar columns are populated,
     * then returns the (now detached) entity for column-level assertions. Reading after the
     * transaction commits is safe because every column inspected here is an eager scalar.
     */
    private BrokerAccountJpaEntity loadEntity(UUID accountId) {
        return QuarkusTransaction.requiringNew().call(() ->
            entityManager.find(BrokerAccountJpaEntity.class, accountId));
    }

    /**
     * Writes the lease columns directly on the row, standing in for the OA3 token-lease
     * adapter's atomic {@code UPDATE ... WHERE}. Runs in its own committed transaction so the
     * subsequent domain update operates against an already-leased row.
     */
    void seedLease(UUID accountId, String holder, long generation) {
        QuarkusTransaction.requiringNew().run(() -> {
            BrokerAccountJpaEntity entity = entityManager.find(BrokerAccountJpaEntity.class, accountId);
            entity.setRefreshLockedBy(holder);
            entity.setRefreshLockedAt(Instant.now().truncatedTo(ChronoUnit.MILLIS));
            entity.setRefreshGeneration(generation);
            entityManager.merge(entity);
        });
    }
}
