package com.quorumtrading.application.usecase;

import com.quorumtrading.model.entity.ConsensusDecision;

/**
 * Use case for executing consensus decisions by placing trades through the broker.
 * This use case:
 * 1. Determines if the decision requires a trade (BUY/SELL vs HOLD)
 * 2. Executes the trade through the BrokerPort
 * 3. Updates the portfolio snapshot
 * 4. Marks the decision as executed
 * 5. Broadcasts WebSocket events for real-time updates
 */
public interface ExecuteConsensusDecisionUseCase {

    /**
     * Execute a consensus decision if it requires trading action.
     *
     * @param decision The consensus decision to execute
     * @return ExecutionResult containing trade details and updated portfolio
     */
    ExecutionResult execute(ConsensusDecision decision);

    /**
     * Result of executing a consensus decision.
     */
    record ExecutionResult(
        boolean tradeExecuted,
        String action,
        String message,
        Long portfolioSnapshotId
    ) {
        public static ExecutionResult noTradeRequired(String action) {
            return new ExecutionResult(false, action, "No trade required for " + action + " decision", null);
        }

        public static ExecutionResult noTradeRequired(String action, Long snapshotId) {
            return new ExecutionResult(false, action, "No trade required for " + action + " decision", snapshotId);
        }

        public static ExecutionResult tradeExecuted(String action, String message, Long snapshotId) {
            return new ExecutionResult(true, action, message, snapshotId);
        }

        public static ExecutionResult tradeFailed(String action, String reason) {
            return new ExecutionResult(false, action, "Trade failed: " + reason, null);
        }
    }
}
