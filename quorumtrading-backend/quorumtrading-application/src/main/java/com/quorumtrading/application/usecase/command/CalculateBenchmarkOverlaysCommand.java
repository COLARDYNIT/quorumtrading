package com.quorumtrading.application.usecase.command;

import com.quorumtrading.model.valueobject.BenchmarkId;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.Symbol;
import com.quorumtrading.model.valueobject.UserId;

import java.util.List;
import java.util.Objects;

/**
 * Command for computing buy-and-hold benchmark overlays.
 *
 * @param benchmarkId The benchmark to overlay against
 * @param symbols Reference assets (e.g., SPY, QQQ, BTC)
 * @param initialCapital Starting capital; nullable — defaults to first simulation's initialCash
 * @param userId The requesting user
 * @param resolution Time bucket for downsampling: "1d", "1h", "1w". Null defaults to "1d".
 */
public record CalculateBenchmarkOverlaysCommand(
    BenchmarkId benchmarkId,
    List<Symbol> symbols,
    Money initialCapital,
    UserId userId,
    String resolution
) {
    public CalculateBenchmarkOverlaysCommand {
        Objects.requireNonNull(benchmarkId, "benchmarkId is required");
        Objects.requireNonNull(symbols, "symbols is required");
        if (symbols.isEmpty()) {
            throw new IllegalArgumentException("At least one symbol is required");
        }
    }

    /**
     * Backwards-compatible constructor without resolution (defaults to null → "1d").
     */
    public CalculateBenchmarkOverlaysCommand(BenchmarkId benchmarkId, List<Symbol> symbols,
                                              Money initialCapital, UserId userId) {
        this(benchmarkId, symbols, initialCapital, userId, null);
    }
}
