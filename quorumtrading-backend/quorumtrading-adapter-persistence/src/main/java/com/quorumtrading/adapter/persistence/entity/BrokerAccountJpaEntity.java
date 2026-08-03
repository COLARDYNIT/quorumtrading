package com.quorumtrading.adapter.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for BrokerAccount persistence.
 *
 * Maps to the broker_accounts table created by Flyway migrations.
 * This is SEPARATE from the domain BrokerAccount to maintain hexagonal architecture boundaries.
 *
 * Uses standard JPA (not Panache) for cross-module compatibility.
 */
@Entity
@Table(name = "broker_accounts")
public class BrokerAccountJpaEntity {

    /**
     * Primary key (UUID).
     */
    @Id
    @Column(name = "id")
    private UUID id;

    /**
     * Foreign key to users table.
     */
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /**
     * Human-readable account name.
     */
    @Column(name = "name", nullable = false)
    private String name;

    /**
     * Type of broker (MOCK, ALPACA, etc.).
     */
    @Column(name = "broker_type", nullable = false)
    private String brokerType;

    /**
     * Encrypted broker credentials (for real brokers).
     */
    @Column(name = "credentials_encrypted")
    private byte[] credentialsEncrypted;

    /**
     * Total balance amount.
     */
    @Column(name = "total_balance_amount", nullable = false, precision = 20, scale = 8)
    private BigDecimal totalBalanceAmount;

    /**
     * Currency for total balance.
     */
    @Column(name = "total_balance_currency", nullable = false)
    private String totalBalanceCurrency;

    /**
     * Total equity (cash + positions market value). Nullable for MOCK accounts.
     */
    @Column(name = "equity_amount", precision = 20, scale = 8)
    private BigDecimal equityAmount;

    /**
     * Amount reserved for pending orders.
     */
    @Column(name = "reserved_balance_amount", nullable = false, precision = 20, scale = 8)
    private BigDecimal reservedBalanceAmount;

    /**
     * Account status (ACTIVE, SUSPENDED, DISCONNECTED).
     */
    @Column(name = "status", nullable = false)
    private String status;

    /**
     * Mock broker configuration (JSONB) - for MOCK broker type.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "mock_config", columnDefinition = "jsonb")
    private String mockConfig;

    /**
     * Saxo environment ('SIM' | 'LIVE') for SAXO_* OAuth accounts; NULL for MOCK / ALPACA_*.
     * Derived from {@code SaxoOAuthCredentials.environment()} by the mapper so the value is
     * queryable as a column without decrypting the credential blob. (V117)
     */
    @Column(name = "broker_environment", length = 10)
    private String brokerEnvironment;

    /**
     * Expiry of the cached OAuth access token; drives the proactive-refresh scheduler probe
     * (V117 partial index {@code idx_broker_accounts_token_refresh}). Derived from
     * {@code SaxoOAuthCredentials.accessTokenExpiry()} by the mapper. NULL for non-OAuth brokers
     * and for freshly-onboarded Saxo credentials that hold only a refresh token. (V117)
     */
    @Column(name = "access_token_expires_at")
    private Instant accessTokenExpiresAt;

    /**
     * Monotonic watermark bumped on each refresh-token rotation; lost-update guard for the
     * rotating-refresh chain. Written exclusively by the token-lease adapter (OA3) via atomic
     * {@code UPDATE ... WHERE}; the domain mapper neither sets nor overwrites it. (V117)
     */
    @Column(name = "refresh_generation")
    private Long refreshGeneration;

    /**
     * Identity of the instance currently holding the per-account refresh lease (NULL = free).
     * Owned by the token-lease adapter (OA3); the domain mapper must preserve it across updates.
     * (V117)
     */
    @Column(name = "refresh_locked_by", length = 255)
    private String refreshLockedBy;

    /**
     * When the refresh lease was acquired; drives stale-lease force-release. Owned by the
     * token-lease adapter (OA3); the domain mapper must preserve it across updates. (V117)
     */
    @Column(name = "refresh_locked_at")
    private Instant refreshLockedAt;

    /**
     * Creation timestamp.
     */
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /**
     * Last update timestamp.
     */
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * True when this account was created implicitly to back a single backtest rather than
     * deliberately by the user. Inline accounts are hidden from listings and deleted with
     * their simulation. (V125)
     */
    @Column(name = "inline", nullable = false)
    private boolean inline;

    /**
     * Default constructor for JPA.
     */
    public BrokerAccountJpaEntity() {}

    /**
     * Set default values before persisting new entity.
     */
    @PrePersist
    public void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (updatedAt == null) {
            updatedAt = Instant.now();
        }
        if (status == null) {
            status = "ACTIVE";
        }
        if (totalBalanceAmount == null) {
            totalBalanceAmount = BigDecimal.ZERO;
        }
        if (reservedBalanceAmount == null) {
            reservedBalanceAmount = BigDecimal.ZERO;
        }
        if (totalBalanceCurrency == null) {
            totalBalanceCurrency = "USD";
        }
    }

    /**
     * Update timestamp before each update.
     */
    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }

    // Getters and setters

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getBrokerType() {
        return brokerType;
    }

    public void setBrokerType(String brokerType) {
        this.brokerType = brokerType;
    }

    public byte[] getCredentialsEncrypted() {
        return credentialsEncrypted;
    }

    public void setCredentialsEncrypted(byte[] credentialsEncrypted) {
        this.credentialsEncrypted = credentialsEncrypted;
    }

    public BigDecimal getTotalBalanceAmount() {
        return totalBalanceAmount;
    }

    public void setTotalBalanceAmount(BigDecimal totalBalanceAmount) {
        this.totalBalanceAmount = totalBalanceAmount;
    }

    public String getTotalBalanceCurrency() {
        return totalBalanceCurrency;
    }

    public void setTotalBalanceCurrency(String totalBalanceCurrency) {
        this.totalBalanceCurrency = totalBalanceCurrency;
    }

    public BigDecimal getEquityAmount() {
        return equityAmount;
    }

    public void setEquityAmount(BigDecimal equityAmount) {
        this.equityAmount = equityAmount;
    }

    public BigDecimal getReservedBalanceAmount() {
        return reservedBalanceAmount;
    }

    public void setReservedBalanceAmount(BigDecimal reservedBalanceAmount) {
        this.reservedBalanceAmount = reservedBalanceAmount;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMockConfig() {
        return mockConfig;
    }

    public void setMockConfig(String mockConfig) {
        this.mockConfig = mockConfig;
    }

    public String getBrokerEnvironment() {
        return brokerEnvironment;
    }

    public void setBrokerEnvironment(String brokerEnvironment) {
        this.brokerEnvironment = brokerEnvironment;
    }

    public Instant getAccessTokenExpiresAt() {
        return accessTokenExpiresAt;
    }

    public void setAccessTokenExpiresAt(Instant accessTokenExpiresAt) {
        this.accessTokenExpiresAt = accessTokenExpiresAt;
    }

    public Long getRefreshGeneration() {
        return refreshGeneration;
    }

    public void setRefreshGeneration(Long refreshGeneration) {
        this.refreshGeneration = refreshGeneration;
    }

    public String getRefreshLockedBy() {
        return refreshLockedBy;
    }

    public void setRefreshLockedBy(String refreshLockedBy) {
        this.refreshLockedBy = refreshLockedBy;
    }

    public Instant getRefreshLockedAt() {
        return refreshLockedAt;
    }

    public void setRefreshLockedAt(Instant refreshLockedAt) {
        this.refreshLockedAt = refreshLockedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public boolean isInline() {
        return inline;
    }

    public void setInline(boolean inline) {
        this.inline = inline;
    }
}
