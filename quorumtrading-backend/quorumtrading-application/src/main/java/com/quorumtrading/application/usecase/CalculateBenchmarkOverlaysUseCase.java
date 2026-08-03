package com.quorumtrading.application.usecase;

import com.quorumtrading.application.usecase.command.CalculateBenchmarkOverlaysCommand;
import com.quorumtrading.model.valueobject.OverlayResult;

import java.util.List;

/**
 * Computes buy-and-hold reference overlays for benchmark comparison.
 *
 * <p>Given a benchmark's date range and initial capital, calculates what the
 * portfolio would be worth if the investor simply bought and held each
 * reference asset (e.g., SPY, QQQ, BTC).</p>
 */
public interface CalculateBenchmarkOverlaysUseCase {

    List<OverlayResult> execute(CalculateBenchmarkOverlaysCommand command);
}
