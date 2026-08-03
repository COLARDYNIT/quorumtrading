package com.quorumtrading.model.service;

import com.quorumtrading.model.enums.ModelTier;
import com.quorumtrading.model.valueobject.LLMModelInfo;
import com.quorumtrading.model.valueobject.ModelEvaluation;
import com.quorumtrading.model.valueobject.TradingContext;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Curated registry that classifies LLM models into tiers per trading context.
 * <p>
 * Based on 6 rounds of A/B testing across 18+ models (Feb 2026).
 * <p>
 * Classification algorithm:
 * <ol>
 *   <li>Exact match in known-good map → return that tier</li>
 *   <li>Pattern match in known-bad list → return AVOID</li>
 *   <li>If free → AVOID</li>
 *   <li>If trusted provider + affordable → BUDGET</li>
 *   <li>Else → EXPERIMENTAL</li>
 * </ol>
 */
public class ModelTierRegistry {

    private static final BigDecimal BUDGET_COST_THRESHOLD = new BigDecimal("5.0"); // per million tokens

    /**
     * Providers whose models are trusted without individual testing.
     *
     * <p>Public and shared: {@code ModelFilter} kept a second, shorter list of its own, and the two
     * disagreed — {@code deepseek} was trusted here and not there, so the platform's OWN shipped
     * orchestrator default failed ModelFilter's LIVE_TRADING gate. One list cannot disagree with
     * itself.</p>
     */
    public static final Set<String> TRUSTED_PROVIDERS = Set.of(
        "openai", "anthropic", "google", "meta-llama", "mistralai", "qwen", "cohere", "xiaomi", "deepseek"
    );

    /**
     * Exact-match map: modelId → (context → evaluation).
     */
    private final Map<String, Map<TradingContext, ModelEvaluation>> knownGood;

    /**
     * Pattern-match list: (substring pattern, reason) for known-bad models.
     */
    private final List<PatternEntry> knownBad;

    public ModelTierRegistry() {
        this.knownGood = buildKnownGoodMap();
        this.knownBad = buildKnownBadList();
    }

    /**
     * Evaluate a model's tier for a specific trading context.
     *
     * @param model   The LLM model to evaluate
     * @param context The trading context
     * @return The tier evaluation with reason
     * @throws IllegalArgumentException if model or context is null
     */
    public ModelEvaluation evaluate(LLMModelInfo model, TradingContext context) {
        if (model == null) {
            throw new IllegalArgumentException("Model cannot be null");
        }
        if (context == null) {
            throw new IllegalArgumentException("Context cannot be null");
        }

        // 1. Exact match in known-good map
        Map<TradingContext, ModelEvaluation> contextMap = knownGood.get(model.id());
        if (contextMap != null && contextMap.containsKey(context)) {
            return contextMap.get(context);
        }

        // 2. Pattern match in known-bad list
        String lowerModelId = model.id().toLowerCase();
        for (PatternEntry entry : knownBad) {
            if (lowerModelId.contains(entry.pattern)) {
                return new ModelEvaluation(ModelTier.AVOID, entry.reason);
            }
        }

        // 3. Free models not in known-good → AVOID.
        //
        // "Free" here means a hosted free tier, which really does fail to parse 95-100% of the
        // time. A model running on the user's OWN hardware costs nothing for an entirely different
        // reason, and labelling it AVOID told a self-hoster their only usable model was unusable.
        if (model.isFree() && !isLocallyHosted(model)) {
            return new ModelEvaluation(ModelTier.AVOID,
                "Free models have 95-100% parse failure rate in trading consensus");
        }
        if (model.isFree()) {
            return new ModelEvaluation(ModelTier.EXPERIMENTAL,
                "Runs on your own hardware — no marginal cost, and untested by this platform");
        }

        // 4. Trusted provider + affordable → BUDGET
        if (model.provider() != null
                && TRUSTED_PROVIDERS.contains(model.provider().toLowerCase())
                && model.pricing().promptCostPerMillion().compareTo(BUDGET_COST_THRESHOLD) <= 0) {
            return new ModelEvaluation(ModelTier.BUDGET,
                "Untested model from trusted provider (" + model.provider() + ") at reasonable cost");
        }

        // 5. Else → EXPERIMENTAL
        return new ModelEvaluation(ModelTier.EXPERIMENTAL,
            "Untested model — use at your own risk");
    }

    /**
     * Whether this model runs on hardware the user controls rather than a hosted free tier.
     *
     * <p>A local server advertises bare names ({@code llama3.2}, {@code qwen3:8b}) with no
     * {@code provider/} namespace, which is exactly what distinguishes them from hosted ids.</p>
     */
    static boolean isLocallyHosted(LLMModelInfo model) {
        return model.provider() == null;
    }

    // ── Known-good map (from A/B testing results) ─────────────────────────

    private static Map<String, Map<TradingContext, ModelEvaluation>> buildKnownGoodMap() {
        Map<String, Map<TradingContext, ModelEvaluation>> map = new java.util.HashMap<>();

        // ── Trading context models (6 rounds of A/B testing, Feb 2026) ──

        // Mistral Small 24B: Best ROI overall for trading
        map.put("mistralai/mistral-small-3.2-24b-instruct", contextMap(
            entry(TradingContext.BACKTESTING, ModelTier.RECOMMENDED, "Best ROI: +13.34% returns at $0.00055/decision"),
            entry(TradingContext.RESEARCH, ModelTier.RECOMMENDED, "Cheapest working model for research ($0.00055/decision)"),
            entry(TradingContext.PAPER_TRADING, ModelTier.PROVEN, "Reliable, respects position limits"),
            entry(TradingContext.LIVE_TRADING, ModelTier.PROVEN, "Proven in testing, good cost/quality balance"),
            entry(TradingContext.CHAT, ModelTier.PROVEN, "Good tool use at $0.10/M — superseded by MiMo-V2-Flash for chat")
        ));

        // Xiaomi MiMo-V2-Flash: Best value chat model (100% pass rate including hero flow)
        map.put("xiaomi/mimo-v2-flash", contextMap(
            entry(TradingContext.CHAT, ModelTier.RECOMMENDED, "100% test pass rate incl. hero flow at $0.14/M — best value for chat")
        ));

        // Gemini 2.0 Flash: DEPRECATED — sunsets March 31, 2026. Replaced by Llama 4 Scout.
        map.put("google/gemini-2.0-flash-001", contextMap(
            entry(TradingContext.BACKTESTING, ModelTier.PROVEN, "DEPRECATED (March 2026). +9.20% returns — migrate to llama-4-scout"),
            entry(TradingContext.RESEARCH, ModelTier.PROVEN, "DEPRECATED (March 2026). Migrate to llama-4-scout"),
            entry(TradingContext.PAPER_TRADING, ModelTier.PROVEN, "DEPRECATED (March 2026). Migrate to llama-4-scout"),
            entry(TradingContext.LIVE_TRADING, ModelTier.PROVEN, "DEPRECATED (March 2026). Migrate to llama-4-scout")
        ));

        // Llama 4 Scout: Fast, reliable JSON output, replaces gemini-2.0-flash in fallback chain
        map.put("meta-llama/llama-4-scout", contextMap(
            entry(TradingContext.BACKTESTING, ModelTier.PROVEN, "Fast, reliable JSON 4/4 at $0.08/$0.30 per M — gemini-2.0-flash replacement"),
            entry(TradingContext.PAPER_TRADING, ModelTier.RECOMMENDED, "Fast, reliable — replaces deprecated gemini-2.0-flash"),
            entry(TradingContext.LIVE_TRADING, ModelTier.PROVEN, "Reliable for trading decisions"),
            entry(TradingContext.RESEARCH, ModelTier.PROVEN, "Reliable for research tasks"),
            entry(TradingContext.CHAT, ModelTier.PROVEN, "Cheapest open-weight chat model at $0.12/$0.45 per million tokens")
        ));

        // DeepSeek V3.2: GPT-5 class reasoning, best orchestrator quality at budget price
        map.put("deepseek/deepseek-v3.2", contextMap(
            entry(TradingContext.BACKTESTING, ModelTier.PROVEN, "GPT-5 class reasoning at $0.25/$0.38 per M — best orchestrator quality"),
            entry(TradingContext.PAPER_TRADING, ModelTier.PROVEN, "Strong reasoning for paper trading decisions"),
            entry(TradingContext.RESEARCH, ModelTier.PROVEN, "Strong reasoning for research tasks"),
            entry(TradingContext.CHAT, ModelTier.PROVEN, "Reliable chat at $0.38/$0.57 per million tokens")
        ));

        // Claude Opus 4.6: Flagship premium model
        map.put("anthropic/claude-opus-4.6", contextMap(
            entry(TradingContext.BACKTESTING, ModelTier.PROVEN, "Flagship premium model"),
            entry(TradingContext.RESEARCH, ModelTier.PROVEN, "Highest reasoning quality"),
            entry(TradingContext.LIVE_TRADING, ModelTier.RECOMMENDED, "Flagship for real money"),
            entry(TradingContext.CHAT, ModelTier.PROVEN, "Flagship chat — premium pricing")
        ));

        // Claude Sonnet 4.5: Premium quality
        map.put("anthropic/claude-sonnet-4.6", contextMap(
            entry(TradingContext.BACKTESTING, ModelTier.PROVEN, "Premium quality at $0.038/decision"),
            entry(TradingContext.RESEARCH, ModelTier.PROVEN, "Strong reasoning for research"),
            entry(TradingContext.PAPER_TRADING, ModelTier.PROVEN, "Premium quality for paper trading"),
            entry(TradingContext.LIVE_TRADING, ModelTier.RECOMMENDED, "Best reasoning quality for real money decisions"),
            entry(TradingContext.CHAT, ModelTier.PROVEN, "Premium chat quality")
        ));

        // Gemini 2.5 Flash: Battle-tested
        map.put("google/gemini-2.5-flash", contextMap(
            entry(TradingContext.BACKTESTING, ModelTier.PROVEN, "Most battle-tested, consistent strategy compliance"),
            entry(TradingContext.RESEARCH, ModelTier.PROVEN, "Reliable for research at $0.005/decision"),
            entry(TradingContext.PAPER_TRADING, ModelTier.PROVEN, "Consistent execution in paper trading"),
            entry(TradingContext.LIVE_TRADING, ModelTier.PROVEN, "Battle-tested, fastest execution")
        ));

        // Qwen 2.5 72B: Strong budget option (backtesting/paper only)
        map.put("qwen/qwen-2.5-72b-instruct", contextMap(
            entry(TradingContext.BACKTESTING, ModelTier.PROVEN, "+11.73% returns at $0.00175/decision, 72B dense parameters"),
            entry(TradingContext.PAPER_TRADING, ModelTier.PROVEN, "Strong returns in paper trading tests")
        ));

        // Cohere Command R7B: Ultra-cheap (backtesting only)
        map.put("cohere/command-r7b-12-2024", contextMap(
            entry(TradingContext.BACKTESTING, ModelTier.PROVEN, "+8.30% returns at ~$0.0003/decision, ultra-cheap fallback")
        ));

        // ── Chat context models (23-model evaluation, Feb 2026) ─────────
        // Tested on: greeting quality (>=15 words), AAPL=stock recognition, backfill trigger.
        // See docs/CHAT_MODEL_EVALUATION.md for full results.
        // Note: some models are known-bad for trading but work well for chat tool use.

        // Qwen Turbo: Cheapest passing model (incl. platform fee)
        map.put("qwen/qwen-turbo", contextMap(
            entry(TradingContext.CHAT, ModelTier.PROVEN, "Cheapest passing chat model at $0.08/M input — 56x cheaper than Sonnet 4")
        ));

        // GPT-5 Nano: Ties cheapest input, 2x output cost
        map.put("openai/gpt-5-nano", contextMap(
            entry(TradingContext.CHAT, ModelTier.PROVEN, "Passes all chat criteria at $0.08/$0.60 per million tokens")
        ));

        // Llama 4 Scout: promoted to trading contexts (see above)

        // Llama 4 Maverick: Strong open-weight option
        map.put("meta-llama/llama-4-maverick", contextMap(
            entry(TradingContext.CHAT, ModelTier.PROVEN, "Reliable chat at $0.23/$0.90 per million tokens")
        ));

        // DeepSeek Chat v3.1: Trading-focused with strong tool use
        map.put("deepseek/deepseek-chat-v3.1", contextMap(
            entry(TradingContext.CHAT, ModelTier.PROVEN, "Strong tool use for chat at $0.23/$1.13 per million tokens")
        ));

        // Grok 4 Fast: Solid mid-range
        map.put("x-ai/grok-4-fast", contextMap(
            entry(TradingContext.CHAT, ModelTier.PROVEN, "Reliable chat at $0.30/$0.75 per million tokens")
        ));

        // DeepSeek v3.2: promoted to trading contexts (see above)

        // GPT-5 Mini: Strong tool use, higher output cost
        map.put("openai/gpt-5-mini", contextMap(
            entry(TradingContext.CHAT, ModelTier.PROVEN, "Strong chat at $0.38/$3.00 per million tokens")
        ));

        // Claude Haiku 4.5: Premium quality, higher cost
        map.put("anthropic/claude-haiku-4.5", contextMap(
            entry(TradingContext.CHAT, ModelTier.PROVEN, "Premium chat quality at $1.50/$7.50 per million tokens")
        ));

        // Gemini 2.5 Pro: Only Google model passing chat test
        map.put("google/gemini-2.5-pro", contextMap(
            entry(TradingContext.CHAT, ModelTier.PROVEN, "Only Google model passing all chat criteria at $1.88/$15.00")
        ));

        return Map.copyOf(map);
    }

    // ── Known-bad patterns ────────────────────────────────────────────────

    private static List<PatternEntry> buildKnownBadList() {
        return List.of(
            new PatternEntry(":free", "FREE tier models have 95-100% parse failure rate"),
            new PatternEntry("claude-3-haiku", "100% parse failure rate — output format incompatible"),
            new PatternEntry("claude-3.5-haiku", "Lost -8.27% despite high reliability — poor entry timing"),
            new PatternEntry("deepseek-r1", "Severe rate limiting and very expensive reasoning tokens"),
            new PatternEntry("deepseek-chat", "Analysis paralysis — rarely or never trades in backtests"),
            new PatternEntry("mistral-nemo", "Ignores position limits (95%+ single positions), math errors"),
            new PatternEntry("llama-3.3-70b", "Ignores position limits (85%+ single positions)"),
            new PatternEntry("qwen3-32b", "100% orchestrator parse failures — MoE architecture insufficient"),
            new PatternEntry("glm-4", "~95% parse failure rate with Chinese AI models"),
            new PatternEntry("gpt-5-nano", "100% parse failure rate"),
            new PatternEntry("gemini-2.5-flash-lite", "Never executes trades despite appearing functional"),
            new PatternEntry("qwen-turbo", "Never executes trades"),
            new PatternEntry("grok-4.1-fast", "10x overpriced with no quality improvement"),
            new PatternEntry("claude-3.5-haiku", "Lost -8.27% despite working — poor entry timing")
        );
    }

    // ── Internal helpers ──────────────────────────────────────────────────

    private record PatternEntry(String pattern, String reason) {}

    private record ContextEntry(TradingContext context, ModelTier tier, String reason) {}

    private static ContextEntry entry(TradingContext context, ModelTier tier, String reason) {
        return new ContextEntry(context, tier, reason);
    }

    private static Map<TradingContext, ModelEvaluation> contextMap(ContextEntry... entries) {
        Map<TradingContext, ModelEvaluation> map = new EnumMap<>(TradingContext.class);
        for (ContextEntry e : entries) {
            map.put(e.context, new ModelEvaluation(e.tier, e.reason));
        }
        return Map.copyOf(map);
    }
}
