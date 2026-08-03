package com.quorumtrading.model.entity;

import com.quorumtrading.model.enums.AccountStatus;
import com.quorumtrading.model.enums.BrokerType;
import com.quorumtrading.model.valueobject.ApiKeyCredentials;
import com.quorumtrading.model.valueobject.BrokerAccountId;
import com.quorumtrading.model.valueobject.BrokerCredentials;
import com.quorumtrading.model.valueobject.MockBrokerConfig;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.SaxoOAuthCredentials;
import com.quorumtrading.model.valueobject.UserId;

import java.time.Instant;
import java.util.Objects;

/**
 * Domain entity representing a user's connection to a broker (real or mock).
 * Manages balance reservations for simulations.
 *
 * <p><b>Aggregate Root</b> - Controls access to broker account state and balance reservations.</p>
 *
 * <p><b>Broker Types:</b></p>
 * <ul>
 *   <li><b>MOCK:</b> Simulated broker for backtesting with configurable costs</li>
 *   <li><b>ALPACA_PAPER:</b> Alpaca paper trading account</li>
 *   <li><b>ALPACA_LIVE:</b> Alpaca live trading account</li>
 * </ul>
 *
 * <p><b>Reserved Capital Model:</b></p>
 * <ul>
 *   <li>Each simulation specifies a trading limit</li>
 *   <li>On simulation start, that amount is reserved from available balance</li>
 *   <li>Simulations can only trade within their reserved allocation</li>
 *   <li>On simulation end, reservation is released back to available balance</li>
 * </ul>
 *
 * <p><b>Invariants Enforced:</b></p>
 * <ul>
 *   <li>Real brokers (ALPACA_*, SAXO_*) require credentials; MOCK does not</li>
 *   <li>Credential family must match the broker: ALPACA_* require {@code ApiKeyCredentials},
 *       SAXO_* require {@code SaxoOAuthCredentials}; the family cannot be swapped on update</li>
 *   <li>Cannot reserve more than available balance</li>
 *   <li>Cannot release more than reserved balance</li>
 *   <li>MockConfig is required for MOCK type, null for real brokers</li>
 * </ul>
 *
 * @see BrokerType
 * @see AccountStatus
 * @see MockBrokerConfig
 */
public class BrokerAccount implements AggregateRoot {

    private final BrokerAccountId id;
    private final UserId userId;
    private String name;
    private final BrokerType brokerType;
    private BrokerCredentials credentials;
    private final MockBrokerConfig mockConfig;
    private final Instant createdAt;

    /**
     * True when this account was created implicitly to back a single backtest, rather than
     * deliberately by the user. An inline account belongs to its simulation: it is hidden from
     * account listings and deleted along with the simulation that created it.
     */
    private final boolean inline;

    private Money totalBalance;
    private Money equity; // Nullable — null for MOCK accounts. For Alpaca: cash + positions market value.
    private Money reservedBalance;
    private AccountStatus status;
    private Instant updatedAt;

    /**
     * Public constructor for persistence layer reconstruction.
     * Use factory methods for creating new accounts.
     *
     * <p>Retained for callers that predate the inline flag; reconstructs a non-inline
     * account.</p>
     */
    public BrokerAccount(
        BrokerAccountId id,
        UserId userId,
        String name,
        BrokerType brokerType,
        BrokerCredentials credentials,
        Money totalBalance,
        Money equity,
        Money reservedBalance,
        AccountStatus status,
        MockBrokerConfig mockConfig,
        Instant createdAt,
        Instant updatedAt
    ) {
        this(id, userId, name, brokerType, credentials, totalBalance, equity, reservedBalance,
            status, mockConfig, createdAt, updatedAt, false);
    }

    /**
     * Public constructor for persistence layer reconstruction, including the inline flag.
     * Use factory methods for creating new accounts.
     */
    public BrokerAccount(
        BrokerAccountId id,
        UserId userId,
        String name,
        BrokerType brokerType,
        BrokerCredentials credentials,
        Money totalBalance,
        Money equity,
        Money reservedBalance,
        AccountStatus status,
        MockBrokerConfig mockConfig,
        Instant createdAt,
        Instant updatedAt,
        boolean inline
    ) {
        this.inline = inline;
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.userId = Objects.requireNonNull(userId, "userId cannot be null");
        this.name = Objects.requireNonNull(name, "name cannot be null");
        this.brokerType = Objects.requireNonNull(brokerType, "brokerType cannot be null");
        this.totalBalance = Objects.requireNonNull(totalBalance, "totalBalance cannot be null");
        this.equity = equity; // Nullable for MOCK accounts
        this.reservedBalance = Objects.requireNonNull(reservedBalance, "reservedBalance cannot be null");
        this.status = Objects.requireNonNull(status, "status cannot be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt cannot be null");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt cannot be null");
        this.credentials = credentials; // Nullable for MOCK
        this.mockConfig = mockConfig; // Nullable for real brokers
    }

    /**
     * Private constructor for factory methods.
     */
    private BrokerAccount(
        UserId userId,
        String name,
        BrokerType brokerType,
        BrokerCredentials credentials,
        Money totalBalance,
        Money equity,
        MockBrokerConfig mockConfig,
        boolean inline
    ) {
        this.inline = inline;
        this.id = BrokerAccountId.generate();
        this.userId = Objects.requireNonNull(userId, "userId cannot be null");
        this.name = Objects.requireNonNull(name, "name cannot be null");
        this.brokerType = Objects.requireNonNull(brokerType, "brokerType cannot be null");
        this.credentials = credentials;
        this.totalBalance = Objects.requireNonNull(totalBalance, "totalBalance cannot be null");
        this.equity = equity; // Nullable for MOCK accounts
        this.reservedBalance = Money.zero(totalBalance.currency());
        this.status = AccountStatus.ACTIVE;
        this.mockConfig = mockConfig;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    /**
     * Creates a mock broker account for backtesting.
     *
     * @param userId the owner of the account
     * @param name display name for the account
     * @param config mock broker configuration with costs and initial capital
     * @return a new mock BrokerAccount in ACTIVE status
     */
    public static BrokerAccount createMock(UserId userId, String name, MockBrokerConfig config) {
        return createMock(userId, name, config, false);
    }

    /**
     * Creates a mock broker account for backtesting, optionally marked as inline.
     *
     * <p>An inline account is created implicitly to back a single backtest rather than
     * deliberately by the user. It is hidden from account listings and deleted with the
     * simulation that created it — otherwise these accumulate indefinitely and outlive the
     * simulations that are their only reason to exist.</p>
     *
     * @param userId the owner of the account
     * @param name display name for the account
     * @param config mock broker configuration with costs and initial capital
     * @param inline whether this account is owned by a single simulation
     * @return a new mock BrokerAccount in ACTIVE status
     */
    public static BrokerAccount createMock(UserId userId, String name, MockBrokerConfig config, boolean inline) {
        Objects.requireNonNull(config, "MockBrokerConfig cannot be null for mock broker");
        return new BrokerAccount(
            userId,
            name,
            BrokerType.MOCK,
            null, // no credentials for mock
            config.initialCapital(),
            null, // no equity for mock
            config,
            inline
        );
    }

    /**
     * Creates a real broker account (Alpaca paper/live or Saxo SIM/LIVE).
     *
     * <p>The credential family must match the broker: ALPACA_* require
     * {@link ApiKeyCredentials}, SAXO_* require {@link SaxoOAuthCredentials}, and MOCK
     * rejects credentials outright (use {@link #createMock} instead).</p>
     *
     * @param userId the owner of the account
     * @param name display name for the account
     * @param brokerType the broker type (must not be MOCK)
     * @param credentials credentials for the broker, of the family the broker requires
     * @param balance current cash balance fetched from the broker
     * @param equity total equity (cash + positions market value), nullable
     * @return a new real BrokerAccount in ACTIVE status
     * @throws IllegalArgumentException if brokerType is MOCK, credentials are null, or the
     *         credential family does not match the broker type
     */
    public static BrokerAccount createReal(
        UserId userId,
        String name,
        BrokerType brokerType,
        BrokerCredentials credentials,
        Money balance,
        Money equity
    ) {
        if (brokerType == BrokerType.MOCK) {
            throw new IllegalArgumentException("Cannot use MOCK broker type for real broker account. Use createMock() instead.");
        }
        if (credentials == null) {
            throw new IllegalArgumentException("Real broker accounts require credentials");
        }
        requireMatchingCredentialFamily(brokerType, credentials);
        return new BrokerAccount(
            userId,
            name,
            brokerType,
            credentials,
            balance,
            equity,
            null, // no mock config for real brokers
            false // a real broker account is always user-owned, never inline
        );
    }

    /**
     * Enforces that the credential family matches the broker type:
     * SAXO_* require {@link SaxoOAuthCredentials}; ALPACA_* require {@link ApiKeyCredentials}.
     * MOCK never reaches here (it carries no credentials).
     *
     * @throws IllegalArgumentException if the credential type does not match the broker
     */
    private static void requireMatchingCredentialFamily(BrokerType brokerType, BrokerCredentials credentials) {
        if (brokerType.isSaxo()) {
            if (!(credentials instanceof SaxoOAuthCredentials)) {
                throw new IllegalArgumentException(
                    "Saxo broker accounts (" + brokerType + ") require SaxoOAuthCredentials, got "
                        + credentials.getClass().getSimpleName());
            }
        } else if (brokerType.isAlpaca()) {
            if (!(credentials instanceof ApiKeyCredentials)) {
                throw new IllegalArgumentException(
                    "Alpaca broker accounts (" + brokerType + ") require ApiKeyCredentials, got "
                        + credentials.getClass().getSimpleName());
            }
        }
    }

    // Business methods

    /**
     * Reserves capital for a simulation.
     * The reserved amount is deducted from available balance.
     *
     * @param amount the amount to reserve
     * @throws IllegalStateException if amount exceeds available balance
     * @throws IllegalArgumentException if currencies don't match
     */
    public void reserve(Money amount) {
        Money available = getAvailableBalance();
        if (amount.compareTo(available) > 0) {
            throw new IllegalStateException(
                String.format("Insufficient available balance. Requested: %s, Available: %s",
                    amount, available)
            );
        }
        this.reservedBalance = this.reservedBalance.add(amount);
        this.updatedAt = Instant.now();
    }

    /**
     * Releases reserved capital back to available balance.
     * Called when a simulation ends.
     *
     * @param amount the amount to release
     * @throws IllegalStateException if amount exceeds reserved balance
     * @throws IllegalArgumentException if currencies don't match
     */
    public void release(Money amount) {
        if (amount.compareTo(this.reservedBalance) > 0) {
            throw new IllegalStateException(
                String.format("Cannot release more than reserved. Requested: %s, Reserved: %s",
                    amount, this.reservedBalance)
            );
        }
        this.reservedBalance = this.reservedBalance.subtract(amount);
        this.updatedAt = Instant.now();
    }

    /**
     * Calculates the available balance (total - reserved).
     *
     * @return the balance available for new reservations
     */
    public Money getAvailableBalance() {
        return totalBalance.subtract(reservedBalance);
    }

    // Status management

    /**
     * Marks the account as having invalid credentials.
     * Used when credential validation fails.
     */
    public void markInvalidCredentials() {
        this.status = AccountStatus.INVALID_CREDENTIALS;
        this.updatedAt = Instant.now();
    }

    /**
     * Marks the account as active.
     * Used after successful credential re-validation.
     */
    public void markActive() {
        this.status = AccountStatus.ACTIVE;
        this.updatedAt = Instant.now();
    }

    /**
     * Disables the account.
     * Disabled accounts cannot be used for new simulations.
     */
    public void disable() {
        this.status = AccountStatus.DISABLED;
        this.updatedAt = Instant.now();
    }

    /**
     * Syncs the total balance from the broker.
     * Called when refreshing account data from the external broker API.
     *
     * @param newBalance the current balance fetched from the broker
     * @throws IllegalArgumentException if currencies don't match
     */
    public void syncBalance(Money newBalance) {
        Objects.requireNonNull(newBalance, "newBalance cannot be null");
        if (!newBalance.currency().equals(this.totalBalance.currency())) {
            throw new IllegalArgumentException(
                String.format("Currency mismatch: expected %s, got %s",
                    this.totalBalance.currency(), newBalance.currency()));
        }
        this.totalBalance = newBalance;
        this.updatedAt = Instant.now();
    }

    /**
     * Syncs the equity (cash + positions market value) from the broker.
     * Only applicable for real broker accounts (Alpaca).
     *
     * @param newEquity the current equity fetched from the broker, or null to clear
     */
    public void syncEquity(Money newEquity) {
        this.equity = newEquity;
        this.updatedAt = Instant.now();
    }

    /**
     * Recalculates the reserved balance to match the given correct amount.
     * Used during balance sync to fix stale reservations from simulations
     * that ended or were deleted without properly releasing capital.
     *
     * @param correctReserved the actual reserved amount based on active simulations
     */
    public void syncReservedBalance(Money correctReserved) {
        Objects.requireNonNull(correctReserved, "correctReserved cannot be null");
        this.reservedBalance = correctReserved;
        this.updatedAt = Instant.now();
    }

    // Helper methods

    /**
     * Checks if this is a mock broker account.
     *
     * @return true if broker type is MOCK
     */
    public boolean isMock() {
        return brokerType == BrokerType.MOCK;
    }

    /**
     * Whether this account was created implicitly to back a single backtest.
     *
     * <p>Inline accounts are an implementation detail of running a backtest without a broker:
     * they are hidden from account listings and deleted with the simulation that created
     * them. Callers reporting a simulation's broker should not present an inline account as
     * one the user chose.</p>
     *
     * @return true if this account belongs to a single simulation
     */
    public boolean isInline() {
        return inline;
    }

    /**
     * Updates the credentials for this broker account.
     * Only allowed for real broker accounts (not MOCK). The credential family cannot be
     * swapped: an Alpaca account stays on {@link ApiKeyCredentials}, a Saxo account stays
     * on {@link SaxoOAuthCredentials}.
     *
     * @param newCredentials the new credentials, of the family this broker requires
     * @throws IllegalStateException if called on a mock account
     * @throws IllegalArgumentException if the credential family does not match the broker type
     */
    public void updateCredentials(BrokerCredentials newCredentials) {
        if (isMock()) {
            throw new IllegalStateException("Cannot update credentials on mock account");
        }
        Objects.requireNonNull(newCredentials, "credentials cannot be null");
        requireMatchingCredentialFamily(brokerType, newCredentials);
        this.credentials = newCredentials;
        this.updatedAt = Instant.now();
    }

    /**
     * Applies a completed OAuth token rotation to this account's credentials.
     *
     * <p>Used by the proactive token-refresh service after exchanging the rotating refresh
     * token for a fresh access token (and new refresh token). The account must already hold
     * {@link SaxoOAuthCredentials} (i.e. be a Saxo account). If the account was sitting in
     * {@link AccountStatus#NEEDS_REAUTH}, a successful rotation moves it back to
     * {@link AccountStatus#ACTIVE}.</p>
     *
     * @param rotated the rotated OAuth credentials (non-null)
     * @throws IllegalStateException if this account does not hold OAuth credentials
     */
    public void applyRefreshedTokens(SaxoOAuthCredentials rotated) {
        Objects.requireNonNull(rotated, "rotated credentials cannot be null");
        if (!(this.credentials instanceof SaxoOAuthCredentials)) {
            throw new IllegalStateException(
                "Cannot apply refreshed OAuth tokens to a non-OAuth account (brokerType=" + brokerType + ")");
        }
        this.credentials = rotated;
        if (this.status == AccountStatus.NEEDS_REAUTH) {
            this.status = AccountStatus.ACTIVE;
        }
        this.updatedAt = Instant.now();
    }

    /**
     * Marks the account as needing re-authorization because its OAuth refresh chain broke
     * and cannot be renewed unattended. The account stops trading until the user re-links it.
     */
    public void markNeedsReauth() {
        this.status = AccountStatus.NEEDS_REAUTH;
        this.updatedAt = Instant.now();
    }

    /**
     * Updates the display name of this broker account.
     *
     * @param newName the new display name
     */
    public void updateName(String newName) {
        Objects.requireNonNull(newName, "name cannot be null");
        this.name = newName;
        this.updatedAt = Instant.now();
    }

    // Getters

    public BrokerAccountId getId() {
        return id;
    }

    public UserId getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }

    public BrokerType getBrokerType() {
        return brokerType;
    }

    public BrokerCredentials getCredentials() {
        return credentials;
    }

    public Money getTotalBalance() {
        return totalBalance;
    }

    /**
     * The settlement currency this account trades and holds cash in.
     *
     * <p>Carried by the account's balance: Alpaca accounts settle in {@code USD},
     * Belgian/Saxo accounts in {@code EUR}. There is no separate currency column —
     * the balance currency <em>is</em> the settlement currency, set at create time
     * from the broker-reported (or OAuth-fetched) balance and persisted via
     * {@code total_balance_currency}. Reading the currency from live Saxo balances is
     * out of scope here (SB5); the account currency is fixed at creation.</p>
     *
     * <p>Downstream of account create this is the single source of truth for the
     * currency a {@code SimulationPortfolio} (and therefore its consensus / DCA
     * contribution {@code Money} arithmetic) must be minted in, so a EUR account never
     * trips {@code Money}'s same-currency guard.</p>
     *
     * @return the account's settlement currency (never null)
     */
    public Money.Currency getSettlementCurrency() {
        return totalBalance.currency();
    }

    /**
     * Returns the total equity (cash + positions market value).
     * Null for MOCK accounts.
     */
    public Money getEquity() {
        return equity;
    }

    public Money getReservedBalance() {
        return reservedBalance;
    }

    public AccountStatus getStatus() {
        return status;
    }

    public MockBrokerConfig getMockConfig() {
        return mockConfig;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public String toString() {
        return "BrokerAccount{" +
            "id=" + id +
            ", userId=" + userId +
            ", name='" + name + '\'' +
            ", brokerType=" + brokerType +
            ", totalBalance=" + totalBalance +
            ", equity=" + equity +
            ", reservedBalance=" + reservedBalance +
            ", availableBalance=" + getAvailableBalance() +
            ", status=" + status +
            ", createdAt=" + createdAt +
            ", updatedAt=" + updatedAt +
            '}';
    }
}
