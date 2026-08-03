package com.quorumtrading.bootstrap.config;

import com.quorumtrading.application.port.out.SystemModelDefaultsPort;
import com.quorumtrading.model.enums.AgentRole;
import com.quorumtrading.model.enums.LlmProvider;
import com.quorumtrading.model.valueobject.TradingContext;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The one place system-level model configuration is read — {@code application.properties} and
 * environment variables alike.
 *
 * <p>Two things used to make this untrue. Per-agent defaults were declared as sixteen explicit
 * constructor parameters, of which three were ever set, so thirteen config keys existed in the
 * source and nowhere else — dead config that reads as live. They are now looked up by name, so a
 * key exists exactly when someone sets it. And {@code QT_LLM_LOCALHTTP_MODEL} was read inside the
 * localhttp adapter's request path, <em>after</em> resolution had finished, which is how the
 * platform came to compute one model, report it, and run another.</p>
 *
 * <p>A deployment-wide pin on a local HTTP server is not a bypass — it is that engine's system
 * default, for every context and every role, because it is the only thing that engine will serve.
 * Modelled as such, the 6-tier chain returns the model that actually runs and every read surface
 * inherits the truth for free.</p>
 */
@Singleton
public class SystemModelDefaultsProvider implements SystemModelDefaultsPort {

    /** Config key prefix for context-global defaults, e.g. {@code ...defaults.paper-trading}. */
    private static final String DEFAULTS_PREFIX = "quorumtrading.llm.defaults.";

    private final String backtestingModel;
    private final String paperTradingModel;
    private final String liveTradingModel;
    private final String researchModel;
    private final String chatModel;

    /**
     * Deployment-wide model pin for a local OpenAI-compatible server ({@code QT_LLM_LOCALHTTP_MODEL}).
     * Blank/unset means the engine picks for itself.
     */
    private final String localHttpPinnedModel;

    /** Per-(context, role) defaults, holding only keys someone actually set. */
    private final Map<TradingContext, Map<AgentRole, String>> agentDefaults;

    public SystemModelDefaultsProvider(
            @ConfigProperty(name = "quorumtrading.llm.defaults.backtesting") String backtestingModel,
            @ConfigProperty(name = "quorumtrading.llm.defaults.paper-trading") String paperTradingModel,
            @ConfigProperty(name = "quorumtrading.llm.defaults.live-trading") String liveTradingModel,
            @ConfigProperty(name = "quorumtrading.llm.defaults.research") String researchModel,
            @ConfigProperty(name = "quorumtrading.llm.defaults.chat",
                    defaultValue = "mistralai/mistral-small-3.2-24b-instruct") String chatModel,
            @ConfigProperty(name = "quorumtrading.llm.localhttp.model") Optional<String> localHttpPinnedModel,
            Config config) {
        this.backtestingModel = backtestingModel;
        this.paperTradingModel = paperTradingModel;
        this.liveTradingModel = liveTradingModel;
        this.researchModel = researchModel;
        this.chatModel = chatModel;
        this.localHttpPinnedModel = localHttpPinnedModel.filter(s -> !s.isBlank()).orElse(null);
        this.agentDefaults = readAgentDefaults(config);
    }

    /**
     * Look up {@code quorumtrading.llm.defaults.{context}.{role}} for every combination, keeping
     * only the ones that resolve. Nothing is declared up front, so an unset key leaves no trace.
     */
    private static Map<TradingContext, Map<AgentRole, String>> readAgentDefaults(Config config) {
        Map<TradingContext, Map<AgentRole, String>> byContext = new EnumMap<>(TradingContext.class);
        for (TradingContext context : TradingContext.values()) {
            Map<AgentRole, String> byRole = new EnumMap<>(AgentRole.class);
            for (AgentRole role : AgentRole.values()) {
                String key = DEFAULTS_PREFIX + contextKey(context) + "." + role.name().toLowerCase(Locale.ROOT);
                config.getOptionalValue(key, String.class)
                        .filter(value -> !value.isBlank())
                        .ifPresent(value -> byRole.put(role, value));
            }
            if (!byRole.isEmpty()) {
                byContext.put(context, Map.copyOf(byRole));
            }
        }
        return Map.copyOf(byContext);
    }

    /** {@code PAPER_TRADING} → {@code paper-trading}, matching the property naming convention. */
    private static String contextKey(TradingContext context) {
        return context.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    @Override
    public Optional<String> getDefaultModel(TradingContext context) {
        String model = switch (context) {
            case BACKTESTING -> backtestingModel;
            case PAPER_TRADING -> paperTradingModel;
            case LIVE_TRADING -> liveTradingModel;
            case RESEARCH -> researchModel;
            case CHAT -> chatModel;
        };
        return Optional.ofNullable(model).filter(s -> !s.isBlank());
    }

    @Override
    public Optional<String> getDefaultModel(TradingContext context, AgentRole role) {
        Map<AgentRole, String> map = agentDefaults.get(context);
        if (map == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(map.get(role));
    }

    @Override
    public Map<AgentRole, String> getAgentDefaults(TradingContext context) {
        return agentDefaults.getOrDefault(context, Map.of());
    }

    @Override
    public Optional<String> getDefaultModel(LlmProvider engine, TradingContext context) {
        if (engine == LlmProvider.LOCAL_HTTP && localHttpPinnedModel != null) {
            return Optional.of(localHttpPinnedModel);
        }
        return getDefaultModel(context);
    }

    /**
     * A pinned local server has no room for per-agent variation: every role gets the pin, which the
     * context-global default already supplies. Returning an empty map lets tier 5 miss cleanly so
     * tier 6 answers with the pin, rather than restating it four times.
     */
    @Override
    public Map<AgentRole, String> getAgentDefaults(LlmProvider engine, TradingContext context) {
        if (engine == LlmProvider.LOCAL_HTTP && localHttpPinnedModel != null) {
            return Map.of();
        }
        return getAgentDefaults(context);
    }

    @Override
    public Map<TradingContext, String> getAllDefaults() {
        Map<TradingContext, String> defaults = new EnumMap<>(TradingContext.class);
        addIfNotBlank(defaults, TradingContext.BACKTESTING, backtestingModel);
        addIfNotBlank(defaults, TradingContext.PAPER_TRADING, paperTradingModel);
        addIfNotBlank(defaults, TradingContext.LIVE_TRADING, liveTradingModel);
        addIfNotBlank(defaults, TradingContext.RESEARCH, researchModel);
        addIfNotBlank(defaults, TradingContext.CHAT, chatModel);
        return Map.copyOf(defaults);
    }

    private <K> void addIfNotBlank(Map<K, String> map, K key, String model) {
        if (model != null && !model.isBlank()) {
            map.put(key, model);
        }
    }
}
