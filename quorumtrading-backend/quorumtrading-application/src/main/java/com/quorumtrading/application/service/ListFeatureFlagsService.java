package com.quorumtrading.application.service;

import com.quorumtrading.application.config.FeatureFlagDefaults;
import com.quorumtrading.application.port.out.FeatureFlagRepository;
import com.quorumtrading.application.usecase.ListFeatureFlagsUseCase;
import com.quorumtrading.model.entity.FeatureFlag;
import com.quorumtrading.model.enums.Feature;
import com.quorumtrading.model.valueobject.UserId;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Lists every registered feature, whether or not it has a stored row yet.
 *
 * <p>Driving off {@link Feature#values()} rather than off the table means a newly added feature is
 * manageable in the admin UI the moment it is declared, even before the seeder has run.</p>
 */
public class ListFeatureFlagsService implements ListFeatureFlagsUseCase {

    private final FeatureFlagRepository repository;
    private final FeatureFlagAdminGuard adminGuard;
    private final FeatureFlagDefaults defaults;

    public ListFeatureFlagsService(FeatureFlagRepository repository,
                                   FeatureFlagAdminGuard adminGuard,
                                   FeatureFlagDefaults defaults) {
        this.repository = repository;
        this.adminGuard = adminGuard;
        this.defaults = defaults;
    }

    @Override
    public List<FeatureFlag> execute(UserId requestingUserId) {
        adminGuard.requireFlagAdmin(requestingUserId, "*");

        Map<Feature, FeatureFlag> stored = new EnumMap<>(Feature.class);
        for (FeatureFlag flag : repository.findAll()) {
            stored.put(flag.getFeature(), flag);
        }

        List<FeatureFlag> result = new ArrayList<>();
        for (Feature feature : Feature.values()) {
            FeatureFlag flag = stored.get(feature);
            result.add(flag != null ? flag : FeatureFlag.seed(feature, defaults.defaultFor(feature)));
        }
        return result;
    }
}
