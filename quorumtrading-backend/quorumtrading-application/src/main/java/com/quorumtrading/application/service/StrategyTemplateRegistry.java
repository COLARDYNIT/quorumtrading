package com.quorumtrading.application.service;

import com.quorumtrading.model.strategy.*;
import com.quorumtrading.model.valueobject.IndicatorType;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Registry for all available strategy templates.
 *
 * <p><b>Hexagonal Architecture:</b> Application Layer Service</p>
 * <p>Provides centralized access to all strategy template implementations.</p>
 *
 * <p><b>DDD Classification:</b> Application Service</p>
 * <p>Coordinates template discovery and retrieval for the application layer.</p>
 *
 * <p><b>Design Pattern:</b> Registry Pattern</p>
 */
public class StrategyTemplateRegistry {

    private final Map<StrategyTemplateType, StrategyTemplate> templatesByType;

    /**
     * Initializes the registry with all available strategy templates.
     */
    public StrategyTemplateRegistry() {
        List<StrategyTemplate> allTemplates = Arrays.asList(
            new MovingAverageCrossoverTemplate(),
            new RSIReversalTemplate(),
            new BollingerBreakoutTemplate(),
            new MACDSignalCrossTemplate()
        );

        // Build index by type for O(1) lookup
        this.templatesByType = allTemplates.stream()
            .collect(Collectors.toMap(
                StrategyTemplate::getType,
                template -> template
            ));
    }

    /**
     * Get all registered strategy templates.
     *
     * @return Unmodifiable list of all templates
     */
    public List<StrategyTemplate> getAllTemplates() {
        return Collections.unmodifiableList(
            new ArrayList<>(templatesByType.values())
        );
    }

    /**
     * Get a specific strategy template by type.
     *
     * @param type Template type to retrieve
     * @return Optional containing the template if found, empty otherwise
     */
    public Optional<StrategyTemplate> getTemplate(StrategyTemplateType type) {
        if (type == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(templatesByType.get(type));
    }

    /**
     * Get all templates that use a specific indicator.
     * Useful for suggesting templates based on available market data.
     *
     * @param indicatorType Indicator type to filter by
     * @return List of templates that require this indicator (may be empty)
     */
    public List<StrategyTemplate> getTemplatesByIndicator(IndicatorType indicatorType) {
        if (indicatorType == null) {
            return Collections.emptyList();
        }

        return templatesByType.values().stream()
            .filter(template -> template.getRequiredIndicators().contains(indicatorType))
            .collect(Collectors.toList());
    }

    /**
     * Get the total number of registered templates.
     *
     * @return Template count
     */
    public int getTemplateCount() {
        return templatesByType.size();
    }
}
