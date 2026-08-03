package com.quorumtrading.adapter.persistence.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "deleted_account_hashes")
public class DeletedAccountHashJpaEntity extends PanacheEntityBase {

    @Id
    @Column(name = "id", nullable = false)
    public UUID id;

    @Column(name = "email_hash", length = 64, unique = true, nullable = false)
    public String emailHash;

    @Column(name = "original_signup_date", nullable = false)
    public Instant originalSignupDate;

    @Column(name = "deleted_at", nullable = false)
    public Instant deletedAt;

    @PrePersist
    public void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (deletedAt == null) {
            deletedAt = Instant.now();
        }
    }
}
