package com.quorumtrading.model.strategy;

import com.quorumtrading.model.valueobject.IndicatorType;
import com.quorumtrading.model.valueobject.Symbol;
import com.quorumtrading.model.enums.InvestmentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for MovingAverageCrossoverTemplate.
 *
 * Strategy: Trend following via moving average crossovers
 * - Entry: Fast MA crosses above slow MA (bullish crossover)
 * - Exit: Fast MA crosses below slow MA, stop loss, or take profit
 */
class MovingAverageCrossoverTemplateTest {

    private MovingAverageCrossoverTemplate template;

    @BeforeEach
    void setUp() {
        template = new MovingAverageCrossoverTemplate();
    }

    @Test
    void getType_shouldReturnMovingAverageCrossover() {
        assertThat(template.getType()).isEqualTo(StrategyTemplateType.MOVING_AVERAGE_CROSSOVER);
    }

    @Test
    void getRequiredIndicators_shouldReturnMovingAverage() {
        Set<IndicatorType> indicators = template.getRequiredIndicators();

        assertThat(indicators)
            .hasSize(1)
            .containsExactly(IndicatorType.MOVING_AVERAGE);
    }

    @Test
    void generateStrategyMarkdown_shouldProduceValidMarkdownWithYamlFrontmatter() {
        // Arrange
        MarketContext context = new MarketContext(
            new Symbol("AAPL", InvestmentType.STOCK),
            Set.of(new Symbol("AAPL", InvestmentType.STOCK), new Symbol("MSFT", InvestmentType.STOCK)),
            Set.of(),
            "Tech stocks showing strong uptrend momentum"
        );

        TemplateParameters parameters = TemplateParameters.builder()
            .withInt("fastPeriod", 50)
            .withInt("slowPeriod", 200)
            .withDecimal("stopLossPercent", new BigDecimal("5"))
            .withDecimal("takeProfitPercent", new BigDecimal("10"))
            .build();

        // Act
        String markdown = template.generateStrategyMarkdown(context, parameters);

        // Assert - Verify YAML frontmatter
        assertThat(markdown)
            .startsWith("---")
            .contains("version: v1.0")
            .contains("consensus_method: MAJORITY_VOTE")
            .contains("consensus_threshold: 0.66")
            .contains("stock_tickers:")
            .contains("  - AAPL")
            .contains("  - MSFT")
            .contains("crypto_tickers: []");

        // Stock strategies use live_schedule + backtest_schedule
        assertThat(markdown)
            .contains("live_schedule:")
            .contains("backtest_schedule:")
            .contains("evaluation_times:");

        // Assert - Verify strategy content
        assertThat(markdown)
            .contains("# Moving Average Crossover Strategy")
            .contains("50-day MA")
            .contains("200-day MA")
            .contains("## Entry Rules")
            .contains("## Exit Rules")
            .contains("## Risk Management")
            .contains("**Stop Loss**: Price falls 5% below entry price")
            .contains("**Take Profit**: Price rises 10% above entry price");
    }

    @Test
    void generateStrategyMarkdown_shouldHandleCryptoTickers() {
        // Arrange
        MarketContext context = new MarketContext(
            new Symbol("BTC", InvestmentType.CRYPTO),
            Set.of(),
            Set.of(new Symbol("BTC", InvestmentType.CRYPTO), new Symbol("ETH", InvestmentType.CRYPTO)),
            "Crypto market showing bullish momentum"
        );

        TemplateParameters parameters = TemplateParameters.builder()
            .withInt("fastPeriod", 20)
            .withInt("slowPeriod", 50)
            .build();

        // Act
        String markdown = template.generateStrategyMarkdown(context, parameters);

        // Assert
        assertThat(markdown)
            .contains("stock_tickers: []")
            .contains("crypto_tickers:")
            .contains("  - BTC")
            .contains("  - ETH")
            .contains("20-day MA")
            .contains("50-day MA");
    }

    @Test
    void generateStrategyMarkdown_shouldUseDefaultParametersWhenNotProvided() {
        // Arrange
        MarketContext context = new MarketContext(
            new Symbol("AAPL", InvestmentType.STOCK),
            Set.of(new Symbol("AAPL", InvestmentType.STOCK)),
            Set.of(),
            "Default parameters test"
        );

        TemplateParameters parameters = TemplateParameters.builder().build();

        // Act
        String markdown = template.generateStrategyMarkdown(context, parameters);

        // Assert - Should use defaults (50/200 MA, 5% stop, 8% take profit)
        assertThat(markdown)
            .contains("50-day MA")
            .contains("200-day MA")
            .contains("**Stop Loss**: Price falls 5% below entry price")
            .contains("**Take Profit**: Price rises 8% above entry price");
    }
}
