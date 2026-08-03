package com.quorumtrading.application.event.listener;

import com.quorumtrading.application.event.ResearchExecutionCompleted;

/**
 * Listener interface for research execution domain events.
 * <p>
 * Implementations of this interface handle the completion of market research executions,
 * particularly for triggering automatic backtesting when a strategy is successfully created.
 * </p>
 * <p>
 * The typical implementation will:
 * <ul>
 *     <li>Observe {@link ResearchExecutionCompleted} events from the event bus</li>
 *     <li>Check if auto-backtesting should be triggered using {@link ResearchExecutionCompleted#shouldTriggerBacktest()}</li>
 *     <li>If yes, invoke the CreateSimulationUseCase to create a backtest simulation</li>
 *     <li>Handle any errors during backtest creation gracefully (log and continue)</li>
 * </ul>
 * </p>
 * <p>
 * Example implementation using Quarkus CDI:
 * <pre>
 * {@code
 * @ApplicationScoped
 * public class AutoBacktestListener implements ResearchExecutionEventListener {
 *     @Inject
 *     CreateSimulationUseCase createSimulation;
 *
 *     @Override
 *     @Transactional
 *     public void onExecutionCompleted(@Observes ResearchExecutionCompleted event) {
 *         if (event.shouldTriggerBacktest()) {
 *             // Create backtest simulation
 *         }
 *     }
 * }
 * }
 * </pre>
 * </p>
 *
 * @see ResearchExecutionCompleted
 */
public interface ResearchExecutionEventListener {

    /**
     * Handles the completion of a research execution.
     * <p>
     * This method is invoked when a market research execution completes,
     * regardless of the outcome. Implementations should check
     * {@link ResearchExecutionCompleted#shouldTriggerBacktest()} to determine
     * if automatic backtesting should be initiated.
     * </p>
     *
     * @param event The research execution completed event containing execution details
     */
    void onExecutionCompleted(ResearchExecutionCompleted event);
}
