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
 * Tests for MACDSignalCrossTemplate.
 *
 * Strategy: Momentum trading using MACD signal line crossovers
 * - Entry: MACD line crosses above signal line (bullish crossover)
 * - Exit: MACD line crosses below signal line (bearish crossover), stop loss, or take profit
 */
class MACDSignalCrossTemplateTest {

    private MACDSignalCrossTemplate template;

    @BeforeEach
    void setUp() {
        template = new MACDSignalCrossTemplate();
    }

    @Test
    void getType_shouldReturnMACDSignalCross() {
        assertThat(template.getType()).isEqualTo(StrategyTemplateType.MACD_SIGNAL_CROSS);
    }

    @Test
    void getRequiredIndicators_shouldReturnMACD() {
        Set<IndicatorType> indicators = template.getRequiredIndicators();

        assertThat(indicators)
            .hasSize(1)
            .containsExactly(IndicatorType.MACD);
    }

    @Test
    void generateStrategyMarkdown_shouldProduceValidMarkdownWithYamlFrontmatter() {
        // Arrange
        MarketContext context = new MarketContext(
            new Symbol("AAPL", InvestmentType.STOCK),
            Set.of(new Symbol("AAPL", InvestmentType.STOCK), new Symbol("GOOGL", InvestmentType.STOCK)),
            Set.of(),
            "Tech stocks showing strong momentum shift"
        );

        TemplateParameters parameters = TemplateParameters.builder()
            .withInt("fastPeriod", 12)
            .withInt("slowPeriod", 26)
            .withInt("signalPeriod", 9)
            .withDecimal("stopLossPercent", new BigDecimal("6"))
            .withDecimal("takeProfitPercent", new BigDecimal("15"))
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
            .contains("  - GOOGL")
            .contains("crypto_tickers: []");

        // Stock strategies use live_schedule + backtest_schedule
        assertThat(markdown)
            .contains("live_schedule:")
            .contains("backtest_schedule:")
            .contains("evaluation_times:");

        // Assert - Verify strategy content
        assertThat(markdown)
            .contains("# MACD Signal Cross Strategy")
            .contains("MACD(12, 26, 9)")
            .contains("## Entry Rules")
            .contains("## Exit Rules")
            .contains("## Risk Management")
            .contains("**Stop Loss**: Price falls 6% below entry price")
            .contains("**Take Profit**: Price rises 15% above entry price");
    }

    @Test
    void generateStrategyMarkdown_shouldHandleCryptoTickers() {
        // Arrange
        MarketContext context = new MarketContext(
            new Symbol("BTC", InvestmentType.CRYPTO),
            Set.of(),
            Set.of(new Symbol("BTC", InvestmentType.CRYPTO), new Symbol("ETH", InvestmentType.CRYPTO)),
            "Crypto momentum building on bullish MACD crossover"
        );

        TemplateParameters parameters = TemplateParameters.builder()
            .withInt("fastPeriod", 12)
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
            new Symbol("TSLA", InvestmentType.STOCK),
            Set.of(new Symbol("TSLA", InvestmentType.STOCK)),
            Set.of(),
            "Default parameters test"
        );

        TemplateParameters parameters = TemplateParameters.builder().build();

        // Act
        String markdown = template.generateStrategyMarkdown(context, parameters);

        // Assert - Should use defaults (12, 26, 9 periods, 5% stop, 12% take profit)
        assertThat(markdown)
            .contains("MACD(12, 26, 9)")
            .contains("**Stop Loss**: Price falls 5% below entry price")
            .contains("**Take Profit**: Price rises 12% above entry price");
    }
}
