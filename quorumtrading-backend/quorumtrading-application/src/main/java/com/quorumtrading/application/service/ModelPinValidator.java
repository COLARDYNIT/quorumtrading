package com.quorumtrading.application.service;

import com.quorumtrading.application.port.out.ActiveLlmEnginePort;
import com.quorumtrading.application.port.out.LLMPort;
import com.quorumtrading.model.enums.AgentRole;
import com.quorumtrading.model.enums.LlmProvider;
import com.quorumtrading.model.exception.UnservableModelPinException;
import com.quorumtrading.model.orchestration.StrategyConfig;
import com.quorumtrading.model.service.ModelCandidateService;
import com.quorumtrading.model.valueobject.ModelCandidate;
import com.quorumtrading.model.valueobject.ModelListingCapability;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Refuses a model pin the active engine will not serve, and says what would work instead.
 *
 * <p>Fail-hard at run time is only defensible if an unservable pin cannot be stored in the first
 * place — otherwise it degrades into crashing on something the system itself allowed the user to
 * save. This closes the write-time holes:</p>
 *
 * <ul>
 *   <li><b>Per-agent pins were never checked at all.</b> {@code llm_models} is tier 1 — the
 *       <em>highest</em> precedence — and reached no validation, so a bad per-agent pin was accepted
 *       at save time and misrouted silently at run time. Both it and {@code llm_model} go through
 *       here now.</li>
 *   <li><b>An unreachable engine used to disable validation.</b> A failed probe reported "no listing
 *       support", which was indistinguishable from "has no catalogue by design", and any string was
 *       accepted. {@link ModelListingCapability} tells those apart: only NOT_SUPPORTED skips.</li>
 * </ul>
 *
 * <p>Validation <em>and</em> the candidate list are produced here, in the application layer, so both
 * driving adapters inherit them and can only differ in rendering.</p>
 *
 * <p>Plain POJO wired in {@code ApplicationConfig}, per the CDI convention.</p>
 */
public class ModelPinValidator {

    private final LLMPort llmPort;
    private final ActiveLlmEnginePort activeLlmEnginePort;
    private final ModelCandidateService candidateService;

    public ModelPinValidator(LLMPort llmPort, ActiveLlmEnginePort activeLlmEnginePort) {
        this(llmPort, activeLlmEnginePort, new ModelCandidateService());
    }

    public ModelPinValidator(LLMPort llmPort, ActiveLlmEnginePort activeLlmEnginePort,
                             ModelCandidateService candidateService) {
        this.llmPort = llmPort;
        this.activeLlmEnginePort = activeLlmEnginePort;
        this.candidateService = candidateService;
    }

    /**
     * Validate every model a strategy pins — the global {@code llm_model} and each entry of
     * {@code llm_models}.
     *
     * @throws UnservableModelPinException naming the first offending pin, with servable alternatives
     */
    public void validateStrategyPins(StrategyConfig config) {
        if (config == null) {
            return;
        }
        validatePin(config.getLlmModel(), null);

        Map<AgentRole, String> agentModels = config.getLlmAgentModels();
        if (agentModels != null) {
            // Iterate the enum rather than the map so the order of the error is stable regardless
            // of how the YAML happened to be written.
            for (AgentRole role : AgentRole.values()) {
                validatePin(agentModels.get(role), role);
            }
        }
    }

    /**
     * Validate one pinned model.
     *
     * @param modelId the pinned id; null/blank is not a pin and passes
     * @param role    the agent this pin is scoped to, or null for a strategy-global pin
     */
    public void validatePin(String modelId, AgentRole role) {
        if (modelId == null || modelId.isBlank()) {
            return;
        }
        if (isServable(modelId)) {
            return;
        }
        throw unservable(modelId, role);
    }

    /** Whether the active engine advertises this id, or cannot be asked at all. */
    public boolean isServable(String modelId) {
        if (modelId == null || modelId.isBlank()) {
            return true;
        }
        return switch (llmPort.modelListingCapability()) {
            // No catalogue by design — there is nothing to check against, so the pin stands.
            case NOT_SUPPORTED -> true;
            // Reachable and enumerable: this is the only case where a real answer exists.
            case ENUMERABLE -> llmPort.getModelInfo(modelId).isPresent();
            // Temporarily unreachable. Accepting anything here is what let unservable preferences
            // through; refusing to *reject* is the honest middle ground — we cannot know.
            case UNAVAILABLE -> true;
        };
    }

    /** Build the rejection, complete with the curated alternatives, without throwing it. */
    public UnservableModelPinException unservable(String modelId, AgentRole role) {
        LlmProvider engine = activeLlmEnginePort.activeEngine().orElse(null);
        List<ModelCandidate> candidates = candidateService.curate(llmPort.getAvailableModels());
        return new UnservableModelPinException(modelId, role, engine, candidates, fullListingHint(candidates));
    }

    /**
     * Only point at the full listing when the candidates really are a subset — on a local server or
     * a CLI the list <em>is</em> everything, and telling the user to go look further is a lie.
     */
    private String fullListingHint(List<ModelCandidate> candidates) {
        int catalogueSize = llmPort.getAvailableModels().size();
        if (catalogueSize <= candidates.size()) {
            return null;
        }
        return "That is a curated subset of " + catalogueSize + " models — see the full list with "
                + "get_available_models (MCP) or GET /api/v1/config/models (REST).";
    }

    /** The active engine, for callers that want to report it alongside their own result. */
    public Optional<LlmProvider> activeEngine() {
        return activeLlmEnginePort.activeEngine();
    }

    /** Everything the active engine advertises — the only legitimate source of alternatives. */
    public List<com.quorumtrading.model.valueobject.LLMModelInfo> servableCatalogue() {
        return llmPort.getAvailableModels();
    }
}
