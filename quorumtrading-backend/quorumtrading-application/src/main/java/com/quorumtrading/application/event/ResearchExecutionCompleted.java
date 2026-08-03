package com.quorumtrading.application.event;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Domain event published when a market research execution completes.
 * <p>
 * This event is used to trigger auto-backtesting if configured. The event
 * includes the execution outcome and whether automatic backtesting should
 * be initiated.
 * </p>
 * <p>
 * Event Flow:
 * <pre>
 * MarketResearchOrchestrator
 *     ↓ (executes research)
 * ResearchExecutionCompleted EVENT
 *     ↓ (published to event bus)
 * ResearchExecutionEventListener
 *     ↓ (if shouldTriggerBacktest())
 * CreateSimulationUseCase
 *     ↓ (creates backtest simulation)
 * SimulationOrchestrator
 * </pre>
 * </p>
 *
 * @param executionId         The unique identifier of the research execution
 * @param marketResearchId    The unique identifier of the market research configuration
 * @param createdStrategyId   The ID of the strategy created by this execution (nullable - only present if outcome is STRATEGY_CREATED)
 * @param autoBacktestEnabled Whether automatic backtesting is enabled for this research
 * @param occurredAt          The timestamp when the event occurred
 */
public record ResearchExecutionCompleted(
        UUID executionId,
        UUID marketResearchId,
        UUID createdStrategyId,
        boolean autoBacktestEnabled,
        Instant occurredAt
) {
    /**
     * Compact constructor with validation.
     * Ensures required fields are not null while allowing createdStrategyId to be null
     * (for NO_OPPORTUNITY and FAILED outcomes).
     */
    public ResearchExecutionCompleted {
        Objects.requireNonNull(executionId, "Execution ID cannot be null");
        Objects.requireNonNull(marketResearchId, "Market research ID cannot be null");
        Objects.requireNonNull(occurredAt, "Occurred at cannot be null");
        // createdStrategyId is nullable - only set when outcome is STRATEGY_CREATED
    }

    /**
     * Factory method for convenient event creation with automatic timestamp.
     *
     * @param executionId         The unique identifier of the research execution
     * @param marketResearchId    The unique identifier of the market research configuration
     * @param createdStrategyId   The ID of the strategy created (nullable)
     * @param autoBacktestEnabled Whether automatic backtesting is enabled
     * @return A new ResearchExecutionCompleted event with current timestamp
     */
    public static ResearchExecutionCompleted of(
            UUID executionId,
            UUID marketResearchId,
            UUID createdStrategyId,
            boolean autoBacktestEnabled
    ) {
        return new ResearchExecutionCompleted(
                executionId,
                marketResearchId,
                createdStrategyId,
                autoBacktestEnabled,
                Instant.now()
        );
    }

    /**
     * Determines whether this event should trigger automatic backtesting.
     * <p>
     * Backtesting is triggered only when:
     * <ul>
     *     <li>Auto-backtesting is enabled for this research, AND</li>
     *     <li>A strategy was successfully created (createdStrategyId is not null)</li>
     * </ul>
     * </p>
     *
     * @return true if automatic backtesting should be triggered, false otherwise
     */
    public boolean shouldTriggerBacktest() {
        return autoBacktestEnabled && createdStrategyId != null;
    }
}
