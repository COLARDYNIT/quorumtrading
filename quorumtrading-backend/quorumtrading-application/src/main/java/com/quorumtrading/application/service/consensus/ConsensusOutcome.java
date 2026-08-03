package com.quorumtrading.application.service.consensus;

import com.quorumtrading.model.entity.LLMDecision;
import com.quorumtrading.model.valueobject.ConsensusAction;

import java.math.BigDecimal;
import java.util.List;

/**
 * Generalized result of consensus evaluation for a single ticker.
 * For MAJORITY: no orchestrator decision, action comes from agent votes.
 * For ORCHESTRATOR_OVERRIDE: orchestrator decision present, action from orchestrator.
 *
 * <p>{@code quantity} is kept alongside the typed action because the orchestrator's
 * {@code tickerQuantity} takes precedence over the quantity embedded in the action
 * JSON (historical behavior preserved).</p>
 */
public record ConsensusOutcome(
    ConsensusAction action,
    BigDecimal quantity,
    LLMDecision orchestratorDecision, // null for MAJORITY
    List<LLMDecision> agentDecisions,
    String note
) {}
