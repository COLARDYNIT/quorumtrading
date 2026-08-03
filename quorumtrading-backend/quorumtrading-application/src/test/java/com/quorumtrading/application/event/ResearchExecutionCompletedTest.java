package com.quorumtrading.application.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@DisplayName("ResearchExecutionCompleted Event Tests")
class ResearchExecutionCompletedTest {

    private static final UUID EXECUTION_ID = UUID.randomUUID();
    private static final UUID MARKET_RESEARCH_ID = UUID.randomUUID();
    private static final UUID STRATEGY_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.now();

    @Test
    @DisplayName("Should create event with factory method")
    void shouldCreateEventWithFactoryMethod() {
        // When
        ResearchExecutionCompleted event = ResearchExecutionCompleted.of(
                EXECUTION_ID,
                MARKET_RESEARCH_ID,
                STRATEGY_ID,
                true
        );

        // Then
        assertThat(event).isNotNull();
        assertThat(event.executionId()).isEqualTo(EXECUTION_ID);
        assertThat(event.marketResearchId()).isEqualTo(MARKET_RESEARCH_ID);
        assertThat(event.createdStrategyId()).isEqualTo(STRATEGY_ID);
        assertThat(event.autoBacktestEnabled()).isTrue();
        assertThat(event.occurredAt()).isNotNull();
        assertThat(event.occurredAt()).isBeforeOrEqualTo(Instant.now());
    }

    @Test
    @DisplayName("Should create event with constructor")
    void shouldCreateEventWithConstructor() {
        // When
        ResearchExecutionCompleted event = new ResearchExecutionCompleted(
                EXECUTION_ID,
                MARKET_RESEARCH_ID,
                STRATEGY_ID,
                true,
                NOW
        );

        // Then
        assertThat(event).isNotNull();
        assertThat(event.executionId()).isEqualTo(EXECUTION_ID);
        assertThat(event.marketResearchId()).isEqualTo(MARKET_RESEARCH_ID);
        assertThat(event.createdStrategyId()).isEqualTo(STRATEGY_ID);
        assertThat(event.autoBacktestEnabled()).isTrue();
        assertThat(event.occurredAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("Should reject null execution ID")
    void shouldRejectNullExecutionId() {
        // When/Then
        assertThatThrownBy(() -> new ResearchExecutionCompleted(
                null,
                MARKET_RESEARCH_ID,
                STRATEGY_ID,
                true,
                NOW
        ))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("Execution ID cannot be null");
    }

    @Test
    @DisplayName("Should reject null market research ID")
    void shouldRejectNullMarketResearchId() {
        // When/Then
        assertThatThrownBy(() -> new ResearchExecutionCompleted(
                EXECUTION_ID,
                null,
                STRATEGY_ID,
                true,
                NOW
        ))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("Market research ID cannot be null");
    }

    @Test
    @DisplayName("Should reject null occurred at timestamp")
    void shouldRejectNullOccurredAt() {
        // When/Then
        assertThatThrownBy(() -> new ResearchExecutionCompleted(
                EXECUTION_ID,
                MARKET_RESEARCH_ID,
                STRATEGY_ID,
                true,
                null
        ))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("Occurred at cannot be null");
    }

    @Test
    @DisplayName("Should allow null created strategy ID (for NO_OPPORTUNITY outcome)")
    void shouldAllowNullCreatedStrategyId() {
        // When
        ResearchExecutionCompleted event = new ResearchExecutionCompleted(
                EXECUTION_ID,
                MARKET_RESEARCH_ID,
                null, // No strategy created
                false,
                NOW
        );

        // Then
        assertThat(event).isNotNull();
        assertThat(event.createdStrategyId()).isNull();
    }

    @Test
    @DisplayName("Should trigger backtest when auto-backtest enabled and strategy created")
    void shouldTriggerBacktestWhenEnabledAndStrategyCreated() {
        // Given
        ResearchExecutionCompleted event = new ResearchExecutionCompleted(
                EXECUTION_ID,
                MARKET_RESEARCH_ID,
                STRATEGY_ID,
                true, // Auto-backtest enabled
                NOW
        );

        // When/Then
        assertThat(event.shouldTriggerBacktest()).isTrue();
    }

    @Test
    @DisplayName("Should NOT trigger backtest when auto-backtest disabled")
    void shouldNotTriggerBacktestWhenDisabled() {
        // Given
        ResearchExecutionCompleted event = new ResearchExecutionCompleted(
                EXECUTION_ID,
                MARKET_RESEARCH_ID,
                STRATEGY_ID,
                false, // Auto-backtest disabled
                NOW
        );

        // When/Then
        assertThat(event.shouldTriggerBacktest()).isFalse();
    }

    @Test
    @DisplayName("Should NOT trigger backtest when no strategy created (createdStrategyId is null)")
    void shouldNotTriggerBacktestWhenNoStrategyCreated() {
        // Given
        ResearchExecutionCompleted event = new ResearchExecutionCompleted(
                EXECUTION_ID,
                MARKET_RESEARCH_ID,
                null, // No strategy created
                true, // Auto-backtest enabled but irrelevant
                NOW
        );

        // When/Then
        assertThat(event.shouldTriggerBacktest()).isFalse();
    }

    @Test
    @DisplayName("Should NOT trigger backtest when both disabled AND no strategy")
    void shouldNotTriggerBacktestWhenDisabledAndNoStrategy() {
        // Given
        ResearchExecutionCompleted event = new ResearchExecutionCompleted(
                EXECUTION_ID,
                MARKET_RESEARCH_ID,
                null, // No strategy created
                false, // Auto-backtest disabled
                NOW
        );

        // When/Then
        assertThat(event.shouldTriggerBacktest()).isFalse();
    }

    @Test
    @DisplayName("Should be immutable (record type)")
    void shouldBeImmutable() {
        // Given
        ResearchExecutionCompleted event1 = ResearchExecutionCompleted.of(
                EXECUTION_ID,
                MARKET_RESEARCH_ID,
                STRATEGY_ID,
                true
        );

        ResearchExecutionCompleted event2 = new ResearchExecutionCompleted(
                EXECUTION_ID,
                MARKET_RESEARCH_ID,
                STRATEGY_ID,
                true,
                event1.occurredAt()
        );

        // When/Then - Records provide automatic equals/hashCode
        assertThat(event1).isEqualTo(event2);
        assertThat(event1.hashCode()).isEqualTo(event2.hashCode());
    }

    @Test
    @DisplayName("Should have meaningful toString (record type)")
    void shouldHaveMeaningfulToString() {
        // Given
        ResearchExecutionCompleted event = new ResearchExecutionCompleted(
                EXECUTION_ID,
                MARKET_RESEARCH_ID,
                STRATEGY_ID,
                true,
                NOW
        );

        // When
        String toString = event.toString();

        // Then - Records provide automatic toString with all fields
        assertThat(toString)
                .contains("ResearchExecutionCompleted")
                .contains(EXECUTION_ID.toString())
                .contains(MARKET_RESEARCH_ID.toString())
                .contains(STRATEGY_ID.toString())
                .contains("true")
                .contains(NOW.toString());
    }
}
