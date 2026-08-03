package com.quorumtrading.application.service;

import com.quorumtrading.model.strategy.*;
import com.quorumtrading.model.valueobject.IndicatorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for StrategyTemplateRegistry.
 * Verifies template registration and retrieval logic.
 */
class StrategyTemplateRegistryTest {

    private StrategyTemplateRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new StrategyTemplateRegistry();
    }

    @Test
    void getAllTemplates_shouldReturnAllFourTemplates() {
        // When
        List<StrategyTemplate> templates = registry.getAllTemplates();

        // Then
        assertThat(templates).hasSize(4);
        assertThat(templates).extracting(StrategyTemplate::getType)
            .containsExactlyInAnyOrder(
                StrategyTemplateType.MOVING_AVERAGE_CROSSOVER,
                StrategyTemplateType.RSI_REVERSAL,
                StrategyTemplateType.BOLLINGER_BREAKOUT,
                StrategyTemplateType.MACD_SIGNAL_CROSS
            );
    }

    @Test
    void getTemplate_shouldReturnCorrectTemplate_byType() {
        // When
        Optional<StrategyTemplate> maTemplate = registry.getTemplate(StrategyTemplateType.MOVING_AVERAGE_CROSSOVER);
        Optional<StrategyTemplate> rsiTemplate = registry.getTemplate(StrategyTemplateType.RSI_REVERSAL);
        Optional<StrategyTemplate> bbTemplate = registry.getTemplate(StrategyTemplateType.BOLLINGER_BREAKOUT);
        Optional<StrategyTemplate> macdTemplate = registry.getTemplate(StrategyTemplateType.MACD_SIGNAL_CROSS);

        // Then
        assertThat(maTemplate).isPresent();
        assertThat(maTemplate.get()).isInstanceOf(MovingAverageCrossoverTemplate.class);

        assertThat(rsiTemplate).isPresent();
        assertThat(rsiTemplate.get()).isInstanceOf(RSIReversalTemplate.class);

        assertThat(bbTemplate).isPresent();
        assertThat(bbTemplate.get()).isInstanceOf(BollingerBreakoutTemplate.class);

        assertThat(macdTemplate).isPresent();
        assertThat(macdTemplate.get()).isInstanceOf(MACDSignalCrossTemplate.class);
    }

    @Test
    void getTemplate_shouldReturnEmpty_forUnknownType() {
        // Given: Using a type that doesn't exist (hypothetical future type)
        // Since all types are implemented, this test verifies null safety

        // When
        Optional<StrategyTemplate> result = registry.getTemplate(null);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void getTemplatesByIndicator_shouldReturnMaTemplate_forMovingAverage() {
        // When
        List<StrategyTemplate> templates = registry.getTemplatesByIndicator(IndicatorType.MOVING_AVERAGE);

        // Then
        assertThat(templates).hasSize(1);
        assertThat(templates.get(0).getType()).isEqualTo(StrategyTemplateType.MOVING_AVERAGE_CROSSOVER);
    }

    @Test
    void getTemplatesByIndicator_shouldReturnRsiTemplate_forRsi() {
        // When
        List<StrategyTemplate> templates = registry.getTemplatesByIndicator(IndicatorType.RSI);

        // Then
        assertThat(templates).hasSize(1);
        assertThat(templates.get(0).getType()).isEqualTo(StrategyTemplateType.RSI_REVERSAL);
    }

    @Test
    void getTemplatesByIndicator_shouldReturnBollingerTemplate_forBollingerBands() {
        // When
        List<StrategyTemplate> templates = registry.getTemplatesByIndicator(IndicatorType.BOLLINGER_BANDS);

        // Then
        assertThat(templates).hasSize(1);
        assertThat(templates.get(0).getType()).isEqualTo(StrategyTemplateType.BOLLINGER_BREAKOUT);
    }

    @Test
    void getTemplatesByIndicator_shouldReturnMacdTemplate_forMacd() {
        // When
        List<StrategyTemplate> templates = registry.getTemplatesByIndicator(IndicatorType.MACD);

        // Then
        assertThat(templates).hasSize(1);
        assertThat(templates.get(0).getType()).isEqualTo(StrategyTemplateType.MACD_SIGNAL_CROSS);
    }

    @Test
    void getTemplatesByIndicator_shouldReturnEmpty_forUnknownIndicator() {
        // When
        List<StrategyTemplate> templates = registry.getTemplatesByIndicator(null);

        // Then
        assertThat(templates).isEmpty();
    }

    @Test
    void registry_shouldNotAllowDuplicateTypes() {
        // When: Get all templates
        List<StrategyTemplate> templates = registry.getAllTemplates();

        // Then: Verify no duplicate types
        long uniqueTypes = templates.stream()
            .map(StrategyTemplate::getType)
            .distinct()
            .count();

        assertThat(uniqueTypes).isEqualTo(templates.size());
    }
}
