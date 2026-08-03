package com.quorumtrading.application.service;

import com.quorumtrading.application.config.FeatureFlagDefaults;
import com.quorumtrading.application.exception.AccessDeniedException;
import com.quorumtrading.application.exception.FeatureDisabledException;
import com.quorumtrading.application.port.out.FeatureFlagEvaluationPort;
import com.quorumtrading.application.port.out.FeatureFlagRepository;
import com.quorumtrading.application.port.out.UserRepository;
import com.quorumtrading.application.usecase.command.UpdateFeatureFlagCommand;
import com.quorumtrading.model.entity.FeatureFlag;
import com.quorumtrading.model.entity.User;
import com.quorumtrading.model.enums.Feature;
import com.quorumtrading.model.enums.OAuthProvider;
import com.quorumtrading.model.valueobject.FeatureFlagContext;
import com.quorumtrading.model.valueobject.FeatureFlagStrategy;
import com.quorumtrading.model.valueobject.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The application-layer rules around feature flags: who may change one, what the defaults mean,
 * and what happens when evaluation is unavailable.
 */
@DisplayName("feature flag services")
class FeatureFlagServicesTest {

    private static final FeatureFlagDefaults DEFAULTS = new FeatureFlagDefaults(Map.of(
        Feature.CHALLENGES, false,
        Feature.MARKET_RESEARCH, true));

    private FeatureFlagRepository repository;
    private UserRepository userRepository;

    private UserId adminId;
    private UserId plainId;

    @BeforeEach
    void setUp() {
        repository = mock(FeatureFlagRepository.class);
        userRepository = mock(UserRepository.class);

        User admin = user("admin@example.com");
        admin.promoteToAdmin();
        adminId = admin.getId();
        when(userRepository.findById(adminId)).thenReturn(Optional.of(admin));

        User plain = user("plain@example.com");
        plainId = plain.getId();
        when(userRepository.findById(plainId)).thenReturn(Optional.of(plain));
    }

    private static User user(String email) {
        return User.create(UserId.generate(), email, "Test User", null, OAuthProvider.GOOGLE);
    }

    private UpdateFeatureFlagService updateService() {
        return new UpdateFeatureFlagService(
            repository, new FeatureFlagAdminGuard(userRepository), DEFAULTS);
    }

    @Nested
    @DisplayName("who may change a flag")
    class Authorization {

        @Test
        @DisplayName("an administrator may")
        void adminMay() {
            when(repository.find(Feature.CHALLENGES)).thenReturn(Optional.empty());
            when(repository.save(any())).thenAnswer(call -> call.getArgument(0));

            FeatureFlag saved = updateService().execute(
                new UpdateFeatureFlagCommand(Feature.CHALLENGES, true, List.of(), adminId));

            assertThat(saved.isEnabled()).isTrue();
            assertThat(saved.getUpdatedBy()).isEqualTo(adminId);
        }

        @Test
        @DisplayName("a plain user may not, and nothing is written")
        void plainUserMayNot() {
            UpdateFeatureFlagService service = updateService();
            UpdateFeatureFlagCommand command =
                new UpdateFeatureFlagCommand(Feature.CHALLENGES, true, List.of(), plainId);

            assertThatThrownBy(() -> service.execute(command))
                .isInstanceOf(AccessDeniedException.class);

            verify(repository, never()).save(any());
        }

        @Test
        @DisplayName("an unidentified caller may not")
        void anonymousMayNot() {
            UpdateFeatureFlagService service = updateService();
            UpdateFeatureFlagCommand command =
                new UpdateFeatureFlagCommand(Feature.CHALLENGES, true, List.of(), null);

            assertThatThrownBy(() -> service.execute(command))
                .isInstanceOf(AccessDeniedException.class);

            verify(repository, never()).save(any());
        }

        @Test
        @DisplayName("the rule is the ADMIN role and nothing else — no per-edition exemption")
        void noEditionExemption() {
            // A standalone self-hoster reaches this by being promoted to ADMIN at boot
            // (LocalBootstrapProvisioner), not by the guard making an exception. An exemption here
            // was true at this layer only, and the sidebar went on hiding the screen from exactly
            // the user it was meant to admit.
            UpdateFeatureFlagService service = updateService();
            UpdateFeatureFlagCommand command =
                new UpdateFeatureFlagCommand(Feature.CHALLENGES, true, List.of(), plainId);

            assertThatThrownBy(() -> service.execute(command))
                .isInstanceOf(AccessDeniedException.class);
        }

        @Test
        @DisplayName("listing flags is administrators-only too — targeting exposes who is on a beta")
        void listingRequiresAdmin() {
            ListFeatureFlagsService service = new ListFeatureFlagsService(
                repository, new FeatureFlagAdminGuard(userRepository), DEFAULTS);

            assertThatThrownBy(() -> service.execute(plainId))
                .isInstanceOf(AccessDeniedException.class);
        }
    }

    @Nested
    @DisplayName("listing")
    class Listing {

        @Test
        @DisplayName("reports every registered feature, even one with no row yet")
        void listsUnseededFeatures() {
            when(repository.findAll()).thenReturn(List.of(
                FeatureFlag.reconstitute(Feature.CHALLENGES, true, List.of(), null, null)));

            List<FeatureFlag> flags = new ListFeatureFlagsService(
                repository, new FeatureFlagAdminGuard(userRepository), DEFAULTS)
                .execute(adminId);

            assertThat(flags).hasSize(Feature.values().length);
            assertThat(flags).extracting(FeatureFlag::getFeature)
                .contains(Feature.CHALLENGES, Feature.MARKET_RESEARCH);

            // The unseeded one falls back to its configured default rather than to "off".
            FeatureFlag research = flags.stream()
                .filter(f -> f.getFeature() == Feature.MARKET_RESEARCH)
                .findFirst().orElseThrow();
            assertThat(research.isEnabled()).isTrue();
        }
    }

    @Nested
    @DisplayName("seeding")
    class Seeding {

        @Test
        @DisplayName("creates only the missing flags, using the profile defaults")
        void seedsMissingOnly() {
            when(repository.findAll()).thenReturn(List.of(
                FeatureFlag.reconstitute(Feature.CHALLENGES, true, List.of(), null, null)));
            when(repository.save(any())).thenAnswer(call -> call.getArgument(0));

            int created = new SeedFeatureFlagsService(repository, DEFAULTS).execute();

            assertThat(created).isEqualTo(Feature.values().length - 1);
            verify(repository, never()).save(argThatIsFeature(Feature.CHALLENGES));
        }

        @Test
        @DisplayName("never overwrites an existing flag, so a redeploy cannot undo an admin change")
        void doesNotOverwrite() {
            // Challenges default is false, but an administrator has switched it on.
            when(repository.findAll()).thenReturn(List.of(
                FeatureFlag.reconstitute(Feature.CHALLENGES, true, List.of(), null, null),
                FeatureFlag.reconstitute(Feature.MARKET_RESEARCH, false, List.of(), null, null)));

            int created = new SeedFeatureFlagsService(repository, DEFAULTS).execute();

            assertThat(created).isZero();
            verify(repository, never()).save(any());
        }

        private static FeatureFlag argThatIsFeature(Feature feature) {
            return org.mockito.ArgumentMatchers.argThat(
                flag -> flag != null && flag.getFeature() == feature);
        }
    }

    @Nested
    @DisplayName("evaluation")
    class Evaluation {

        private FeatureFlagEvaluationPort port;
        private EvaluateFeatureService service;

        @BeforeEach
        void setUp() {
            port = mock(FeatureFlagEvaluationPort.class);
            service = new EvaluateFeatureService(port, DEFAULTS, userRepository);
        }

        @Test
        @DisplayName("passes the configured default as the fallback, per feature")
        void passesPerFeatureFallback() {
            when(port.isEnabled(any(), any(), anyBoolean())).thenReturn(true);

            service.isEnabled(Feature.CHALLENGES, FeatureFlagContext.anonymous());
            service.isEnabled(Feature.MARKET_RESEARCH, FeatureFlagContext.anonymous());

            verify(port).isEnabled(eq(Feature.CHALLENGES), any(), eq(false));
            verify(port).isEnabled(eq(Feature.MARKET_RESEARCH), any(), eq(true));
        }

        @Test
        @DisplayName("a null context is treated as anonymous, not as a failure")
        void nullContextIsAnonymous() {
            when(port.isEnabled(any(), any(), anyBoolean())).thenReturn(true);

            assertThat(service.isEnabled(Feature.CHALLENGES, null)).isTrue();
        }

        @Test
        @DisplayName("requireEnabled names the flag in the refusal")
        void requireEnabledNamesTheFlag() {
            when(port.isEnabled(any(), any(), anyBoolean())).thenReturn(false);

            assertThatThrownBy(() ->
                service.requireEnabled(Feature.CHALLENGES, FeatureFlagContext.anonymous()))
                .isInstanceOf(FeatureDisabledException.class)
                .hasMessageContaining("challenges");
        }

        @Test
        @DisplayName("requireEnabled passes silently when the feature is on")
        void requireEnabledPasses() {
            when(port.isEnabled(any(), any(), anyBoolean())).thenReturn(true);

            service.requireEnabled(Feature.CHALLENGES, FeatureFlagContext.anonymous());
        }

        @Test
        @DisplayName("evaluateAll answers for every registered feature")
        void evaluatesEveryFeature() {
            when(port.isEnabled(any(), any(), anyBoolean())).thenReturn(true);

            Map<Feature, Boolean> all = service.evaluateAll(FeatureFlagContext.anonymous());

            assertThat(all).hasSize(Feature.values().length).containsValue(true);
        }

        @Test
        @DisplayName("evaluating by user id resolves their email so email targeting works")
        void resolvesEmailForUserId() {
            when(port.isEnabled(any(), any(), anyBoolean())).thenReturn(true);

            service.isEnabledForUser(Feature.CHALLENGES, plainId);

            verify(port).isEnabled(eq(Feature.CHALLENGES),
                org.mockito.ArgumentMatchers.argThat(
                    context -> "plain@example.com".equals(context.userEmail())),
                eq(false));
        }

        @Test
        @DisplayName("a null user id evaluates anonymously")
        void nullUserIsAnonymous() {
            when(port.isEnabled(any(), any(), anyBoolean())).thenReturn(false);

            service.isEnabledForUser(Feature.CHALLENGES, null);

            verify(port).isEnabled(eq(Feature.CHALLENGES),
                org.mockito.ArgumentMatchers.argThat(FeatureFlagContext::isAnonymous), eq(false));
        }

        @Test
        @DisplayName("a caller we cannot look up is still evaluated, by id rather than anonymously")
        void unknownUserStillCarriesId() {
            UserId ghost = UserId.generate();
            when(userRepository.findById(ghost)).thenReturn(Optional.empty());
            when(port.isEnabled(any(), any(), anyBoolean())).thenReturn(true);

            service.isEnabledForUser(Feature.CHALLENGES, ghost);

            verify(port).isEnabled(eq(Feature.CHALLENGES),
                org.mockito.ArgumentMatchers.argThat(
                    context -> ghost.equals(context.userId()) && context.isAnonymous()),
                eq(false));
        }

        @Test
        @DisplayName("requireEnabledForUser refuses when the feature is off for them")
        void requireEnabledForUserRefuses() {
            when(port.isEnabled(any(), any(), anyBoolean())).thenReturn(false);

            assertThatThrownBy(() -> service.requireEnabledForUser(Feature.MARKET_RESEARCH, plainId))
                .isInstanceOf(FeatureDisabledException.class)
                .hasMessageContaining("market-research");
        }
    }

    @Nested
    @DisplayName("defaults")
    class Defaults {

        @Test
        @DisplayName("an unlisted feature defaults to off rather than to a missing entry")
        void unlistedFeatureDefaultsOff() {
            FeatureFlagDefaults sparse = new FeatureFlagDefaults(Map.of());

            for (Feature feature : Feature.values()) {
                assertThat(sparse.defaultFor(feature)).as("%s", feature).isFalse();
            }
        }

        @Test
        @DisplayName("a null map is tolerated")
        void nullMapIsTolerated() {
            assertThat(new FeatureFlagDefaults(null).defaultFor(Feature.CHALLENGES)).isFalse();
        }
    }

    @Test
    @DisplayName("the disabled-feature refusal names the flag an administrator has to toggle")
    void refusalNamesTheFlag() {
        FeatureDisabledException exception = new FeatureDisabledException(Feature.MARKET_RESEARCH);

        assertThat(exception.getFlagName()).isEqualTo("market-research");
        assertThat(exception.getFeature()).isEqualTo(Feature.MARKET_RESEARCH);
        assertThat(exception.getMessage()).contains("market-research");
    }

    @Test
    @DisplayName("an update targeted at emails is stored as a userWithId strategy")
    void updateStoresTargeting() {
        when(repository.find(Feature.CHALLENGES)).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(call -> call.getArgument(0));

        FeatureFlag saved = updateService().execute(new UpdateFeatureFlagCommand(
            Feature.CHALLENGES, true,
            List.of(FeatureFlagStrategy.forEmails(List.of("beta@example.com"))),
            adminId));

        assertThat(saved.isOnForEveryone()).isFalse();
        assertThat(saved.getStrategies())
            .singleElement()
            .satisfies(strategy -> assertThat(strategy.parameters())
                .containsEntry("userIds", "beta@example.com"));
    }
}
