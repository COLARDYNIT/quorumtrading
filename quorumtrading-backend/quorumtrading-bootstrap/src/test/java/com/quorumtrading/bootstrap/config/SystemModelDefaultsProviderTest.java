package com.quorumtrading.bootstrap.config;

import com.quorumtrading.model.enums.AgentRole;
import com.quorumtrading.model.enums.LlmProvider;
import com.quorumtrading.model.valueobject.TradingContext;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigValue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * System defaults are engine-scoped: "use this when nothing better is specified" has no answer
 * until you know which engine will run it. A deployment-wide pin on a local server is that
 * engine's default for every context and role — not a bypass applied after resolution.
 */
@DisplayName("SystemModelDefaultsProvider — engine-scoped defaults, and no dead config keys")
class SystemModelDefaultsProviderTest {

    /** A Config that answers only from the map it was given — an unset key stays unset. */
    private static Config configOf(Map<String, String> values) {
        return new Config() {
            @Override
            @SuppressWarnings("unchecked")
            public <T> T getValue(String name, Class<T> type) {
                return (T) values.get(name);
            }

            @Override
            @SuppressWarnings("unchecked")
            public <T> Optional<T> getOptionalValue(String name, Class<T> type) {
                return Optional.ofNullable((T) values.get(name));
            }

            @Override
            public ConfigValue getConfigValue(String name) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Iterable<String> getPropertyNames() {
                return values.keySet();
            }

            @Override
            public Iterable<org.eclipse.microprofile.config.spi.ConfigSource> getConfigSources() {
                return java.util.List.of();
            }

            @Override
            public <T> Optional<org.eclipse.microprofile.config.spi.Converter<T>> getConverter(Class<T> forType) {
                return Optional.empty();
            }

            @Override
            public <T> T unwrap(Class<T> type) {
                throw new UnsupportedOperationException();
            }
        };
    }

    private static SystemModelDefaultsProvider provider(String pin, Map<String, String> perAgentKeys) {
        return new SystemModelDefaultsProvider(
                "hosted/backtesting",
                "hosted/paper",
                "hosted/live",
                "hosted/research",
                "hosted/chat",
                Optional.ofNullable(pin),
                configOf(perAgentKeys));
    }

    @Test
    @DisplayName("a LOCAL_HTTP pin is the default for every context — the engine serves nothing else")
    void pinIsTheDefaultForEveryContextOnLocalHttp() {
        SystemModelDefaultsProvider provider = provider("llama3.2", Map.of());

        for (TradingContext context : TradingContext.values()) {
            assertThat(provider.getDefaultModel(LlmProvider.LOCAL_HTTP, context)).contains("llama3.2");
        }
    }

    @Test
    @DisplayName("a LOCAL_HTTP pin leaves no per-agent defaults — tier 5 misses so tier 6 answers with the pin")
    void pinLeavesNoPerAgentDefaults() {
        SystemModelDefaultsProvider provider = provider("llama3.2",
                Map.of("quorumtrading.llm.defaults.backtesting.orchestrator", "deepseek/deepseek-v3.2"));

        assertThat(provider.getAgentDefaults(LlmProvider.LOCAL_HTTP, TradingContext.BACKTESTING)).isEmpty();
    }

    @Test
    @DisplayName("the pin does not leak onto other engines — OpenRouter keeps the hosted defaults")
    void pinDoesNotLeakOntoOtherEngines() {
        SystemModelDefaultsProvider provider = provider("llama3.2", Map.of());

        assertThat(provider.getDefaultModel(LlmProvider.OPENROUTER, TradingContext.BACKTESTING))
                .contains("hosted/backtesting");
        assertThat(provider.getDefaultModel(LlmProvider.LOCAL, TradingContext.LIVE_TRADING))
                .contains("hosted/live");
    }

    @Test
    @DisplayName("an unknown engine falls back to the configured defaults rather than guessing")
    void unknownEngineUsesConfiguredDefaults() {
        SystemModelDefaultsProvider provider = provider("llama3.2", Map.of());

        assertThat(provider.getDefaultModel(null, TradingContext.BACKTESTING)).contains("hosted/backtesting");
    }

    @Test
    @DisplayName("LOCAL_HTTP without a pin uses the configured defaults — nothing is invented")
    void localHttpWithoutPinUsesConfiguredDefaults() {
        SystemModelDefaultsProvider provider = provider(null, Map.of());

        assertThat(provider.getDefaultModel(LlmProvider.LOCAL_HTTP, TradingContext.BACKTESTING))
                .contains("hosted/backtesting");
    }

    @Test
    @DisplayName("a blank pin is not a pin")
    void blankPinIsIgnored() {
        SystemModelDefaultsProvider provider = provider("   ", Map.of());

        assertThat(provider.getDefaultModel(LlmProvider.LOCAL_HTTP, TradingContext.BACKTESTING))
                .contains("hosted/backtesting");
    }

    @Test
    @DisplayName("a per-agent default exists exactly when someone set the key — no declared-but-dead keys")
    void perAgentDefaultsComeOnlyFromKeysThatAreSet() {
        SystemModelDefaultsProvider provider = provider(null, Map.of(
                "quorumtrading.llm.defaults.backtesting.orchestrator", "deepseek/deepseek-v3.2",
                "quorumtrading.llm.defaults.paper-trading.technical", "some/technical-model"));

        assertThat(provider.getAgentDefaults(TradingContext.BACKTESTING))
                .containsExactly(Map.entry(AgentRole.ORCHESTRATOR, "deepseek/deepseek-v3.2"));
        assertThat(provider.getAgentDefaults(TradingContext.PAPER_TRADING))
                .containsExactly(Map.entry(AgentRole.TECHNICAL, "some/technical-model"));
        assertThat(provider.getAgentDefaults(TradingContext.LIVE_TRADING)).isEmpty();
        assertThat(provider.getAgentDefaults(TradingContext.RESEARCH)).isEmpty();
    }

    @Test
    @DisplayName("a blank per-agent value is treated as unset")
    void blankPerAgentValueIsUnset() {
        SystemModelDefaultsProvider provider = provider(null,
                Map.of("quorumtrading.llm.defaults.backtesting.technical", "  "));

        assertThat(provider.getAgentDefaults(TradingContext.BACKTESTING)).isEmpty();
    }

    @Test
    @DisplayName("the per-agent key grammar is {context-kebab}.{role-lower}")
    void perAgentKeyGrammar() {
        SystemModelDefaultsProvider provider = provider(null,
                Map.of("quorumtrading.llm.defaults.live-trading.sentiment", "some/sentiment-model"));

        assertThat(provider.getDefaultModel(TradingContext.LIVE_TRADING, AgentRole.SENTIMENT))
                .contains("some/sentiment-model");
    }
}
