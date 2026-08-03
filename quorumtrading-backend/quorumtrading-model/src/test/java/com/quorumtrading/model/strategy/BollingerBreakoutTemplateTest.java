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
 * Tests for BollingerBreakoutTemplate.
 *
 * Strategy: Volatility breakout trading using Bollinger Bands
 * - Entry: Price breaks above upper band (bullish) or below lower band (bearish)
 * - Exit: Price returns inside bands, stop loss, or take profit
 */
class BollingerBreakoutTemplateTest {

    private BollingerBreakoutTemplate template;

    @BeforeEach
    void setUp() {
        template = new BollingerBreakoutTemplate();
    }

    @Test
    void getType_shouldReturnBollingerBreakout() {
        assertThat(template.getType()).isEqualTo(StrategyTemplateType.BOLLINGER_BREAKOUT);
    }

    @Test
    void getRequiredIndicators_shouldReturnBollingerBands() {
        Set<IndicatorType> indicators = template.getRequiredIndicators();

        assertThat(indicators)
            .hasSize(1)
            .containsExactly(IndicatorType.BOLLINGER_BANDS);
    }

    @Test
    void generateStrategyMarkdown_shouldProduceValidMarkdownWithYamlFrontmatter() {
        // Arrange
        MarketContext context = new MarketContext(
            new Symbol("SPY", InvestmentType.STOCK),
            Set.of(new Symbol("SPY", InvestmentType.STOCK), new Symbol("QQQ", InvestmentType.STOCK)),
            Set.of(),
            "Market showing high volatility, ideal for breakout trading"
        );

        TemplateParameters parameters = TemplateParameters.builder()
            .withInt("period", 20)
            .withDecimal("stdDevMultiplier", new BigDecimal("2.0"))
            .withDecimal("stopLossPercent", new BigDecimal("3"))
            .withDecimal("takeProfitPercent", new BigDecimal("8"))
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
            .contains("  - QQQ")
            .contains("  - SPY")
            .contains("crypto_tickers: []");

        // Stock strategies use live_schedule + backtest_schedule
        assertThat(markdown)
            .contains("live_schedule:")
            .contains("backtest_schedule:")
            .contains("evaluation_times:");

        // Assert - Verify strategy content
        assertThat(markdown)
            .contains("# Bollinger Breakout Strategy")
            .contains("20-period Bollinger Bands")
            .contains("2.0 standard deviations")
            .contains("## Entry Rules")
            .contains("## Exit Rules")
            .contains("## Risk Management")
            .contains("**Stop Loss**: Price falls 3% below entry price")
            .contains("**Take Profit**: Price rises 8% above entry price");
    }

    @Test
    void generateStrategyMarkdown_shouldHandleCryptoTickers() {
        // Arrange
        MarketContext context = new MarketContext(
            new Symbol("ETH", InvestmentType.CRYPTO),
            Set.of(),
            Set.of(new Symbol("ETH", InvestmentType.CRYPTO), new Symbol("SOL", InvestmentType.CRYPTO)),
            "Crypto volatility spike creates breakout opportunities"
        );

        TemplateParameters parameters = TemplateParameters.builder()
            .withInt("period", 20)
            .build();

        // Act
        String markdown = template.generateStrategyMarkdown(context, parameters);

        // Assert
        assertThat(markdown)
            .contains("stock_tickers: []")
            .contains("crypto_tickers:")
            .contains("  - ETH")
            .contains("  - SOL");
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

        // Assert - Should use defaults (20-period, 2.0 std dev, 4% stop, 10% take profit)
        assertThat(markdown)
            .contains("20-period Bollinger Bands")
            .contains("2.0 standard deviations")
            .contains("**Stop Loss**: Price falls 4% below entry price")
            .contains("**Take Profit**: Price rises 10% above entry price");
    }
}
