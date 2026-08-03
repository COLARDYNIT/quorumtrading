package com.quorumtrading.model.entity;

import com.quorumtrading.model.enums.RecommendationType;
import com.quorumtrading.model.valueobject.LLMUsage;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.Percentage;
import com.quorumtrading.model.valueobject.SimulationId;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

/**
 * Domain entity representing a decision made by a single LLM agent.
 * Pure domain model without persistence concerns.
 *
 * <p><b>Child Entity</b> - Part of the Simulation aggregate.</p>
 *
 * <p><b>Domain Responsibilities:</b></p>
 * <ul>
 *   <li>Encapsulate agent recommendation with confidence scoring</li>
 *   <li>Provide decision quality assessment (confidence, staleness)</li>
 *   <li>Support consensus algorithms (agreement, weighting)</li>
 *   <li>Track decision metadata (timing, model used)</li>
 * </ul>
 *
 * <p><b>Business Rules:</b></p>
 * <ul>
 *   <li>Decisions become stale over time (configurable staleness threshold)</li>
 *   <li>Confidence scores determine decision weight in consensus</li>
 *   <li>Time decay reduces influence of older decisions</li>
 * </ul>
 *
 * @see Simulation (parent aggregate root)
 * @see ConsensusDecision
 */
public class LLMDecision {

    private Long id;

    private SimulationId simulationId;

    private Instant decisionTimestamp;

    private String agentId;

    private String role;

    private String ticker;

    private String llmModel;

    /** What the resolution chain asked for; null when it matched {@link #llmModel} or is unknown. */
    private String requestedModel;

    private String promptUsed;

    private String reasoning; // JSON representation

    private RecommendationType recommendation;

    private String recommendedAction; // JSON representation

    private Percentage confidenceScore;

    private Integer executionTimeMs;

    private BigDecimal tickerQuantity; // Quantity recommended by agent

    private LLMUsage llmUsage; // Token usage and cost tracking

    private String indicatorSnapshot; // JSON snapshot of technical indicators at decision time

    private String uiPayload; // Optional A2UI declarative UI payload (JSON)

    public LLMDecision() {
    }

    public LLMDecision(
            SimulationId simulationId,
            Instant decisionTimestamp,
            String agentId,
            String role,
            String llmModel
    ) {
        this.simulationId = simulationId;
        this.decisionTimestamp = decisionTimestamp;
        this.agentId = agentId;
        this.role = role;
        this.llmModel = llmModel;
    }

    // Domain methods for proper state management

    /**
     * Records the agent's confidence in their recommendation.
     * Validates that confidence is between 0.0 and 1.0.
     *
     * @param score The confidence score (0.0 - 1.0)
     * @throws IllegalArgumentException if score is null or outside valid range
     */
    public void recordConfidenceScore(Percentage score) {
        if (score == null) {
            throw new IllegalArgumentException("Confidence score cannot be null");
        }
        double value = score.value().doubleValue();
        if (value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(
                String.format("Confidence must be between 0.0 and 1.0, got %.2f", value)
            );
        }
        this.confidenceScore = score;
    }

    /**
     * Records the agent's recommendation type.
     * Validates that recommendation is not null.
     *
     * @param recommendation The recommendation type
     * @throws IllegalArgumentException if recommendation is null
     */
    public void recordRecommendation(RecommendationType recommendation) {
        if (recommendation == null) {
            throw new IllegalArgumentException("Recommendation type cannot be null");
        }
        this.recommendation = recommendation;
    }

    /**
     * Records the agent's reasoning for the recommendation.
     * Validates that reasoning is not null or empty.
     *
     * @param reasoning The reasoning text (usually JSON)
     * @throws IllegalArgumentException if reasoning is null or empty
     */
    public void recordReasoning(String reasoning) {
        if (reasoning == null || reasoning.trim().isEmpty()) {
            throw new IllegalArgumentException("Reasoning cannot be null or empty");
        }
        this.reasoning = reasoning;
    }

    /**
     * Records the agent's recommended action.
     * Validates that action is not null or empty.
     *
     * @param action The recommended action (usually JSON)
     * @throws IllegalArgumentException if action is null or empty
     */
    public void recordRecommendedAction(String action) {
        if (action == null || action.trim().isEmpty()) {
            throw new IllegalArgumentException("Recommended action cannot be null or empty");
        }
        this.recommendedAction = action;
    }

    /**
     * Records the execution time for the LLM query.
     * Validates that time is non-negative.
     *
     * @param timeMs Execution time in milliseconds
     * @throws IllegalArgumentException if timeMs is negative
     */
    public void recordExecutionTime(Integer timeMs) {
        if (timeMs != null && timeMs < 0) {
            throw new IllegalArgumentException(
                String.format("Execution time cannot be negative: %d ms", timeMs)
            );
        }
        this.executionTimeMs = timeMs;
    }

    /**
     * Records the ticker symbol this decision applies to.
     * Validates that ticker is not null or empty.
     *
     * @param ticker The ticker symbol
     * @throws IllegalArgumentException if ticker is null or empty
     */
    public void recordTicker(String ticker) {
        if (ticker == null || ticker.trim().isEmpty()) {
            throw new IllegalArgumentException("Ticker symbol cannot be null or empty");
        }
        this.ticker = ticker;
    }

    /**
     * Records the recommended quantity to trade.
     * Validates that quantity is non-negative.
     *
     * @param quantity The quantity to trade
     * @throws IllegalArgumentException if quantity is negative
     */
    public void recordQuantity(BigDecimal quantity) {
        if (quantity != null && quantity.signum() < 0) {
            throw new IllegalArgumentException(
                String.format("Quantity cannot be negative: %s", quantity)
            );
        }
        this.tickerQuantity = quantity;
    }

    /**
     * Records the LLM usage for this decision (token counts and cost).
     *
     * @param usage The LLM usage metrics
     */
    public void recordLLMUsage(LLMUsage usage) {
        this.llmUsage = usage;
    }

    // Business methods (query/analysis methods - no state changes)

    /**
     * Checks if the decision meets a specific confidence threshold.
     *
     * @param threshold Confidence threshold to check against
     * @return true if confidence score meets or exceeds threshold
     */
    public boolean isHighConfidence(Percentage threshold) {
        if (confidenceScore == null || threshold == null) {
            return false;
        }
        return confidenceScore.value().compareTo(threshold.value()) >= 0;
    }

    /**
     * Checks if the decision meets the default high confidence threshold (80%).
     *
     * @return true if confidence score >= 0.80
     */
    public boolean isHighConfidence() {
        return confidenceScore != null && confidenceScore.value().doubleValue() >= 0.80;
    }

    /**
     * Checks if the decision is stale based on a maximum age.
     * Stale decisions should have reduced weight in consensus or be excluded entirely.
     *
     * @param maxAge Maximum age before decision is considered stale
     * @return true if decision age exceeds maxAge
     */
    public boolean isStale(Duration maxAge) {
        if (decisionTimestamp == null || maxAge == null) {
            return false;
        }
        Duration age = Duration.between(decisionTimestamp, Instant.now());
        return age.compareTo(maxAge) > 0;
    }

    /**
     * Checks if the decision is stale using a default threshold of 1 hour.
     *
     * @return true if decision is older than 1 hour
     */
    public boolean isStale() {
        return isStale(Duration.ofHours(1));
    }

    /**
     * Checks if this decision agrees with another decision (same recommendation type).
     *
     * @param other The other decision to compare against
     * @return true if both decisions have the same recommendation type
     */
    public boolean agreesWith(LLMDecision other) {
        if (other == null || this.recommendation == null || other.recommendation == null) {
            return false;
        }
        return this.recommendation == other.recommendation;
    }

    /**
     * Calculates a weighted score for this decision based on confidence and age.
     * Applies time decay to reduce weight of older decisions.
     *
     * <p><b>Weight Calculation:</b></p>
     * <ul>
     *   <li>Base weight = confidence score (0.0 - 1.0)</li>
     *   <li>If decision is stale (> maxAge): weight *= 0.5 (50% decay)</li>
     *   <li>Result is clamped to [0.0, 1.0]</li>
     * </ul>
     *
     * @param maxAge Maximum age before applying decay factor
     * @return Weighted score between 0.0 and 1.0
     */
    public double calculateWeight(Duration maxAge) {
        if (confidenceScore == null) {
            return 0.0;
        }

        double baseWeight = confidenceScore.value().doubleValue();

        // Apply time decay if decision is stale
        if (isStale(maxAge)) {
            baseWeight *= 0.5;  // 50% decay for stale decisions
        }

        // Clamp to valid range
        return Math.max(0.0, Math.min(1.0, baseWeight));
    }

    /**
     * Calculates a weighted score using default max age of 1 hour.
     *
     * @return Weighted score between 0.0 and 1.0
     */
    public double calculateWeight() {
        return calculateWeight(Duration.ofHours(1));
    }

    /**
     * Gets the age of this decision.
     *
     * @return Duration since decision was made
     */
    public Duration getAge() {
        if (decisionTimestamp == null) {
            return Duration.ZERO;
        }
        return Duration.between(decisionTimestamp, Instant.now());
    }

    public boolean isBuyRecommendation() {
        return recommendation == RecommendationType.BUY;
    }

    public boolean isSellRecommendation() {
        return recommendation == RecommendationType.SELL;
    }

    public boolean isHoldRecommendation() {
        return recommendation == RecommendationType.HOLD;
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

    public String getAgentId() {
        return agentId;
    }

    public void setAgentId(String agentId) {
        this.agentId = agentId;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getTicker() {
        return ticker;
    }

    /**
     * @deprecated Use {@link #recordTicker(String)} instead
     */
    @Deprecated(forRemoval = false, since = "1.0.0")
    public void setTicker(String ticker) {
        this.ticker = ticker;
    }

    public String getLlmModel() {
        return llmModel;
    }

    public void setLlmModel(String llmModel) {
        this.llmModel = llmModel;
    }

    /**
     * The model the resolution chain asked for, which may differ from {@link #getLlmModel()} — the
     * one that actually answered.
     *
     * <p>{@code llmModel} stays the <em>actual</em> model and must not be repurposed: it is the one
     * genuinely honest field in this record and every cost and audit surface depends on it. Storing
     * the request alongside it is what makes a substitution visible instead of merely logged.</p>
     */
    public String getRequestedModel() {
        return requestedModel;
    }

    /** Records what was asked for, so a divergence from what answered is queryable rather than lost. */
    public void recordRequestedModel(String requestedModel) {
        this.requestedModel = requestedModel;
    }

    public String getPromptUsed() {
        return promptUsed;
    }

    public void setPromptUsed(String promptUsed) {
        this.promptUsed = promptUsed;
    }

    public String getReasoning() {
        return reasoning;
    }

    /**
     * @deprecated Use {@link #recordReasoning(String)} instead
     */
    @Deprecated(forRemoval = false, since = "1.0.0")
    public void setReasoning(String reasoning) {
        this.reasoning = reasoning;
    }

    public RecommendationType getRecommendation() {
        return recommendation;
    }

    /**
     * @deprecated Use {@link #recordRecommendation(RecommendationType)} instead
     */
    @Deprecated(forRemoval = false, since = "1.0.0")
    public void setRecommendation(RecommendationType recommendation) {
        this.recommendation = recommendation;
    }

    public String getRecommendedAction() {
        return recommendedAction;
    }

    /**
     * @deprecated Use {@link #recordRecommendedAction(String)} instead
     */
    @Deprecated(forRemoval = false, since = "1.0.0")
    public void setRecommendedAction(String recommendedAction) {
        this.recommendedAction = recommendedAction;
    }

    public Percentage getConfidenceScore() {
        return confidenceScore;
    }

    /**
     * @deprecated Use {@link #recordConfidenceScore(Percentage)} instead
     */
    @Deprecated(forRemoval = false, since = "1.0.0")
    public void setConfidenceScore(Percentage confidenceScore) {
        this.confidenceScore = confidenceScore;
    }

    public Integer getExecutionTimeMs() {
        return executionTimeMs;
    }

    /**
     * @deprecated Use {@link #recordExecutionTime(Integer)} instead
     */
    @Deprecated(forRemoval = false, since = "1.0.0")
    public void setExecutionTimeMs(Integer executionTimeMs) {
        this.executionTimeMs = executionTimeMs;
    }

    public BigDecimal getTickerQuantity() {
        return tickerQuantity;
    }

    /**
     * @deprecated Use {@link #recordQuantity(BigDecimal)}} instead
     */
    @Deprecated(forRemoval = false, since = "1.0.0")
    public void setTickerQuantity(BigDecimal tickerQuantity) {
        this.tickerQuantity = tickerQuantity;
    }

    public LLMUsage getLLMUsage() {
        return llmUsage != null ? llmUsage : LLMUsage.ZERO;
    }

    public void setLLMUsage(LLMUsage llmUsage) {
        this.llmUsage = llmUsage;
    }

    /**
     * Get the billed cost for this decision (raw cost with the platform markup applied).
     */
    public Money getBilledCost() {
        return getLLMUsage().billedCostAsMoney();
    }

    /**
     * Get the raw LLM cost for this decision.
     */
    public Money getRawCost() {
        return getLLMUsage().rawCostAsMoney();
    }

    /**
     * Get the technical indicator snapshot at decision time.
     * Contains JSON with RSI, MACD, SMA, Bollinger Bands values.
     */
    public String getIndicatorSnapshot() {
        return indicatorSnapshot;
    }

    /**
     * Records the technical indicator snapshot at decision time.
     * Used for audit trail and decision traceability.
     *
     * @param indicatorSnapshot JSON representation of indicator values
     */
    public void recordIndicatorSnapshot(String indicatorSnapshot) {
        this.indicatorSnapshot = indicatorSnapshot;
    }

    public void setIndicatorSnapshot(String indicatorSnapshot) {
        this.indicatorSnapshot = indicatorSnapshot;
    }

    /**
     * Records an optional A2UI declarative UI payload.
     * Used by the orchestrator agent to attach visual analysis components.
     *
     * @param uiPayload JSON A2UI payload, or null if not provided
     */
    public void recordUiPayload(String uiPayload) {
        this.uiPayload = uiPayload;
    }

    public String getUiPayload() {
        return uiPayload;
    }

    public void setUiPayload(String uiPayload) {
        this.uiPayload = uiPayload;
    }
}
