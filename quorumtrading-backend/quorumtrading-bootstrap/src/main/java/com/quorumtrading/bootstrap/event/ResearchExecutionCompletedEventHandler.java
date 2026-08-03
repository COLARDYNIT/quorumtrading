package com.quorumtrading.bootstrap.event;

import com.quorumtrading.application.event.ResearchExecutionCompleted;
import com.quorumtrading.application.event.listener.ResearchExecutionEventListener;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CDI event handler that observes ResearchExecutionCompleted events and delegates to AutoBacktestService.
 *
 * <p><b>Hexagonal Architecture:</b></p>
 * This adapter lives in the bootstrap layer (adapter) because it uses CDI-specific annotations.
 * The application layer (AutoBacktestService) remains pure and framework-agnostic.
 * The domain events are published through the EventPublisher port.
 *
 * <p><b>Event Flow:</b></p>
 * 1. ExecuteMarketResearchUseCase publishes ResearchExecutionCompleted event
 * 2. CdiEventPublisher fires CDI event
 * 3. This handler observes the CDI event synchronously
 * 4. Handler delegates to AutoBacktestService (application layer)
 * 5. AutoBacktestService decides whether to trigger auto-backtest
 *
 * <p><b>Design Notes:</b></p>
 * Uses @ObservesAsync since events are fired asynchronously via CdiEventPublisher.fireAsync().
 * This decouples event handling from the publishing thread.
 */
@ApplicationScoped
public class ResearchExecutionCompletedEventHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ResearchExecutionCompletedEventHandler.class);

    @Inject
    ResearchExecutionEventListener autoBacktestService;

    /**
     * Observes ResearchExecutionCompleted events asynchronously and delegates to AutoBacktestService.
     * Uses @ObservesAsync since events are fired with fireAsync().
     *
     * @param event the research execution completed event
     */
    void onResearchExecutionCompleted(@ObservesAsync ResearchExecutionCompleted event) {
        LOG.debug("Received ResearchExecutionCompleted event for execution: {}", event.executionId());

        try {
            // Delegate to application service
            autoBacktestService.onExecutionCompleted(event);

        } catch (Exception e) {
            // Catch all exceptions to prevent propagation to other event observers
            // Log error but don't rethrow - other events should still be processed
            LOG.error("Failed to handle research execution completed event: {}", event.executionId(), e);
        }
    }
}
