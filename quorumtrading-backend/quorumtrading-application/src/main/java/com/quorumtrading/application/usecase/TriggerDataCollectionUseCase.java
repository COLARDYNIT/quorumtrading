package com.quorumtrading.application.usecase;

import com.quorumtrading.application.usecase.command.TriggerDataCollectionCommand;

/**
 * Use case for triggering market data collection from providers.
 */
public interface TriggerDataCollectionUseCase {

    /**
     * Execute the trigger data collection use case.
     *
     * @param command the command containing optional category filter
     * @return result of the data collection operation
     */
    DataCollectionResult execute(TriggerDataCollectionCommand command);
}
