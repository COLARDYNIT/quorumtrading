package com.quorumtrading.model.entity;

import com.quorumtrading.model.enums.Feature;
import com.quorumtrading.model.valueobject.FeatureFlagContext;
import com.quorumtrading.model.valueobject.FeatureFlagStrategy;
import com.quorumtrading.model.valueobject.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("FeatureFlag")
class FeatureFlagTest {

    @Nested
    @DisplayName("an enabled flag always has an audience")
    class Normalisation {

        @Test
        @DisplayName("switching on with no strategies means everyone")
        void enabledWithNoStrategiesMeansEveryone() {
            FeatureFlag flag = FeatureFlag.seed(Feature.CHALLENGES, true);

            // Unleash evaluates an enabled flag with zero strategies as OFF. Storing that verbatim
            // would give an administrator a toggle that reads "enabled" and does nothing.
            assertThat(flag.getStrategies()).hasSize(1);
            assertThat(flag.getStrategies().get(0).isUnconditional()).isTrue();
            assertThat(flag.isOnForEveryone()).isTrue();
        }

        @Test
        @DisplayName("switching off does not invent an audience")
        void disabledKeepsNoStrategies() {
            FeatureFlag flag = FeatureFlag.seed(Feature.CHALLENGES, false);

            assertThat(flag.getStrategies()).isEmpty();
            assertThat(flag.isOnForEveryone()).isFalse();
        }

        @Test
        @DisplayName("an explicit audience is kept as given")
        void explicitStrategiesAreKept() {
            FeatureFlag flag = FeatureFlag.reconstitute(Feature.MARKET_RESEARCH, true,
                List.of(FeatureFlagStrategy.forEmails(List.of("a@example.com"))),
                Instant.now(), null);

            assertThat(flag.getStrategies()).hasSize(1);
            assertThat(flag.isOnForEveryone()).isFalse();
        }

        @Test
        @DisplayName("nulls in the strategy list are dropped rather than stored")
        void nullStrategiesAreDropped() {
            List<FeatureFlagStrategy> withNull = new ArrayList<>();
            withNull.add(null);
            withNull.add(FeatureFlagStrategy.everyone());

            FeatureFlag flag = FeatureFlag.reconstitute(
                Feature.CHALLENGES, true, withNull, Instant.now(), null);

            assertThat(flag.getStrategies()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("changing a flag")
    class Changes {

        @Test
        @DisplayName("records who made the change")
        void recordsTheAuthor() {
            FeatureFlag flag = FeatureFlag.seed(Feature.CHALLENGES, false);
            UserId admin = UserId.generate();
            Instant before = flag.getUpdatedAt();

            flag.changeTo(true, List.of(), admin);

            assertThat(flag.isEnabled()).isTrue();
            assertThat(flag.getUpdatedBy()).isEqualTo(admin);
            assertThat(flag.getUpdatedAt()).isAfterOrEqualTo(before);
        }

        @Test
        @DisplayName("switching off drops the previous targeting")
        void switchingOffClearsAudience() {
            FeatureFlag flag = FeatureFlag.reconstitute(Feature.CHALLENGES, true,
                List.of(FeatureFlagStrategy.forEmails(List.of("a@example.com"))),
                Instant.now(), null);

            flag.changeTo(false, List.of(), UserId.generate());

            assertThat(flag.isEnabled()).isFalse();
            assertThat(flag.getStrategies()).isEmpty();
        }

        @Test
        @DisplayName("retargeting an already-enabled flag replaces the audience")
        void retargeting() {
            FeatureFlag flag = FeatureFlag.seed(Feature.CHALLENGES, true);

            flag.changeTo(true, List.of(FeatureFlagStrategy.forEmails(List.of("b@example.com"))),
                UserId.generate());

            assertThat(flag.isOnForEveryone()).isFalse();
            assertThat(flag.getStrategies())
                .singleElement()
                .extracting(FeatureFlagStrategy::name)
                .isEqualTo(FeatureFlagStrategy.USER_WITH_ID);
        }

        @Test
        @DisplayName("the exposed strategy list cannot be mutated from outside")
        void strategiesAreUnmodifiable() {
            FeatureFlag flag = FeatureFlag.seed(Feature.CHALLENGES, true);

            assertThatThrownBy(() -> flag.getStrategies().add(FeatureFlagStrategy.everyone()))
                .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Test
    @DisplayName("a flag must name a feature")
    void featureIsRequired() {
        assertThatThrownBy(() -> FeatureFlag.reconstitute(null, true, List.of(), Instant.now(), null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Feature is required");
    }

    @Test
    @DisplayName("a rehydrated flag with no timestamp gets one rather than a null")
    void missingTimestampIsFilledIn() {
        FeatureFlag flag = FeatureFlag.reconstitute(
            Feature.CHALLENGES, false, List.of(), null, null);

        assertThat(flag.getUpdatedAt()).isNotNull();
    }

    @Nested
    @DisplayName("the feature registry")
    class Registry {

        @Test
        @DisplayName("resolves a flag name back to its feature, case-insensitively")
        void resolvesByName() {
            assertThat(Feature.fromFlagName("challenges")).contains(Feature.CHALLENGES);
            assertThat(Feature.fromFlagName("MARKET-RESEARCH")).contains(Feature.MARKET_RESEARCH);
        }

        @Test
        @DisplayName("an unknown or missing name resolves to nothing rather than a guess")
        void unknownNameIsEmpty() {
            assertThat(Feature.fromFlagName("no-such-feature")).isEmpty();
            assertThat(Feature.fromFlagName(null)).isEmpty();
        }

        @Test
        @DisplayName("every feature has a kebab-case flag name and a description")
        void everyFeatureIsWellFormed() {
            for (Feature feature : Feature.values()) {
                assertThat(feature.flagName())
                    .as("%s flag name", feature)
                    .matches("[a-z0-9]+(-[a-z0-9]+)*");
                assertThat(feature.description()).as("%s description", feature).isNotBlank();
            }
        }
    }

    @Nested
    @DisplayName("evaluation context")
    class Context {

        @Test
        @DisplayName("an anonymous context carries no identity")
        void anonymous() {
            assertThat(FeatureFlagContext.anonymous().isAnonymous()).isTrue();
            assertThat(FeatureFlagContext.forEmail("  ").isAnonymous()).isTrue();
            assertThat(FeatureFlagContext.forEmail(null).isAnonymous()).isTrue();
        }

        @Test
        @DisplayName("an identified context carries the email that targeting matches on")
        void identified() {
            FeatureFlagContext context = FeatureFlagContext.forEmail("someone@example.com");

            assertThat(context.isAnonymous()).isFalse();
            assertThat(context.userEmail()).isEqualTo("someone@example.com");
        }

        @Test
        @DisplayName("adding a property returns a copy and leaves the original alone")
        void withIsCopyOnWrite() {
            FeatureFlagContext original = FeatureFlagContext.forEmail("a@example.com");

            FeatureFlagContext extended = original.with("edition", "standalone");

            assertThat(original.properties()).isEmpty();
            assertThat(extended.properties()).containsEntry("edition", "standalone");
            assertThat(extended.userEmail()).isEqualTo("a@example.com");
        }

        @Test
        @DisplayName("a null key or value is ignored rather than stored")
        void withIgnoresNulls() {
            FeatureFlagContext context = FeatureFlagContext.anonymous()
                .with(null, "x")
                .with("k", null)
                .with("  ", "x");

            assertThat(context.properties()).isEmpty();
        }
    }

    @Nested
    @DisplayName("activation strategies")
    class Strategies {

        @Test
        @DisplayName("targeting several emails produces one comma-separated userWithId strategy")
        void forEmailsJoins() {
            FeatureFlagStrategy strategy =
                FeatureFlagStrategy.forEmails(List.of("a@example.com", " b@example.com "));

            assertThat(strategy.name()).isEqualTo(FeatureFlagStrategy.USER_WITH_ID);
            assertThat(strategy.parameters()).containsEntry("userIds", "a@example.com,b@example.com");
        }

        @Test
        @DisplayName("targeting nobody is rejected — it would read as a working toggle that is off")
        void forEmailsRejectsEmpty() {
            List<String> blanks = List.of("  ");
            assertThatThrownBy(() -> FeatureFlagStrategy.forEmails(blanks))
                .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> FeatureFlagStrategy.forEmails(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("a strategy must be named")
        void nameIsRequired() {
            assertThatThrownBy(() -> new FeatureFlagStrategy(" ", null))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("parameters default to empty and are defensively copied")
        void parametersAreSafe() {
            FeatureFlagStrategy strategy = new FeatureFlagStrategy("default", null);

            assertThat(strategy.parameters()).isEmpty();
            assertThatThrownBy(() -> strategy.parameters().put("k", "v"))
                .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("only the default strategy is unconditional")
        void unconditionalIsDefaultOnly() {
            assertThat(FeatureFlagStrategy.everyone().isUnconditional()).isTrue();
            assertThat(FeatureFlagStrategy.forEmails(List.of("a@b.com")).isUnconditional()).isFalse();
        }
    }
}
