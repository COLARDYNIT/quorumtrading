package com.quorumtrading.bootstrap.event;

import com.quorumtrading.application.event.BudgetDepletedEvent;
import com.quorumtrading.application.event.BudgetRestoredEvent;
import com.quorumtrading.application.event.BudgetWarningEvent;
import com.quorumtrading.application.event.listener.BudgetEventListener;
import com.quorumtrading.application.port.out.MarketResearchRepository;
import com.quorumtrading.application.port.out.SimulationRepository;
import com.quorumtrading.application.usecase.notification.DispatchNotificationUseCase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CDI event handler that observes budget-related domain events and delegates to BudgetEventListener.
 *
 * <p><b>Hexagonal Architecture:</b></p>
 * This adapter lives in the bootstrap layer because it uses CDI-specific annotations.
 * The application layer (BudgetEventListener) remains pure and framework-agnostic.
 *
 * <p><b>Event Flow:</b></p>
 * <ol>
 *   <li>RecordLLMUsageUseCase publishes BudgetDepletedEvent/BudgetRestoredEvent</li>
 *   <li>CdiEventPublisher fires CDI event</li>
 *   <li>This handler observes the CDI event</li>
 *   <li>Handler creates and delegates to BudgetEventListener (application layer)</li>
 *   <li>BudgetEventListener pauses/resumes relevant entities</li>
 * </ol>
 */
@ApplicationScoped
public class BudgetEventHandler {

    private static final Logger LOG = LoggerFactory.getLogger(BudgetEventHandler.class);

    @Inject
    MarketResearchRepository marketResearchRepository;

    @Inject
    SimulationRepository simulationRepository;

    @Inject
    DispatchNotificationUseCase dispatchNotificationUseCase;

    private BudgetEventListener newListener() {
        return new BudgetEventListener(
            marketResearchRepository,
            simulationRepository,
            dispatchNotificationUseCase
        );
    }

    /**
     * Observes BudgetDepletedEvent and delegates to application service.
     *
     * @param event the budget depleted event
     */
    @Transactional
    void onBudgetDepleted(@Observes BudgetDepletedEvent event) {
        LOG.debug("Received BudgetDepletedEvent for user: {}", event.userId().value());

        try {
            newListener().onBudgetDepleted(event);
        } catch (Exception e) {
            // Catch all exceptions to prevent propagation to other event observers
            LOG.error("Failed to handle budget depleted event for user: {}", event.userId().value(), e);
        }
    }

    /**
     * Observes BudgetRestoredEvent and delegates to application service.
     *
     * @param event the budget restored event
     */
    @Transactional
    void onBudgetRestored(@Observes BudgetRestoredEvent event) {
        LOG.debug("Received BudgetRestoredEvent for user: {}", event.userId().value());

        try {
            newListener().onBudgetRestored(event);
        } catch (Exception e) {
            // Catch all exceptions to prevent propagation to other event observers
            LOG.error("Failed to handle budget restored event for user: {}", event.userId().value(), e);
        }
    }

    /**
     * Observes BudgetWarningEvent and dispatches a single user-level
     * SIMULATION_LOW_BUDGET notification. UserBudget already enforces the
     * one-shot semantic at the domain level — this observer only fires on
     * the AVAILABLE→WARNING transition.
     */
    void onBudgetWarning(@Observes BudgetWarningEvent event) {
        LOG.debug("Received BudgetWarningEvent for user: {} (usage {}%)",
            event.userId().value(), event.usagePercentage());

        try {
            newListener().onBudgetWarning(event);
        } catch (Exception e) {
            LOG.error("Failed to handle budget warning event for user: {}", event.userId().value(), e);
        }
    }
}
