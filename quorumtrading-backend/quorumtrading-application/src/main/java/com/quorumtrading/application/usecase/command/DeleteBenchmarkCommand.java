package com.quorumtrading.application.usecase.command;

import com.quorumtrading.model.valueobject.BenchmarkId;
import com.quorumtrading.model.valueobject.UserId;

/**
 * Command for deleting a benchmark.
 *
 * @param benchmarkId ID of the benchmark to delete
 * @param requestingUserId ID of the user requesting deletion (for authorization)
 */
public record DeleteBenchmarkCommand(
    BenchmarkId benchmarkId,
    UserId requestingUserId
) {
}
