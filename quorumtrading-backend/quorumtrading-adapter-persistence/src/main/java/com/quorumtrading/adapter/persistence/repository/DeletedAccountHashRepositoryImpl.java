package com.quorumtrading.adapter.persistence.repository;

import com.quorumtrading.application.port.out.DeletedAccountHashRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.UUID;

@ApplicationScoped
@Transactional(Transactional.TxType.REQUIRED)
public class DeletedAccountHashRepositoryImpl implements DeletedAccountHashRepository {

    @Inject
    EntityManager entityManager;

    @Override
    @Transactional
    public void save(String emailHash, Instant originalSignupDate) {
        entityManager.createNativeQuery(
            "INSERT INTO deleted_account_hashes (id, email_hash, original_signup_date, deleted_at) " +
            "VALUES (?1, ?2, ?3, ?4) ON CONFLICT (email_hash) DO NOTHING"
        )
        .setParameter(1, UUID.randomUUID())
        .setParameter(2, emailHash)
        .setParameter(3, originalSignupDate)
        .setParameter(4, Instant.now())
        .executeUpdate();
    }

    @Override
    public boolean existsByEmailHash(String emailHash) {
        Long count = (Long) entityManager.createQuery(
            "SELECT COUNT(d) FROM DeletedAccountHashJpaEntity d WHERE d.emailHash = :hash"
        )
        .setParameter("hash", emailHash)
        .getSingleResult();

        return count > 0;
    }
}
