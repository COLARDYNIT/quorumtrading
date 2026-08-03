package com.quorumtrading.application.usecase.result;

public record ForceEndSimulationResult(
    boolean success,
    boolean liquidated,
    int liquidatedCount,
    String message
) {

    public static ForceEndSimulationResult completed(String message) {
        return new ForceEndSimulationResult(true, false, 0, message);
    }

    public static ForceEndSimulationResult completedWithLiquidation(int liquidatedCount, String message) {
        return new ForceEndSimulationResult(true, true, liquidatedCount, message);
    }

    public static ForceEndSimulationResult failure(String message) {
        return new ForceEndSimulationResult(false, false, 0, message);
    }
}
