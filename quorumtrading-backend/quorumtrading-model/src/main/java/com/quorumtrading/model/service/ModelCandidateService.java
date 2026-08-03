package com.quorumtrading.model.service;

import com.quorumtrading.model.enums.ModelTier;
import com.quorumtrading.model.valueobject.LLMModelInfo;
import com.quorumtrading.model.valueobject.ModelCandidate;
import com.quorumtrading.model.valueobject.ModelEvaluation;
import com.quorumtrading.model.valueobject.TradingContext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Turns "that model will not work" into "these will".
 *
 * <p>The curation is deliberately borrowed rather than invented: it intersects the active engine's
 * catalogue with the tiers {@link ModelTierRegistry} already assigns, so there is no second opinion
 * to drift from the first. Because step one is an intersection with what the engine actually
 * advertises, the list cannot offer a model the engine would refuse — offering OpenRouter ids to an
 * Ollama user is the headline defect wearing a different hat.</p>
 *
 * <p>Sizing rules:</p>
 * <ol>
 *   <li>≤ {@value #SMALL_CATALOGUE} ids — return all of them. That is the complete answer for a
 *       local server and for a CLI alias set, and a complete answer beats a curated one.</li>
 *   <li>Otherwise the RECOMMENDED/PROVEN intersection, capped, each annotated with its tier.</li>
 *   <li>If that intersection is empty, the cheapest entries labelled untested. An empty list is a
 *       dead end for the caller, so there is always a floor.</li>
 * </ol>
 *
 * <p>A strategy is context-agnostic — the same pin may be backtested today and run live next month —
 * so the tier is taken as the best rating across <em>any</em> trading context rather than narrowed
 * to one, which would over-reject.</p>
 */
public class ModelCandidateService {

    /** At or below this size the whole catalogue is the answer. */
    static final int SMALL_CATALOGUE = 20;

    /** Never return more than this, however large the catalogue. */
    static final int MAX_CANDIDATES = 20;

    private final ModelTierRegistry tierRegistry;

    public ModelCandidateService() {
        this(new ModelTierRegistry());
    }

    public ModelCandidateService(ModelTierRegistry tierRegistry) {
        this.tierRegistry = tierRegistry;
    }

    /**
     * Candidates drawn from {@code catalogue} — which must be the <em>active engine's</em> catalogue.
     *
     * @param catalogue everything the engine advertises; empty yields an empty list, because there
     *                  is genuinely nothing to offer
     */
    public List<ModelCandidate> curate(List<LLMModelInfo> catalogue) {
        if (catalogue == null || catalogue.isEmpty()) {
            return List.of();
        }

        if (catalogue.size() <= SMALL_CATALOGUE) {
            return catalogue.stream()
                    .sorted(Comparator.comparing(LLMModelInfo::id))
                    .map(this::annotate)
                    .toList();
        }

        List<ModelCandidate> curated = new ArrayList<>();
        for (LLMModelInfo model : catalogue) {
            BestRating rating = bestRating(model);
            if (rating != null && (rating.tier == ModelTier.RECOMMENDED || rating.tier == ModelTier.PROVEN)) {
                curated.add(ModelCandidate.rated(model.id(), rating.tier, rating.context, rating.reason));
            }
        }

        if (!curated.isEmpty()) {
            curated.sort(Comparator.comparing((ModelCandidate c) -> c.tier().ordinal())
                    .thenComparing(ModelCandidate::id));
            return curated.size() > MAX_CANDIDATES ? List.copyOf(curated.subList(0, MAX_CANDIDATES))
                    : List.copyOf(curated);
        }

        // Floor: nothing the platform has an opinion about is servable here. Offer the cheapest
        // entries and say plainly that they are untested, rather than returning a dead end.
        return catalogue.stream()
                .sorted(Comparator.comparing((LLMModelInfo m) -> m.pricing().promptCostPerMillion())
                        .thenComparing(LLMModelInfo::id))
                .limit(MAX_CANDIDATES)
                .map(m -> ModelCandidate.untested(m.id()))
                .toList();
    }

    /** Annotate with a rating when one exists; a small catalogue is returned whole either way. */
    private ModelCandidate annotate(LLMModelInfo model) {
        BestRating rating = bestRating(model);
        if (rating == null || rating.tier == ModelTier.EXPERIMENTAL || rating.tier == ModelTier.AVOID) {
            return ModelCandidate.untested(model.id());
        }
        return ModelCandidate.rated(model.id(), rating.tier, rating.context, rating.reason);
    }

    /** The best tier this model earns in any trading context, with the context that earned it. */
    private BestRating bestRating(LLMModelInfo model) {
        BestRating best = null;
        for (TradingContext context : TradingContext.values()) {
            ModelEvaluation evaluation = tierRegistry.evaluate(model, context);
            if (evaluation == null || evaluation.tier() == null) {
                continue;
            }
            if (best == null || evaluation.tier().ordinal() < best.tier.ordinal()) {
                best = new BestRating(evaluation.tier(), context, evaluation.reason());
            }
        }
        return best;
    }

    private record BestRating(ModelTier tier, TradingContext context, String reason) {}
}
