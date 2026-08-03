package com.quorumtrading.application.port.out;

import com.quorumtrading.model.marketdata.BackfillJob;
import com.quorumtrading.model.valueobject.SimulationId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BackfillJobRepository {
    void save(BackfillJob request);
    Optional<BackfillJob> findById(UUID id);
    List<BackfillJob> findPendingJobs();
    void update(BackfillJob request);

    /**
     * Find backfill jobs linked to a specific simulation.
     * Returns the most recent job if multiple exist.
     *
     * @param simulationId The simulation to find backfill jobs for
     * @return Optional containing the most recent backfill job, or empty if none found
     */
    Optional<BackfillJob> findByLinkedSimulationId(SimulationId simulationId);
}
