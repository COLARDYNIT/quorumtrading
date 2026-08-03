package com.quorumtrading.model.valueobject;

import com.quorumtrading.model.service.PlatformMarkup;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Value object representing LLM usage metrics and costs.
 * Captures token counts and associated costs from OpenRouter API responses.
 *
 * <p>
 * Cost Calculation:
 * - rawCost: The actual cost from OpenRouter (in USD)
 * - billedCost: rawCost * the platform markup rate ({@link PlatformMarkup})
 * </p>
 */
public record LLMUsage(
    String model,
    int promptTokens,
    int completionTokens,
    int totalTokens,
    BigDecimal rawCost
) {

    /** Minimum billable cost to prevent DECIMAL(10,6) storage rounding a real cost to zero */
    public static final BigDecimal MINIMUM_BILLABLE_COST = new BigDecimal("0.000001");

    public static final LLMUsage ZERO = new LLMUsage(null, 0, 0, 0, BigDecimal.ZERO);

    public LLMUsage {
        if (promptTokens < 0) {
            throw new IllegalArgumentException("Prompt tokens cannot be negative");
        }
        if (completionTokens < 0) {
            throw new IllegalArgumentException("Completion tokens cannot be negative");
        }
        if (totalTokens < 0) {
            throw new IllegalArgumentException("Total tokens cannot be negative");
        }
        if (rawCost == null) {
            rawCost = BigDecimal.ZERO;
        }
        if (rawCost.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Raw cost cannot be negative");
        }
    }

    /**
     * Calculate the billed cost using the configured platform markup rate.
     */
    public BigDecimal billedCost() {
        return billedCost(PlatformMarkup.rate());
    }

    /**
     * Calculate the billed cost using an explicit markup rate.
     *
     * @param markupRate The markup rate (e.g., 1.25 for 25% markup)
     */
    public BigDecimal billedCost(BigDecimal markupRate) {
        if (markupRate == null) {
            markupRate = PlatformMarkup.rate();
        }
        BigDecimal result = rawCost.multiply(markupRate).setScale(8, RoundingMode.HALF_UP);
        // Enforce minimum so DECIMAL(10,6) storage never rounds a real cost to zero
        if (result.compareTo(BigDecimal.ZERO) > 0 && result.compareTo(MINIMUM_BILLABLE_COST) < 0) {
            return MINIMUM_BILLABLE_COST;
        }
        return result;
    }

    /**
     * Create Money value object for the billed cost using the configured platform markup rate.
     */
    public Money billedCostAsMoney() {
        return Money.usd(billedCost());
    }

    /**
     * Create Money value object for the billed cost using an explicit markup rate.
     *
     * @param markupRate The markup rate (e.g., 1.25 for 25% markup)
     */
    public Money billedCostAsMoney(BigDecimal markupRate) {
        return Money.usd(billedCost(markupRate));
    }

    /**
     * Create Money value object for the raw cost.
     */
    public Money rawCostAsMoney() {
        return Money.usd(rawCost);
    }

    /**
     * Add two LLMUsage instances together (for accumulating costs).
     * The model from the first usage is retained (aggregated usage may have multiple models).
     */
    public LLMUsage add(LLMUsage other) {
        if (other == null) {
            return this;
        }
        // When aggregating, use this.model (or other.model if this.model is null)
        String aggregatedModel = this.model != null ? this.model : other.model;
        return new LLMUsage(
            aggregatedModel,
            this.promptTokens + other.promptTokens,
            this.completionTokens + other.completionTokens,
            this.totalTokens + other.totalTokens,
            this.rawCost.add(other.rawCost)
        );
    }

    /**
     * Create LLMUsage from OpenRouter API response values with model info.
     *
     * @param model            The model used (e.g., "openai/gpt-4o")
     * @param promptTokens     Number of tokens in the prompt
     * @param completionTokens Number of tokens in the completion
     * @param cost             Cost in USD from OpenRouter
     */
    public static LLMUsage of(String model, int promptTokens, int completionTokens, BigDecimal cost) {
        return new LLMUsage(model, promptTokens, completionTokens, promptTokens + completionTokens, cost);
    }

    /**
     * Create LLMUsage when cost is provided as a double.
     *
     * @param model            The model used (e.g., "openai/gpt-4o")
     * @param promptTokens     Number of tokens in the prompt
     * @param completionTokens Number of tokens in the completion
     * @param cost             Cost in USD from OpenRouter
     */
    public static LLMUsage of(String model, int promptTokens, int completionTokens, double cost) {
        return new LLMUsage(model, promptTokens, completionTokens, promptTokens + completionTokens, BigDecimal.valueOf(cost));
    }

    /**
     * Create LLMUsage from OpenRouter API response values (without model, for backward compatibility).
     *
     * @param promptTokens     Number of tokens in the prompt
     * @param completionTokens Number of tokens in the completion
     * @param totalTokens      Total token count
     * @param cost             Cost in USD from OpenRouter
     */
    public static LLMUsage of(int promptTokens, int completionTokens, int totalTokens, BigDecimal cost) {
        return new LLMUsage(null, promptTokens, completionTokens, totalTokens, cost);
    }

    /**
     * Create LLMUsage when cost is provided as a double (without model, for backward compatibility).
     */
    public static LLMUsage of(int promptTokens, int completionTokens, int totalTokens, double cost) {
        return new LLMUsage(null, promptTokens, completionTokens, totalTokens, BigDecimal.valueOf(cost));
    }
}
