package com.quorumtrading.model.entity;

import com.quorumtrading.model.enums.ConsensusMethod;
import com.quorumtrading.model.enums.ExecutionOutcome;
import com.quorumtrading.model.enums.InvestmentType;
import com.quorumtrading.model.valueobject.ConsensusAction;
import com.quorumtrading.model.valueobject.LLMUsage;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.SimulationId;
import com.quorumtrading.model.valueobject.Symbol;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Pure domain entity representing a consensus decision made by multiple agents.
 * This is the core business object without persistence concerns.
 */
public class ConsensusDecision {

    private Long id;
    private SimulationId simulationId;
    private Instant decisionTimestamp;
    private List<Long> llmDecisionIds;
    private ConsensusMethod consensusMethod;
    private ConsensusAction finalAction; // typed action; raw() carries the persisted JSON form
    private boolean executed;
    private Money executionPrice;
    private BigDecimal quantity; // Quantity to trade (if specified by orchestrator)
    private String notes;
    private Symbol symbol; // Trading symbol (e.g., BTC, AAPL)
    private InvestmentType investmentType; // Asset type (CRYPTO, STOCK)
    private ExecutionOutcome executionOutcome; // Execution outcome (PENDING, EXECUTED, NO_TRADE_REQUIRED, REJECTED)

    // LLM cost tracking
    private LLMUsage llmUsage;

    public ConsensusDecision() {
        this.llmDecisionIds = new ArrayList<>();
        this.executed = false;
        this.executionOutcome = ExecutionOutcome.PENDING;
    }

    public ConsensusDecision(
            SimulationId simulationId,
            Instant decisionTimestamp,
            ConsensusMethod consensusMethod,
            ConsensusAction finalAction
    ) {
        this();
        this.simulationId = simulationId;
        this.decisionTimestamp = decisionTimestamp;
        this.consensusMethod = consensusMethod;
        this.finalAction = finalAction;
    }

    // Business methods
    public void markAsExecuted(Money price) {
        this.executed = true;
        this.executionPrice = price;
    }

    public void addLlmDecisionId(Long decisionId) {
        this.llmDecisionIds.add(decisionId);
    }

    public void addNote(String note) {
        if (this.notes == null) {
            this.notes = note;
        } else {
            this.notes = this.notes + "\n" + note;
        }
    }

    public boolean isExecuted() {
        return executed;
    }

    // Getters and setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public SimulationId getSimulationId() {
        return simulationId;
    }

    public void setSimulationId(SimulationId simulationId) {
        this.simulationId = simulationId;
    }

    public Instant getDecisionTimestamp() {
        return decisionTimestamp;
    }

    public void setDecisionTimestamp(Instant decisionTimestamp) {
        this.decisionTimestamp = decisionTimestamp;
    }

    public List<Long> getLlmDecisionIds() {
        return new ArrayList<>(llmDecisionIds);
    }

    public void setLlmDecisionIds(List<Long> llmDecisionIds) {
        this.llmDecisionIds = new ArrayList<>(llmDecisionIds);
    }

    public ConsensusMethod getConsensusMethod() {
        return consensusMethod;
    }

    public void setConsensusMethod(ConsensusMethod consensusMethod) {
        this.consensusMethod = consensusMethod;
    }

    /** The typed, parsed-once view of the final action. */
    public ConsensusAction getAction() {
        return finalAction;
    }

    /**
     * The raw string form of the final action — the representation persisted in
     * {@code consensus_decisions.final_action} and exposed on every DTO. Kept so the
     * REST/MCP/WebSocket wire format is unchanged by the typed-action refactor.
     */
    public String getFinalAction() {
        return finalAction != null ? finalAction.raw() : null;
    }

    public void setFinalAction(ConsensusAction finalAction) {
        this.finalAction = finalAction;
    }

    public boolean getExecuted() {
        return executed;
    }

    public void setExecuted(boolean executed) {
        this.executed = executed;
    }

    public Money getExecutionPrice() {
        return executionPrice;
    }

    public void setExecutionPrice(Money executionPrice) {
        this.executionPrice = executionPrice;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public Symbol getSymbol() {
        return symbol;
    }

    public void setSymbol(Symbol symbol) {
        this.symbol = symbol;
    }

    public InvestmentType getInvestmentType() {
        return investmentType;
    }

    public void setInvestmentType(InvestmentType investmentType) {
        this.investmentType = investmentType;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public ExecutionOutcome getExecutionOutcome() {
        return executionOutcome;
    }

    public void setExecutionOutcome(ExecutionOutcome executionOutcome) {
        this.executionOutcome = executionOutcome;
    }

    // LLM cost tracking methods

    /**
     * Add LLM usage to this decision (accumulates with existing usage).
     */
    public void addLLMUsage(LLMUsage usage) {
        if (usage == null) {
            return;
        }
        if (this.llmUsage == null) {
            this.llmUsage = usage;
        } else {
            this.llmUsage = this.llmUsage.add(usage);
        }
    }

    public LLMUsage getLLMUsage() {
        return llmUsage != null ? llmUsage : LLMUsage.ZERO;
    }

    public void setLLMUsage(LLMUsage llmUsage) {
        this.llmUsage = llmUsage;
    }

    /**
     * Get the billed cost for this consensus decision (raw cost with the platform markup applied).
     */
    public Money getBilledCost() {
        return getLLMUsage().billedCostAsMoney();
    }

    /**
     * Get the raw LLM cost for this consensus decision.
     */
    public Money getRawCost() {
        return getLLMUsage().rawCostAsMoney();
    }
}
