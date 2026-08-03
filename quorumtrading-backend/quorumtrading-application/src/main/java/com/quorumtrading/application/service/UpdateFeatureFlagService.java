package com.quorumtrading.application.service;

import com.quorumtrading.application.config.FeatureFlagDefaults;
import com.quorumtrading.application.port.out.FeatureFlagRepository;
import com.quorumtrading.application.usecase.UpdateFeatureFlagUseCase;
import com.quorumtrading.application.usecase.command.UpdateFeatureFlagCommand;
import com.quorumtrading.model.entity.FeatureFlag;
import com.quorumtrading.model.valueobject.FeatureFlagStrategy;

import java.util.logging.Logger;

/**
 * Applies an administrator's change to a feature flag and records who made it.
 */
public class UpdateFeatureFlagService implements UpdateFeatureFlagUseCase {

    private static final Logger LOG = Logger.getLogger(UpdateFeatureFlagService.class.getName());

    private final FeatureFlagRepository repository;
    private final FeatureFlagAdminGuard adminGuard;
    private final FeatureFlagDefaults defaults;

    public UpdateFeatureFlagService(FeatureFlagRepository repository,
                                    FeatureFlagAdminGuard adminGuard,
                                    FeatureFlagDefaults defaults) {
        this.repository = repository;
        this.adminGuard = adminGuard;
        this.defaults = defaults;
    }

    @Override
    public FeatureFlag execute(UpdateFeatureFlagCommand command) {
        adminGuard.requireFlagAdmin(command.requestingUserId(), command.feature().flagName());

        FeatureFlag flag = repository.find(command.feature())
            .orElseGet(() -> FeatureFlag.seed(command.feature(), defaults.defaultFor(command.feature())));
        flag.changeTo(command.enabled(), command.strategies(), command.requestingUserId());
        FeatureFlag saved = repository.save(flag);

        LOG.info(String.format("Feature flag '%s' set to enabled=%s (%s) by user %s",
            command.feature().flagName(),
            saved.isEnabled(),
            describeAudience(saved),
            command.requestingUserId()));
        return saved;
    }

    private static String describeAudience(FeatureFlag flag) {
        if (!flag.isEnabled()) {
            return "off for everyone";
        }
        if (flag.isOnForEveryone()) {
            return "on for everyone";
        }
        return "on for " + flag.getStrategies().stream()
            .map(FeatureFlagStrategy::name)
            .toList();
    }
}
