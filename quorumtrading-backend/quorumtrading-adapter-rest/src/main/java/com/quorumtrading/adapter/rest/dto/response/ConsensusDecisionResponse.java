package com.quorumtrading.adapter.rest.dto.response;

import java.math.BigDecimal;

/**
 * Consensus decision response DTO matching frontend expectations.
 * Maps from application layer ConsensusDecision.
 */
public class ConsensusDecisionResponse {

    public String id;
    public String simulationId;
    public String decisionTimestamp;
    public String consensusMethod;
    public String finalAction;
    public boolean executed;
    public String executionOutcome; // PENDING, EXECUTED, NO_TRADE_REQUIRED, REJECTED
    public String notes;
    public String symbol;
    public String investmentType;
    /** Billed cost in USD (raw LLM cost with the platform markup applied) */
    public BigDecimal billedCostUsd;
    /** Execution price when trade was executed (null if not executed or HOLD) */
    public BigDecimal executionPrice;
    /** Quantity traded (null if not executed or HOLD) */
    public BigDecimal quantity;

    public ConsensusDecisionResponse() {}

    public ConsensusDecisionResponse(String id, String simulationId, String decisionTimestamp,
                                     String consensusMethod, String finalAction,
                                     boolean executed, String executionOutcome, String notes,
                                     String symbol, String investmentType, BigDecimal billedCostUsd,
                                     BigDecimal executionPrice, BigDecimal quantity) {
        this.id = id;
        this.simulationId = simulationId;
        this.decisionTimestamp = decisionTimestamp;
        this.consensusMethod = consensusMethod;
        this.finalAction = finalAction;
        this.executed = executed;
        this.executionOutcome = executionOutcome;
        this.notes = notes;
        this.symbol = symbol;
        this.investmentType = investmentType;
        this.billedCostUsd = billedCostUsd;
        this.executionPrice = executionPrice;
        this.quantity = quantity;
    }
}
