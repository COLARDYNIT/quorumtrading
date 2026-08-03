package com.quorumtrading.adapter.llm.local;

import com.quorumtrading.application.orchestration.agents.LLMConfig;
import com.quorumtrading.application.orchestration.agents.LLMQueryResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * The zero-marginal-cost guarantee for the local CLI engine, stated as a test rather than as a
 * comment.
 *
 * <p>The CLI adapter shells out to a tool the user already runs and pays for separately ({@code
 * claude}, {@code qwen}). Nothing is metered per call, so nothing may be charged per call. Together
 * with {@code LocalHttpZeroCostContractTest} this closes the pair of local engines: whatever a
 * self-hosted install reports as spend, it did not come from either of them.</p>
 *
 * <p>The tool is stubbed rather than executed, so this test never shells out and never needs a CLI
 * installed.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("LocalLLMAdapter — CLI-served inference is always $0")
class LocalCliZeroCostContractTest {

    private LocalLLMAdapter adapter;

    @Mock private CodexExecutor codexExecutor;
    @Mock private ClaudeExecutor claudeExecutor;
    @Mock private QwenExecutor qwenExecutor;
    @Mock private GeminiExecutor geminiExecutor;
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
        adapter.codexParser = codexParser;
        adapter.claudeParser = claudeParser;
        adapter.geminiParser = geminiParser;
        adapter.qwenParser = qwenParser;
        adapter.dummyExecutor = dummyExecutor;
        adapter.dummyParser = dummyParser;
        adapter.defaultTool = "qwen";
        adapter.failureCache = failureCache;
        adapter.fallbackTools = List.of("qwen", "claude", "codex", "gemini");
    }

    private LLMQueryResult queryViaQwen() {
        when(failureCache.isToolBlacklisted(anyString())).thenReturn(false);
        when(qwenExecutor.isInstalled()).thenReturn(true);
        // Three-arg: the adapter now hands the resolved model (and any effort) to the tool, rather
        // than the old hardcoded flag. Cost is unaffected — a local CLI bills nothing either way.
        when(qwenExecutor.execute(anyString(), any(), any())).thenReturn("raw qwen output");
        when(qwenParser.parse(anyString())).thenReturn("normalized answer");

        return adapter.queryWithUsage("analyse AAPL", new LLMConfig("technical", 0.7, 1000));
    }

    @Test
    @DisplayName("a CLI-served call records no cost")
    void cliCallCostsNothing() {
        LLMQueryResult result = queryViaQwen();

        assertThat(result.usage().rawCost())
            .as("a locally executed CLI tool bills nothing per call")
            .isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.usage().billedCost())
            .as("and the markup must not lift $0 to the minimum billable cost")
            .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("the answer still comes back — free is not degraded")
    void cliCallStillReturnsItsAnswer() {
        assertThat(queryViaQwen().content()).isEqualTo("normalized answer");
    }
}
