package com.quorumtrading.application.usecase;

import com.quorumtrading.application.usecase.command.JoinChallengeCommand;
import com.quorumtrading.model.valueobject.SimulationId;

/**
 * Use case for joining a challenge with a strategy.
 * Creates a new simulation with the challenge's constraints and links it.
 * If the user already has an entry, the old simulation is detached first.
 */
public interface JoinChallengeUseCase {

    /**
     * Join a challenge by creating a simulation from a strategy.
     *
     * @param command the command containing challenge ID and strategy ID
     * @return the ID of the created simulation
     * @throws java.util.NoSuchElementException if challenge or strategy not found
     * @throws IllegalStateException if challenge is not accepting entries
     */
    SimulationId execute(JoinChallengeCommand command);
}
