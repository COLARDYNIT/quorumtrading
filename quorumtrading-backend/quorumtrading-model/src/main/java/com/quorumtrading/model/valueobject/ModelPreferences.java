package com.quorumtrading.model.valueobject;

import com.quorumtrading.model.enums.AgentKind;
import com.quorumtrading.model.enums.AgentRole;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Value object representing user's model preferences per trading context,
 * with optional per-agent overrides.
 * <p>
 * Internal map uses {@link ModelPreferenceKey} as key, supporting both
 * global context preferences (e.g., BACKTESTING) and agent-specific ones
 * (e.g., BACKTESTING_TECHNICAL).
 */
public record ModelPreferences(
    Map<ModelPreferenceKey, ModelId> preferences,
    Map<ModelPreferenceKey, ModelEffort> efforts
) {

    /**
     * Empty preferences (will use system defaults).
     */
    public static final ModelPreferences EMPTY = new ModelPreferences(Map.of(), Map.of());

    public ModelPreferences {
        preferences = preferences == null ? Map.of() : Map.copyOf(preferences);
        // A parallel map rather than a compound value: effort is a second dimension of the same
        // choice, and folding it into ModelId would make ModelId stop being a model id.
        efforts = efforts == null ? Map.of() : Map.copyOf(efforts);
    }

    /** Preferences without any effort levels. */
    public ModelPreferences(Map<ModelPreferenceKey, ModelId> preferences) {
        this(preferences, Map.of());
    }

    /** The effort requested for a key, empty when none was expressed. */
    public Optional<ModelEffort> effortFor(ModelPreferenceKey key) {
        return Optional.ofNullable(efforts.get(key));
    }

    /**
     * The effort that applies to an agent: the per-agent entry, else the context-global one.
     * Follows the same precedence as the model itself, because it is part of the same choice.
     */
    public Optional<ModelEffort> resolveEffortForAgent(TradingContext context, AgentKind kind) {
        return effortFor(ModelPreferenceKey.forAgent(context, kind))
            .or(() -> effortFor(ModelPreferenceKey.global(context)));
    }

    /** The same preferences with an effort attached to one key; null clears it. */
    public ModelPreferences withEffort(ModelPreferenceKey key, ModelEffort effort) {
        Map<ModelPreferenceKey, ModelEffort> updated = new HashMap<>(efforts);
        if (effort == null) {
            updated.remove(key);
        } else {
            updated.put(key, effort);
        }
        return new ModelPreferences(preferences, updated);
    }

    /**
     * Create preferences from a ModelPreferenceKey map.
     */
    public static ModelPreferences of(Map<ModelPreferenceKey, ModelId> prefs) {
        return new ModelPreferences(prefs, Map.of());
    }

    /**
     * Create preferences from a legacy TradingContext-keyed map (backwards compatible).
     */
    public static ModelPreferences ofContextMap(Map<TradingContext, ModelId> contextPrefs) {
        Map<ModelPreferenceKey, ModelId> map = new HashMap<>();
        for (var entry : contextPrefs.entrySet()) {
            map.put(ModelPreferenceKey.global(entry.getKey()), entry.getValue());
        }
        return new ModelPreferences(map, Map.of());
    }

    /**
     * Get the global preferred model for a context (backwards compatible).
     */
    public Optional<ModelId> getFor(TradingContext context) {
        return Optional.ofNullable(preferences.get(ModelPreferenceKey.global(context)));
    }

    /**
     * Get the agent-specific preferred model for a context + agent.
     */
    public Optional<ModelId> getForAgent(TradingContext context, AgentKind kind) {
        return Optional.ofNullable(preferences.get(ModelPreferenceKey.forAgent(context, kind)));
    }

    /**
     * Get the agent-specific preferred model for a context + consensus agent role.
     */
    public Optional<ModelId> getForAgent(TradingContext context, AgentRole role) {
        return getForAgent(context, AgentKind.of(role));
    }

    /**
     * Resolve the model for a specific agent: checks agent-specific first, then falls back to global.
     */
    public Optional<ModelId> resolveForAgent(TradingContext context, AgentKind kind) {
        return getForAgent(context, kind)
            .or(() -> getFor(context));
    }

    /**
     * Create new preferences with an updated global context preference.
     */
    public ModelPreferences withPreference(TradingContext context, ModelId modelId) {
        Map<ModelPreferenceKey, ModelId> newPrefs = new HashMap<>(preferences);
        ModelPreferenceKey key = ModelPreferenceKey.global(context);
        if (modelId != null) {
            newPrefs.put(key, modelId);
        } else {
            newPrefs.remove(key);
        }
        return new ModelPreferences(newPrefs, efforts);
    }

    /**
     * Create new preferences with an updated agent-specific preference.
     */
    public ModelPreferences withAgentPreference(TradingContext context, AgentRole role, ModelId modelId) {
        return withAgentPreference(context, AgentKind.of(role), modelId);
    }

    /**
     * Create new preferences with an updated agent-specific preference, for any agent kind.
     */
    public ModelPreferences withAgentPreference(TradingContext context, AgentKind kind, ModelId modelId) {
        Map<ModelPreferenceKey, ModelId> newPrefs = new HashMap<>(preferences);
        ModelPreferenceKey key = ModelPreferenceKey.forAgent(context, kind);
        if (modelId != null) {
            newPrefs.put(key, modelId);
        } else {
            newPrefs.remove(key);
        }
        return new ModelPreferences(newPrefs, efforts);
    }

    /**
     * Create new preferences with a preference removed.
     */
    public ModelPreferences withoutPreference(TradingContext context) {
        return withPreference(context, null);
    }

    /**
     * Get all agent-specific overrides for a given context.
     * Returns a map of AgentRole to ModelId (only entries that are explicitly set).
     */
    public Map<AgentRole, ModelId> getAgentOverrides(TradingContext context) {
        Map<AgentRole, ModelId> overrides = new LinkedHashMap<>();
        for (AgentRole role : AgentRole.values()) {
            ModelPreferenceKey key = ModelPreferenceKey.forAgent(context, role);
            ModelId modelId = preferences.get(key);
            if (modelId != null) {
                overrides.put(role, modelId);
            }
        }
        return overrides;
    }

    /**
     * Get all agent overrides for a context, across both the consensus and research vocabularies.
     */
    public Map<AgentKind, ModelId> getAllAgentOverrides(TradingContext context) {
        Map<AgentKind, ModelId> overrides = new LinkedHashMap<>();
        for (AgentKind kind : AgentKind.values()) {
            ModelId modelId = preferences.get(ModelPreferenceKey.forAgent(context, kind));
            if (modelId != null) {
                overrides.put(kind, modelId);
            }
        }
        return overrides;
    }

    /**
     * Check if there are any preferences set.
     */
    public boolean isEmpty() {
        return preferences.isEmpty() && efforts.isEmpty();
    }

    /**
     * Check if a global preference is set for a context.
     */
    public boolean hasPreferenceFor(TradingContext context) {
        return preferences.containsKey(ModelPreferenceKey.global(context));
    }

    /**
     * Check if any agent-specific overrides exist across all contexts.
     */
    public boolean hasAnyAgentOverrides() {
        return preferences.keySet().stream().anyMatch(key -> !key.isGlobal());
    }

    /**
     * Get all agent overrides grouped by context name.
     * Returns a map like { "BACKTESTING": { "TECHNICAL": ModelId } }.
     */
    public Map<String, Map<String, ModelId>> getAllAgentOverridesGrouped() {
        Map<String, Map<String, ModelId>> grouped = new LinkedHashMap<>();
        for (var entry : preferences.entrySet()) {
            ModelPreferenceKey key = entry.getKey();
            if (!key.isGlobal()) {
                grouped.computeIfAbsent(key.context().name(), k -> new LinkedHashMap<>())
                    .put(key.agentKind().name(), entry.getValue());
            }
        }
        return grouped;
    }
}
