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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ModelTierRegistry Domain Service Tests")
class ModelTierRegistryTest {

    private ModelTierRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ModelTierRegistry();
    }

    /**
     * Create a model with a given prompt cost PER MILLION tokens (user-facing cost).
     * LLMPricing stores cost per token, so we divide by 1M.
     */
    private LLMModelInfo model(String id, String provider, BigDecimal promptCostPerMillion) {
        BigDecimal perToken = promptCostPerMillion.divide(BigDecimal.valueOf(1_000_000), 10, java.math.RoundingMode.HALF_UP);
        return new LLMModelInfo(
            id, id, null, 128000, 0,
            LLMPricing.of(perToken, perToken.multiply(BigDecimal.TWO)),
            List.of("text"), provider, null, false, null
        );
    }

    private LLMModelInfo freeModel(String id, String provider) {
        return new LLMModelInfo(
            id, id, null, 128000, 0,
            LLMPricing.FREE,
            List.of("text"), provider, null, false, null
        );
    }

    @Nested
    @DisplayName("Known-Good Exact Matches")
    class KnownGoodTests {

        @Test
        @DisplayName("Mistral Small 24B should be RECOMMENDED for BACKTESTING")
        void mistralSmallRecommendedForBacktesting() {
            LLMModelInfo model = model("mistralai/mistral-small-3.2-24b-instruct", "mistralai", new BigDecimal("0.1"));

            ModelEvaluation eval = registry.evaluate(model, TradingContext.BACKTESTING);

            assertEquals(ModelTier.RECOMMENDED, eval.tier());
            assertNotNull(eval.reason());
        }

        @Test
        @DisplayName("Mistral Small 24B should be RECOMMENDED for RESEARCH")
        void mistralSmallRecommendedForResearch() {
            LLMModelInfo model = model("mistralai/mistral-small-3.2-24b-instruct", "mistralai", new BigDecimal("0.1"));

            ModelEvaluation eval = registry.evaluate(model, TradingContext.RESEARCH);

            assertEquals(ModelTier.RECOMMENDED, eval.tier());
        }

        @Test
        @DisplayName("Mistral Small 24B should be PROVEN for LIVE_TRADING")
        void mistralSmallProvenForLiveTrading() {
            LLMModelInfo model = model("mistralai/mistral-small-3.2-24b-instruct", "mistralai", new BigDecimal("0.1"));

            ModelEvaluation eval = registry.evaluate(model, TradingContext.LIVE_TRADING);

            assertEquals(ModelTier.PROVEN, eval.tier());
        }

        @Test
        @DisplayName("Gemini 2.0 Flash should be PROVEN for PAPER_TRADING (deprecated)")
        void geminiFlashProvenForPaperTrading() {
            LLMModelInfo model = model("google/gemini-2.0-flash-001", "google", new BigDecimal("0.1"));

            ModelEvaluation eval = registry.evaluate(model, TradingContext.PAPER_TRADING);

            assertEquals(ModelTier.PROVEN, eval.tier());
            assertTrue(eval.reason().contains("DEPRECATED"));
        }

        @Test
        @DisplayName("Llama 4 Scout should be RECOMMENDED for PAPER_TRADING")
        void llamaScoutRecommendedForPaperTrading() {
            LLMModelInfo model = model("meta-llama/llama-4-scout", "meta-llama", new BigDecimal("0.08"));

            ModelEvaluation eval = registry.evaluate(model, TradingContext.PAPER_TRADING);

            assertEquals(ModelTier.RECOMMENDED, eval.tier());
        }

        @Test
        @DisplayName("DeepSeek V3.2 should be PROVEN for BACKTESTING")
        void deepseekV32ProvenForBacktesting() {
            LLMModelInfo model = model("deepseek/deepseek-v3.2", "deepseek", new BigDecimal("0.25"));

            ModelEvaluation eval = registry.evaluate(model, TradingContext.BACKTESTING);

            assertEquals(ModelTier.PROVEN, eval.tier());
            assertTrue(eval.reason().contains("GPT-5"));
        }

        @Test
        @DisplayName("Llama 4 Scout should be PROVEN for BACKTESTING")
        void llamaScoutProvenForBacktesting() {
            LLMModelInfo model = model("meta-llama/llama-4-scout", "meta-llama", new BigDecimal("0.08"));

            ModelEvaluation eval = registry.evaluate(model, TradingContext.BACKTESTING);

            assertEquals(ModelTier.PROVEN, eval.tier());
        }

        @Test
        @DisplayName("Gemini 2.0 Flash should be PROVEN for BACKTESTING")
        void geminiFlashProvenForBacktesting() {
            LLMModelInfo model = model("google/gemini-2.0-flash-001", "google", new BigDecimal("0.1"));

            ModelEvaluation eval = registry.evaluate(model, TradingContext.BACKTESTING);

            assertEquals(ModelTier.PROVEN, eval.tier());
        }

        @Test
        @DisplayName("Claude Opus 4.6 should be RECOMMENDED for LIVE_TRADING")
        void claudeOpusRecommendedForLiveTrading() {
            LLMModelInfo model = model("anthropic/claude-opus-4.6", "anthropic", new BigDecimal("15.0"));

            ModelEvaluation eval = registry.evaluate(model, TradingContext.LIVE_TRADING);

            assertEquals(ModelTier.RECOMMENDED, eval.tier());
        }

        @Test
        @DisplayName("Claude Opus 4.6 should be PROVEN for BACKTESTING and CHAT")
        void claudeOpusProvenForBacktestingAndChat() {
            LLMModelInfo model = model("anthropic/claude-opus-4.6", "anthropic", new BigDecimal("15.0"));

            assertEquals(ModelTier.PROVEN, registry.evaluate(model, TradingContext.BACKTESTING).tier());
            assertEquals(ModelTier.PROVEN, registry.evaluate(model, TradingContext.CHAT).tier());
        }

        @Test
        @DisplayName("Claude Sonnet 4.5 should be RECOMMENDED for LIVE_TRADING")
        void claudeSonnetRecommendedForLiveTrading() {
            LLMModelInfo model = model("anthropic/claude-sonnet-4.6", "anthropic", new BigDecimal("3.0"));

            ModelEvaluation eval = registry.evaluate(model, TradingContext.LIVE_TRADING);

            assertEquals(ModelTier.RECOMMENDED, eval.tier());
        }

        @Test
        @DisplayName("Claude Sonnet 4.5 should be PROVEN for BACKTESTING")
        void claudeSonnetProvenForBacktesting() {
            LLMModelInfo model = model("anthropic/claude-sonnet-4.6", "anthropic", new BigDecimal("3.0"));

            ModelEvaluation eval = registry.evaluate(model, TradingContext.BACKTESTING);

            assertEquals(ModelTier.PROVEN, eval.tier());
        }

        @Test
        @DisplayName("Gemini 2.5 Flash should be PROVEN for all contexts")
        void gemini25FlashProvenForAll() {
            LLMModelInfo model = model("google/gemini-2.5-flash", "google", new BigDecimal("0.15"));

            for (TradingContext ctx : new TradingContext[]{
                TradingContext.BACKTESTING, TradingContext.PAPER_TRADING,
                TradingContext.LIVE_TRADING, TradingContext.RESEARCH}) {
                ModelEvaluation eval = registry.evaluate(model, ctx);
                assertEquals(ModelTier.PROVEN, eval.tier(),
                    "Expected PROVEN for context " + ctx);
            }
        }

        @Test
        @DisplayName("Qwen 2.5 72B should be PROVEN for BACKTESTING and PAPER_TRADING")
        void qwen25ProvenForBacktestAndPaper() {
            LLMModelInfo model = model("qwen/qwen-2.5-72b-instruct", "qwen", new BigDecimal("0.3"));

            assertEquals(ModelTier.PROVEN, registry.evaluate(model, TradingContext.BACKTESTING).tier());
            assertEquals(ModelTier.PROVEN, registry.evaluate(model, TradingContext.PAPER_TRADING).tier());
        }

        @Test
        @DisplayName("Cohere Command R7B should be PROVEN for BACKTESTING")
        void cohereCommandProvenForBacktesting() {
            LLMModelInfo model = model("cohere/command-r7b-12-2024", "cohere", new BigDecimal("0.04"));

            assertEquals(ModelTier.PROVEN, registry.evaluate(model, TradingContext.BACKTESTING).tier());
        }
    }

    @Nested
    @DisplayName("Known-Bad Pattern Matches")
    class KnownBadTests {

        @Test
        @DisplayName("Free tier models should be AVOID")
        void freeModelsAvoid() {
            LLMModelInfo model = model("meta-llama/llama-3.3-70b-instruct:free", "meta-llama", BigDecimal.ZERO);

            ModelEvaluation eval = registry.evaluate(model, TradingContext.BACKTESTING);

            assertEquals(ModelTier.AVOID, eval.tier());
            assertTrue(eval.reason().toLowerCase().contains("free"));
        }

        @Test
        @DisplayName("Claude 3 Haiku should be AVOID")
        void claude3HaikuAvoid() {
            LLMModelInfo model = model("anthropic/claude-3-haiku-20240307", "anthropic", new BigDecimal("0.25"));

            ModelEvaluation eval = registry.evaluate(model, TradingContext.BACKTESTING);

            assertEquals(ModelTier.AVOID, eval.tier());
        }

        @Test
        @DisplayName("DeepSeek R1 should be AVOID (expensive reasoning tokens)")
        void deepseekR1Avoid() {
            LLMModelInfo model = model("deepseek/deepseek-r1", "deepseek", new BigDecimal("0.5"));

            ModelEvaluation eval = registry.evaluate(model, TradingContext.PAPER_TRADING);

            assertEquals(ModelTier.AVOID, eval.tier());
        }

        @Test
        @DisplayName("DeepSeek Chat (old) should be AVOID (analysis paralysis)")
        void deepseekChatAvoid() {
            LLMModelInfo model = model("deepseek/deepseek-chat", "deepseek", new BigDecimal("0.3"));

            ModelEvaluation eval = registry.evaluate(model, TradingContext.BACKTESTING);

            assertEquals(ModelTier.AVOID, eval.tier());
        }

        @Test
        @DisplayName("Mistral Nemo should be AVOID")
        void mistralNemoAvoid() {
            LLMModelInfo model = model("mistralai/mistral-nemo", "mistralai", new BigDecimal("0.1"));

            ModelEvaluation eval = registry.evaluate(model, TradingContext.LIVE_TRADING);

            assertEquals(ModelTier.AVOID, eval.tier());
        }

        @Test
        @DisplayName("Llama 3.3 70B should be AVOID")
        void llama33Avoid() {
            LLMModelInfo model = model("meta-llama/llama-3.3-70b-instruct", "meta-llama", new BigDecimal("0.3"));

            ModelEvaluation eval = registry.evaluate(model, TradingContext.BACKTESTING);

            assertEquals(ModelTier.AVOID, eval.tier());
        }

        @Test
        @DisplayName("Qwen3 32B should be AVOID")
        void qwen3_32bAvoid() {
            LLMModelInfo model = model("qwen/qwen3-32b", "qwen", new BigDecimal("0.2"));

            ModelEvaluation eval = registry.evaluate(model, TradingContext.BACKTESTING);

            assertEquals(ModelTier.AVOID, eval.tier());
        }

        @Test
        @DisplayName("GLM-4 should be AVOID")
        void glm4Avoid() {
            LLMModelInfo model = model("zhipuai/glm-4-flash", "zhipuai", new BigDecimal("0.1"));

            ModelEvaluation eval = registry.evaluate(model, TradingContext.RESEARCH);

            assertEquals(ModelTier.AVOID, eval.tier());
        }

        @Test
        @DisplayName("GPT-5 Nano should be AVOID")
        void gpt5NanoAvoid() {
            LLMModelInfo model = model("openai/gpt-5-nano", "openai", new BigDecimal("0.1"));

            ModelEvaluation eval = registry.evaluate(model, TradingContext.BACKTESTING);

            assertEquals(ModelTier.AVOID, eval.tier());
        }

        @Test
        @DisplayName("Gemini 2.5 Flash Lite should be AVOID")
        void geminiFlashLiteAvoid() {
            LLMModelInfo model = model("google/gemini-2.5-flash-lite-preview", "google", new BigDecimal("0.05"));

            ModelEvaluation eval = registry.evaluate(model, TradingContext.PAPER_TRADING);

            assertEquals(ModelTier.AVOID, eval.tier());
        }

        @Test
        @DisplayName("Qwen Turbo should be AVOID")
        void qwenTurboAvoid() {
            LLMModelInfo model = model("qwen/qwen-turbo", "qwen", new BigDecimal("0.05"));

            ModelEvaluation eval = registry.evaluate(model, TradingContext.BACKTESTING);

            assertEquals(ModelTier.AVOID, eval.tier());
        }

        @Test
        @DisplayName("Grok 4.1 Fast should be AVOID")
        void grokFastAvoid() {
            LLMModelInfo model = model("x-ai/grok-4.1-fast", "x-ai", new BigDecimal("5.0"));

            ModelEvaluation eval = registry.evaluate(model, TradingContext.LIVE_TRADING);

            assertEquals(ModelTier.AVOID, eval.tier());
        }

        @Test
        @DisplayName("Claude 3.5 Haiku should be AVOID")
        void claude35HaikuAvoid() {
            LLMModelInfo model = model("anthropic/claude-3.5-haiku-20241022", "anthropic", new BigDecimal("1.0"));

            ModelEvaluation eval = registry.evaluate(model, TradingContext.BACKTESTING);

            assertEquals(ModelTier.AVOID, eval.tier());
        }
    }

    @Nested
    @DisplayName("Free Model Classification")
    class FreeModelTests {

        @Test
        @DisplayName("Free model not in known-good should be AVOID")
        void freeUnknownModelAvoid() {
            LLMModelInfo model = freeModel("unknown/some-free-model", "unknown");

            ModelEvaluation eval = registry.evaluate(model, TradingContext.BACKTESTING);

            assertEquals(ModelTier.AVOID, eval.tier());
            assertTrue(eval.reason().toLowerCase().contains("free"));
        }
    }

    @Nested
    @DisplayName("Unknown Model Classification")
    class UnknownModelTests {

        @Test
        @DisplayName("Unknown model from trusted provider with low cost should be BUDGET")
        void unknownTrustedProviderBudget() {
            LLMModelInfo model = model("google/some-new-model", "google", new BigDecimal("0.5"));

            ModelEvaluation eval = registry.evaluate(model, TradingContext.BACKTESTING);

            assertEquals(ModelTier.BUDGET, eval.tier());
        }

        @Test
        @DisplayName("Unknown model from trusted provider with high cost should be EXPERIMENTAL")
        void unknownTrustedProviderExpensiveExperimental() {
            LLMModelInfo model = model("anthropic/claude-future", "anthropic", new BigDecimal("20.0"));

            ModelEvaluation eval = registry.evaluate(model, TradingContext.BACKTESTING);

            assertEquals(ModelTier.EXPERIMENTAL, eval.tier());
        }

        @Test
        @DisplayName("Unknown model from untrusted provider should be EXPERIMENTAL")
        void unknownUntrustedProvider() {
            LLMModelInfo model = model("acme/super-model", "acme", new BigDecimal("1.0"));

            ModelEvaluation eval = registry.evaluate(model, TradingContext.LIVE_TRADING);

            assertEquals(ModelTier.EXPERIMENTAL, eval.tier());
        }
    }

    @Nested
    @DisplayName("Priority Rules")
    class PriorityTests {

        @Test
        @DisplayName("Exact match takes priority over pattern match")
        void exactMatchOverPattern() {
            // Mistral Small 24B is known-good, but also contains "mistral" which could match patterns.
            // The exact match should win.
            LLMModelInfo model = model("mistralai/mistral-small-3.2-24b-instruct", "mistralai", new BigDecimal("0.1"));

            ModelEvaluation eval = registry.evaluate(model, TradingContext.BACKTESTING);

            assertEquals(ModelTier.RECOMMENDED, eval.tier());
        }
    }

    @Nested
    @DisplayName("Context Coverage")
    class ContextCoverageTests {

        @ParameterizedTest
        @EnumSource(value = TradingContext.class, names = {"BACKTESTING", "PAPER_TRADING", "LIVE_TRADING", "RESEARCH", "CHAT"})
        @DisplayName("Each trading context should have at least one RECOMMENDED model")
        void eachContextHasRecommended(TradingContext context) {
            // Test the well-known models to find at least one RECOMMENDED per context
            List<LLMModelInfo> knownModels = List.of(
                model("mistralai/mistral-small-3.2-24b-instruct", "mistralai", new BigDecimal("0.1")),
                model("meta-llama/llama-4-scout", "meta-llama", new BigDecimal("0.08")),
                model("anthropic/claude-sonnet-4.6", "anthropic", new BigDecimal("3.0")),
                model("xiaomi/mimo-v2-flash", "xiaomi", new BigDecimal("0.14"))
            );

            boolean hasRecommended = knownModels.stream()
                .anyMatch(m -> registry.evaluate(m, context).tier() == ModelTier.RECOMMENDED);

            assertTrue(hasRecommended, "Context " + context + " should have at least one RECOMMENDED model");
        }
    }

    @Nested
    @DisplayName("CHAT Context")
    class ChatContextTests {

        @Test
        @DisplayName("Mistral Small should be PROVEN for CHAT")
        void mistralSmallProvenForChat() {
            LLMModelInfo model = model("mistralai/mistral-small-3.2-24b-instruct", "mistralai", new BigDecimal("0.10"));

            ModelEvaluation eval = registry.evaluate(model, TradingContext.CHAT);

            assertEquals(ModelTier.PROVEN, eval.tier());
        }

        @Test
        @DisplayName("Qwen Turbo should be PROVEN for CHAT")
        void qwenTurboProvenForChat() {
            LLMModelInfo model = model("qwen/qwen-turbo", "qwen", new BigDecimal("0.05"));

            ModelEvaluation eval = registry.evaluate(model, TradingContext.CHAT);

            assertEquals(ModelTier.PROVEN, eval.tier());
        }

        @Test
        @DisplayName("Qwen Turbo should still be AVOID for trading contexts")
        void qwenTurboStillAvoidForTrading() {
            LLMModelInfo model = model("qwen/qwen-turbo", "qwen", new BigDecimal("0.05"));

            assertEquals(ModelTier.AVOID, registry.evaluate(model, TradingContext.BACKTESTING).tier());
        }

        @Test
        @DisplayName("GPT-5 Nano should be PROVEN for CHAT but AVOID for trading")
        void gpt5NanoProvenForChatAvoidForTrading() {
            LLMModelInfo model = model("openai/gpt-5-nano", "openai", new BigDecimal("0.05"));

            assertEquals(ModelTier.PROVEN, registry.evaluate(model, TradingContext.CHAT).tier());
            assertEquals(ModelTier.AVOID, registry.evaluate(model, TradingContext.BACKTESTING).tier());
        }

        @Test
        @DisplayName("DeepSeek Chat v3.1 should be PROVEN for CHAT but AVOID for trading")
        void deepseekChatV31ProvenForChatAvoidForTrading() {
            LLMModelInfo model = model("deepseek/deepseek-chat-v3.1", "deepseek", new BigDecimal("0.15"));

            assertEquals(ModelTier.PROVEN, registry.evaluate(model, TradingContext.CHAT).tier());
            assertEquals(ModelTier.AVOID, registry.evaluate(model, TradingContext.BACKTESTING).tier());
        }

        @Test
        @DisplayName("DeepSeek v3.2 should be PROVEN for CHAT and BACKTESTING")
        void deepseekV32ProvenForChatAndTrading() {
            LLMModelInfo model = model("deepseek/deepseek-v3.2", "deepseek", new BigDecimal("0.25"));

            assertEquals(ModelTier.PROVEN, registry.evaluate(model, TradingContext.CHAT).tier());
            assertEquals(ModelTier.PROVEN, registry.evaluate(model, TradingContext.BACKTESTING).tier());
        }

        @Test
        @DisplayName("All 11 passing chat models should be PROVEN or RECOMMENDED")
        void allPassingChatModelsRegistered() {
            record ChatModel(String id, String provider, BigDecimal cost) {}
            var models = List.of(
                new ChatModel("qwen/qwen-turbo", "qwen", new BigDecimal("0.05")),
                new ChatModel("openai/gpt-5-nano", "openai", new BigDecimal("0.05")),
                new ChatModel("xiaomi/mimo-v2-flash", "xiaomi", new BigDecimal("0.14")),
                new ChatModel("meta-llama/llama-4-scout", "meta-llama", new BigDecimal("0.08")),
                new ChatModel("meta-llama/llama-4-maverick", "meta-llama", new BigDecimal("0.15")),
                new ChatModel("deepseek/deepseek-chat-v3.1", "deepseek", new BigDecimal("0.15")),
                new ChatModel("x-ai/grok-4-fast", "x-ai", new BigDecimal("0.20")),
                new ChatModel("deepseek/deepseek-v3.2", "deepseek", new BigDecimal("0.25")),
                new ChatModel("openai/gpt-5-mini", "openai", new BigDecimal("0.25")),
                new ChatModel("anthropic/claude-haiku-4.5", "anthropic", new BigDecimal("1.00")),
                new ChatModel("google/gemini-2.5-pro", "google", new BigDecimal("1.25"))
            );

            for (var m : models) {
                LLMModelInfo info = model(m.id, m.provider, m.cost);
                ModelEvaluation eval = registry.evaluate(info, TradingContext.CHAT);
                assertTrue(
                    eval.tier() == ModelTier.RECOMMENDED || eval.tier() == ModelTier.PROVEN,
                    m.id + " should be RECOMMENDED or PROVEN for CHAT but was " + eval.tier()
                );
            }
        }

        @Test
        @DisplayName("CHAT context should classify unknown models like other contexts")
        void chatContextClassifiesUnknown() {
            LLMModelInfo model = model("acme/chat-model", "acme", new BigDecimal("1.0"));

            ModelEvaluation eval = registry.evaluate(model, TradingContext.CHAT);

            assertEquals(ModelTier.EXPERIMENTAL, eval.tier());
        }

        @Test
        @DisplayName("CHAT context should still AVOID DeepSeek R1 (known-bad)")
        void chatContextAvoidsDeepseekR1() {
            LLMModelInfo model = model("deepseek/deepseek-r1", "deepseek", new BigDecimal("0.5"));

            ModelEvaluation eval = registry.evaluate(model, TradingContext.CHAT);

            assertEquals(ModelTier.AVOID, eval.tier());
        }

        @Test
        @DisplayName("Unknown DeepSeek model should be BUDGET (trusted provider)")
        void unknownDeepseekBudget() {
            LLMModelInfo model = model("deepseek/deepseek-v4", "deepseek", new BigDecimal("0.5"));

            ModelEvaluation eval = registry.evaluate(model, TradingContext.CHAT);

            assertEquals(ModelTier.BUDGET, eval.tier());
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Null model should throw IllegalArgumentException")
        void nullModelThrows() {
            assertThrows(IllegalArgumentException.class,
                () -> registry.evaluate(null, TradingContext.BACKTESTING));
        }

        @Test
        @DisplayName("Null context should throw IllegalArgumentException")
        void nullContextThrows() {
            LLMModelInfo model = model("openai/gpt-4o", "openai", new BigDecimal("5.0"));

            assertThrows(IllegalArgumentException.class,
                () -> registry.evaluate(model, null));
        }

        @Test
        @DisplayName("Pattern matching should be case-insensitive")
        void patternMatchingCaseInsensitive() {
            LLMModelInfo model = model("DeepSeek/DeepSeek-R1", "DeepSeek", new BigDecimal("0.5"));

            ModelEvaluation eval = registry.evaluate(model, TradingContext.BACKTESTING);

            assertEquals(ModelTier.AVOID, eval.tier());
        }
    }
}
