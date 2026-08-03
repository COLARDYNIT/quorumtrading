package com.quorumtrading.application.event.listener;

import com.quorumtrading.application.event.BudgetDepletedEvent;
import com.quorumtrading.application.event.BudgetRestoredEvent;
import com.quorumtrading.application.event.BudgetWarningEvent;
import com.quorumtrading.application.port.out.MarketResearchRepository;
import com.quorumtrading.application.port.out.SimulationRepository;
import com.quorumtrading.application.usecase.command.DispatchNotificationCommand;
import com.quorumtrading.application.usecase.notification.DispatchNotificationUseCase;
import com.quorumtrading.model.entity.MarketResearch;
import com.quorumtrading.model.entity.Simulation;
import com.quorumtrading.model.enums.NotificationType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Application-layer event listener for budget-related domain events.
 *
 * <p><b>Responsibilities:</b></p>
 * <ul>
 *   <li>Handle BudgetDepletedEvent: Pause all active user research and simulations</li>
 *   <li>Handle BudgetRestoredEvent: Resume entities that were paused due to budget</li>
 * </ul>
 *
 * <p><b>Hexagonal Architecture:</b></p>
 * This service is in the application layer and is framework-agnostic.
 * It receives dependencies through constructor injection. The bootstrap layer
 * wires this service and delegates CDI events to it.
 *
 * <p><b>Cross-Context Communication:</b></p>
 * Uses domain events for cross-aggregate communication, following DDD best practices.
 * The UserBudget aggregate publishes events, and this listener coordinates the response
 * across MarketResearch and Simulation aggregates.
 */
public class BudgetEventListener {

    private static final Logger LOG = LoggerFactory.getLogger(BudgetEventListener.class);

    private static final String BUDGET_DEPLETED_REASON =
        com.quorumtrading.model.valueobject.ExternalPauseReason.BUDGET_DEPLETED;

    private final MarketResearchRepository marketResearchRepository;
    private final SimulationRepository simulationRepository;
    private final DispatchNotificationUseCase dispatchNotificationUseCase;

    public BudgetEventListener(
        MarketResearchRepository marketResearchRepository,
        SimulationRepository simulationRepository,
        DispatchNotificationUseCase dispatchNotificationUseCase
    ) {
        this.marketResearchRepository = marketResearchRepository;
        this.simulationRepository = simulationRepository;
        this.dispatchNotificationUseCase = dispatchNotificationUseCase;
    }

    /**
     * Handle budget depleted event by pausing all active user entities.
     *
     * <p>Pauses:</p>
     * <ul>
     *   <li>All ACTIVE MarketResearch configurations owned by the user</li>
     *   <li>All SIMULATING or LIVE simulations owned by the user (through strategy)</li>
     * </ul>
     *
     * @param event the budget depleted event
     */
    public void onBudgetDepleted(BudgetDepletedEvent event) {
        LOG.info("Handling budget depleted event for user: {}", event.userId().value());

        // Pause all active market research
        List<MarketResearch> activeResearch = marketResearchRepository.findActiveByOwnerId(event.userId());
        int researchPaused = 0;
        for (MarketResearch research : activeResearch) {
            if (!research.isPausedForExternalReason()) {
                research.pauseForExternalReason(BUDGET_DEPLETED_REASON);
                marketResearchRepository.save(research);
                researchPaused++;
                LOG.debug("Paused market research {} due to budget depletion", research.getId());
            }
        }

        // Pause all running simulations
        List<Simulation> runningSimulations = simulationRepository.findRunningByOwnerId(event.userId());
        int simulationsPaused = 0;
        for (Simulation simulation : runningSimulations) {
            if (!simulation.isPausedForExternalReason()) {
                simulationRepository.updatePauseState(simulation.getSimulationId(), simulation.isPaused(), true, BUDGET_DEPLETED_REASON);
                simulationsPaused++;
                LOG.debug("Paused simulation {} due to budget depletion", simulation.getSimulationId().value());
            }
        }

        LOG.info("Budget depletion handled for user {}: {} research paused, {} simulations paused",
            event.userId().value(), researchPaused, simulationsPaused);
    }

    /**
     * Handle budget restored event by resuming entities that were paused due to budget.
     *
     * <p>Only resumes entities that were specifically paused for BUDGET_DEPLETED reason.</p>
     *
     * @param event the budget restored event
     */
    public void onBudgetRestored(BudgetRestoredEvent event) {
        LOG.info("Handling budget restored event for user: {}", event.userId().value());

        // Resume market research that was paused due to budget
        List<MarketResearch> pausedResearch = marketResearchRepository.findPausedForExternalReasonByOwnerId(event.userId());
        int researchResumed = 0;
        for (MarketResearch research : pausedResearch) {
            if (BUDGET_DEPLETED_REASON.equals(research.getExternalPauseReason())) {
                research.resumeFromExternalPause();
                marketResearchRepository.save(research);
                researchResumed++;
                LOG.debug("Resumed market research {} after budget restored", research.getId());
            }
        }

        // Resume simulations that were paused due to budget
        List<Simulation> pausedSimulations = simulationRepository.findPausedForExternalReasonByOwnerId(event.userId());
        int simulationsResumed = 0;
        for (Simulation simulation : pausedSimulations) {
            if (BUDGET_DEPLETED_REASON.equals(simulation.getExternalPauseReason())) {
                simulation.resumeFromExternalPause();
                simulationRepository.save(simulation);
                simulationsResumed++;
                LOG.debug("Resumed simulation {} after budget restored", simulation.getSimulationId().value());
            }
        }

        LOG.info("Budget restoration handled for user {}: {} research resumed, {} simulations resumed",
            event.userId().value(), researchResumed, simulationsResumed);
    }

    /**
     * Handle budget warning event by dispatching a single SIMULATION_LOW_BUDGET notification.
     * One-shot per warning-threshold crossing — UserBudget enforces this at the aggregate level
     * via the previousStatus/newStatus transition guard, so we don't need a flag here.
     */
    public void onBudgetWarning(BudgetWarningEvent event) {
        if (dispatchNotificationUseCase == null) {
            return; // notification subsystem not wired (e.g. minimal test bootstrap)
        }
        LOG.info("Dispatching SIMULATION_LOW_BUDGET notification for user {} (usage {}%)",
            event.userId().value(), event.usagePercentage());
        try {
            BigDecimal remainingPct = event.usagePercentage() == null
                    ? BigDecimal.ZERO
                    : BigDecimal.valueOf(100).subtract(event.usagePercentage()).setScale(0, RoundingMode.HALF_UP);
            Map<String, Object> payload = new HashMap<>();
            payload.put("usagePercentage", event.usagePercentage() == null ? "" : event.usagePercentage().toPlainString());
            payload.put("remainingPct", remainingPct.toPlainString());
            if (event.budgetLimit() != null) {
                payload.put("budgetLimit", event.budgetLimit().toPlainString());
            }
            if (event.currentSpend() != null) {
                payload.put("currentSpend", event.currentSpend().toPlainString());
            }
            String title = "Your AI budget is running low";
            String body = "Top up before it runs out to keep your simulations and research running.";
            dispatchNotificationUseCase.execute(new DispatchNotificationCommand(
                event.userId(),
                NotificationType.SIMULATION_LOW_BUDGET,
                title,
                body,
                payload,
                null
            ));
        } catch (Exception e) {
            LOG.warn("Failed to dispatch SIMULATION_LOW_BUDGET notification for user {}: {}",
                event.userId().value(), e.getMessage());
        }
    }
}
