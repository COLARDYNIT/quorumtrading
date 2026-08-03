package com.quorumtrading.application.service;

import com.quorumtrading.application.port.out.StrategyRepository;
import com.quorumtrading.application.port.out.UserBudgetRepository;
import com.quorumtrading.application.usecase.llm.UnservablePinReport;
import com.quorumtrading.model.entity.Strategy;
import com.quorumtrading.model.entity.UserBudget;
import com.quorumtrading.model.enums.AgentRole;
import com.quorumtrading.model.orchestration.StrategyConfig;
import com.quorumtrading.model.orchestration.StrategyConfigParser;
import com.quorumtrading.model.service.ModelCandidateService;
import com.quorumtrading.model.valueobject.ModelId;
import com.quorumtrading.model.valueobject.ModelPreferenceKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Sweeps everything already stored against the engine that is active now.
 *
 * <p>The write-time check only ever sees the engine that was active when the write happened. Store
 * a valid OpenRouter preference, then switch to a local server, and nothing revalidates — the
 * preference is still there, still displayed as honoured, and unservable. That is the realistic
 * route to a broken pin, so the switch itself is where it has to be caught.</p>
 *
 * <p>Read-only by design: it reports, it does not repair. Repairing means picking a model for the
 * user, which is the silent substitution this work exists to remove.</p>
 *
 * <p>Plain POJO wired in {@code ApplicationConfig}, per the CDI convention.</p>
 */
public class RevalidateStoredModelPinsService {

    private static final Logger LOG = LoggerFactory.getLogger(RevalidateStoredModelPinsService.class);

    private final UserBudgetRepository userBudgetRepository;
    private final StrategyRepository strategyRepository;
    private final ModelPinValidator modelPinValidator;
    private final ModelCandidateService candidateService;
    private final StrategyConfigParser parser = new StrategyConfigParser();

    public RevalidateStoredModelPinsService(UserBudgetRepository userBudgetRepository,
                                            StrategyRepository strategyRepository,
                                            ModelPinValidator modelPinValidator) {
        this(userBudgetRepository, strategyRepository, modelPinValidator, new ModelCandidateService());
    }

    public RevalidateStoredModelPinsService(UserBudgetRepository userBudgetRepository,
                                            StrategyRepository strategyRepository,
                                            ModelPinValidator modelPinValidator,
                                            ModelCandidateService candidateService) {
        this.userBudgetRepository = userBudgetRepository;
        this.strategyRepository = strategyRepository;
        this.modelPinValidator = modelPinValidator;
        this.candidateService = candidateService;
    }

    /** Everything stored that the active engine will not serve. */
    public UnservablePinReport revalidate() {
        List<UnservablePinReport.UnservablePin> preferences = new ArrayList<>();
        List<UnservablePinReport.UnservablePin> strategyPins = new ArrayList<>();

        collectUnservablePreferences(preferences);
        collectUnservableStrategyPins(strategyPins);

        if (preferences.isEmpty() && strategyPins.isEmpty()) {
            return UnservablePinReport.EMPTY;
        }

        LOG.warn("Engine change leaves {} stored model pin(s) unservable: {} preference(s), {} strategy pin(s)",
                preferences.size() + strategyPins.size(), preferences.size(), strategyPins.size());
        return new UnservablePinReport(preferences, strategyPins, candidates());
    }

    private void collectUnservablePreferences(List<UnservablePinReport.UnservablePin> into) {
        for (UserBudget budget : userBudgetRepository.findAll()) {
            Map<ModelPreferenceKey, ModelId> prefs = budget.getModelPreferences().preferences();
            for (Map.Entry<ModelPreferenceKey, ModelId> entry : prefs.entrySet()) {
                String modelId = entry.getValue() == null ? null : entry.getValue().value();
                if (!modelPinValidator.isServable(modelId)) {
                    into.add(new UnservablePinReport.UnservablePin(
                            "preference " + entry.getKey().toStorageKey(), modelId));
                }
            }
        }
    }

    private void collectUnservableStrategyPins(List<UnservablePinReport.UnservablePin> into) {
        for (Strategy strategy : strategyRepository.findAll()) {
            StrategyConfig config;
            try {
                config = parser.parse(strategy.getContent());
            } catch (Exception e) {
                // A strategy that does not parse has bigger problems than its model pin, and they
                // are reported elsewhere. Skipping keeps the sweep from failing on unrelated damage.
                LOG.debug("Skipping unparseable strategy '{}' during pin revalidation: {}",
                        strategy.getName(), e.getMessage());
                continue;
            }

            if (!modelPinValidator.isServable(config.getLlmModel())) {
                into.add(new UnservablePinReport.UnservablePin(
                        "strategy '" + strategy.getName() + "' (llm_model)", config.getLlmModel()));
            }

            Map<AgentRole, String> agentModels = config.getLlmAgentModels();
            if (agentModels == null) {
                continue;
            }
            for (AgentRole role : AgentRole.values()) {
                String pinned = agentModels.get(role);
                if (pinned != null && !modelPinValidator.isServable(pinned)) {
                    into.add(new UnservablePinReport.UnservablePin(
                            "strategy '" + strategy.getName() + "' (llm_models." + role.name() + ")", pinned));
                }
            }
        }
    }

    private List<com.quorumtrading.model.valueobject.ModelCandidate> candidates() {
        try {
            return candidateService.curate(modelPinValidator.servableCatalogue());
        } catch (Exception e) {
            LOG.debug("Could not build candidate list for the pin report: {}", e.getMessage());
            return List.of();
        }
    }
}
