package com.quorumtrading.adapter.persistence.repository;

import com.quorumtrading.adapter.persistence.entity.PortfolioJpaEntity;
import com.quorumtrading.adapter.persistence.mapper.PortfolioMapper;
import com.quorumtrading.application.port.out.PortfolioRepository;
import com.quorumtrading.model.entity.PortfolioSnapshot;
import com.quorumtrading.model.valueobject.SimulationId;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Repository adapter for Portfolio.
 * Implements the domain repository interface using Panache and a mapper.
 */
@ApplicationScoped
@Transactional(Transactional.TxType.REQUIRED)
public class PortfolioRepositoryImpl implements PortfolioRepository, PanacheRepository<PortfolioJpaEntity> {

    private static final Logger log = LoggerFactory.getLogger(PortfolioRepositoryImpl.class);

    @Inject
    PortfolioMapper mapper;

    @Override
    @Transactional
    public PortfolioSnapshot save(PortfolioSnapshot portfolio) {
        log.info("Saving portfolio snapshot for simulation: {}, timestamp: {}, totalValue: {}, cash: {}",
            portfolio.getSimulationId().value(),
            portfolio.getTimestamp(),
            portfolio.getTotalValue(),
            portfolio.getCashAvailable());

        try {
            PortfolioJpaEntity entity = mapper.toJpaEntity(portfolio);
            log.debug("Mapped to JPA entity: simulationId={}, timestamp={}, totalValueUsd={}, cashAvailable={}, positionsJson={}",
                entity.getSimulationId(), entity.getTimestamp(), entity.getTotalValueUsd(),
                entity.getCashAvailable(), entity.getPositionsJson());

            persist(entity);
            log.info("Persisted portfolio snapshot with id: {} for simulation: {}",
                entity.id, entity.getSimulationId());

            // Force flush to ensure the entity is written to DB and catch any constraint violations
            getEntityManager().flush();
            log.info("Flushed portfolio snapshot to database: id={}", entity.id);

            return mapper.toDomain(entity);
        } catch (Exception e) {
            log.error("Failed to save portfolio snapshot for simulation {}: {}",
                portfolio.getSimulationId().value(), e.getMessage(), e);
            throw e;
        }
    }

    @Override
    public Optional<PortfolioSnapshot> findLatestBySimulationId(SimulationId simulationId) {
        // Query more than 1 result to handle case where latest is corrupted
        // Filter out null (corrupted) records and return first valid one
        return find("simulationId = ?1 order by timestamp desc", simulationId.value())
            .page(0, 10) // Get up to 10 to find first valid one
            .list()
            .stream()
            .map(mapper::toDomain)
            .filter(Objects::nonNull)
            .findFirst();
    }

    @Override
    public Optional<PortfolioSnapshot> findOldestBySimulationId(SimulationId simulationId) {
        // Query the oldest (first) snapshot - typically the initial portfolio state
        // Filter out null (corrupted) records and return first valid one
        return find("simulationId = ?1 order by timestamp asc", simulationId.value())
            .page(0, 10) // Get up to 10 to find first valid one
            .list()
            .stream()
            .map(mapper::toDomain)
            .filter(Objects::nonNull)
            .findFirst();
    }

    @Override
    public List<PortfolioSnapshot> findAllBySimulationId(SimulationId simulationId) {
        return find("simulationId = ?1 order by timestamp asc", simulationId.value())
            .list()
            .stream()
            .map(mapper::toDomain)
            .filter(Objects::nonNull) // Filter out corrupted ($0) snapshots
            .collect(Collectors.toList());
    }

    @Override
    public List<PortfolioSnapshot> findBySimulationIdAndTimeRange(SimulationId simulationId, Instant start, Instant end) {
        return find("simulationId = ?1 and timestamp >= ?2 and timestamp <= ?3 order by timestamp asc",
                    simulationId.value(), start, end)
            .list()
            .stream()
            .map(mapper::toDomain)
            .filter(Objects::nonNull) // Filter out corrupted ($0) snapshots
            .collect(Collectors.toList());
    }

    @Override
    public List<PortfolioSnapshot> findBySimulationId(SimulationId simulationId, Instant from, Instant to, Integer limit) {
        StringBuilder queryBuilder = new StringBuilder("simulationId = ?1");
        List<Object> params = new java.util.ArrayList<>();
        params.add(simulationId.value());

        if (from != null) {
            queryBuilder.append(" and timestamp >= ?").append(params.size() + 1);
            params.add(from);
        }

        if (to != null) {
            queryBuilder.append(" and timestamp <= ?").append(params.size() + 1);
            params.add(to);
        }

        queryBuilder.append(" order by timestamp desc");

        var query = find(queryBuilder.toString(), params.toArray());

        if (limit != null && limit > 0) {
            return query.page(0, limit).list()
                .stream()
                .map(mapper::toDomain)
                .filter(Objects::nonNull) // Filter out corrupted ($0) snapshots
                .collect(Collectors.toList());
        } else {
            return query.list()
                .stream()
                .map(mapper::toDomain)
                .filter(Objects::nonNull) // Filter out corrupted ($0) snapshots
                .collect(Collectors.toList());
        }
    }

    @Override
    public List<PortfolioSnapshot> findBySimulationId(SimulationId simulationId, Instant from, Instant to,
                                                       Integer limit, String resolution, boolean excludePositions) {
        boolean daily = "daily".equalsIgnoreCase(resolution);

        // Build column selection: skip positions column when excluded (avoids JSONB I/O + deserialization)
        String columns = excludePositions
            ? "id, simulation_id, timestamp, total_value_usd, cash_available"
            : "id, simulation_id, timestamp, positions, total_value_usd, cash_available";

        StringBuilder sql = new StringBuilder();
        List<Object> params = new ArrayList<>();

        if (daily) {
            // Use DISTINCT ON to pick one snapshot per day (the last one each day)
            sql.append("SELECT DISTINCT ON (DATE(timestamp)) ").append(columns)
               .append(" FROM portfolio_snapshots WHERE simulation_id = ?");
        } else {
            sql.append("SELECT ").append(columns)
               .append(" FROM portfolio_snapshots WHERE simulation_id = ?");
        }
        params.add(simulationId.value());

        if (from != null) {
            sql.append(" AND timestamp >= ?");
            params.add(from);
        }
        if (to != null) {
            sql.append(" AND timestamp <= ?");
            params.add(to);
        }

        if (daily) {
            // DISTINCT ON requires matching ORDER BY prefix, then pick last snapshot per day
            sql.append(" ORDER BY DATE(timestamp) DESC, timestamp DESC");
        } else {
            sql.append(" ORDER BY timestamp DESC");
        }

        if (limit != null && limit > 0) {
            sql.append(" LIMIT ?");
            params.add(limit);
        }

        var query = getEntityManager().createNativeQuery(sql.toString());
        for (int i = 0; i < params.size(); i++) {
            query.setParameter(i + 1, params.get(i));
        }

        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();

        return rows.stream()
            .map(row -> mapRowToSnapshot(row, excludePositions))
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

    private PortfolioSnapshot mapRowToSnapshot(Object[] row, boolean excludePositions) {
        // Column order matches the SELECT above
        Long id = ((Number) row[0]).longValue();
        UUID simId = (UUID) row[1];
        // Hibernate 6 + PostgreSQL TIMESTAMPTZ returns OffsetDateTime from native queries
        Instant timestamp;
        if (row[2] instanceof java.time.OffsetDateTime odt) {
            timestamp = odt.toInstant();
        } else if (row[2] instanceof java.sql.Timestamp ts) {
            timestamp = ts.toInstant();
        } else {
            timestamp = (Instant) row[2];
        }

        int colIdx = 3;
        String positionsJson = null;
        if (!excludePositions) {
            positionsJson = row[colIdx] != null ? row[colIdx].toString() : null;
            colIdx++;
        }

        BigDecimal totalValueUsd = (BigDecimal) row[colIdx++];
        BigDecimal cashAvailable = (BigDecimal) row[colIdx];

        // Validate totalValue
        if (totalValueUsd == null || totalValueUsd.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }

        // Build a JPA entity to reuse the existing mapper
        PortfolioJpaEntity entity = new PortfolioJpaEntity();
        entity.id = id;
        entity.setSimulationId(simId);
        entity.setTimestamp(timestamp);
        entity.setPositionsJson(excludePositions ? "[]" : positionsJson);
        entity.setTotalValueUsd(totalValueUsd);
        entity.setCashAvailable(cashAvailable);

        return mapper.toDomain(entity);
    }

    @Override
    public List<DailyPortfolioValue> findDailyTotalValueBySimulationIds(List<UUID> simulationIds) {
        if (simulationIds == null || simulationIds.isEmpty()) {
            return Collections.emptyList();
        }

        // Build IN clause placeholders
        StringBuilder placeholders = new StringBuilder();
        for (int i = 0; i < simulationIds.size(); i++) {
            if (i > 0) placeholders.append(", ");
            placeholders.append("?").append(i + 1);
        }

        String sql = "SELECT DISTINCT ON (simulation_id, DATE(timestamp)) " +
            "simulation_id, timestamp, total_value_usd " +
            "FROM portfolio_snapshots " +
            "WHERE simulation_id IN (" + placeholders + ") " +
            "AND total_value_usd > 0 " +
            "ORDER BY simulation_id, DATE(timestamp) DESC, timestamp DESC";

        var query = getEntityManager().createNativeQuery(sql);
        for (int i = 0; i < simulationIds.size(); i++) {
            query.setParameter(i + 1, simulationIds.get(i));
        }

        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();

        // The projection is the adapter's private business: decode it here, so callers get a
        // typed point instead of an Object[] they have to cast by column position.
        return rows.stream()
            .map(row -> new DailyPortfolioValue(
                toUuid(row[0]),
                toInstant(row[1]),
                (BigDecimal) row[2]))
            .collect(Collectors.toList());
    }

    private static UUID toUuid(Object raw) {
        return raw instanceof UUID uuid ? uuid : UUID.fromString(raw.toString());
    }

    /** Hibernate 6 hands back OffsetDateTime for TIMESTAMPTZ; older drivers hand back Timestamp. */
    private static Instant toInstant(Object raw) {
        if (raw instanceof java.time.OffsetDateTime odt) {
            return odt.toInstant();
        }
        if (raw instanceof java.sql.Timestamp ts) {
            return ts.toInstant();
        }
        return (Instant) raw;
    }

    @Override
    public long countBySimulationIdAfter(SimulationId simulationId, Instant afterInclusive) {
        return count("simulationId = ?1 AND timestamp >= ?2", simulationId.value(), afterInclusive);
    }

    @Override
    @Transactional
    public long deleteBySimulationIdAfter(SimulationId simulationId, Instant afterInclusive) {
        return delete("simulationId = ?1 AND timestamp >= ?2", simulationId.value(), afterInclusive);
    }

    @Override
    @Transactional
    public int deleteBySimulationId(SimulationId simulationId) {
        return (int) delete("simulationId", simulationId.value());
    }
}
