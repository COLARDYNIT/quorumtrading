package com.quorumtrading.application.port.out;

/**
 * Port for submitting tasks for asynchronous execution.
 * Allows the application layer to offload long-running work (e.g., simulation execution)
 * without depending on framework-specific executors like ManagedExecutor.
 */
public interface AsyncExecutionPort {

    /**
     * Submit a task for asynchronous execution.
     * The task will run on a dedicated thread pool, not blocking the caller.
     *
     * @param task the runnable to execute asynchronously
     */
    void submitAsync(Runnable task);
}
