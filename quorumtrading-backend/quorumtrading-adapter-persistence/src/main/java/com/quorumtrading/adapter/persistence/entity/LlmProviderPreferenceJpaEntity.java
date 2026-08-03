package com.quorumtrading.adapter.persistence.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * JPA mapping for the single-row {@code llm_provider_preference} table (V120).
 * The row id is always {@code 1} — enforced by a DB CHECK constraint.
 */
@Entity
@Table(name = "llm_provider_preference")
public class LlmProviderPreferenceJpaEntity extends PanacheEntityBase {

    public static final short SINGLETON_ID = 1;

    @Id
    public Short id;

    /** Selected engine name ({@code LOCAL} / {@code OPENROUTER}); {@code null} until chosen. */
    @Column(name = "provider", length = 20)
    public String provider;

    @Column(name = "openrouter_api_key_encrypted")
    public byte[] openrouterApiKeyEncrypted;

    @Column(name = "configured_at", nullable = false)
    public Instant configuredAt;
}
