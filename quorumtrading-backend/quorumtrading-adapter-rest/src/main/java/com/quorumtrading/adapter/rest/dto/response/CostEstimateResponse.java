package com.quorumtrading.adapter.rest.dto.response;

import com.quorumtrading.model.valueobject.CostEstimate;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * Response DTO for cost estimation results.
 */
@Schema(description = "Estimated AI budget cost for a backtest or benchmark")
public record CostEstimateResponse(
    @Schema(description = "Best-estimate total cost in USD", example = "9.15")
    BigDecimal estimatedCost,

    @Schema(description = "Lower bound (80% of estimate)", example = "7.32")
    BigDecimal lowerBound,

    @Schema(description = "Upper bound (120% of estimate)", example = "10.98")
    BigDecimal upperBound,

    @Schema(description = "Number of trading days", example = "130")
    int tradingDays,

    @Schema(description = "Total LLM API calls expected", example = "6500")
    long totalLLMCalls,

    @Schema(description = "Number of tickers per round", example = "16")
    int tickerCount,

    @Schema(description = "Agents per consensus round (incl. orchestrator)", example = "5")
    int agentsPerRound,

    @Schema(description = "Model ID that will be used", example = "google/gemini-2.5-flash-preview-05-20")
    String modelId,

    @Schema(description = "Human-readable model name", example = "Gemini 2.5 Flash")
    String modelName,

    @Schema(description = "Cost per LLM call in USD", example = "0.00140660")
    BigDecimal costPerCall
) {
    public static CostEstimateResponse fromDomain(CostEstimate estimate) {
        return new CostEstimateResponse(
            estimate.estimatedCost(),
            estimate.lowerBound(),
            estimate.upperBound(),
            estimate.tradingDays(),
            estimate.totalLLMCalls(),
            estimate.tickerCount(),
            estimate.agentsPerRound(),
            estimate.modelId(),
            estimate.modelName(),
            estimate.costPerCall()
        );
    }
}
