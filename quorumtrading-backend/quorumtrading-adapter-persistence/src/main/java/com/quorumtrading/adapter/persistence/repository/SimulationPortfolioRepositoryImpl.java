package com.quorumtrading.adapter.persistence.repository;

import com.quorumtrading.adapter.persistence.entity.SimulationPortfolioJpaEntity;
import com.quorumtrading.adapter.persistence.mapper.SimulationPortfolioMapper;
import com.quorumtrading.application.port.out.SimulationPortfolioRepository;
import com.quorumtrading.model.entity.SimulationPortfolio;
import com.quorumtrading.model.valueobject.BrokerAccountId;
import com.quorumtrading.model.valueobject.SimulationId;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * JPA EntityManager-based implementation of the SimulationPortfolioRepository port.
 *
 * <p>This adapter bridges the domain port interface to the JPA persistence layer.
 * Uses EntityManager instead of Panache static methods to support cross-module
 * entity discovery in Quarkus.</p>
 *
 * <p><b>Transaction Handling:</b> All write operations are wrapped in transactions.
 * Read operations use the existing transaction context if available.</p>
 *
 * <p><b>Key Design Decisions:</b></p>
 * <ul>
 *   <li>SimulationId is the primary key (1:1 with Simulation)</li>
 *   <li>Positions are serialized to JSONB via the mapper</li>
 *   <li>updatedAt timestamp is automatically set on save</li>
 * </ul>
 */
@ApplicationScoped
@Transactional(Transactional.TxType.REQUIRED)
public class SimulationPortfolioRepositoryImpl implements SimulationPortfolioRepository {

    private static final Logger log = LoggerFactory.getLogger(SimulationPortfolioRepositoryImpl.class);

    @Inject
    SimulationPortfolioMapper mapper;

    @Inject
    EntityManager entityManager;

    @Override
    @Transactional
    public SimulationPortfolio save(SimulationPortfolio portfolio) {
        log.debug("Saving simulation portfolio for simulation: {}", portfolio.getSimulationId());
        SimulationPortfolioJpaEntity entity = mapper.toJpaEntity(portfolio);

        // Check if this is an update (entity with this simulation ID exists)
        UUID simulationUuid = portfolio.getSimulationId().value();
        SimulationPortfolioJpaEntity existing = entityManager.find(
            SimulationPortfolioJpaEntity.class,
            simulationUuid
        );

        if (existing != null) {
            // Update existing portfolio
            existing.setBrokerAccountId(entity.getBrokerAccountId());
            existing.setPositions(entity.getPositions());
            existing.setCashAvailable(entity.getCashAvailable());
            existing.setCashReserved(entity.getCashReserved());
            existing.setRealizedPnl(entity.getRealizedPnl());
            existing.setUpdatedAt(Instant.now());
            entity = entityManager.merge(existing);
            log.debug("Updated existing simulation portfolio: {}", simulationUuid);
        } else {
            // New portfolio
            entity.setUpdatedAt(Instant.now());
            entityManager.persist(entity);
            log.debug("Persisted new simulation portfolio: {}", simulationUuid);
        }

        return mapper.toDomain(entity);
    }

    @Override
    public Optional<SimulationPortfolio> findById(SimulationId simulationId) {
        UUID uuid = simulationId.value();
        log.debug("Finding simulation portfolio by simulation ID: {}", uuid);

        return Optional.ofNullable(entityManager.find(SimulationPortfolioJpaEntity.class, uuid))
            .map(mapper::toDomain);
    }

    @Override
    public List<SimulationPortfolio> findByBrokerAccountId(BrokerAccountId brokerAccountId) {
        UUID uuid = brokerAccountId.value();
        log.debug("Finding simulation portfolios by broker account ID: {}", uuid);

        TypedQuery<SimulationPortfolioJpaEntity> query = entityManager.createQuery(
            "SELECT sp FROM SimulationPortfolioJpaEntity sp WHERE sp.brokerAccountId = :brokerAccountId ORDER BY sp.updatedAt DESC",
            SimulationPortfolioJpaEntity.class
        );
        query.setParameter("brokerAccountId", uuid);

        return query.getResultList()
            .stream()
            .map(mapper::toDomain)
            .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void delete(SimulationId simulationId) {
        UUID uuid = simulationId.value();
        log.debug("Deleting simulation portfolio: {}", uuid);

        SimulationPortfolioJpaEntity entity = entityManager.find(SimulationPortfolioJpaEntity.class, uuid);
        if (entity != null) {
            entityManager.remove(entity);
            log.debug("Deleted simulation portfolio: {}", uuid);
        } else {
            log.warn("Simulation portfolio not found for deletion: {}", uuid);
        }
    }

    @Override
    public boolean existsById(SimulationId simulationId) {
        UUID uuid = simulationId.value();
        log.debug("Checking existence of simulation portfolio: {}", uuid);

        TypedQuery<Long> query = entityManager.createQuery(
            "SELECT COUNT(sp) FROM SimulationPortfolioJpaEntity sp WHERE sp.simulationId = :simulationId",
            Long.class
        );
        query.setParameter("simulationId", uuid);

        return query.getSingleResult() > 0;
    }
}
