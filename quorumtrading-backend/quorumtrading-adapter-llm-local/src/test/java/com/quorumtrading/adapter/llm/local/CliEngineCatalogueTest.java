package com.quorumtrading.adapter.llm.local;

import com.quorumtrading.application.orchestration.agents.LLMConfig;
import com.quorumtrading.application.orchestration.agents.LLMQueryResult;
import com.quorumtrading.model.valueobject.ModelEffort;
import com.quorumtrading.model.valueobject.ModelListingCapability;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The CLI engine as a first-class engine rather than a best-effort one.
 *
 * <p>Its tools were always model-selectable; this adapter simply never asked them, and hardcoded
 * {@code --model haiku} instead. That gap — an adapter gap, not a tool limitation — is why the
 * six-tier chain was decorative here, why preference validation no-op'd, and why
 * {@code llm_decisions} recorded the string {@code claude}, which is not a model.</p>
 *
 * <p>These assertions are the deliberate inversion of
 * {@code LocalLLMModelIsIgnoredCharacterisationTest}.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("LocalLLMAdapter — the CLI engine has a catalogue and honours the resolved model")
class CliEngineCatalogueTest {

    private LocalLLMAdapter adapter;

    @Mock private CodexExecutor codexExecutor;
    @Mock private ClaudeExecutor claudeExecutor;
    @Mock private QwenExecutor qwenExecutor;
    @Mock private GeminiExecutor geminiExecutor;
    @Mock private OpencodeExecutor opencodeExecutor;
    @Mock private CodexResponseParser codexParser;
    @Mock private ClaudeResponseParser claudeParser;
    @Mock private GeminiResponseParser geminiParser;
    @Mock private QwenResponseParser qwenParser;
    @Mock private DummyExecutor dummyExecutor;
    @Mock private DummyResponseParser dummyParser;
    @Mock private ToolFailureCache failureCache;

    @BeforeEach
    void setUp() {
        adapter = new LocalLLMAdapter();
        adapter.codexExecutor = codexExecutor;
        adapter.claudeExecutor = claudeExecutor;
        adapter.geminiExecutor = geminiExecutor;
        adapter.qwenExecutor = qwenExecutor;
        adapter.opencodeExecutor = opencodeExecutor;
        adapter.codexParser = codexParser;
        adapter.claudeParser = claudeParser;
        adapter.geminiParser = geminiParser;
        adapter.qwenParser = qwenParser;
        adapter.opencodeParser = new OpencodeResponseParser();
        adapter.dummyExecutor = dummyExecutor;
        adapter.dummyParser = dummyParser;
        adapter.defaultTool = "claude";
        adapter.failureCache = failureCache;
        adapter.fallbackTools = List.of("claude");

        when(failureCache.isToolBlacklisted(anyString())).thenReturn(false);
    }

    private void claudeIsInstalledAdvertising(List<String> models) {
        when(claudeExecutor.isInstalled()).thenReturn(true);
        when(claudeExecutor.supportedModels()).thenReturn(models);
        when(claudeExecutor.supportsEffort()).thenReturn(true);
    }

    @Test
    @DisplayName("an installed tool's models become the engine's catalogue")
    void installedToolSuppliesTheCatalogue() {
        claudeIsInstalledAdvertising(List.of("haiku", "sonnet", "opus", "fable"));

        assertThat(adapter.supportsModelListing()).isTrue();
        assertThat(adapter.getAvailableModels())
                .extracting(m -> m.id())
                .containsExactly("fable", "haiku", "opus", "sonnet");
        assertThat(adapter.modelListingCapability()).isEqualTo(ModelListingCapability.ENUMERABLE);
    }

    @Test
    @DisplayName("an uninstalled tool contributes nothing — a chain entry is not evidence it exists")
    void uninstalledToolContributesNothing() {
        when(claudeExecutor.isInstalled()).thenReturn(false);
        when(claudeExecutor.supportedModels()).thenReturn(List.of("haiku", "sonnet"));

        assertThat(adapter.getAvailableModels()).isEmpty();
        assertThat(adapter.supportsModelListing()).isFalse();
    }

    @Test
    @DisplayName("no catalogue is NOT_SUPPORTED, not UNAVAILABLE — a retry will not help")
    void emptyCatalogueIsNotSupportedRatherThanUnavailable() {
        when(claudeExecutor.isInstalled()).thenReturn(true);
        when(claudeExecutor.supportedModels()).thenReturn(List.of());

        assertThat(adapter.modelListingCapability()).isEqualTo(ModelListingCapability.NOT_SUPPORTED);
    }

    @Test
    @DisplayName("the resolved model reaches the tool — selecting sonnet runs sonnet")
    void resolvedModelReachesTheTool() {
        claudeIsInstalledAdvertising(List.of("haiku", "sonnet"));
        when(claudeExecutor.execute(anyString(), anyString(), any())).thenReturn("raw");
        when(claudeParser.parse(anyString())).thenReturn("normalized");

        adapter.query("prompt", new LLMConfig("sonnet", 0.3, 1000));

        ArgumentCaptor<String> model = ArgumentCaptor.forClass(String.class);
        verify(claudeExecutor).execute(anyString(), model.capture(), any());
        assertThat(model.getValue()).isEqualTo("sonnet");
    }

    @Test
    @DisplayName("sonnet at high effort passes the effort through as its own dimension")
    void effortIsPassedThroughSeparately() {
        claudeIsInstalledAdvertising(List.of("sonnet"));
        when(claudeExecutor.execute(anyString(), anyString(), any())).thenReturn("raw");
        when(claudeParser.parse(anyString())).thenReturn("normalized");

        adapter.query("prompt", new LLMConfig("sonnet", 0.3, 1000).withEffort(ModelEffort.HIGH));

        ArgumentCaptor<String> model = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<ModelEffort> effort = ArgumentCaptor.forClass(ModelEffort.class);
        verify(claudeExecutor).execute(anyString(), model.capture(), effort.capture());
        assertThat(model.getValue())
                .as("the model id stays pure — no compound 'sonnet:high'")
                .isEqualTo("sonnet");
        assertThat(effort.getValue()).isEqualTo(ModelEffort.HIGH);
    }

    @Test
    @DisplayName("no effort requested means no effort passed — an engine must not invent a level")
    void noEffortMeansNull() {
        claudeIsInstalledAdvertising(List.of("sonnet"));
        when(claudeExecutor.execute(anyString(), anyString(), any())).thenReturn("raw");
        when(claudeParser.parse(anyString())).thenReturn("normalized");

        adapter.query("prompt", new LLMConfig("sonnet", 0.3, 1000));

        ArgumentCaptor<ModelEffort> effort = ArgumentCaptor.forClass(ModelEffort.class);
        verify(claudeExecutor).execute(anyString(), anyString(), effort.capture());
        assertThat(effort.getValue()).isNull();
    }

    @Test
    @DisplayName("llm_decisions records the MODEL now, not the tool name")
    void recordsTheModelNotTheToolName() {
        claudeIsInstalledAdvertising(List.of("sonnet"));
        when(claudeExecutor.execute(anyString(), any(), any())).thenReturn("raw");
        when(claudeParser.parse(anyString())).thenReturn("normalized");

        LLMQueryResult result = adapter.queryWithUsage("prompt", new LLMConfig("sonnet", 0.3, 1000));

        assertThat(result.model()).isEqualTo("sonnet");
    }

    @Test
    @DisplayName("effort is part of what ran, so it appears in the record")
    void recordedModelIncludesEffort() {
        claudeIsInstalledAdvertising(List.of("sonnet"));
        when(claudeExecutor.execute(anyString(), any(), any())).thenReturn("raw");
        when(claudeParser.parse(anyString())).thenReturn("normalized");

        LLMQueryResult result = adapter.queryWithUsage("prompt",
                new LLMConfig("sonnet", 0.3, 1000).withEffort(ModelEffort.HIGH));

        assertThat(result.model()).isEqualTo("sonnet (effort=high)");
    }

    @Test
    @DisplayName("with no model resolved the tool name is the most specific true thing we know")
    void unresolvedModelFallsBackToTheToolName() {
        claudeIsInstalledAdvertising(List.of("sonnet"));
        when(claudeExecutor.execute(anyString(), any(), any())).thenReturn("raw");
        when(claudeParser.parse(anyString())).thenReturn("normalized");

        LLMQueryResult result = adapter.queryWithUsage("prompt", new LLMConfig(null, 0.3, 1000));

        assertThat(result.model()).isEqualTo("claude");
    }

    /**
     * opencode is the one CLI engine that reports usage, and the adapter must pass it through
     * rather than flattening it to the ZERO that means "unknown" everywhere else.
     */
    @Test
    @DisplayName("token counts an engine really reported are recorded, not flattened to zero")
    void reportedTokenCountsSurvive() {
        adapter.defaultTool = "opencode";
        adapter.fallbackTools = List.of("opencode");
        when(opencodeExecutor.isInstalled()).thenReturn(true);
        when(opencodeExecutor.execute(anyString(), any(), any())).thenReturn("""
                {"type":"text","part":{"type":"text","text":"HOLD"}}
                {"type":"step_finish","part":{"type":"step-finish","tokens":{"total":13822,\
                "input":13800,"output":12,"reasoning":10},"cost":0}}
                """);

        LLMQueryResult result = adapter.queryWithUsage("prompt",
                new LLMConfig("openrouter/x/y", 0.3, 1000));

        assertThat(result.content()).isEqualTo("HOLD");
        assertThat(result.usage().promptTokens()).isEqualTo(13800);
        assertThat(result.usage().completionTokens()).isEqualTo(22);
        assertThat(result.usage().totalTokens()).isEqualTo(13822);
    }

    /** An engine that reports nothing still records ZERO, which downstream reads as "unknown". */
    @Test
    @DisplayName("an engine that reports no usage still records the unknown marker")
    void unreportedUsageStaysZero() {
        claudeIsInstalledAdvertising(List.of("sonnet"));
        when(claudeExecutor.execute(anyString(), any(), any())).thenReturn("raw");
        when(claudeParser.parse(anyString())).thenReturn("normalized");

        LLMQueryResult result = adapter.queryWithUsage("prompt", new LLMConfig("sonnet", 0.3, 1000));

        assertThat(result.usage().totalTokens()).isZero();
    }
}
