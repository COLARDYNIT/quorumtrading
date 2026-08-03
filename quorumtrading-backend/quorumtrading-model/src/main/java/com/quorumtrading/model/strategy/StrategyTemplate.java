package com.quorumtrading.model.strategy;

import com.quorumtrading.model.valueobject.IndicatorType;
import java.util.Set;

/**
 * Strategy template contract for generating strategy markdown.
 * Each implementation represents a specific trading pattern.
 *
 * <p><b>DDD Classification:</b> Domain Service Interface</p>
 * Defines behavior for strategy template implementations.
 *
 * <p><b>Strategy Pattern:</b></p>
 * Each template encapsulates a different trading strategy.
 * Templates are selected by LLM based on market conditions.
 */
public interface StrategyTemplate {

    /**
     * Get the unique template identifier.
     */
    StrategyTemplateType getType();

    /**
     * Generate strategy markdown based on market context and parameters.
     *
     * @param context Market context (tickers, rationale)
     * @param parameters LLM-suggested parameters (MA periods, RSI thresholds, etc.)
     * @return Complete strategy markdown with YAML frontmatter
     */
    String generateStrategyMarkdown(MarketContext context, TemplateParameters parameters);

    /**
     * Get required indicators for this template.
     * Used by IndicatorCalculationService to know which indicators to calculate.
     */
    Set<IndicatorType> getRequiredIndicators();
}
