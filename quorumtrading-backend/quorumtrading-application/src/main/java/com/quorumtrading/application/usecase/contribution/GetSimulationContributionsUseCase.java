package com.quorumtrading.application.usecase.contribution;

import com.quorumtrading.model.entity.Contribution;
import com.quorumtrading.model.valueobject.SimulationId;
import com.quorumtrading.model.valueobject.UserId;

import java.util.List;

public interface GetSimulationContributionsUseCase {
    /**
     * Contributions for one simulation, ordered by date ascending.
     *
     * <p>Took only the id, so any caller could read another user's contribution history.
     * Ownership resolves through the parent simulation.</p>
     */
    List<Contribution> execute(SimulationId simulationId, UserId requestingUserId);
}
