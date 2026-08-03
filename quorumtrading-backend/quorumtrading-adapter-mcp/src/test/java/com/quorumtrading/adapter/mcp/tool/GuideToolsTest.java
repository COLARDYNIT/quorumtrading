package com.quorumtrading.adapter.mcp.tool;

import io.quarkiverse.mcp.server.ToolResponse;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class GuideToolsTest {

    @Inject
    GuideTools guideTools;

    @ParameterizedTest
    @ValueSource(strings = {"platform-overview", "trading-concepts", "alpaca-setup", "strategy-format", "market-research", "strategy-cheatsheet"})
    void getGuide_returnsContentForKnownTopic(String topic) {
        ToolResponse response = guideTools.getGuide(Optional.of(topic));

        assertThat(response.isError()).isFalse();
        String text = response.firstContent().asText().text();
        assertThat(text).contains("#"); // markdown heading
        assertThat(text.length()).isGreaterThan(100);
    }

    @Test
    void getGuide_noTopicReturnsAvailableList() {
        ToolResponse response = guideTools.getGuide(Optional.empty());

        assertThat(response.isError()).isFalse();
        String text = response.firstContent().asText().text();
        assertThat(text).contains("Available Guides");
        assertThat(text).contains("platform-overview");
        assertThat(text).contains("trading-concepts");
        assertThat(text).contains("alpaca-setup");
        assertThat(text).contains("strategy-format");
        assertThat(text).contains("market-research");
        assertThat(text).contains("strategy-cheatsheet");
    }

    @Test
    void getGuide_blankTopicReturnsAvailableList() {
        ToolResponse response = guideTools.getGuide(Optional.of("  "));

        assertThat(response.isError()).isFalse();
        String text = response.firstContent().asText().text();
        assertThat(text).contains("Available Guides");
    }

    @Test
    void getGuide_unknownTopicReturnsErrorWithList() {
        ToolResponse response = guideTools.getGuide(Optional.of("nonexistent"));

        assertThat(response.isError()).isFalse();
        String text = response.firstContent().asText().text();
        assertThat(text).contains("Unknown topic: 'nonexistent'");
        assertThat(text).contains("Available Guides");
        assertThat(text).contains("platform-overview");
    }

    @Test
    void getGuide_platformOverviewContainsCoreConceptSections() {
        ToolResponse response = guideTools.getGuide(Optional.of("platform-overview"));

        String text = response.firstContent().asText().text();
        assertThat(text).contains("Strategies");
        assertThat(text).contains("Simulations");
        assertThat(text).contains("AI Agents");
        assertThat(text).contains("Consensus Methods");
        assertThat(text).contains("Market Research");
        assertThat(text).contains("Benchmarks");
        assertThat(text).contains("Portfolio Management");
    }

    @Test
    void getGuide_tradingConceptsContainsIndicators() {
        ToolResponse response = guideTools.getGuide(Optional.of("trading-concepts"));

        String text = response.firstContent().asText().text();
        assertThat(text).contains("RSI");
        assertThat(text).contains("MACD");
        assertThat(text).contains("Bollinger Bands");
        assertThat(text).contains("Order Types");
        assertThat(text).contains("Risk Management");
    }

    @Test
    void getGuide_alpacaSetupContainsBrokerSteps() {
        ToolResponse response = guideTools.getGuide(Optional.of("alpaca-setup"));

        String text = response.firstContent().asText().text();
        assertThat(text).contains("Alpaca");
        assertThat(text).contains("Paper");
        assertThat(text).contains("Live");
        assertThat(text).contains("API Keys");
        assertThat(text).contains("validate_broker_credentials");
    }

    @Test
    void getGuide_topicIsCaseInsensitive() {
        ToolResponse response = guideTools.getGuide(Optional.of("PLATFORM-OVERVIEW"));

        assertThat(response.isError()).isFalse();
        String text = response.firstContent().asText().text();
        assertThat(text).contains("QuorumTrading Platform Overview");
    }
}
