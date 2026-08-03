package com.quorumtrading.application.usecase;

import com.quorumtrading.application.usecase.command.CalculateSimulationOverlaysCommand;
import com.quorumtrading.model.valueobject.OverlayResult;

import java.util.List;

/**
 * Computes buy-and-hold reference overlays for a simulation's date range and initial capital.
 */
public interface CalculateSimulationOverlaysUseCase {

    List<OverlayResult> execute(CalculateSimulationOverlaysCommand command);
}
