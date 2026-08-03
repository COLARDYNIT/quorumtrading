package com.quorumtrading.application.service;

import com.quorumtrading.application.port.out.ActiveLlmEnginePort;
import com.quorumtrading.application.port.out.LLMPort;
import com.quorumtrading.model.enums.LlmProvider;
import com.quorumtrading.model.valueobject.ModelListingCapability;
import com.quorumtrading.model.valueobject.ModelResolution;
import com.quorumtrading.model.valueobject.ResolutionTier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Post-resolution negotiation: given a model the chain chose and the engine that will run it,
 * decide whether it is served, substituted, or refused.
 *
 * <p>This is deliberately <em>not</em> a default. A default answers "use this when nothing better
 * is specified"; this answers "this engine can only serve these ids", which applies after
 * resolution regardless of which tier won. Conflating them is what produced an engine-blind default
 * set in the first place.</p>
 *
 * <p>The policy turns on who asked (see {@link ResolutionTier#isExplicitChoice()}):</p>
 * <ul>
 *   <li><b>Strategy pin or user preference (tiers 1–4) — REJECT.</b> Someone explicitly chose this
 *       model. A backtest whose strategy pinned a model it never ran is not a valid result, and an
 *       explicit preference that cannot be served is an error rather than an invitation to guess.</li>
 *   <li><b>System default (tiers 5–6) — SUBSTITUTE silently.</b> Nobody asked for anything
 *       specific, so the engine's own model is the sensible fallback. With engine-scoped defaults
 *       in place this is normally a no-op, because the default already <em>is</em> something the
 *       engine can serve.</li>
 * </ul>
 *
 * <p>Plain POJO wired in {@code ApplicationConfig}, per the CDI convention.</p>
 */
public class EngineCapabilityService {

    private static final Logger logger = LoggerFactory.getLogger(EngineCapabilityService.class);

    private final LLMPort llmPort;
    private final ActiveLlmEnginePort activeLlmEnginePort;

    public EngineCapabilityService(LLMPort llmPort, ActiveLlmEnginePort activeLlmEnginePort) {
        this.llmPort = llmPort;
        this.activeLlmEnginePort = activeLlmEnginePort;
    }

    /**
     * Negotiate {@code requested} against the active engine.
     *
     * @param requested the model the resolution chain produced, may be {@code null}
     * @param tier      which tier produced it — decides reject-vs-substitute
     */
    public ModelResolution negotiate(String requested, ResolutionTier tier) {
        if (requested == null || requested.isBlank()) {
            return ModelResolution.unresolved(activeEngine());
        }

        // Catalogue lookups can touch the network and, for some engines, the database. This runs on
        // the agent hot path, so a failure here must never take down a call the engine could have
        // served: it means "could not check", and an unverified model is not a refused one.
        ModelListingCapability capability;
        boolean servable;
        try {
            capability = llmPort.modelListingCapability();
            servable = capability == ModelListingCapability.ENUMERABLE
                    && llmPort.getModelInfo(requested).isPresent();
        } catch (RuntimeException e) {
            logger.debug("Could not check model '{}' against the active engine ({}); proceeding unverified",
                    requested, e.getMessage());
            return ModelResolution.served(requested, tier, activeEngine());
        }

        if (capability != ModelListingCapability.ENUMERABLE) {
            // Nothing to negotiate against. An engine with no catalogue by design has to be
            // trusted; an unreachable one will fail at call time with its own error, which is a
            // better message than anything invented here.
            return ModelResolution.served(requested, tier, activeEngine());
        }

        if (servable) {
            return ModelResolution.served(requested, tier, activeEngine());
        }

        LlmProvider engine = activeEngine();
        if (tier.isExplicitChoice()) {
            logger.warn("Model '{}' chosen at tier {} cannot be served by engine {} — refusing rather "
                    + "than substituting, because someone explicitly asked for it",
                    requested, tier, engine);
            return ModelResolution.rejected(requested, tier, engine,
                    "the active engine does not advertise this model");
        }

        String substitute = firstServableModel();
        if (substitute == null) {
            // Nothing to substitute WITH. Refusing here would block a run over the platform's own
            // default — a model nobody chose — which is precisely what tiers 5-6 must never do. An
            // engine that reports an empty catalogue has told us nothing, so the honest move is to
            // send the request and let the engine raise a real error if it genuinely cannot serve
            // it. Rejecting on the strength of an empty list is inventing a fact.
            logger.debug("System default '{}' could not be checked against engine {} (empty catalogue); "
                    + "sending it unverified rather than refusing to run", requested, engine);
            return ModelResolution.served(requested, tier, engine);
        }
        logger.info("System default '{}' is not served by engine {}; substituting '{}'",
                requested, engine, substitute);
        return ModelResolution.substituted(requested, substitute, tier, engine,
                "the active engine does not advertise the system default; using its own first model");
    }

    /** Which engine is active, or {@code null} when that cannot be determined right now. */
    private LlmProvider activeEngine() {
        try {
            return activeLlmEnginePort.activeEngine().orElse(null);
        } catch (RuntimeException e) {
            logger.debug("Could not determine the active LLM engine: {}", e.getMessage());
            return null;
        }
    }

    /** The engine's own first model, used only as the tier 5–6 safety net. */
    private String firstServableModel() {
        try {
            return llmPort.getAvailableModels().stream()
                    .map(com.quorumtrading.model.valueobject.LLMModelInfo::id)
                    .sorted()
                    .findFirst()
                    .orElse(null);
        } catch (RuntimeException e) {
            logger.debug("Could not read the active engine's catalogue for a substitute: {}", e.getMessage());
            return null;
        }
    }
}
