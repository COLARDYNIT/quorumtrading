package com.quorumtrading.adapter.llm.local;

import com.quorumtrading.application.orchestration.agents.LLMConfig;
import com.quorumtrading.application.orchestration.agents.LLMQueryException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Tests for LocalLLMAdapter.
 */
@ExtendWith(MockitoExtension.class)
class LocalLLMAdapterTest {

    private LocalLLMAdapter adapter;

    @Mock
    private CodexExecutor codexExecutor;

    @Mock
    private ClaudeExecutor claudeExecutor;

    @Mock
    private QwenExecutor qwenExecutor;

    @Mock
    private GeminiExecutor geminiExecutor;

    @Mock
    private CodexResponseParser codexParser;

    @Mock
    private ClaudeResponseParser claudeParser;

    @Mock
    private GeminiResponseParser geminiParser;

    @Mock
    private QwenResponseParser qwenParser;

    @Mock
    private DummyExecutor dummyExecutor;

    @Mock
    private DummyResponseParser dummyParser;

    @Mock
    private ToolFailureCache failureCache;

    @BeforeEach
    void setUp() {
        // Create adapter with mocked dependencies
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
        adapter.defaultTool = "qwen"; // Default configuration value
        adapter.failureCache = failureCache; // Mock the failure cache
        adapter.fallbackTools = List.of("qwen", "claude", "codex", "gemini"); // Default fallback chain
    }

    @Test
    void testGetProviderNameReturnsLocalLLM() {
        assertThat(adapter.getProviderName()).isEqualTo("local-llm");
    }

    @Test
    void testQueryUsesConfiguredToolForAgent() {
        // Simulate configuration: quorumtrading.agents.technical.llm-tool=codex
        LLMConfig config = new LLMConfig("technical", 0.7, 1000);

        when(failureCache.isToolBlacklisted(anyString())).thenReturn(false);
        when(codexExecutor.isInstalled()).thenReturn(true);
        when(codexExecutor.execute(anyString(), any(), any())).thenReturn("raw response from codex");
        when(codexParser.parse(anyString())).thenReturn("normalized response");

        String result = adapter.query("test prompt", config);

        assertThat(result).isEqualTo("normalized response");
        verify(codexExecutor).execute(eq("test prompt"), any(), any());
        verify(codexParser).parse("raw response from codex");
    }

    @Test
    void testQueryFallsBackToDefaultWhenToolNotConfigured() {
        // Unknown agent should fallback to default (qwen)
        LLMConfig config = new LLMConfig("unknown-agent", 0.7, 1000);

        when(failureCache.isToolBlacklisted(anyString())).thenReturn(false);
        when(qwenExecutor.isInstalled()).thenReturn(true);
        when(qwenExecutor.execute(anyString(), any(), any())).thenReturn("raw response from qwen");
        when(qwenParser.parse(anyString())).thenReturn("normalized response");

        String result = adapter.query("test prompt", config);

        assertThat(result).isEqualTo("normalized response");
        verify(qwenExecutor).execute(eq("test prompt"), any(), any());
        verify(qwenParser).parse("raw response from qwen");
    }

    @Test
    void testQueryFallsBackToDefaultWhenToolNotInstalled() {
        // Simulate Codex not installed, should fallback to qwen in chain
        LLMConfig config = new LLMConfig("technical", 0.7, 1000);

        when(failureCache.isToolBlacklisted(anyString())).thenReturn(false);
        when(codexExecutor.isInstalled()).thenReturn(false);
        when(qwenExecutor.isInstalled()).thenReturn(true);
        when(qwenExecutor.execute(anyString(), any(), any())).thenReturn("raw response from qwen");
        when(qwenParser.parse(anyString())).thenReturn("normalized response");

        String result = adapter.query("test prompt", config);

        assertThat(result).isEqualTo("normalized response");
        verify(codexExecutor, never()).execute(anyString(), any(), any());
        verify(qwenExecutor).execute(eq("test prompt"), any(), any());
        verify(qwenParser).parse("raw response from qwen");
    }

    @Test
    void testQueryExecutesAndParsesResponse() {
        // Test full flow
        LLMConfig config = new LLMConfig("fundamental", 0.7, 1000);

        when(failureCache.isToolBlacklisted(anyString())).thenReturn(false);
        when(geminiExecutor.isInstalled()).thenReturn(true);
        when(geminiExecutor.execute(eq("analyze this"), any(), any())).thenReturn("{\"response\":\"analysis result\"}");
        when(geminiParser.parse("{\"response\":\"analysis result\"}")).thenReturn("analysis result");

        String result = adapter.query("analyze this", config);

        assertThat(result).isEqualTo("analysis result");
        verify(geminiExecutor).execute(eq("analyze this"), any(), any());
        verify(geminiParser).parse("{\"response\":\"analysis result\"}");
    }

    @Test
    void testQueryThrowsExceptionWhenDefaultToolFails() {
        LLMConfig config = new LLMConfig("technical", 0.7, 1000);

        // Codex not installed, fallback to qwen which is also not installed
        when(failureCache.isToolBlacklisted(anyString())).thenReturn(false);
        when(codexExecutor.isInstalled()).thenReturn(false);
        when(qwenExecutor.isInstalled()).thenReturn(false);
        when(claudeExecutor.isInstalled()).thenReturn(false);
        when(geminiExecutor.isInstalled()).thenReturn(false);

        assertThatThrownBy(() -> adapter.query("test prompt", config))
            .isInstanceOf(LLMQueryException.class)
            .hasMessageContaining("All LLM tools exhausted");
    }

    @Test
    void testQueryHandlesNullConfig() {
        assertThatThrownBy(() -> adapter.query("test prompt", null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testQueryLogsToolSelection() {
        // This test verifies that tool selection logic works
        LLMConfig config = new LLMConfig("sentiment", 0.7, 1000);

        when(failureCache.isToolBlacklisted(anyString())).thenReturn(false);
        when(claudeExecutor.isInstalled()).thenReturn(true);
        when(claudeExecutor.execute(anyString(), any(), any())).thenReturn("raw response");
        when(claudeParser.parse(anyString())).thenReturn("normalized response");

        String result = adapter.query("test prompt", config);

        assertThat(result).isNotNull();
        // Verify the correct tool was selected based on config
        verify(claudeExecutor).isInstalled();
        verify(claudeExecutor).execute(eq("test prompt"), any(), any());
    }

    @Test
    void testIsAvailableReturnsTrueWhenAtLeastOneToolInstalled() {
        when(qwenExecutor.isInstalled()).thenReturn(false);
        when(claudeExecutor.isInstalled()).thenReturn(true);
        // Configured chain is [qwen, claude, codex, gemini]; claude short-circuits before codex/gemini.

        assertThat(adapter.isAvailable()).isTrue();
    }

    @Test
    void testIsAvailableReturnsFalseWhenNoToolsInstalled() {
        when(qwenExecutor.isInstalled()).thenReturn(false);
        when(claudeExecutor.isInstalled()).thenReturn(false);
        when(codexExecutor.isInstalled()).thenReturn(false);
        when(geminiExecutor.isInstalled()).thenReturn(false);

        assertThat(adapter.isAvailable()).isFalse();
    }

    @Test
    void testIsAvailableIgnoresAlwaysInstalledDummyExecutor() {
        // Regression: DummyExecutor.isInstalled() returns true unconditionally. It must NOT make
        // LOCAL look available when no real CLI is in the configured chain (the Docker-image case,
        // where a phantom "available" would then fail at the first consensus call).
        when(qwenExecutor.isInstalled()).thenReturn(false);
        when(claudeExecutor.isInstalled()).thenReturn(false);
        when(codexExecutor.isInstalled()).thenReturn(false);
        when(geminiExecutor.isInstalled()).thenReturn(false);

        assertThat(adapter.isAvailable()).isFalse();
        verify(dummyExecutor, org.mockito.Mockito.never()).isInstalled();
    }

    @Test
    void testFilterBlacklistedTools_noBlacklist() {
        List<String> chain = List.of("qwen", "claude", "codex");

        when(failureCache.isToolBlacklisted("qwen")).thenReturn(false);
        when(failureCache.isToolBlacklisted("claude")).thenReturn(false);
        when(failureCache.isToolBlacklisted("codex")).thenReturn(false);

        List<String> filtered = adapter.filterBlacklistedTools(chain);

        assertThat(filtered).hasSize(3);
        assertThat(filtered).containsExactly("qwen", "claude", "codex");
    }

    @Test
    void testFilterBlacklistedTools_withBlacklist() {
        List<String> chain = List.of("qwen", "claude", "codex");

        when(failureCache.isToolBlacklisted("qwen")).thenReturn(false);
        when(failureCache.isToolBlacklisted("claude")).thenReturn(true); // Claude is blacklisted
        when(failureCache.isToolBlacklisted("codex")).thenReturn(false);

        List<String> filtered = adapter.filterBlacklistedTools(chain);

        assertThat(filtered).hasSize(2);
        assertThat(filtered).containsExactly("qwen", "codex");
        assertThat(filtered).doesNotContain("claude");
    }

    @Test
    void testQuery_firstToolSucceeds_noFallback() {
        LLMConfig config = new LLMConfig("technical", 0.7, 1000);

        // Mock codex (first tool for technical) to succeed
        when(failureCache.isToolBlacklisted(anyString())).thenReturn(false);
        when(codexExecutor.isInstalled()).thenReturn(true);
        when(codexExecutor.execute(anyString(), any(), any())).thenReturn("raw response from codex");
        when(codexParser.parse(anyString())).thenReturn("normalized response");

        String result = adapter.query("test prompt", config);

        assertThat(result).isEqualTo("normalized response");
        verify(codexExecutor).execute(eq("test prompt"), any(), any());
        verify(codexParser).parse("raw response from codex");
    }

    @Test
    void testQuery_allToolsFail_throwsException() {
        LLMConfig config = new LLMConfig("technical", 0.7, 1000);

        // Mock all executors in chain to fail
        when(failureCache.isToolBlacklisted(anyString())).thenReturn(false);
        when(codexExecutor.isInstalled()).thenReturn(false);
        when(qwenExecutor.isInstalled()).thenReturn(false);
        when(claudeExecutor.isInstalled()).thenReturn(false);
        when(geminiExecutor.isInstalled()).thenReturn(false);

        assertThatThrownBy(() -> adapter.query("test prompt", config))
            .isInstanceOf(LLMQueryException.class)
            .hasMessageContaining("All LLM tools exhausted");
    }

    @Test
    void testQuery_firstToolFailsSecondSucceeds() {
        LLMConfig config = new LLMConfig("technical", 0.7, 1000);

        // Mock codex to fail, qwen to succeed
        when(failureCache.isToolBlacklisted(anyString())).thenReturn(false);
        when(codexExecutor.isInstalled()).thenReturn(false);
        when(qwenExecutor.isInstalled()).thenReturn(true);
        when(qwenExecutor.execute(anyString(), any(), any())).thenReturn("raw response from qwen");
        when(qwenParser.parse(anyString())).thenReturn("normalized response");

        String result = adapter.query("test prompt", config);

        assertThat(result).isEqualTo("normalized response");
        verify(qwenExecutor).execute(eq("test prompt"), any(), any());
        verify(qwenParser).parse("raw response from qwen");
        // Verify third tool was not tried
        verify(claudeExecutor, never()).execute(anyString(), any(), any());
        verify(geminiExecutor, never()).execute(anyString(), any(), any());
    }

    @Test
    void testQuery_allToolsBlacklisted_throwsException() {
        LLMConfig config = new LLMConfig("technical", 0.7, 1000);

        // Mock all tools as blacklisted
        when(failureCache.isToolBlacklisted(anyString())).thenReturn(true);

        assertThatThrownBy(() -> adapter.query("test prompt", config))
            .isInstanceOf(LLMQueryException.class)
            .hasMessageContaining("All tools blacklisted");
    }

    @Test
    void testQuery_recordsFailureInCache() {
        LLMConfig config = new LLMConfig("technical", 0.7, 1000);

        // Mock codex to fail with exception
        when(failureCache.isToolBlacklisted(anyString())).thenReturn(false);
        when(codexExecutor.isInstalled()).thenReturn(true);
        when(codexExecutor.execute(anyString(), any(), any())).thenThrow(new CLIExecutionException("codex error"));
        // Mock qwen to succeed
        when(qwenExecutor.isInstalled()).thenReturn(true);
        when(qwenExecutor.execute(anyString(), any(), any())).thenReturn("raw response");
        when(qwenParser.parse(anyString())).thenReturn("normalized response");

        String result = adapter.query("test prompt", config);

        assertThat(result).isEqualTo("normalized response");
        // Verify failure was recorded
        verify(failureCache).recordFailure("codex");
    }
}
