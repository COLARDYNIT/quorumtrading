package com.quorumtrading.model.entity;

import com.quorumtrading.model.enums.InvestmentType;
import com.quorumtrading.model.valueobject.*;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import static org.assertj.core.api.Assertions.*;

class MarketResearchTest {

    private static final UserId TEST_OWNER = UserId.generate();

    @Test
    void shouldCreateMarketResearch() {
        // Given
        String name = "BTC Momentum Research";
        String instructions = "# Strategy Guidelines\nCreate momentum strategies";
        Set<Symbol> assets = Set.of(new Symbol("BTC", InvestmentType.CRYPTO));
        ResearchInterval interval = new ResearchInterval(7, ResearchInterval.Unit.DAYS);

        // When
        MarketResearch research = new MarketResearch(name, instructions, assets, interval, false, null, TEST_OWNER);

        // Then
        assertThat(research.getId()).isNotNull();
        assertThat(research.getName()).isEqualTo(name);
        assertThat(research.getMarkdownInstructions()).isEqualTo(instructions);
        assertThat(research.getTargetAssets()).containsExactly(new Symbol("BTC", InvestmentType.CRYPTO));
        assertThat(research.getInterval()).isEqualTo(interval);
        assertThat(research.getStatus()).isEqualTo(ResearchStatus.ACTIVE);
        assertThat(research.isEnableAutoBacktest()).isFalse();
        assertThat(research.getBacktestConfig()).isNull();
        assertThat(research.getNextScheduledRun()).isNotNull();
        assertThat(research.getCreatedAt()).isNotNull();
        assertThat(research.getLastExecutedAt()).isNull();
        assertThat(research.getOwnerId()).isEqualTo(TEST_OWNER);
    }

    @Test
    void shouldCreateWithBacktestConfig() {
        // Given
        BacktestConfiguration config = new BacktestConfiguration(Duration.ofDays(30), null, null);

        // When
        MarketResearch research = new MarketResearch(
            "Test Research", "instructions", Set.of(new Symbol("ETH", InvestmentType.CRYPTO)),
            new ResearchInterval(7, ResearchInterval.Unit.DAYS), true, config, TEST_OWNER
        );

        // Then
        assertThat(research.isEnableAutoBacktest()).isTrue();
        assertThat(research.getBacktestConfig()).isEqualTo(config);
    }

    @Test
    void shouldScheduleImmediateExecutionOnCreation() {
        // Given
        ResearchInterval interval = new ResearchInterval(7, ResearchInterval.Unit.DAYS);
        Instant before = Instant.now();

        // When
        MarketResearch research = new MarketResearch(
            "Test", "instructions", Set.of(new Symbol("BTC", InvestmentType.CRYPTO)), interval, false, null, TEST_OWNER
        );

        // Then - nextScheduledRun should be approximately now (immediate execution)
        Instant after = Instant.now();
        assertThat(research.getNextScheduledRun()).isBetween(before, after.plusSeconds(1));
    }

    @Test
    void shouldPauseActiveResearch() {
        // Given
        MarketResearch research = new MarketResearch(
            "Test", "instructions", Set.of(new Symbol("BTC", InvestmentType.CRYPTO)),
            new ResearchInterval(7, ResearchInterval.Unit.DAYS), false, null, TEST_OWNER
        );

        // When
        research.pause();

        // Then
        assertThat(research.getStatus()).isEqualTo(ResearchStatus.PAUSED);
    }

    @Test
    void shouldResumePausedResearch() {
        // Given
        MarketResearch research = new MarketResearch(
            "Test", "instructions", Set.of(new Symbol("BTC", InvestmentType.CRYPTO)),
            new ResearchInterval(7, ResearchInterval.Unit.DAYS), false, null, TEST_OWNER
        );
        research.pause();
        Instant pausedNextRun = research.getNextScheduledRun();

        // When
        research.resume();

        // Then
        assertThat(research.getStatus()).isEqualTo(ResearchStatus.ACTIVE);
        assertThat(research.getNextScheduledRun()).isAfter(pausedNextRun);
    }

    @Test
    void shouldCompleteResearch() {
        // Given
        MarketResearch research = new MarketResearch(
            "Test", "instructions", Set.of(new Symbol("BTC", InvestmentType.CRYPTO)),
            new ResearchInterval(7, ResearchInterval.Unit.DAYS), false, null, TEST_OWNER
        );

        // When
        research.complete();

        // Then
        assertThat(research.getStatus()).isEqualTo(ResearchStatus.COMPLETED);
    }

    @Test
    void shouldRejectPauseOnCompletedResearch() {
        // Given
        MarketResearch research = new MarketResearch(
            "Test", "instructions", Set.of(new Symbol("BTC", InvestmentType.CRYPTO)),
            new ResearchInterval(7, ResearchInterval.Unit.DAYS), false, null, TEST_OWNER
        );
        research.complete();

        // When/Then
        assertThatThrownBy(research::pause)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Cannot transition from COMPLETED to PAUSED");
    }

    @Test
    void shouldMarkAsExecuted() {
        // Given
        MarketResearch research = new MarketResearch(
            "Test", "instructions", Set.of(new Symbol("BTC", InvestmentType.CRYPTO)),
            new ResearchInterval(7, ResearchInterval.Unit.DAYS), false, null, TEST_OWNER
        );
        Instant oldNextRun = research.getNextScheduledRun();

        // When
        research.markAsExecuted();

        // Then
        assertThat(research.getLastExecutedAt()).isNotNull();
        assertThat(research.getNextScheduledRun()).isAfter(oldNextRun);
    }

    @Test
    void shouldRejectBlankName() {
        // When/Then
        assertThatThrownBy(() -> new MarketResearch(
            "  ", "instructions", Set.of(new Symbol("BTC", InvestmentType.CRYPTO)),
            new ResearchInterval(7, ResearchInterval.Unit.DAYS), false, null, TEST_OWNER
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Name cannot be blank");
    }

    @Test
    void shouldAllowEmptyTargetAssets() {
        // Target assets are now optional - LLM can discover assets from instructions
        // When
        MarketResearch research = new MarketResearch(
            "Test", "instructions", Set.of(),
            new ResearchInterval(7, ResearchInterval.Unit.DAYS), false, null, TEST_OWNER
        );

        // Then
        assertThat(research.getTargetAssets()).isEmpty();
    }

    @Test
    void shouldAllowNullTargetAssets() {
        // Target assets are now optional - null should become empty set
        // When
        MarketResearch research = new MarketResearch(
            "Test", "instructions", null,
            new ResearchInterval(7, ResearchInterval.Unit.DAYS), false, null, TEST_OWNER
        );

        // Then
        assertThat(research.getTargetAssets()).isEmpty();
    }

    @Test
    void shouldRejectAutoBacktestWithoutConfig() {
        // When/Then
        assertThatThrownBy(() -> new MarketResearch(
            "Test", "instructions", Set.of(new Symbol("BTC", InvestmentType.CRYPTO)),
            new ResearchInterval(7, ResearchInterval.Unit.DAYS), true, null, TEST_OWNER
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Backtest configuration required when auto-backtest enabled");
    }

    @Test
    void shouldRejectNullOwnerId() {
        // When/Then
        assertThatThrownBy(() -> new MarketResearch(
            "Test", "instructions", Set.of(new Symbol("BTC", InvestmentType.CRYPTO)),
            new ResearchInterval(7, ResearchInterval.Unit.DAYS), false, null, null
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("OwnerId cannot be null");
    }
}
