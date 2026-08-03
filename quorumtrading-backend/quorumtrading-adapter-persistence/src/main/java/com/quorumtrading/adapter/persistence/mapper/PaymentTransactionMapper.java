package com.quorumtrading.adapter.persistence.mapper;

import com.quorumtrading.adapter.persistence.entity.PaymentTransactionJpaEntity;
import com.quorumtrading.model.entity.PaymentTransaction;
import com.quorumtrading.model.valueobject.UserId;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Mapper between PaymentTransaction domain entity and JPA entity.
 */
@ApplicationScoped
public class PaymentTransactionMapper {

    /**
     * Convert JPA entity to domain entity.
     */
    public PaymentTransaction toDomain(PaymentTransactionJpaEntity jpa) {
        if (jpa == null) {
            return null;
        }

        PaymentTransaction domain = new PaymentTransaction();
        domain.setId(jpa.getId());
        domain.setUserId(new UserId(jpa.getUserId()));
        domain.setProviderTransactionId(jpa.getProviderTransactionId());
        domain.setProvider(jpa.getProvider());
        domain.setAmount(jpa.getAmount());
        domain.setCurrency(jpa.getCurrency());
        domain.setStatus(mapStatus(jpa.getStatus()));
        domain.setDescription(jpa.getDescription());
        domain.setMetadata(jpa.getMetadata());
        domain.setCreatedAt(jpa.getCreatedAt());
        domain.setCompletedAt(jpa.getCompletedAt());

        return domain;
    }

    /**
     * Convert domain entity to JPA entity.
     */
    public PaymentTransactionJpaEntity toJpa(PaymentTransaction domain) {
        if (domain == null) {
            return null;
        }

        PaymentTransactionJpaEntity jpa = new PaymentTransactionJpaEntity();
        jpa.setId(domain.getId());
        jpa.setUserId(domain.getUserId().value());
        jpa.setProviderTransactionId(domain.getProviderTransactionId());
        jpa.setProvider(domain.getProvider());
        jpa.setAmount(domain.getAmount());
        jpa.setCurrency(domain.getCurrency());
        jpa.setStatus(mapStatus(domain.getStatus()));
        jpa.setDescription(domain.getDescription());
        jpa.setMetadata(domain.getMetadata());
        jpa.setCreatedAt(domain.getCreatedAt());
        jpa.setCompletedAt(domain.getCompletedAt());

        return jpa;
    }

    private PaymentTransaction.Status mapStatus(PaymentTransactionJpaEntity.Status jpaStatus) {
        if (jpaStatus == null) {
            return null;
        }
        return switch (jpaStatus) {
            case PENDING -> PaymentTransaction.Status.PENDING;
            case SUCCEEDED -> PaymentTransaction.Status.SUCCEEDED;
            case FAILED -> PaymentTransaction.Status.FAILED;
            case CANCELED -> PaymentTransaction.Status.CANCELED;
            case REFUNDED -> PaymentTransaction.Status.REFUNDED;
        };
    }

    private PaymentTransactionJpaEntity.Status mapStatus(PaymentTransaction.Status domainStatus) {
        if (domainStatus == null) {
            return null;
        }
        return switch (domainStatus) {
            case PENDING -> PaymentTransactionJpaEntity.Status.PENDING;
            case SUCCEEDED -> PaymentTransactionJpaEntity.Status.SUCCEEDED;
            case FAILED -> PaymentTransactionJpaEntity.Status.FAILED;
            case CANCELED -> PaymentTransactionJpaEntity.Status.CANCELED;
            case REFUNDED -> PaymentTransactionJpaEntity.Status.REFUNDED;
        };
    }
}
