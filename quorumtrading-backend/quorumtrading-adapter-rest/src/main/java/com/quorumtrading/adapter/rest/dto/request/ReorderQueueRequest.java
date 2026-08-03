package com.quorumtrading.adapter.rest.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Request DTO for reordering the simulation queue.
 *
 * @param orderedSimulationIds List of simulation IDs in desired execution order (first = next to run)
 */
public record ReorderQueueRequest(
    @NotNull(message = "Ordered simulation IDs cannot be null")
    @NotEmpty(message = "Ordered simulation IDs cannot be empty")
    List<String> orderedSimulationIds
) {
}
