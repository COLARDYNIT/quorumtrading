package com.quorumtrading.application.service;

import com.quorumtrading.application.config.FeatureFlagDefaults;
import com.quorumtrading.application.exception.FeatureDisabledException;
import com.quorumtrading.application.port.out.FeatureFlagEvaluationPort;
import com.quorumtrading.application.port.out.UserRepository;
import com.quorumtrading.application.usecase.EvaluateFeatureUseCase;
import com.quorumtrading.model.entity.User;
import com.quorumtrading.model.enums.Feature;
import com.quorumtrading.model.valueobject.FeatureFlagContext;
import com.quorumtrading.model.valueobject.UserId;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * Evaluates feature flags through the Unleash-backed port, applying the profile default as the
 * fallback when a flag has no definition yet.
 */
public class EvaluateFeatureService implements EvaluateFeatureUseCase {

    private final FeatureFlagEvaluationPort evaluationPort;
    private final FeatureFlagDefaults defaults;
    private final UserRepository userRepository;

    public EvaluateFeatureService(FeatureFlagEvaluationPort evaluationPort,
                                  FeatureFlagDefaults defaults,
                                  UserRepository userRepository) {
        this.evaluationPort = evaluationPort;
        this.defaults = defaults;
        this.userRepository = userRepository;
    }

    @Override
    public boolean isEnabled(Feature feature, FeatureFlagContext context) {
        FeatureFlagContext resolved = context == null ? FeatureFlagContext.anonymous() : context;
        return evaluationPort.isEnabled(feature, resolved, defaults.defaultFor(feature));
    }

    @Override
    public void requireEnabled(Feature feature, FeatureFlagContext context) {
        if (!isEnabled(feature, context)) {
            throw new FeatureDisabledException(feature);
        }
    }

    @Override
    public Map<Feature, Boolean> evaluateAll(FeatureFlagContext context) {
        Map<Feature, Boolean> result = new EnumMap<>(Feature.class);
        for (Feature feature : Feature.values()) {
            result.put(feature, isEnabled(feature, context));
        }
        return result;
    }

    @Override
    public boolean isEnabledForUser(Feature feature, UserId userId) {
        return isEnabled(feature, contextFor(userId));
    }

    @Override
    public void requireEnabledForUser(Feature feature, UserId userId) {
        requireEnabled(feature, contextFor(userId));
    }

    private FeatureFlagContext contextFor(UserId userId) {
        if (userId == null) {
            return FeatureFlagContext.anonymous();
        }
        // A caller we cannot look up is still a caller — evaluate them by id rather than dropping
        // to anonymous, so an unconditional flag keeps working when the users table is unhappy.
        Optional<User> user = userRepository.findById(userId);
        return user.map(found -> FeatureFlagContext.forUser(userId, found.getEmail()))
            .orElseGet(() -> new FeatureFlagContext(userId, null, Map.of()));
    }
}
