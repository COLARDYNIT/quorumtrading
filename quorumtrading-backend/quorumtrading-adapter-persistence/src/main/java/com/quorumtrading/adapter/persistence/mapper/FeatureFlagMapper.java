package com.quorumtrading.adapter.persistence.mapper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quorumtrading.adapter.persistence.entity.FeatureFlagJpaEntity;
import com.quorumtrading.model.entity.FeatureFlag;
import com.quorumtrading.model.enums.Feature;
import com.quorumtrading.model.valueobject.FeatureFlagStrategy;
import com.quorumtrading.model.valueobject.UserId;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Converts between {@link FeatureFlag} and its row.
 *
 * <p>Strategies are stored as a JSON array in the same shape Unleash uses on the wire, so the
 * evaluation adapter can hand them to the SDK with no second translation.</p>
 */
@ApplicationScoped
public class FeatureFlagMapper {

    private static final Logger LOG = Logger.getLogger(FeatureFlagMapper.class.getName());
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<List<Map<String, Object>>> STRATEGY_LIST =
        new TypeReference<>() { };
    private static final String EMPTY_JSON_ARRAY = "[]";

    public FeatureFlagJpaEntity toJpaEntity(FeatureFlag flag) {
        FeatureFlagJpaEntity entity = new FeatureFlagJpaEntity();
        entity.setName(flag.getFeature().flagName());
        entity.setEnabled(flag.isEnabled());
        entity.setStrategies(serialiseStrategies(flag.getStrategies()));
        entity.setUpdatedAt(flag.getUpdatedAt());
        entity.setUpdatedBy(flag.getUpdatedBy() == null ? null : flag.getUpdatedBy().value());
        return entity;
    }

    /**
     * @return null when the row's name is not in the {@link Feature} registry — a flag that was
     *         removed from the code but whose row is still around. Callers filter these out.
     */
    public FeatureFlag toDomain(FeatureFlagJpaEntity entity) {
        Feature feature = Feature.fromFlagName(entity.getName()).orElse(null);
        if (feature == null) {
            LOG.fine(() -> "Ignoring unknown feature flag row: " + entity.getName());
            return null;
        }
        return FeatureFlag.reconstitute(
            feature,
            entity.isEnabled(),
            deserialiseStrategies(entity.getName(), entity.getStrategies()),
            entity.getUpdatedAt(),
            entity.getUpdatedBy() == null ? null : new UserId(entity.getUpdatedBy()));
    }

    private String serialiseStrategies(List<FeatureFlagStrategy> strategies) {
        if (strategies == null || strategies.isEmpty()) {
            return EMPTY_JSON_ARRAY;
        }
        List<Map<String, Object>> raw = new ArrayList<>();
        for (FeatureFlagStrategy strategy : strategies) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("name", strategy.name());
            entry.put("parameters", strategy.parameters());
            raw.add(entry);
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(raw);
        } catch (Exception e) {
            throw new IllegalStateException("Could not serialise feature flag strategies", e);
        }
    }

    private List<FeatureFlagStrategy> deserialiseStrategies(String flagName, String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<Map<String, Object>> raw = OBJECT_MAPPER.readValue(json, STRATEGY_LIST);
            List<FeatureFlagStrategy> strategies = new ArrayList<>();
            for (Map<String, Object> entry : raw) {
                Object name = entry.get("name");
                if (name == null) {
                    continue;
                }
                strategies.add(new FeatureFlagStrategy(name.toString(), stringify(entry.get("parameters"))));
            }
            return strategies;
        } catch (Exception e) {
            // A flag whose strategies will not parse is treated as having none. Combined with the
            // entity's normalisation that means "on for everyone" rather than a boot failure —
            // losing the targeting is bad, but taking the instance down over it is worse.
            LOG.log(Level.WARNING, e,
                () -> "Could not parse strategies for feature flag '" + flagName + "', treating as unconditional");
            return List.of();
        }
    }

    private static Map<String, String> stringify(Object parameters) {
        if (!(parameters instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                result.put(entry.getKey().toString(), entry.getValue().toString());
            }
        }
        return result;
    }
}
