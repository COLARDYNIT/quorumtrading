package com.quorumtrading.application.service;

import com.quorumtrading.application.config.FeatureFlagDefaults;
import com.quorumtrading.application.port.out.FeatureFlagRepository;
import com.quorumtrading.application.usecase.SeedFeatureFlagsUseCase;
import com.quorumtrading.model.entity.FeatureFlag;
import com.quorumtrading.model.enums.Feature;

import java.util.EnumSet;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Materialises the profile defaults into rows on first boot.
 *
 * <p>Strictly insert-if-absent. Overwriting on every boot would make the database a cache of the
 * properties file rather than the source of truth, and would silently undo an administrator's
 * change on the next restart.</p>
 */
public class SeedFeatureFlagsService implements SeedFeatureFlagsUseCase {

    private static final Logger LOG = Logger.getLogger(SeedFeatureFlagsService.class.getName());

    private final FeatureFlagRepository repository;
    private final FeatureFlagDefaults defaults;

    public SeedFeatureFlagsService(FeatureFlagRepository repository, FeatureFlagDefaults defaults) {
        this.repository = repository;
        this.defaults = defaults;
    }

    @Override
    public int execute() {
        Set<Feature> existing = EnumSet.noneOf(Feature.class);
        for (FeatureFlag flag : repository.findAll()) {
            existing.add(flag.getFeature());
        }

        int created = 0;
        for (Feature feature : Feature.values()) {
            if (existing.contains(feature)) {
                continue;
            }
            boolean enabled = defaults.defaultFor(feature);
            repository.save(FeatureFlag.seed(feature, enabled));
            created++;
            LOG.info(String.format("Seeded feature flag '%s' with default enabled=%s",
                feature.flagName(), enabled));
        }
        return created;
    }
}
