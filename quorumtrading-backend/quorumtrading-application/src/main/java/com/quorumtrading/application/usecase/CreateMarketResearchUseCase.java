package com.quorumtrading.application.usecase;

import com.quorumtrading.application.usecase.command.CreateMarketResearchCommand;
import com.quorumtrading.model.entity.MarketResearch;

/**
 * Use case for creating a new market research configuration.
 * <p>
 * This use case orchestrates:
 * <ul>
 *   <li>Validation of command input</li>
 *   <li>Creation of MarketResearch domain entity</li>
 *   <li>Persistence of the new research configuration</li>
 * </ul>
 * </p>
 */
public interface CreateMarketResearchUseCase {

    /**
     * Execute the use case to create a market research configuration.
     *
     * @param command The command containing research configuration details
     * @return The created and persisted MarketResearch entity
     * @throws NullPointerException if command is null
     * @throws IllegalArgumentException if command validation fails
     */
    MarketResearch execute(CreateMarketResearchCommand command);
}
