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
 * Tests for RSIReversalTemplate.
 *
 * Strategy: Mean reversion trading using RSI indicator
 * - Entry: RSI oversold (typically < 30)
 * - Exit: RSI overbought (typically > 70), stop loss, or take profit
 */
class RSIReversalTemplateTest {

    private RSIReversalTemplate template;

    @BeforeEach
    void setUp() {
        template = new RSIReversalTemplate();
    }

    @Test
    void getType_shouldReturnRSIReversal() {
        assertThat(template.getType()).isEqualTo(StrategyTemplateType.RSI_REVERSAL);
    }

    @Test
    void getRequiredIndicators_shouldReturnRSI() {
        Set<IndicatorType> indicators = template.getRequiredIndicators();

        assertThat(indicators)
            .hasSize(1)
            .containsExactly(IndicatorType.RSI);
    }

    @Test
    void generateStrategyMarkdown_shouldProduceValidMarkdownWithYamlFrontmatter() {
        // Arrange
        MarketContext context = new MarketContext(
            new Symbol("TSLA", InvestmentType.STOCK),
            Set.of(new Symbol("TSLA", InvestmentType.STOCK), new Symbol("NVDA", InvestmentType.STOCK)),
            Set.of(),
            "Tech stocks experiencing volatility, ideal for mean reversion"
        );

        TemplateParameters parameters = TemplateParameters.builder()
            .withInt("rsiPeriod", 14)
            .withDecimal("oversoldThreshold", new BigDecimal("30"))
            .withDecimal("overboughtThreshold", new BigDecimal("70"))
            .withDecimal("stopLossPercent", new BigDecimal("4"))
            .withDecimal("takeProfitPercent", new BigDecimal("12"))
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
            .contains("  - NVDA")
            .contains("  - TSLA")
            .contains("crypto_tickers: []");

        // Stock strategies use live_schedule + backtest_schedule
        assertThat(markdown)
            .contains("live_schedule:")
            .contains("backtest_schedule:")
            .contains("evaluation_times:");

        // Assert - Verify strategy content
        assertThat(markdown)
            .contains("# RSI Reversal Strategy")
            .contains("14-period RSI")
            .contains("oversold (< 30)")
            .contains("overbought (> 70)")
            .contains("## Entry Rules")
            .contains("## Exit Rules")
            .contains("## Risk Management")
            .contains("**Stop Loss**: Price falls 4% below entry price")
            .contains("**Take Profit**: Price rises 12% above entry price");
    }

    @Test
    void generateStrategyMarkdown_shouldHandleCryptoTickers() {
        // Arrange
        MarketContext context = new MarketContext(
            new Symbol("BTC", InvestmentType.CRYPTO),
            Set.of(),
            Set.of(new Symbol("BTC", InvestmentType.CRYPTO), new Symbol("ETH", InvestmentType.CRYPTO)),
            "Crypto market oversold, expecting bounce"
        );

        TemplateParameters parameters = TemplateParameters.builder()
            .withInt("rsiPeriod", 14)
            .build();

        // Act
        String markdown = template.generateStrategyMarkdown(context, parameters);

        // Assert
        assertThat(markdown)
            .contains("stock_tickers: []")
            .contains("crypto_tickers:")
            .contains("  - BTC")
            .contains("  - ETH");
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

        // Assert - Should use defaults (14-period, 30/70 thresholds, 5% stop, 10% take profit)
        assertThat(markdown)
            .contains("14-period RSI")
            .contains("oversold (< 30)")
            .contains("overbought (> 70)")
            .contains("**Stop Loss**: Price falls 5% below entry price")
            .contains("**Take Profit**: Price rises 10% above entry price");
    }
}
