package com.quorumtrading.application.usecase;

import com.quorumtrading.application.usecase.command.ReachConsensusCommand;

/**
 * Use case for reaching consensus across all AI agents for trading decisions.
 *
 * For each investment type + symbol combination in the portfolio (and cash at hand),
 * this use case will:
 * 1. Query each agent (Technical, Fundamental, Sentiment) for recommendations
 * 2. Store each LLMDecision and fire WebSocket event for real-time updates
 * 3. Query orchestrator agent with all agent decisions for final decision
 * 4. Store orchestrator LLMDecision and fire WebSocket event
 * 5. Use orchestrator's decision to determine final action (BUY/HOLD/SELL)
 * 6. Create and store ConsensusDecision based on orchestrator's recommendation
 */
public interface ReachConsensusUseCase {

    /**
     * Execute the consensus-reaching process.
     *
     * @param command the command containin.execg portfolio, market events, simulation, and strategy
     */
    void execute(ReachConsensusCommand command);
}
