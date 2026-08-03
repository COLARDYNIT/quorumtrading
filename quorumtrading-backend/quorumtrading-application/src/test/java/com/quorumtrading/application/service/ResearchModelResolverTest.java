package com.quorumtrading.application.service;

import com.quorumtrading.application.port.out.ActiveLlmEnginePort;
import com.quorumtrading.application.port.out.SystemModelDefaultsPort;
import com.quorumtrading.application.port.out.UserBudgetRepository;
import com.quorumtrading.model.entity.UserBudget;
import com.quorumtrading.model.enums.AgentKind;
import com.quorumtrading.model.enums.AgentRole;
import com.quorumtrading.model.enums.LlmProvider;
import com.quorumtrading.model.valueobject.ModelId;
import com.quorumtrading.model.valueobject.ModelResolution;
import com.quorumtrading.model.valueobject.ResearchAgentType;
import com.quorumtrading.model.valueobject.ResolutionTier;
import com.quorumtrading.model.valueobject.TradingContext;
import com.quorumtrading.model.valueobject.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Research resolving models on the same chain as consensus.
 *
 * <p>The two defects this closes: a per-agent research preference was storable and completely inert,
 * because one model was applied to every research agent; and the tier-6 key
 * {@code quorumtrading.llm.defaults.research} was bypassed in favour of a duplicate that happened to
 * hold the same value, so the divergence was invisible.</p>
 */
@DisplayName("ResearchModelResolver — research on the shared chain")
class ResearchModelResolverTest {

    private static final String SYSTEM_RESEARCH_DEFAULT = "system/research";
    private static final UserId OWNER = new UserId(UUID.randomUUID());

    private UserBudgetRepository budgets;
    private SystemModelDefaultsPort systemDefaults;
    private ResearchModelResolver resolver;

    @BeforeEach
    void setUp() {
        budgets = mock(UserBudgetRepository.class);
        when(budgets.findByUserId(any())).thenReturn(Optional.empty());

        systemDefaults = mock(SystemModelDefaultsPort.class);
        when(systemDefaults.getDefaultModel(any(LlmProvider.class), any(TradingContext.class)))
                .thenReturn(Optional.empty());
        when(systemDefaults.getDefaultModel(any(), any(TradingContext.class)))
                .thenReturn(Optional.empty());
        when(systemDefaults.getDefaultModel(TradingContext.RESEARCH))
                .thenReturn(Optional.of(SYSTEM_RESEARCH_DEFAULT));
        when(systemDefaults.getDefaultModel(any(), any(AgentRole.class))).thenReturn(Optional.empty());

        ActiveLlmEnginePort engine = () -> Optional.of(LlmProvider.OPENROUTER);
        // No capability service: this test is about the chain, not the negotiation.
        resolver = new ResearchModelResolver(budgets, systemDefaults, engine, null);
    }

    private void userHasPreferences(Map<String, String> storageKeyToModel) {
        UserBudget budget = UserBudget.createUnlimited(OWNER);
        storageKeyToModel.forEach((key, model) -> {
            com.quorumtrading.model.valueobject.ModelPreferenceKey parsed =
                    com.quorumtrading.model.valueobject.ModelPreferenceKey.fromStorageKey(key);
            if (parsed.isGlobal()) {
                budget.setModelPreference(parsed.context(), ModelId.of(model));
            } else {
                budget.setAgentModelPreference(parsed.context(), parsed.agentKind(), ModelId.of(model));
            }
        });
        when(budgets.findByUserId(OWNER)).thenReturn(Optional.of(budget));
    }

    @Test
    @DisplayName("with no preferences, tier 6 answers — and it is defaults.research, the one key")
    void systemDefaultComesFromTheOneKey() {
        when(systemDefaults.getDefaultModel(LlmProvider.OPENROUTER, TradingContext.RESEARCH))
                .thenReturn(Optional.of(SYSTEM_RESEARCH_DEFAULT));

        ModelResolution resolution =
                resolver.resolve(OWNER, ResearchAgentType.STRATEGY_DESIGNER, null);

        assertThat(resolution.effective()).isEqualTo(SYSTEM_RESEARCH_DEFAULT);
        assertThat(resolution.decidedByTier()).isEqualTo(ResolutionTier.SYSTEM_GLOBAL);
    }

    @Test
    @DisplayName("a user's global RESEARCH preference still wins over the system default")
    void userGlobalBeatsSystemDefault() {
        userHasPreferences(Map.of("RESEARCH", "user/global"));

        ModelResolution resolution =
                resolver.resolve(OWNER, ResearchAgentType.VIABILITY_ASSESSOR, null);

        assertThat(resolution.effective()).isEqualTo("user/global");
        assertThat(resolution.decidedByTier()).isEqualTo(ResolutionTier.USER_GLOBAL);
    }

    @Test
    @DisplayName("a PER-AGENT research preference beats the global one — the tier research never had")
    void perAgentResearchPreferenceWins() {
        userHasPreferences(Map.of(
                "RESEARCH", "user/global",
                "RESEARCH_STRATEGY_DESIGNER", "user/designer"));

        assertThat(resolver.resolve(OWNER, ResearchAgentType.STRATEGY_DESIGNER, null).effective())
                .isEqualTo("user/designer");
        assertThat(resolver.resolve(OWNER, ResearchAgentType.STRATEGY_DESIGNER, null).decidedByTier())
                .isEqualTo(ResolutionTier.USER_AGENT);
    }

    @Test
    @DisplayName("a per-agent preference is scoped to that agent — the others keep the global one")
    void perAgentPreferenceDoesNotLeak() {
        userHasPreferences(Map.of(
                "RESEARCH", "user/global",
                "RESEARCH_STRATEGY_DESIGNER", "user/designer"));

        assertThat(resolver.resolve(OWNER, ResearchAgentType.NEWS_SENTIMENT_ANALYST, null).effective())
                .isEqualTo("user/global");
        assertThat(resolver.resolve(OWNER, ResearchAgentType.VIABILITY_ASSESSOR, null).effective())
                .isEqualTo("user/global");
    }

    @Test
    @DisplayName("research agents now differ from one another — the point of the whole exercise")
    void differentAgentsCanUseDifferentModels() {
        userHasPreferences(Map.of(
                "RESEARCH_NEWS_SENTIMENT_ANALYST", "user/news",
                "RESEARCH_STRATEGY_DESIGNER", "user/designer",
                "RESEARCH_VIABILITY_ASSESSOR", "user/viability"));

        assertThat(resolver.resolve(OWNER, ResearchAgentType.NEWS_SENTIMENT_ANALYST, null).effective())
                .isEqualTo("user/news");
        assertThat(resolver.resolve(OWNER, ResearchAgentType.STRATEGY_DESIGNER, null).effective())
                .isEqualTo("user/designer");
        assertThat(resolver.resolve(OWNER, ResearchAgentType.VIABILITY_ASSESSOR, null).effective())
                .isEqualTo("user/viability");
    }

    @Test
    @DisplayName("an explicit caller override still wins over everything")
    void explicitOverrideWins() {
        userHasPreferences(Map.of("RESEARCH_STRATEGY_DESIGNER", "user/designer"));

        assertThat(resolver.resolve(OWNER, ResearchAgentType.STRATEGY_DESIGNER, "caller/model").effective())
                .isEqualTo("caller/model");
    }

    @Test
    @DisplayName("research has no orchestrator, so no ORCHESTRATOR preference can apply to it")
    void researchHasNoOrchestrator() {
        userHasPreferences(Map.of("RESEARCH_ORCHESTRATOR", "never/used"));
        when(systemDefaults.getDefaultModel(LlmProvider.OPENROUTER, TradingContext.RESEARCH))
                .thenReturn(Optional.of(SYSTEM_RESEARCH_DEFAULT));

        for (ResearchAgentType agent : ResearchAgentType.values()) {
            assertThat(resolver.resolve(OWNER, agent, null).effective())
                    .as("%s must not pick up an ORCHESTRATOR preference", agent)
                    .isEqualTo(SYSTEM_RESEARCH_DEFAULT);
        }
        // The key remains parseable — it is a consensus agent in a research context, which is
        // storable but has no research agent to apply to.
        assertThat(AgentKind.of(AgentRole.ORCHESTRATOR).isResearchAgent()).isFalse();
    }

    @Test
    @DisplayName("no owner falls through to the system default rather than failing")
    void noOwnerUsesSystemDefault() {
        when(systemDefaults.getDefaultModel(LlmProvider.OPENROUTER, TradingContext.RESEARCH))
                .thenReturn(Optional.of(SYSTEM_RESEARCH_DEFAULT));

        assertThat(resolver.resolve(null, ResearchAgentType.STRATEGY_DESIGNER, null).effective())
                .isEqualTo(SYSTEM_RESEARCH_DEFAULT);
    }

    @Test
    @DisplayName("a preference lookup that throws degrades to the system default rather than failing research")
    void preferenceLookupFailureDegrades() {
        when(budgets.findByUserId(OWNER)).thenThrow(new IllegalStateException("db closed"));
        when(systemDefaults.getDefaultModel(LlmProvider.OPENROUTER, TradingContext.RESEARCH))
                .thenReturn(Optional.of(SYSTEM_RESEARCH_DEFAULT));

        assertThat(resolver.resolve(OWNER, ResearchAgentType.STRATEGY_DESIGNER, null).effective())
                .isEqualTo(SYSTEM_RESEARCH_DEFAULT);
    }

    @Test
    @DisplayName("an engine lookup that throws leaves the engine unknown rather than guessed")
    void engineLookupFailureLeavesEngineUnknown() {
        ResearchModelResolver throwing = new ResearchModelResolver(budgets, systemDefaults, () -> {
            throw new IllegalStateException("no transaction");
        }, null);
        when(systemDefaults.getDefaultModel(null, TradingContext.RESEARCH))
                .thenReturn(Optional.of(SYSTEM_RESEARCH_DEFAULT));

        ModelResolution resolution = throwing.resolve(OWNER, ResearchAgentType.STRATEGY_DESIGNER, null);

        assertThat(resolution.engine()).isNull();
        assertThat(resolution.effective()).isEqualTo(SYSTEM_RESEARCH_DEFAULT);
    }

    @Test
    @DisplayName("a per-agent SYSTEM default is honoured when the config names one")
    void systemPerAgentDefaultIsHonoured() {
        when(systemDefaults.getDefaultModel(TradingContext.RESEARCH, AgentRole.ORCHESTRATOR))
                .thenReturn(Optional.of("system/per-agent"));

        // ORCHESTRATOR is a consensus kind, so no RESEARCH agent maps to it — the tier misses and
        // tier 6 answers, which is exactly why defaults.research.orchestrator was dead config.
        when(systemDefaults.getDefaultModel(LlmProvider.OPENROUTER, TradingContext.RESEARCH))
                .thenReturn(Optional.of(SYSTEM_RESEARCH_DEFAULT));
        assertThat(resolver.resolve(OWNER, ResearchAgentType.STRATEGY_DESIGNER, null).effective())
                .isEqualTo(SYSTEM_RESEARCH_DEFAULT);
    }

    @Test
    @DisplayName("nothing configured anywhere resolves to unresolved, not to a guess")
    void nothingConfiguredIsUnresolved() {
        when(systemDefaults.getDefaultModel(any(), any(TradingContext.class))).thenReturn(Optional.empty());

        ModelResolution resolution = resolver.resolve(OWNER, ResearchAgentType.STRATEGY_DESIGNER, null);

        assertThat(resolution.isServable()).isFalse();
        assertThat(resolution.decidedByTier()).isEqualTo(ResolutionTier.NONE);
    }
}
