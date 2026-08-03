package com.quorumtrading.model.service;

import com.quorumtrading.model.enums.ModelTier;
import com.quorumtrading.model.valueobject.LLMModelInfo;
import com.quorumtrading.model.valueobject.LLMPricing;
import com.quorumtrading.model.valueobject.ModelCandidate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The curated subset offered alongside a rejected pin.
 *
 * <p>The one rule that must never bend: candidates come from the <em>active engine's</em> catalogue.
 * Offering an OpenRouter id to someone running Ollama is the original defect in a different hat, so
 * every case here starts from a catalogue and can only ever return a subset of it.</p>
 */
@DisplayName("ModelCandidateService — what to offer when a pin will not work")
class ModelCandidateServiceTest {

    private final ModelCandidateService service = new ModelCandidateService();

    private static LLMModelInfo free(String id) {
        return LLMModelInfo.of(id, id, LLMPricing.FREE);
    }

    private static LLMModelInfo priced(String id, String costPerMillion) {
        return new LLMModelInfo(id, id, null, 32768, 4096,
                LLMPricing.of(new BigDecimal(costPerMillion), new BigDecimal(costPerMillion)),
                List.of("text"), id.contains("/") ? id.substring(0, id.indexOf('/')) : null,
                null, false, null);
    }

    @Test
    @DisplayName("an empty catalogue offers nothing — there is genuinely nothing to suggest")
    void emptyCatalogueOffersNothing() {
        assertThat(service.curate(List.of())).isEmpty();
        assertThat(service.curate(null)).isEmpty();
    }

    @Test
    @DisplayName("a small catalogue is returned whole — a complete answer beats a curated one")
    void smallCatalogueIsReturnedWhole() {
        List<LLMModelInfo> catalogue = List.of(free("qwen3:8b"), free("llama3.2"), free("gemma3:12b"));

        assertThat(service.curate(catalogue))
                .extracting(ModelCandidate::id)
                .containsExactly("gemma3:12b", "llama3.2", "qwen3:8b");
    }

    @Test
    @DisplayName("candidates never include an id the engine does not advertise")
    void candidatesAreAlwaysASubsetOfTheCatalogue() {
        List<LLMModelInfo> catalogue = List.of(free("llama3.2"), free("qwen3:8b"));

        assertThat(service.curate(catalogue))
                .extracting(ModelCandidate::id)
                .isSubsetOf("llama3.2", "qwen3:8b");
    }

    @Test
    @DisplayName("a large catalogue is narrowed to models the platform actually rates, capped at 20")
    void largeCatalogueIsNarrowedToRatedModels() {
        List<LLMModelInfo> catalogue = new ArrayList<>();
        catalogue.add(priced("mistralai/mistral-small-3.2-24b-instruct", "0.10"));
        for (int i = 0; i < 40; i++) {
            catalogue.add(priced("nobody/model-" + i, "1.00"));
        }

        List<ModelCandidate> candidates = service.curate(catalogue);

        assertThat(candidates).hasSizeLessThanOrEqualTo(20);
        assertThat(candidates)
                .extracting(ModelCandidate::id)
                .contains("mistralai/mistral-small-3.2-24b-instruct");
        assertThat(candidates)
                .filteredOn(c -> c.id().equals("mistralai/mistral-small-3.2-24b-instruct"))
                .allSatisfy(c -> assertThat(c.tier()).isIn(ModelTier.RECOMMENDED, ModelTier.PROVEN));
    }

    @Test
    @DisplayName("with nothing rated, the cheapest are offered and labelled untested rather than a dead end")
    void unratedLargeCatalogueFallsBackToCheapestUntested() {
        List<LLMModelInfo> catalogue = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            catalogue.add(priced("nobody/model-" + i, String.valueOf(30 - i)));
        }

        List<ModelCandidate> candidates = service.curate(catalogue);

        assertThat(candidates).hasSize(20);
        assertThat(candidates).allSatisfy(c -> assertThat(c.tier()).isNull());
        // Cheapest first: model-29 costs 1, model-0 costs 30.
        assertThat(candidates.get(0).id()).isEqualTo("nobody/model-29");
    }

    @Test
    @DisplayName("a rating is taken from the best trading context, not narrowed to one")
    void ratingUsesTheBestContextAcrossAll() {
        List<LLMModelInfo> catalogue = List.of(priced("mistralai/mistral-small-3.2-24b-instruct", "0.10"));

        ModelCandidate candidate = service.curate(catalogue).get(0);

        assertThat(candidate.tier()).isNotNull();
        assertThat(candidate.bestContext()).isNotNull();
    }

    @Test
    @DisplayName("describe() is the one rendering both adapters share, so they cannot drift")
    void describeRendersTierAndContext() {
        assertThat(ModelCandidate.untested("llama3.2").describe()).isEqualTo("llama3.2 (untested)");
        assertThat(ModelCandidate.rated("x/y", ModelTier.RECOMMENDED,
                com.quorumtrading.model.valueobject.TradingContext.BACKTESTING, "why").describe())
                .isEqualTo("x/y (RECOMMENDED for BACKTESTING)");
    }
}
