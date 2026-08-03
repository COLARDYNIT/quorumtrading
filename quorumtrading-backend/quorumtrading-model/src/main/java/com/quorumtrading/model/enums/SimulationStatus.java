package com.quorumtrading.model.enums;

/**
 * Status of a simulation execution.
 */
public enum SimulationStatus {
    PENDING("Pending", "Simulation is queued but not yet started"),
    WAITING_FOR_DATA("Waiting for Data", "Waiting for market data backfill to complete"),
    SIMULATING("Simulating", "Simulation is catching up with historical data (scheduler disabled)"),
    LIVE("Live", "Simulation has reached current time and is running in real-time"),
    COMPLETED("Completed", "Simulation finished successfully"),
    FAILED("Failed", "Simulation encountered an error");

    private final String displayName;
    private final String description;

    SimulationStatus(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }
}
