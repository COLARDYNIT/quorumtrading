package com.quorumtrading.adapter.featureflag.unleash;

import com.quorumtrading.application.port.out.FeatureFlagRepository;
import com.quorumtrading.model.entity.FeatureFlag;
import com.quorumtrading.model.enums.Feature;
import com.quorumtrading.model.valueobject.FeatureFlagContext;
import com.quorumtrading.model.valueobject.FeatureFlagStrategy;
import com.quorumtrading.model.valueobject.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Evaluation behaviour, including the context targeting that is the whole reason for putting a
 * feature flag library behind this rather than a boolean column.
 */
class UnleashFeatureFlagEvaluatorTest {

    private FeatureFlagRepository repository;
    private UnleashFeatureFlagEvaluator evaluator;

    @BeforeEach
    void setUp() {
        repository = mock(FeatureFlagRepository.class);
        when(repository.versionStamp()).thenReturn("1:1000");

        DatabaseToggleSource toggleSource = new DatabaseToggleSource();
        toggleSource.featureFlagRepository = repository;
        toggleSource.cacheTtlSeconds = 300;

        evaluator = new UnleashFeatureFlagEvaluator();
        evaluator.toggleSource = toggleSource;
        evaluator.profile = "test";
    }

    private void givenFlags(FeatureFlag... flags) {
        when(repository.findAll()).thenReturn(List.of(flags));
        evaluator.init();
    }

    private static FeatureFlag flag(Feature feature, boolean enabled, FeatureFlagStrategy... strategies) {
        return FeatureFlag.reconstitute(feature, enabled, List.of(strategies), Instant.now(), null);
    }

    @Test
    @DisplayName("a flag enabled for everyone is on for an anonymous caller")
    void enabledForEveryone() {
        givenFlags(flag(Feature.CHALLENGES, true, FeatureFlagStrategy.everyone()));

        assertThat(evaluator.isEnabled(Feature.CHALLENGES, FeatureFlagContext.anonymous(), false)).isTrue();
    }

    @Test
    @DisplayName("a disabled flag stays off even when a strategy would match")
    void disabledBeatsStrategies() {
        givenFlags(flag(Feature.CHALLENGES, false, FeatureFlagStrategy.everyone()));

        assertThat(evaluator.isEnabled(Feature.CHALLENGES, FeatureFlagContext.anonymous(), true)).isFalse();
    }

    @Test
    @DisplayName("a flag targeted at an email is on for that person and off for everyone else")
    void targetsASingleEmail() {
        givenFlags(flag(Feature.MARKET_RESEARCH, true,
            FeatureFlagStrategy.forEmails(List.of("insider@example.com"))));

        FeatureFlagContext insider =
            FeatureFlagContext.forUser(UserId.generate(), "insider@example.com");
        FeatureFlagContext outsider =
            FeatureFlagContext.forUser(UserId.generate(), "someone.else@example.com");

        assertThat(evaluator.isEnabled(Feature.MARKET_RESEARCH, insider, false)).isTrue();
        assertThat(evaluator.isEnabled(Feature.MARKET_RESEARCH, outsider, false)).isFalse();
        assertThat(evaluator.isEnabled(Feature.MARKET_RESEARCH, FeatureFlagContext.anonymous(), false)).isFalse();
    }

    @Test
    @DisplayName("a flag with several targeted emails matches any of them")
    void targetsSeveralEmails() {
        givenFlags(flag(Feature.CHALLENGES, true,
            FeatureFlagStrategy.forEmails(List.of("first@example.com", "second@example.com"))));

        assertThat(evaluator.isEnabled(Feature.CHALLENGES,
            FeatureFlagContext.forUser(UserId.generate(), "second@example.com"), false)).isTrue();
    }

    @Test
    @DisplayName("a feature with no row yet falls back to the profile default")
    void unknownFlagUsesFallback() {
        givenFlags();

        assertThat(evaluator.isEnabled(Feature.MARKET_RESEARCH, FeatureFlagContext.anonymous(), true)).isTrue();
        assertThat(evaluator.isEnabled(Feature.CHALLENGES, FeatureFlagContext.anonymous(), false)).isFalse();
    }

    @Test
    @DisplayName("a flag store outage falls back to the profile default instead of failing the request")
    void repositoryFailureUsesFallback() {
        DatabaseToggleSource broken = new DatabaseToggleSource();
        broken.featureFlagRepository = mock(FeatureFlagRepository.class);
        broken.cacheTtlSeconds = 300;
        when(broken.featureFlagRepository.versionStamp())
            .thenThrow(new IllegalStateException("database unavailable"));

        UnleashFeatureFlagEvaluator brokenEvaluator = new UnleashFeatureFlagEvaluator();
        brokenEvaluator.toggleSource = broken;
        brokenEvaluator.profile = "test";
        brokenEvaluator.init();

        assertThat(brokenEvaluator.isEnabled(Feature.MARKET_RESEARCH, FeatureFlagContext.anonymous(), true))
            .isTrue();
        assertThat(brokenEvaluator.isEnabled(Feature.CHALLENGES, FeatureFlagContext.anonymous(), false))
            .isFalse();
    }

    @Test
    @DisplayName("extra context properties reach Unleash without breaking evaluation")
    void passesContextProperties() {
        givenFlags(flag(Feature.CHALLENGES, true, FeatureFlagStrategy.everyone()));

        FeatureFlagContext context = FeatureFlagContext
            .forUser(UserId.generate(), "someone@example.com")
            .with("edition", "standalone");

        assertThat(evaluator.isEnabled(Feature.CHALLENGES, context, false)).isTrue();
        assertThat(context.properties()).containsEntry("edition", "standalone");
    }

    @Test
    @DisplayName("definitions are read once and served from cache on later evaluations")
    void cachesDefinitions() {
        givenFlags(flag(Feature.CHALLENGES, true, FeatureFlagStrategy.everyone()));

        for (int i = 0; i < 25; i++) {
            evaluator.isEnabled(Feature.CHALLENGES, FeatureFlagContext.anonymous(), false);
        }

        org.mockito.Mockito.verify(repository, org.mockito.Mockito.times(1)).findAll();
        org.mockito.Mockito.verify(repository, org.mockito.Mockito.never()).find(any());
    }
}
