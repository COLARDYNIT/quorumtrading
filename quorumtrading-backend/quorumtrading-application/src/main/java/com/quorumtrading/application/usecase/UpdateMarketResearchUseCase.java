package com.quorumtrading.application.usecase;

import com.quorumtrading.application.usecase.command.UpdateMarketResearchCommand;
import com.quorumtrading.model.entity.MarketResearch;

/**
 * Use case for updating an existing market research configuration.
 * <p>
 * This use case orchestrates:
 * <ul>
 *   <li>Validation of command input</li>
 *   <li>Loading of existing MarketResearch entity</li>
 *   <li>Application of partial updates</li>
 *   <li>Persistence of the updated configuration</li>
 * </ul>
 * </p>
 */
public interface UpdateMarketResearchUseCase {

    /**
     * Execute the use case to update a market research configuration.
     *
     * @param command The command containing update details (only non-null fields are updated)
     * @return The updated and persisted MarketResearch entity
     * @throws NullPointerException if command is null
     * @throws IllegalArgumentException if command validation fails
     * @throws com.quorumtrading.application.exception.EntityNotFoundException if market research not found
     */
    MarketResearch execute(UpdateMarketResearchCommand command);
}
