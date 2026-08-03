package com.quorumtrading.adapter.persistence.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quorumtrading.adapter.persistence.entity.BrokerAccountJpaEntity;
import com.quorumtrading.model.entity.BrokerAccount;
import com.quorumtrading.model.enums.AccountStatus;
import com.quorumtrading.model.enums.BrokerType;
import com.quorumtrading.model.enums.CostPreset;
import com.quorumtrading.model.valueobject.BrokerAccountId;
import com.quorumtrading.adapter.persistence.crypto.CredentialEncryptor;
import com.quorumtrading.model.valueobject.BrokerCredentials;
import com.quorumtrading.model.valueobject.MockBrokerConfig;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.SaxoOAuthCredentials;
import com.quorumtrading.model.valueobject.UserId;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * Mapper between BrokerAccount domain entities and BrokerAccountJpaEntity.
 *
 * <p>Handles the following conversions:</p>
 * <ul>
 *   <li>Value objects (BrokerAccountId, UserId, Money) to/from primitives</li>
 *   <li>MockBrokerConfig to/from JSONB</li>
 *   <li>BrokerCredentials encryption/decryption via AES-256-GCM</li>
 *   <li>Enums (BrokerType, AccountStatus) to/from strings</li>
 * </ul>
 */
@ApplicationScoped
public class BrokerAccountMapper {

    private static final Logger log = LoggerFactory.getLogger(BrokerAccountMapper.class);

    @Inject
    CredentialEncryptor credentialEncryptor;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Converts a domain BrokerAccount to a JPA entity for persistence.
     *
     * @param account the domain entity
     * @return the JPA entity ready for persistence
     */
    public BrokerAccountJpaEntity toJpaEntity(BrokerAccount account) {
        BrokerAccountJpaEntity entity = new BrokerAccountJpaEntity();

        // Map ID (UUID wrapper to UUID)
        if (account.getId() != null) {
            entity.setId(account.getId().value());
        }

        // Map UserId (UUID wrapper to UUID)
        entity.setUserId(account.getUserId().value());

        // Basic fields
        entity.setName(account.getName());
        entity.setBrokerType(account.getBrokerType().name());
        entity.setStatus(account.getStatus().name());

        // Map Money values
        entity.setTotalBalanceAmount(account.getTotalBalance().amount());
        entity.setTotalBalanceCurrency(account.getTotalBalance().currency().name());
        entity.setEquityAmount(account.getEquity() != null ? account.getEquity().amount() : null);
        entity.setReservedBalanceAmount(account.getReservedBalance().amount());

        // Map timestamps
        entity.setCreatedAt(account.getCreatedAt());
        entity.setUpdatedAt(account.getUpdatedAt());

        // Inline accounts belong to a single simulation (V125)
        entity.setInline(account.isInline());

        // Encrypt credentials (AES-256-GCM)
        if (account.getCredentials() != null) {
            entity.setCredentialsEncrypted(credentialEncryptor.encrypt(account.getCredentials()));
        }

        // Project Saxo OAuth fields onto queryable columns (V117). These mirror what is also
        // sealed inside the encrypted credential blob, but as plain columns they let the
        // refresh scheduler probe access_token_expires_at (partial index) without decrypting,
        // and let queries filter by environment. Lease columns (refresh_generation /
        // refresh_locked_by / refresh_locked_at) are deliberately NOT touched here — they are
        // owned by the token-lease adapter (OA3) and preserved across updates by the repository.
        if (account.getCredentials() instanceof SaxoOAuthCredentials saxo) {
            entity.setBrokerEnvironment(saxo.environment().name());
            entity.setAccessTokenExpiresAt(saxo.accessTokenExpiry());
        }

        // Serialize MockBrokerConfig to JSON
        if (account.getMockConfig() != null) {
            entity.setMockConfig(serializeMockConfig(account.getMockConfig()));
        }

        return entity;
    }

    /**
     * Converts a JPA entity to a domain BrokerAccount.
     *
     * @param entity the JPA entity from persistence
     * @return the domain entity with all business logic intact
     */
    public BrokerAccount toDomain(BrokerAccountJpaEntity entity) {
        // Map ID
        BrokerAccountId id = new BrokerAccountId(entity.getId());

        // Map UserId
        UserId userId = new UserId(entity.getUserId());

        // Map BrokerType enum
        BrokerType brokerType = BrokerType.valueOf(entity.getBrokerType());

        // Map AccountStatus enum
        AccountStatus status = AccountStatus.valueOf(entity.getStatus());

        // Map Money values
        Money.Currency currency = Money.Currency.valueOf(entity.getTotalBalanceCurrency());
        Money totalBalance = new Money(entity.getTotalBalanceAmount(), currency);
        Money equity = entity.getEquityAmount() != null ? new Money(entity.getEquityAmount(), currency) : null;
        Money reservedBalance = new Money(entity.getReservedBalanceAmount(), currency);

        // Decrypt credentials (AES-256-GCM)
        BrokerCredentials credentials = null;
        if (entity.getCredentialsEncrypted() != null && entity.getCredentialsEncrypted().length > 0) {
            credentials = credentialEncryptor.decrypt(entity.getCredentialsEncrypted());
        }

        // Deserialize MockBrokerConfig from JSON
        MockBrokerConfig mockConfig = null;
        if (entity.getMockConfig() != null && !entity.getMockConfig().isEmpty()) {
            mockConfig = deserializeMockConfig(entity.getMockConfig(), totalBalance);
        }

        return new BrokerAccount(
            id,
            userId,
            entity.getName(),
            brokerType,
            credentials,
            totalBalance,
            equity,
            reservedBalance,
            status,
            mockConfig,
            entity.getCreatedAt(),
            entity.getUpdatedAt(),
            entity.isInline()
        );
    }

    /**
     * Serializes MockBrokerConfig to JSON string for JSONB storage.
     *
     * @param config the mock broker configuration
     * @return JSON string representation
     */
    private String serializeMockConfig(MockBrokerConfig config) {
        try {
            Map<String, Object> configMap = new HashMap<>();
            configMap.put("initialCapitalAmount", config.initialCapital().amount().toString());
            configMap.put("initialCapitalCurrency", config.initialCapital().currency().name());
            configMap.put("commissionRate", config.commissionRate().toString());
            configMap.put("cryptoCommissionRate", config.cryptoCommissionRate().toString());
            configMap.put("slippageBase", config.slippageBase().toString());
            configMap.put("slippageImpactPerUnit", config.slippageImpactPerUnit().toString());
            configMap.put("slippageVolatilityMultiplier", config.slippageVolatilityMultiplier().toString());
            configMap.put("preset", config.preset().name());
            return objectMapper.writeValueAsString(configMap);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize MockBrokerConfig", e);
            throw new RuntimeException("Failed to serialize MockBrokerConfig", e);
        }
    }

    /**
     * Deserializes MockBrokerConfig from JSON string.
     *
     * @param json the JSON string from JSONB storage
     * @param accountBalance the account's total balance (used as fallback for initial capital)
     * @return the MockBrokerConfig, or a default config on error
     */
    @SuppressWarnings("unchecked")
    private MockBrokerConfig deserializeMockConfig(String json, Money accountBalance) {
        try {
            Map<String, Object> configMap = objectMapper.readValue(json, Map.class);

            // Parse initial capital
            Money initialCapital;
            if (configMap.containsKey("initialCapitalAmount")) {
                BigDecimal amount = new BigDecimal(configMap.get("initialCapitalAmount").toString());
                Money.Currency currency = Money.Currency.valueOf(
                    configMap.getOrDefault("initialCapitalCurrency", "USD").toString()
                );
                initialCapital = new Money(amount, currency);
            } else {
                // Fallback to account balance
                initialCapital = accountBalance;
            }

            // Parse cost parameters
            BigDecimal commissionRate = new BigDecimal(
                configMap.getOrDefault("commissionRate", "0").toString()
            );
            // Backward compat: if cryptoCommissionRate is absent, default to commissionRate
            BigDecimal cryptoCommissionRate = new BigDecimal(
                configMap.getOrDefault("cryptoCommissionRate", commissionRate.toString()).toString()
            );
            BigDecimal slippageBase = new BigDecimal(
                configMap.getOrDefault("slippageBase", "0").toString()
            );
            BigDecimal slippageImpactPerUnit = new BigDecimal(
                configMap.getOrDefault("slippageImpactPerUnit", "0").toString()
            );
            BigDecimal slippageVolatilityMultiplier = new BigDecimal(
                configMap.getOrDefault("slippageVolatilityMultiplier", "1").toString()
            );

            // Parse preset
            CostPreset preset = CostPreset.CUSTOM;
            if (configMap.containsKey("preset")) {
                try {
                    preset = CostPreset.valueOf(configMap.get("preset").toString());
                } catch (IllegalArgumentException e) {
                    log.warn("Unknown cost preset: {}, using CUSTOM", configMap.get("preset"));
                }
            }

            // Create config based on preset or custom
            if (preset != CostPreset.CUSTOM) {
                return MockBrokerConfig.fromPreset(initialCapital, preset);
            } else {
                return MockBrokerConfig.custom(
                    initialCapital,
                    commissionRate,
                    cryptoCommissionRate,
                    slippageBase,
                    slippageImpactPerUnit,
                    slippageVolatilityMultiplier
                );
            }
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize MockBrokerConfig, using defaults", e);
            // Return default config with account balance
            return MockBrokerConfig.fromPreset(accountBalance, CostPreset.ZERO_FEES);
        }
    }
}
