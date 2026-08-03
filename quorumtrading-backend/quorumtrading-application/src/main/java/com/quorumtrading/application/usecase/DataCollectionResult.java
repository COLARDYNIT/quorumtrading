package com.quorumtrading.application.usecase;

import java.util.List;

/**
 * Result of a data collection operation.
 *
 * @param providerName name of the provider(s) that collected data
 * @param eventsCollected number of events collected
 * @param errors list of error messages encountered during collection
 */
public record DataCollectionResult(
    String providerName,
    int eventsCollected,
    List<String> errors
) {
}
