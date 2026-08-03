package com.quorumtrading.adapter.rest.dto.request;

import jakarta.validation.constraints.NotNull;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Request body for toggling per-simulation BUY/SELL decision notifications.
 */
@Schema(description = "Toggle notify-on-decision for a simulation")
public record UpdateSimulationNotificationPreferenceRequest(
        @NotNull
        @Schema(description = "Whether the owner wants notifications for this simulation's executed BUY/SELL decisions. " +
                "HOLDs never trigger notifications regardless.",
                example = "true",
                required = true)
        Boolean notifyDecisions) {
}
