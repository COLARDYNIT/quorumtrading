package com.quorumtrading.adapter.persistence.repository;

import com.quorumtrading.model.entity.PortfolioSnapshot;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import io.quarkus.panache.common.Parameters;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class PortfolioSnapshotPanacheDAO implements PanacheRepository<PortfolioSnapshot> {

    /**
     * Count portfolio snapshots at or after a given timestamp.
     */
    public long countBySimulationIdAfter(UUID simulationId, Instant afterInclusive) {
        return count("simulationId = :simulationId AND timestamp >= :cutoff",
            Parameters.with("simulationId", simulationId).and("cutoff", afterInclusive));
    }

    /**
     * Delete portfolio snapshots at or after a given timestamp.
     */
    public long deleteBySimulationIdAfter(UUID simulationId, Instant afterInclusive) {
        return delete("simulationId = :simulationId AND timestamp >= :cutoff",
            Parameters.with("simulationId", simulationId).and("cutoff", afterInclusive));
    }

    public List<PortfolioSnapshot> findBySimulationId(UUID simulationId, Instant from, Instant to, Integer limit) {
        StringBuilder query = new StringBuilder("simulationId = :simulationId");
        Parameters params = Parameters.with("simulationId", simulationId);

        if (from != null) {
            query.append(" AND timestamp >= :from");
            params.and("from", from);
        }

        if (to != null) {
            query.append(" AND timestamp <= :to");
            params.and("to", to);
        }

        query.append(" ORDER BY timestamp DESC");

        if (limit != null && limit > 0) {
            return find(query.toString(), params).page(0, limit).list();
        } else {
            // Default limit of 100
            return find(query.toString(), params).page(0, 100).list();
        }
    }
}
