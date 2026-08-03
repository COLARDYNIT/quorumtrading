package com.quorumtrading.adapter.rest.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;

/**
 * LLM decision response DTO matching frontend expectations.
 * Maps from application layer LLMDecision.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LLMDecisionResponse {

    public String id;
    public String simulationId;
    public String agentType;
    public String decisionTimestamp;
    public String reasoning;
    public String suggestedAction;
    public double confidence;
    public String ticker;  // Ticker symbol for the asset this decision is about

    // LLM cost tracking fields
    public String modelUsed;      // LLM model used (e.g., "openai/gpt-4o")
    public Integer promptTokens;  // Number of prompt tokens
    public Integer completionTokens;  // Number of completion tokens
    public Integer totalTokens;   // Total tokens (prompt + completion)
    public BigDecimal rawCostUsd; // Raw cost in USD (before markup)
    public Integer executionTimeMs; // LLM execution time in ms (0 = cached decision)

    // Technical indicators snapshot
    public String indicatorSnapshot; // JSON snapshot of technical indicators at decision time

    // A2UI declarative UI payload
    public String uiPayload; // Optional A2UI JSON payload for visual analysis

    public LLMDecisionResponse() {}

    public LLMDecisionResponse(String id, String simulationId, String agentType,
                               String decisionTimestamp, String reasoning,
                               String suggestedAction, double confidence, String ticker,
                               String modelUsed, Integer promptTokens, Integer completionTokens,
                               Integer totalTokens, BigDecimal rawCostUsd, Integer executionTimeMs,
                               String indicatorSnapshot, String uiPayload) {
        this.id = id;
        this.simulationId = simulationId;
        this.agentType = agentType;
        this.decisionTimestamp = decisionTimestamp;
        this.reasoning = reasoning;
        this.suggestedAction = suggestedAction;
        this.confidence = confidence;
        this.ticker = ticker;
        this.modelUsed = modelUsed;
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
        this.totalTokens = totalTokens;
        this.rawCostUsd = rawCostUsd;
        this.executionTimeMs = executionTimeMs;
        this.indicatorSnapshot = indicatorSnapshot;
        this.uiPayload = uiPayload;
    }
}
