package com.quorumtrading.model.valueobject;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SimulationCostTest {

    @Nested
    @DisplayName("Validation")
    class ValidationTests {

        @Test
        void rejectsNegativeCost() {
            assertThatThrownBy(() -> new SimulationCost(
                new BigDecimal("-1"), 0, 0, 0, 0, false, 100))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void rejectsNegativeTokens() {
            assertThatThrownBy(() -> new SimulationCost(
                BigDecimal.ZERO, -1, 0, 0, 0, false, 100))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void rejectsProgressOutOfRange() {
            assertThatThrownBy(() -> new SimulationCost(
                BigDecimal.ZERO, 0, 0, 0, 0, false, 101))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void nullCostDefaultsToZero() {
            SimulationCost cost = new SimulationCost(null, 0, 0, 0, 0, false, 100);
            assertThat(cost.totalCost()).isEqualByComparingTo(BigDecimal.ZERO);
        }
    }

    @Nested
    @DisplayName("Formatting")
    class FormattingTests {

        @Test
        void formattedCostRoundsToTwoDecimals() {
            SimulationCost cost = new SimulationCost(
                new BigDecimal("1.0456"), 100, 50, 150, 5, false, 100);
            assertThat(cost.formattedCost()).isEqualTo("$1.05");
        }

        @Test
        void formattedCostShowsZero() {
            SimulationCost cost = new SimulationCost(
                BigDecimal.ZERO, 0, 0, 0, 0, false, 100);
            assertThat(cost.formattedCost()).isEqualTo("$0.00");
        }
    }

    @Nested
    @DisplayName("Cost Extrapolation")
    class ExtrapolationTests {

        @Test
        void completedSimulationReturnsActualCost() {
            SimulationCost cost = new SimulationCost(
                new BigDecimal("1.00"), 100, 50, 150, 5, false, 100);
            assertThat(cost.estimatedTotalCost()).isEqualByComparingTo(new BigDecimal("1.00"));
        }

        @Test
        void partialSimulationExtrapolatesCost() {
            // 50% progress, $1.00 spent -> estimated $2.00
            SimulationCost cost = new SimulationCost(
                new BigDecimal("1.00"), 100, 50, 150, 5, true, 50);
            assertThat(cost.estimatedTotalCost()).isEqualByComparingTo(new BigDecimal("2.000000"));
        }

        @Test
        void partialAt25PercentExtrapolates4x() {
            // 25% progress, $0.50 spent -> estimated $2.00
            SimulationCost cost = new SimulationCost(
                new BigDecimal("0.50"), 100, 50, 150, 3, true, 25);
            assertThat(cost.estimatedTotalCost()).isEqualByComparingTo(new BigDecimal("2.000000"));
        }

        @Test
        void partialAtZeroProgressReturnsCurrentCost() {
            SimulationCost cost = new SimulationCost(
                new BigDecimal("0.10"), 10, 5, 15, 1, true, 0);
            assertThat(cost.estimatedTotalCost()).isEqualByComparingTo(new BigDecimal("0.10"));
        }

        @Test
        void formattedEstimatedTotalCost() {
            SimulationCost cost = new SimulationCost(
                new BigDecimal("1.04"), 100, 50, 150, 5, true, 50);
            assertThat(cost.formattedEstimatedTotalCost()).isEqualTo("$2.08");
        }
    }
}
