package com.quorumtrading.application.service;

import com.quorumtrading.application.event.ResearchExecutionCompleted;
import com.quorumtrading.application.orchestration.agents.LLMConfig;
import com.quorumtrading.application.orchestration.agents.LLMQueryResult;
import com.quorumtrading.application.port.out.EventPublisher;
import com.quorumtrading.application.port.out.LLMPort;
import com.quorumtrading.application.port.out.ResearchExecutionRepository;
import com.quorumtrading.application.port.out.StrategyRepository;
import com.quorumtrading.model.entity.MarketResearch;
import com.quorumtrading.model.enums.InvestmentType;
import com.quorumtrading.model.valueobject.BacktestConfiguration;
import com.quorumtrading.model.valueobject.ResearchInterval;
import com.quorumtrading.model.valueobject.Symbol;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.quorumtrading.application.service.TestConstants.TEST_USER_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The orchestrator's flat-model fallback, used only when no {@code ResearchModelResolver} is wired.
 *
 * <p>This was research's <em>whole</em> resolver: honour the user's global RESEARCH preference, else
 * a constructor-injected default from a duplicate config key, and apply that one model to every
 * research agent — which is why per-agent research preferences were storable and inert, and why
 * {@code quorumtrading.llm.defaults.research.orchestrator} could never have had an effect.</p>
 *
 * <p>Production now wires a resolver and research runs on the shared chain; see
 * {@code ResearchModelResolverTest}. These assertions stay because the fallback still exists for
 * lightweight construction, and because they record precisely what was replaced.</p>
 */
@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Research model resolution — the parallel resolver, as it behaves today")
class ResearchModelResolutionCharacterisationTest {

    private static final String SYSTEM_DEFAULT = "system/research-default";
    private static final String SYSTEM_DEFAULT_ONLINE = SYSTEM_DEFAULT + ":online";

    @Mock private LLMPort llmPort;
    @Mock private StrategyRepository strategyRepository;
    @Mock private ResearchExecutionRepository executionRepository;
    @Mock private EventPublisher<ResearchExecutionCompleted> eventPublisher;

    private MarketResearchOrchestrator orchestrator;
    private MarketResearch research;

    private static final String LOW_VIABILITY = """
        {
          "viabilityScore": 10,
          "recommendation": "REJECT",
          "currentMarketConditions": "n/a",
          "assetAccessibility": "n/a",
          "opportunityQuality": "n/a",
          "riskAssessment": "n/a",
          "timingAnalysis": "n/a",
          "recommendedTickers": "None",
          "majorConcerns": "none",
          "detailedReasoning": "short-circuit so the run stays cheap"
        }
        """;

    @BeforeEach
    void setUp() {
        orchestrator = new MarketResearchOrchestrator(
                llmPort, strategyRepository, executionRepository, eventPublisher,
                null, null,
                SYSTEM_DEFAULT, SYSTEM_DEFAULT_ONLINE,
                0.7, 2000,
                null, false, 60, null, null, null, null, null);

        research = new MarketResearch(
                "Daily BTC Research",
                "# Research Instructions\nFind BTC trading opportunities",
                Set.of(new Symbol("BTC", InvestmentType.CRYPTO)),
                new ResearchInterval(1, ResearchInterval.Unit.DAYS),
                true,
                new BacktestConfiguration(
                        Duration.ofDays(30),
                        Instant.parse("2024-01-01T00:00:00Z"),
                        Instant.parse("2024-01-31T00:00:00Z")),
                TEST_USER_ID);

        // Low viability short-circuits after the two parallel agents — enough to observe the model.
        when(llmPort.queryWithUsage(anyString(), any(LLMConfig.class)))
                .thenReturn(LLMQueryResult.withoutUsage(LOW_VIABILITY, SYSTEM_DEFAULT));
        when(executionRepository.save(any(com.quorumtrading.model.entity.ResearchExecution.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private List<String> modelsUsed() {
        ArgumentCaptor<LLMConfig> captor = ArgumentCaptor.forClass(LLMConfig.class);
        verify(llmPort, atLeastOnce()).queryWithUsage(anyString(), captor.capture());
        return captor.getAllValues().stream().map(LLMConfig::model).toList();
    }

    @Test
    @DisplayName("the user's global RESEARCH preference is honoured — it reaches every research agent")
    void userGlobalResearchPreferenceIsHonoured() {
        orchestrator.executeResearch(research, UUID.randomUUID(), "user/picked-model");

        assertThat(modelsUsed())
                .isNotEmpty()
                .allSatisfy(model -> assertThat(model).startsWith("user/picked-model"));
    }

    @Test
    @DisplayName("with no user preference and no resolver, the constructor-injected default is used")
    void systemDefaultComesFromTheInjectedFallback() {
        orchestrator.executeResearch(research, UUID.randomUUID(), null);

        assertThat(modelsUsed())
                .isNotEmpty()
                .allSatisfy(model -> assertThat(model).startsWith(SYSTEM_DEFAULT));
    }

    @Test
    @DisplayName("every research agent gets the same model — so a per-agent research default is dead config")
    void oneModelIsAppliedToEveryResearchAgent() {
        orchestrator.executeResearch(research, UUID.randomUUID(), "user/picked-model");

        // The only variation is the ':online' web-search decoration, never the model itself.
        assertThat(modelsUsed())
                .isNotEmpty()
                .extracting(model -> model.replace(":online", ""))
                .containsOnly("user/picked-model");
    }

    @Test
    @DisplayName("web search is expressed by decorating the chosen model with ':online'")
    void onlineSuffixIsADecorationOnTheChosenModel() {
        orchestrator.executeResearch(research, UUID.randomUUID(), "user/picked-model");

        assertThat(modelsUsed()).contains("user/picked-model:online");
    }
}
