package com.quorumtrading.application.usecase;

import com.quorumtrading.application.usecase.command.DeleteBenchmarkCommand;

/**
 * Use case for deleting a benchmark.
 */
public interface DeleteBenchmarkUseCase {

    /**
     * Execute the delete benchmark use case.
     *
     * @param command the command containing the benchmark ID to delete
     * @throws java.util.NoSuchElementException if benchmark not found
     */
    void execute(DeleteBenchmarkCommand command);
}
