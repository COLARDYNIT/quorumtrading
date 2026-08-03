package com.quorumtrading.application.usecase;

import com.quorumtrading.application.port.out.LLMPort;
import com.quorumtrading.application.service.ModelPinValidator;
import com.quorumtrading.application.port.out.UserBudgetRepository;
import com.quorumtrading.model.entity.UserBudget;
import com.quorumtrading.model.enums.AgentRole;
import com.quorumtrading.model.service.ModelFilter;
import com.quorumtrading.model.valueobject.LLMModelInfo;
import com.quorumtrading.model.valueobject.ModelId;
import com.quorumtrading.model.valueobject.ModelListingCapability;
import com.quorumtrading.model.valueobject.ModelPreferenceKey;
import com.quorumtrading.model.valueobject.ModelPreferences;
import com.quorumtrading.model.valueobject.TradingContext;
import com.quorumtrading.model.valueobject.UserId;
import org.jboss.logging.Logger;

import java.util.Map;
import java.util.Optional;

/**
 * Use case for updating a user's model preferences for different trading contexts.
 * Supports both global context preferences and per-agent overrides.
 */
public class UpdateModelPreferencesUseCase {

    private static final Logger LOG = Logger.getLogger(UpdateModelPreferencesUseCase.class);

    private final UserBudgetRepository userBudgetRepository;
    private final LLMPort llmPort;
    private final ModelFilter modelFilter;

    /** Optional; supplies the servable alternatives that turn a refusal into something actionable. */
    private final ModelPinValidator modelPinValidator;

    public UpdateModelPreferencesUseCase(
        UserBudgetRepository userBudgetRepository,
        LLMPort llmPort,
        ModelFilter modelFilter
    ) {
        this(userBudgetRepository, llmPort, modelFilter, null);
    }

    public UpdateModelPreferencesUseCase(
        UserBudgetRepository userBudgetRepository,
        LLMPort llmPort,
        ModelFilter modelFilter,
        ModelPinValidator modelPinValidator
    ) {
        this.userBudgetRepository = userBudgetRepository;
        this.llmPort = llmPort;
        this.modelFilter = modelFilter;
        this.modelPinValidator = modelPinValidator;
    }

    /**
     * Update global model preferences for a user.
     */
    public ModelPreferences execute(UserId userId, Map<TradingContext, String> preferences) {
        return execute(userId, preferences, Map.of());
    }

    /**
     * Update global and per-agent model preferences for a user.
     *
     * @param userId          The user ID
     * @param preferences     Map of TradingContext to model ID string (global prefs)
     * @param agentOverrides  Map of storage key (e.g. "BACKTESTING_TECHNICAL") to model ID string
     * @return Updated model preferences
     */
    public ModelPreferences execute(UserId userId, Map<TradingContext, String> preferences, Map<String, String> agentOverrides) {
        LOG.debugf("Updating model preferences for user %s: global=%s, agent=%s", userId.value(), preferences, agentOverrides);

        // Get or create user budget
        UserBudget budget = userBudgetRepository.findByUserId(userId)
            .orElseGet(() -> UserBudget.createUnlimited(userId));

        // Validate and set each global preference
        for (Map.Entry<TradingContext, String> entry : preferences.entrySet()) {
            TradingContext context = entry.getKey();
            String modelIdStr = entry.getValue();

            if (modelIdStr == null || modelIdStr.isBlank()) {
                budget.clearModelPreference(context);
                LOG.debugf("Cleared model preference for context %s", context);
            } else {
                validateModel(modelIdStr, context);
                budget.setModelPreference(context, ModelId.of(modelIdStr));
                LOG.debugf("Set model preference for context %s to %s", context, modelIdStr);
            }
        }

        // Validate and set each per-agent override.
        //
        // An unrecognised key used to be logged and skipped, so the caller was told the update
        // succeeded while their preference silently ceased to exist. fromStorageKey now throws, and
        // that refusal is propagated: a rejected preference is strictly better than a vanished one.
        for (Map.Entry<String, String> entry : agentOverrides.entrySet()) {
            ModelPreferenceKey key = ModelPreferenceKey.fromStorageKey(entry.getKey());
            if (key.isGlobal()) {
                throw new IllegalArgumentException("Not an agent-specific preference key: '"
                        + entry.getKey() + "'. Set context-global preferences through the global map.");
            }

            String modelIdStr = entry.getValue();
            if (modelIdStr == null || modelIdStr.isBlank()) {
                budget.clearAgentModelPreference(key.context(), key.agentKind());
                LOG.debugf("Cleared agent model preference for %s", entry.getKey());
            } else {
                validateModel(modelIdStr, key.context());
                budget.setAgentModelPreference(key.context(), key.agentKind(), ModelId.of(modelIdStr));
                LOG.debugf("Set agent model preference for %s to %s", entry.getKey(), modelIdStr);
            }
        }

        // Save and return
        UserBudget saved = userBudgetRepository.save(budget);
        LOG.infof("Updated model preferences for user %s", userId.value());

        return saved.getModelPreferences();
    }

    /**
     * Set a single model preference for a specific context.
     */
    public ModelPreferences execute(UserId userId, TradingContext context, String modelId) {
        return execute(userId, Map.of(context, modelId != null ? modelId : ""));
    }

    /**
     * Get current model preferences for a user.
     */
    public ModelPreferences getPreferences(UserId userId) {
        return userBudgetRepository.findByUserId(userId)
            .map(UserBudget::getModelPreferences)
            .orElse(ModelPreferences.EMPTY);
    }

    /**
     * Validate that a model exists and is valid for the given context.
     *
     * <p>Rejection here is what makes fail-hard at run time defensible: a preference the active
     * engine cannot serve must never reach storage, because the run-time policy is to refuse rather
     * than substitute. The refusal carries the servable alternatives, so a caller — including an
     * LLM driving MCP — can correct itself without a second round-trip.</p>
     */
    private void validateModel(String modelId, TradingContext context) {
        ModelListingCapability capability = llmPort.modelListingCapability();
        if (capability == ModelListingCapability.NOT_SUPPORTED) {
            LOG.warn("Cannot validate model - the active LLM engine has no model catalogue by design");
            return;
        }
        if (capability == ModelListingCapability.UNAVAILABLE) {
            // Not the same thing. The engine does have a catalogue, we just could not read it —
            // treating that as "no validation possible" is how any string used to get through.
            LOG.warnf("Cannot validate model '%s' - the active LLM engine's catalogue is unreachable "
                    + "right now; storing it unvalidated", modelId);
            return;
        }

        Optional<LLMModelInfo> modelOpt = llmPort.getModelInfo(modelId);
        if (modelOpt.isEmpty()) {
            if (modelPinValidator != null) {
                throw modelPinValidator.unservable(modelId, null);
            }
            throw new IllegalArgumentException("Model not found: " + modelId);
        }

        LLMModelInfo model = modelOpt.get();
        if (!modelFilter.isValidForContext(model, context)) {
            String requirements = modelFilter.getContextRequirements(context);
            throw new IllegalArgumentException(
                "Model '" + modelId + "' is not valid for context " + context + ". " + requirements
            );
        }
    }
}
