package com.quorumtrading.adapter.rest.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * DTOs for scheduled simulation REST endpoints.
 */
public final class ScheduledSimulationDto {

    private ScheduledSimulationDto() {
        // Utility class
    }

    /**
     * Response for manual trigger request.
     */
    @Schema(description = "Response for manual trigger request")
    public record TriggerResponse(
        @Schema(description = "Confirmation message") String message,
        @Schema(description = "ID of the created simulation") String simulationId
    ) {}

    /**
     * Response for schedule retrieval.
     */
    @Schema(description = "Scheduled simulation configuration")
    public record ScheduleResponse(
        @Schema(description = "Schedule ID") String id,
        @Schema(description = "Strategy ID") String strategyId,
        @Schema(description = "Schedule frequency") String frequency,
        @Schema(description = "Whether scheduling is enabled") boolean enabled,
        @Schema(description = "Next scheduled run time") String nextRunAt,
        @Schema(description = "Last run time") String lastRunAt
    ) {}
}
