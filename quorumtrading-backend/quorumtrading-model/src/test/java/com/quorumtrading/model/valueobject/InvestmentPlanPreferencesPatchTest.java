package com.quorumtrading.model.valueobject;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The update tools promise "pass only the fields you want to change". Preferences used to travel as
 * a whole value object rebuilt from whichever arguments a caller supplied, so every omitted field
 * arrived as null/empty and overwrote the stored one — an amount edit erased the plan's allow-list,
 * widening the picker from one ticker to the user's whole strategy pool.
 *
 * <p>These tests pin the merge, field by field, because the damage is invisible: the update returns
 * a plan that looks fine and only the next funding tick reveals what it will now buy.</p>
 */
class InvestmentPlanPreferencesPatchTest {

    private static final InvestmentPlanPreferences STORED = new InvestmentPlanPreferences(
            "conservative", List.of("BTC"), List.of("DOGE"), 1, true);

    @Nested
    @DisplayName("a patch leaves everything it does not mention alone")
    class LeavesUnmentionedFieldsAlone {

        @Test
        @DisplayName("an empty patch changes nothing")
        void emptyPatchIsIdentity() {
            assertThat(InvestmentPlanPreferencesPatch.EMPTY.applyTo(STORED)).isEqualTo(STORED);
        }

        @Test
        @DisplayName("setting the block-list preserves allow-list, risk tolerance and the cap")
        void blockListEditPreservesTheRest() {
            InvestmentPlanPreferencesPatch patch = new InvestmentPlanPreferencesPatch(
                    Optional.empty(), Optional.empty(), Optional.of(List.of("GME")),
                    Optional.empty(), Optional.empty());

            InvestmentPlanPreferences merged = patch.applyTo(STORED);

            assertThat(merged.blockedTickers()).containsExactly("GME");
            assertThat(merged.allowedTickers()).containsExactly("BTC");
            assertThat(merged.riskTolerance()).isEqualTo("conservative");
            assertThat(merged.maxStrategiesActive()).isEqualTo(1);
            assertThat(merged.preferExistingHoldings()).isTrue();
        }

        @Test
        @DisplayName("setting the allow-list preserves the stored block-list")
        void allowListEditPreservesBlockList() {
            InvestmentPlanPreferencesPatch patch = new InvestmentPlanPreferencesPatch(
                    Optional.empty(), Optional.of(List.of("ETH")), Optional.empty(),
                    Optional.empty(), Optional.empty());

            assertThat(patch.applyTo(STORED).blockedTickers()).containsExactly("DOGE");
        }

        @Test
        @DisplayName("setting the cap preserves both ticker lists")
        void capEditPreservesTickerLists() {
            InvestmentPlanPreferencesPatch patch = new InvestmentPlanPreferencesPatch(
                    Optional.empty(), Optional.empty(), Optional.empty(),
                    Optional.of(3), Optional.empty());

            InvestmentPlanPreferences merged = patch.applyTo(STORED);

            assertThat(merged.maxStrategiesActive()).isEqualTo(3);
            assertThat(merged.allowedTickers()).containsExactly("BTC");
            assertThat(merged.blockedTickers()).containsExactly("DOGE");
        }
    }

    @Nested
    @DisplayName("clearing is expressible, and distinct from omitting")
    class Clearing {

        @Test
        @DisplayName("an empty list clears a ticker list")
        void emptyListClears() {
            InvestmentPlanPreferencesPatch patch = new InvestmentPlanPreferencesPatch(
                    Optional.empty(), Optional.of(List.of()), Optional.empty(),
                    Optional.empty(), Optional.empty());

            InvestmentPlanPreferences merged = patch.applyTo(STORED);

            assertThat(merged.allowedTickers()).isEmpty();
            assertThat(merged.blockedTickers()).containsExactly("DOGE");
        }

        @Test
        @DisplayName("a blank risk tolerance clears it; an absent one keeps it")
        void blankRiskToleranceClears() {
            InvestmentPlanPreferencesPatch clear = new InvestmentPlanPreferencesPatch(
                    Optional.of("  "), Optional.empty(), Optional.empty(),
                    Optional.empty(), Optional.empty());

            assertThat(clear.applyTo(STORED).riskTolerance()).isNull();
            assertThat(InvestmentPlanPreferencesPatch.EMPTY.applyTo(STORED).riskTolerance())
                    .isEqualTo("conservative");
        }
    }

    @Nested
    @DisplayName("edges")
    class Edges {

        @Test
        @DisplayName("a null base is treated as empty preferences rather than throwing")
        void nullBaseIsEmpty() {
            InvestmentPlanPreferencesPatch patch = new InvestmentPlanPreferencesPatch(
                    Optional.empty(), Optional.of(List.of("SOL")), Optional.empty(),
                    Optional.empty(), Optional.empty());

            InvestmentPlanPreferences merged = patch.applyTo(null);

            assertThat(merged.allowedTickers()).containsExactly("SOL");
            assertThat(merged.blockedTickers()).isEmpty();
            assertThat(merged.riskTolerance()).isNull();
        }

        @Test
        @DisplayName("null Optionals in the canonical constructor normalise to absent")
        void nullOptionalsNormalise() {
            InvestmentPlanPreferencesPatch patch =
                    new InvestmentPlanPreferencesPatch(null, null, null, null, null);

            assertThat(patch.isEmpty()).isTrue();
            assertThat(patch.applyTo(STORED)).isEqualTo(STORED);
        }

        @Test
        @DisplayName("isEmpty is false as soon as any field is specified")
        void isEmptyTracksSpecifiedFields() {
            InvestmentPlanPreferencesPatch patch = new InvestmentPlanPreferencesPatch(
                    Optional.empty(), Optional.empty(), Optional.empty(),
                    Optional.empty(), Optional.of(false));

            assertThat(patch.isEmpty()).isFalse();
            assertThat(patch.applyTo(STORED).preferExistingHoldings()).isFalse();
        }

        @Test
        @DisplayName("ofTickerPolicy sets both lists and leaves the other preferences alone")
        void ofTickerPolicySetsOnlyTheLists() {
            InvestmentPlanPreferences merged =
                    InvestmentPlanPreferencesPatch.ofTickerPolicy(List.of("ETH"), List.of("GME"))
                            .applyTo(STORED);

            assertThat(merged.allowedTickers()).containsExactly("ETH");
            assertThat(merged.blockedTickers()).containsExactly("GME");
            assertThat(merged.riskTolerance()).isEqualTo("conservative");
            assertThat(merged.maxStrategiesActive()).isEqualTo(1);
        }

        @Test
        @DisplayName("ofTickerPolicy treats null lists as cleared, not as unspecified")
        void ofTickerPolicyTreatsNullAsCleared() {
            InvestmentPlanPreferences merged =
                    InvestmentPlanPreferencesPatch.ofTickerPolicy(null, null).applyTo(STORED);

            assertThat(merged.allowedTickers()).isEmpty();
            assertThat(merged.blockedTickers()).isEmpty();
        }
    }
}
