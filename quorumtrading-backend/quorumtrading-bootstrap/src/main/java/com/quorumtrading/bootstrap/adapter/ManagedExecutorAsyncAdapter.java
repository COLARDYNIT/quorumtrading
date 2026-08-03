package com.quorumtrading.bootstrap.adapter;

import com.quorumtrading.application.port.out.AsyncExecutionPort;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ManagedContext;
import org.eclipse.microprofile.context.ManagedExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Bootstrap adapter that implements AsyncExecutionPort using a ManagedExecutor.
 * Bridges the application layer's async execution needs with the Quarkus framework.
 *
 * <p>Wraps each task with explicit CDI request context activation to ensure
 * JPA EntityManager and other request-scoped beans are available, even in
 * native image mode where ManagedExecutor context propagation may not work.</p>
 */
public class ManagedExecutorAsyncAdapter implements AsyncExecutionPort {

    private static final Logger LOG = LoggerFactory.getLogger(ManagedExecutorAsyncAdapter.class);

    private final ManagedExecutor managedExecutor;

    public ManagedExecutorAsyncAdapter(ManagedExecutor managedExecutor) {
        this.managedExecutor = managedExecutor;
    }

    @Override
    public void submitAsync(Runnable task) {
        managedExecutor.runAsync(() -> {
                ManagedContext requestContext = Arc.container().requestContext();
                requestContext.activate();
                try {
                    task.run();
                } finally {
                    requestContext.terminate();
                }
            })
            .exceptionally(ex -> {
                LOG.error("Async task failed: {}", ex.getMessage(), ex);
                return null;
            });
    }
}
