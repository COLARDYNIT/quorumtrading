package com.quorumtrading.model.service;

import com.quorumtrading.model.enums.ModelTier;
import com.quorumtrading.model.valueobject.LLMModelInfo;
import com.quorumtrading.model.valueobject.LLMPricing;
import com.quorumtrading.model.valueobject.ModelEvaluation;
import com.quorumtrading.model.valueobject.TradingContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ModelFilter Domain Service Tests")
class ModelFilterTest {

    private ModelFilter modelFilter;

    @BeforeEach
    void setUp() {
        modelFilter = new ModelFilter();
    }

    private LLMModelInfo createModel(String id, String provider, int contextLength, Integer parameterSizeB, boolean supportsWebSearch) {
        return new LLMModelInfo(
            id, id, null, contextLength, 0,
            LLMPricing.of(new BigDecimal("1.0"), new BigDecimal("2.0")),
            List.of("text"), provider, null, supportsWebSearch, parameterSizeB
        );
    }

    private LLMModelInfo createModelWithPricing(String id, BigDecimal promptCost, BigDecimal completionCost) {
        return new LLMModelInfo(
            id, id, null, 32768, 0,
            LLMPricing.of(promptCost, completionCost),
            List.of("text"), "openai", null, false, 70
        );
    }

    @Nested
    @DisplayName("LIVE_TRADING Context Filtering")
    class LiveTradingFiltering {

        @Test
        @DisplayName("should filter models by minimum context length (32K)")
        void shouldFilterByMinContextLength() {
            List<LLMModelInfo> models = List.of(
                createModel("openai/gpt-4o", "openai", 128000, 70, false), // passes
                createModel("openai/gpt-mini", "openai", 16000, 70, false) // fails - too small
            );

            List<LLMModelInfo> filtered = modelFilter.filterForContext(models, TradingContext.LIVE_TRADING);

            assertEquals(1, filtered.size());
            assertEquals("openai/gpt-4o", filtered.get(0).id());
        }

        @Test
        @DisplayName("should filter models by trusted providers only")
        void shouldFilterByTrustedProviders() {
            List<LLMModelInfo> models = List.of(
                createModel("openai/gpt-4o", "openai", 128000, 70, false),
                createModel("anthropic/claude", "anthropic", 200000, 70, false),
                createModel("google/gemini", "google", 100000, 70, false),
                createModel("meta-llama/llama", "meta-llama", 128000, 70, false),
                createModel("mistralai/mistral", "mistralai", 128000, 70, false),
                // deepseek is trusted. This filter kept a shorter list of its own than
                // ModelTierRegistry did, and the two disagreed on exactly this provider — so the
                // platform's OWN shipped orchestrator default failed its own LIVE_TRADING gate.
                createModel("deepseek/deepseek", "deepseek", 128000, 70, false),
                createModel("nobody/model", "nobody", 128000, 70, false)          // genuinely untrusted
            );

            List<LLMModelInfo> filtered = modelFilter.filterForContext(models, TradingContext.LIVE_TRADING);

            assertEquals(6, filtered.size());
            assertTrue(filtered.stream().anyMatch(m -> m.provider().equals("deepseek")));
            assertTrue(filtered.stream().noneMatch(m -> m.provider().equals("nobody")));
        }

        @Test
        @DisplayName("a local model is judged on its measured capabilities, not excluded for having no vendor")
        void localModelsAreJudgedOnCapabilities() {
            // No provider namespace = running on the user's own hardware. Excluding these on the
            // provider check meant the context and parameter gates never got to say anything about
            // them, whatever their real capabilities.
            LLMModelInfo bigLocal = createModel("llama3.3", null, 131072, 70, false);
            LLMModelInfo smallContextLocal = createModel("tiny", null, 4096, 70, false);
            LLMModelInfo smallParamsLocal = createModel("tiny-params", null, 131072, 3, false);

            assertTrue(modelFilter.isValidForContext(bigLocal, TradingContext.LIVE_TRADING));
            assertFalse(modelFilter.isValidForContext(smallContextLocal, TradingContext.LIVE_TRADING));
            assertFalse(modelFilter.isValidForContext(smallParamsLocal, TradingContext.LIVE_TRADING));
        }

        @Test
        @DisplayName("an unmeasured context window is unknown, not small — the same rule as a null param count")
        void unknownContextLengthIsNotAFailure() {
            // A server that does not implement /api/show reports 0 for every model. Refusing them
            // all on the strength of a number nobody measured would be inventing a fact.
            LLMModelInfo unmeasured = createModel("lmstudio-model", null, 0, 70, false);

            assertTrue(modelFilter.isValidForContext(unmeasured, TradingContext.LIVE_TRADING));
        }

        @Test
        @DisplayName("the two trusted-provider lists are one list, so they cannot disagree")
        void trustedProviderListIsShared() {
            for (String provider : ModelTierRegistry.TRUSTED_PROVIDERS) {
                assertTrue(
                    modelFilter.isValidForContext(
                        createModel(provider + "/model", provider, 128000, 70, false),
                        TradingContext.LIVE_TRADING),
                    "provider trusted by ModelTierRegistry must pass ModelFilter: " + provider);
            }
        }

        @Test
        @DisplayName("should filter models by minimum parameter size (8B)")
        void shouldFilterByMinParameterSize() {
            List<LLMModelInfo> models = List.of(
                createModel("openai/gpt-4o-70b", "openai", 128000, 70, false),    // passes
                createModel("openai/gpt-4o-8b", "openai", 128000, 8, false),      // passes - exactly 8B
                createModel("openai/gpt-mini-7b", "openai", 128000, 7, false)     // fails - too small
            );

            List<LLMModelInfo> filtered = modelFilter.filterForContext(models, TradingContext.LIVE_TRADING);

            assertEquals(2, filtered.size());
        }

        @Test
        @DisplayName("should allow models with unknown parameter size (null)")
        void shouldAllowModelsWithUnknownParameterSize() {
            List<LLMModelInfo> models = List.of(
                createModel("openai/gpt-4o", "openai", 128000, null, false), // passes - unknown size allowed
                createModel("openai/gpt-mini", "openai", 128000, 7, false)   // fails - known but too small
            );

            List<LLMModelInfo> filtered = modelFilter.filterForContext(models, TradingContext.LIVE_TRADING);

            assertEquals(1, filtered.size());
            assertEquals("openai/gpt-4o", filtered.get(0).id());
        }

        @Test
        @DisplayName("should include free models from trusted providers")
        void shouldIncludeFreeModelsFromTrustedProviders() {
            LLMModelInfo freeModel = new LLMModelInfo(
                "meta-llama/llama-3.3-70b-instruct:free", "Llama 3.3 70B Free", null,
                131072, 0, LLMPricing.FREE, List.of("text"), "meta-llama", null, false, 70
            );

            List<LLMModelInfo> filtered = modelFilter.filterForContext(List.of(freeModel), TradingContext.LIVE_TRADING);

            assertEquals(1, filtered.size());
            assertTrue(filtered.get(0).isFree());
        }

        @Test
        @DisplayName("a model with no provider namespace is local, and is judged on its capabilities")
        void shouldJudgeNullProviderModelsOnCapabilities() {
            // This used to assert rejection. A model id with no `vendor/` namespace is what a local
            // server advertises — llama3.2, qwen3:8b — so blanket rejection meant no local model
            // could ever be used for LIVE_TRADING regardless of how capable it actually was, and
            // the context and parameter gates never got to run. Same reasoning as ModelTierRegistry
            // no longer labelling a locally-hosted model AVOID for costing nothing.
            LLMModelInfo capableLocal = createModel("llama3.3", null, 128000, 70, false);
            LLMModelInfo weakLocal = createModel("tiny", null, 2048, 70, false);

            assertEquals(List.of(capableLocal),
                modelFilter.filterForContext(List.of(capableLocal), TradingContext.LIVE_TRADING));
            assertTrue(modelFilter.filterForContext(List.of(weakLocal), TradingContext.LIVE_TRADING).isEmpty());
        }
    }

    @Nested
    @DisplayName("BACKTESTING Context Filtering")
    class BacktestingFiltering {

        @Test
        @DisplayName("should return all models sorted by cost (cheapest first)")
        void shouldSortByCostCheapestFirst() {
            List<LLMModelInfo> models = List.of(
                createModelWithPricing("expensive", new BigDecimal("10.0"), new BigDecimal("20.0")),
                createModelWithPricing("cheap", new BigDecimal("0.5"), new BigDecimal("1.0")),
                createModelWithPricing("free", BigDecimal.ZERO, BigDecimal.ZERO),
                createModelWithPricing("medium", new BigDecimal("3.0"), new BigDecimal("6.0"))
            );

            List<LLMModelInfo> filtered = modelFilter.filterForContext(models, TradingContext.BACKTESTING);

            assertEquals(4, filtered.size());
            assertEquals("free", filtered.get(0).id());
            assertEquals("cheap", filtered.get(1).id());
            assertEquals("medium", filtered.get(2).id());
            assertEquals("expensive", filtered.get(3).id());
        }

        @Test
        @DisplayName("should include all models regardless of provider or size")
        void shouldIncludeAllModels() {
            List<LLMModelInfo> models = List.of(
                createModel("deepseek/deepseek", "deepseek", 16000, 7, false), // would fail LIVE_TRADING
                createModel("openai/gpt-4o", "openai", 128000, 70, false)
            );

            List<LLMModelInfo> filtered = modelFilter.filterForContext(models, TradingContext.BACKTESTING);

            assertEquals(2, filtered.size());
        }
    }

    @Nested
    @DisplayName("RESEARCH Context Filtering")
    class ResearchFiltering {

        @Test
        @DisplayName("should include all models (web search enabled via :online suffix)")
        void shouldIncludeAllModels() {
            List<LLMModelInfo> models = List.of(
                createModel("perplexity/sonar-online", "perplexity", 128000, null, true),
                createModel("openai/gpt-4o", "openai", 128000, 70, false),
                createModel("qwen/qwen3-next-80b", "qwen", 128000, 80, false)
            );

            List<LLMModelInfo> filtered = modelFilter.filterForContext(models, TradingContext.RESEARCH);

            assertEquals(3, filtered.size());
        }
    }

    @Nested
    @DisplayName("PAPER_TRADING Context Filtering")
    class PaperTradingFiltering {

        @Test
        @DisplayName("should return all models without filtering")
        void shouldReturnAllModels() {
            List<LLMModelInfo> models = List.of(
                createModel("any/model1", "any", 1000, 1, false),
                createModel("any/model2", null, 500, null, true)
            );

            List<LLMModelInfo> filtered = modelFilter.filterForContext(models, TradingContext.PAPER_TRADING);

            assertEquals(2, filtered.size());
        }
    }

    @Nested
    @DisplayName("isValidForContext Method")
    class IsValidForContextTests {

        @Test
        @DisplayName("should validate LIVE_TRADING model correctly")
        void shouldValidateLiveTradingModel() {
            LLMModelInfo valid = createModel("openai/gpt-4o", "openai", 128000, 70, false);
            LLMModelInfo smallContext = createModel("openai/gpt-mini", "openai", 16000, 70, false);
            LLMModelInfo smallParams = createModel("openai/gpt-7b", "openai", 128000, 7, false);
            LLMModelInfo untrustedProvider = createModel("nobody/model", "nobody", 128000, 70, false);
            LLMModelInfo deepseek = createModel("deepseek/model", "deepseek", 128000, 70, false);

            assertTrue(modelFilter.isValidForContext(valid, TradingContext.LIVE_TRADING));
            assertFalse(modelFilter.isValidForContext(smallContext, TradingContext.LIVE_TRADING));
            assertFalse(modelFilter.isValidForContext(smallParams, TradingContext.LIVE_TRADING));
            assertFalse(modelFilter.isValidForContext(untrustedProvider, TradingContext.LIVE_TRADING));
            // deepseek is the platform's own shipped orchestrator default; rejecting it here was
            // the visible symptom of two trusted-provider lists that disagreed.
            assertTrue(modelFilter.isValidForContext(deepseek, TradingContext.LIVE_TRADING));
        }

        @Test
        @DisplayName("should return false for null model")
        void shouldReturnFalseForNullModel() {
            assertFalse(modelFilter.isValidForContext(null, TradingContext.LIVE_TRADING));
            assertFalse(modelFilter.isValidForContext(null, TradingContext.RESEARCH));
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("should return empty list for null input")
        void shouldReturnEmptyListForNullInput() {
            List<LLMModelInfo> result = modelFilter.filterForContext(null, TradingContext.LIVE_TRADING);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("should return empty list for empty input")
        void shouldReturnEmptyListForEmptyInput() {
            List<LLMModelInfo> result = modelFilter.filterForContext(List.of(), TradingContext.LIVE_TRADING);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("should handle case-insensitive provider matching")
        void shouldHandleCaseInsensitiveProviderMatching() {
            List<LLMModelInfo> models = List.of(
                createModel("OpenAI/gpt-4o", "OpenAI", 128000, 70, false),     // uppercase
                createModel("ANTHROPIC/claude", "ANTHROPIC", 200000, 70, false) // all caps
            );

            List<LLMModelInfo> filtered = modelFilter.filterForContext(models, TradingContext.LIVE_TRADING);

            assertEquals(2, filtered.size());
        }
    }

    @Nested
    @DisplayName("Context Requirements Description")
    class ContextRequirementsTests {

        @Test
        @DisplayName("should return appropriate description for each context")
        void shouldReturnAppropriateDescriptions() {
            String liveReqs = modelFilter.getContextRequirements(TradingContext.LIVE_TRADING);
            String backtestReqs = modelFilter.getContextRequirements(TradingContext.BACKTESTING);
            String researchReqs = modelFilter.getContextRequirements(TradingContext.RESEARCH);
            String paperReqs = modelFilter.getContextRequirements(TradingContext.PAPER_TRADING);

            assertTrue(liveReqs.contains("trusted providers"));
            assertTrue(liveReqs.contains("32K+"));
            assertTrue(liveReqs.contains("8B+"));
            assertTrue(backtestReqs.contains("cost"));
            assertTrue(researchReqs.contains("All models supported"));
            assertNotNull(paperReqs);
        }
    }

    @Nested
    @DisplayName("evaluateModel - Tier Evaluation")
    class EvaluateModelTests {

        @Test
        @DisplayName("should return RECOMMENDED for known-good models in their best context")
        void shouldReturnRecommendedForKnownGood() {
            LLMModelInfo model = createModel("mistralai/mistral-small-3.2-24b-instruct", "mistralai", 128000, null, false);

            ModelEvaluation eval = modelFilter.evaluateModel(model, TradingContext.BACKTESTING);

            assertEquals(ModelTier.RECOMMENDED, eval.tier());
            assertNotNull(eval.reason());
        }

        @Test
        @DisplayName("should return AVOID for known-bad patterns")
        void shouldReturnAvoidForKnownBad() {
            LLMModelInfo model = createModel("deepseek/deepseek-r1", "deepseek", 128000, null, false);

            ModelEvaluation eval = modelFilter.evaluateModel(model, TradingContext.BACKTESTING);

            assertEquals(ModelTier.AVOID, eval.tier());
        }

        @Test
        @DisplayName("should return EXPERIMENTAL for unknown models")
        void shouldReturnExperimentalForUnknown() {
            LLMModelInfo model = createModel("acme/model", "acme", 128000, null, false);

            ModelEvaluation eval = modelFilter.evaluateModel(model, TradingContext.RESEARCH);

            assertEquals(ModelTier.EXPERIMENTAL, eval.tier());
        }
    }
}
