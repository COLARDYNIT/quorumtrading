package com.quorumtrading.integration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quorumtrading.application.chat.ChatToolRegistry;
import com.quorumtrading.application.chat.FollowUpSuggestionService;
import com.quorumtrading.application.chat.SystemPromptBuilder;
import com.quorumtrading.application.orchestration.agents.LLMConfig;
import com.quorumtrading.application.port.out.ConversationalLLMPort;
import com.quorumtrading.model.valueobject.ChatCompletionResult;
import com.quorumtrading.model.valueobject.ChatMessage;
import com.quorumtrading.model.valueobject.ToolCall;
import com.quorumtrading.model.valueobject.ToolDefinition;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prompt engineering test harness for validating and comparing system prompt variants
 * across multiple LLM models.
 * <p>
 * This test is NOT run during normal CI — it requires an OPENROUTER_API_KEY and calls
 * real LLM APIs. Run manually:
 * <pre>
 * # Existing cheap tests only (hero flow excluded)
 * ./mvnw test -pl quorumtrading-bootstrap \
 *   -Dtest=ChatPromptEngineeringTest \
 *   -Dgroups=prompt-engineering -DexcludedGroups=""
 *
 * # All tests including expensive hero flow
 * ./mvnw test -pl quorumtrading-bootstrap \
 *   -Dtest=ChatPromptEngineeringTest \
 *   -DexcludedGroups=""
 *
 * # Only expensive hero flow tests
 * ./mvnw test -pl quorumtrading-bootstrap \
 *   -Dtest=ChatPromptEngineeringTest \
 *   -Dgroups=prompt-engineering-expensive -DexcludedGroups=""
 * </pre>
 * <p>
 * <b>Prompt improvement workflow:</b>
 * <ol>
 *   <li>Run with only v0-production. See which scenarios fail on which models.</li>
 *   <li>Read the failing LLM responses (logged to stdout). Diagnose the root cause.</li>
 *   <li>Add a v1-... entry to CHAT_PROMPT_VARIANTS with improved wording.</li>
 *   <li>Re-run. Both v0 and v1 execute in the same run — summary shows which is better.</li>
 *   <li>Iterate with v2, v3, etc. Promote the winner into production code.</li>
 * </ol>
 */
@QuarkusTest
@Tag("prompt-engineering")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ChatPromptEngineeringTest {

    @Inject
    ConversationalLLMPort llmPort;

    private final ObjectMapper objectMapper = new ObjectMapper();

    static final int RUNS_PER_SCENARIO = 3;
    static final int DELAY_MS = 500;
    static final List<String> MODELS = List.of(
        "xiaomi/mimo-v2-flash",
        "qwen/qwen-turbo",
        "google/gemini-2.5-flash",
        "mistralai/mistral-small-3.2-24b-instruct"
    );

    // --- Claude models (expensive, tagged separately) ---
    static final List<String> CLAUDE_MODELS = List.of(
        "anthropic/claude-opus-5.5",
        "anthropic/claude-sonnet-5.5",
        "anthropic/claude-haiku-5.5"
    );

    // --- Hero flow constants ---
    static final int HERO_FLOW_RUNS = 1; // expensive, 1 run per model
    static final int HERO_FLOW_MAX_STEPS = 10;
    static final Set<String> HERO_FLOW_EXPECTED_TOOLS = Set.of(
        "backfill_price_data", "get_indicators", "create_strategy",
        "create_simulation", "start_simulation", "query_portfolio_snapshots"
    );

    // --- Mock tool results for hero flow agentic loop ---
    static final Map<String, String> HERO_TOOL_RESULTS = Map.of(
        "backfill_price_data", """
            {"jobId":"job-001","status":"COMPLETED","ticker":"BTC","assetType":"CRYPTO",\
            "startDate":"2025-11-09","endDate":"2026-02-07","recordsLoaded":90}""",
        "get_indicators", """
            {"ticker":"BTC","investmentType":"CRYPTO","indicators":{\
            "RSI_14":42.5,"SMA_50":96250.0,"SMA_200":88100.0,\
            "MACD":{"value":150.3,"signal":120.1,"histogram":30.2},\
            "bollingerBands":{"upper":102000.0,"middle":96250.0,"lower":90500.0}},\
            "warnings":[]}""",
        "create_strategy", """
            {"id":"strat-001","name":"BTC Momentum Rider","status":"ACTIVE",\
            "createdAt":"2026-02-07T12:00:00Z"}""",
        "create_simulation", """
            {"id":"sim-001","name":"BTC Momentum Backtest","status":"PENDING",\
            "strategyId":"strat-001","symbol":"BTC","investmentType":"CRYPTO",\
            "startDate":"2025-11-09","endDate":"2026-02-07","initialCash":"10000.00"}""",
        "start_simulation", """
            {"id":"sim-001","status":"SIMULATING","message":"Simulation started successfully"}""",
        "query_portfolio_snapshots", """
            {"simulationId":"sim-001","snapshots":[{"timestamp":"2025-12-01T00:00:00Z",\
            "totalValue":10450.00,"cashAvailable":2100.00,"positions":[{"ticker":"BTC",\
            "quantity":0.085,"currentPrice":98200.00}]},{"timestamp":"2026-02-07T00:00:00Z",\
            "totalValue":11340.00,"cashAvailable":1800.00,"positions":[{"ticker":"BTC",\
            "quantity":0.095,"currentPrice":100420.00}]}]}"""
    );

    // --- Prompt variant maps ---
    // Production prompt is always v0. Add variants here to compare.
    // After testing, promote the winner into the production source file.
    //
    // History:
    //   v1-action-mandate → PROMOTED to production (Feb 2026)
    //     Fixed gemini-flash create_strategy (0% → 100%) and get_indicators_btc (0% → 100%)
    //     No regressions on qwen-turbo or mistral-small.
    //   v2-tool-first-rule → REJECTED: regressed mistral-small create_strategy (0/3) and qwen-turbo simulation_query (1/3)
    static final Map<String, String> CHAT_PROMPT_VARIANTS = new LinkedHashMap<>() {{
        put("v0-production", SystemPromptBuilder.BASE_SYSTEM_PROMPT);
        // Add future variants here to compare against v0-production.
        // Example: put("v3-shorter-prompt", "...");
    }};

    // Archived: v2-tool-first-rule scored 87% overall (vs v1's 100%). Kept for reference.
    static final String ARCHIVED_V2_TOOL_FIRST_RULE = """
            You are the QuorumTrading assistant — a concise, terminal-style AI for managing trading strategies, simulations, and market research.

            ## RULE #1: Tool Calls Over Text
            If the user's message matches ANY available tool, you MUST call that tool.
            - Queries ("what are my...") → call the list/get tool
            - Actions ("create a...", "delete...", "start...", "run...") → call the create/delete/start/execute tool IMMEDIATELY with correct parameters
            - NEVER describe what you will do — just call the tool
            - ONLY respond with text when NO tool matches (greetings, capability questions, things you cannot do)

            The User Context section below is a snapshot that may be outdated — it is NOT the source of truth. \
            NEVER say "no strategies", "no simulations", or "no research" without calling the relevant tool first.

            ## Capabilities
            - **Strategies:** list, create, view content, analyze performance metrics
            - **Simulations:** create backtests, start/pause, monitor progress, analyze portfolio & consensus decisions
            - **Market research:** create configs, execute, review agent decisions and viability scores
            - **Market data:** check indicators (RSI-14, SMA-50/200, MACD, Bollinger Bands), backfill price history
            - **Broker accounts:** view connected accounts and balances

            ## Response Rules
            - Be concise and direct. This is a terminal interface.
            - **Always use markdown hyperlinks** when mentioning strategies, simulations, benchmarks, or market research: \
            [Name](/strategies/{id}), [Name](/simulations/{id}), [Name](/benchmarks/{id}), [Name](/market-research/{id})
            - Format numbers: percentages with 2 decimals, money with $ prefix
            - Use bullet points for lists, tables for comparisons
            - Summarize large tool results into key insights

            ## Disambiguation
            When the user refers to an entity by type (e.g. "my strategy", "the simulation", "my research") \
            and there is only one of that type in their context, use it directly — do not ask "which one?". \
            Only ask for clarification when there are genuinely multiple candidates.

            ## Tool Tips
            - For portfolio analysis, call evaluate_portfolio_value first to get fresh market prices
            - When asked about strategy performance, use get_strategy_performance
            - When a user asks to run/execute research, use execute_market_research with the research ID
            - For creating simulations, you need a strategyId — check the user's strategies first

            ## CANNOT Do (Never Pretend Otherwise)
            Do NOT claim to perform any action that isn't backed by an available tool. Specifically, you CANNOT:
            - Set up alerts, notifications, or reminders
            - Schedule periodic monitoring or automated check-ins
            - Send emails, SMS, or push notifications
            - Access external APIs beyond what the tools provide
            - Remember things between sessions (sessions are stateless)
            - Execute trades in real-time (simulations are backtests or paper trading)

            If a user asks for something you cannot do, say so clearly and suggest alternatives.
            """;

    // History:
    //   v1-strict-label → PROMOTED to production (Feb 2026)
    //     Fixed gemini-flash followup_valid_json (33% → 100%) by adding explicit "max 20 characters" rule.
    //     No regressions on qwen-turbo or mistral-small.
    //   v2-few-shot → Also scored 100%, but v1-strict-label is closer to original structure.
    static final Map<String, String> FOLLOWUP_PROMPT_VARIANTS = new LinkedHashMap<>() {{
        put("v0-production", FollowUpSuggestionService.SYSTEM_PROMPT);
        // Add future variants here to compare against v0-production.
    }};

    // --- Results tracking ---
    // Key format: "variant|model|scenario", value: [passes, total]
    static final ConcurrentHashMap<String, int[]> results = new ConcurrentHashMap<>();

    // Valid tool names from production registry
    static final Set<String> VALID_TOOL_NAMES;
    static {
        Set<String> names = new HashSet<>();
        for (ToolDefinition tool : ChatToolRegistry.allTools()) {
            names.add(tool.name());
        }
        VALID_TOOL_NAMES = Collections.unmodifiableSet(names);
    }

    // ========================================================================
    // Chat Prompt — Tool Selection Scenarios
    // ========================================================================

    @ParameterizedTest(name = "[{0}] {1} → list_strategies")
    @MethodSource("chatVariantModelProvider")
    void shouldCallListStrategies(String variant, String model) {
        runToolSelectionScenario(variant, model, "list_strategies",
            "What are my strategies?",
            result -> {
                assertThat(result.hasToolCalls()).as("should produce tool calls").isTrue();
                assertThat(result.toolCalls()).anyMatch(tc -> tc.functionName().equals("list_strategies"));
            });
    }

    @ParameterizedTest(name = "[{0}] {1} → get_indicators (BTC)")
    @MethodSource("chatVariantModelProvider")
    void shouldCallGetIndicatorsBtc(String variant, String model) {
        runToolSelectionScenario(variant, model, "get_indicators_btc",
            "Show me BTC indicators",
            result -> {
                assertThat(result.hasToolCalls()).as("should produce tool calls").isTrue();
                assertThat(result.toolCalls()).anyMatch(tc -> {
                    if (!tc.functionName().equals("get_indicators")) return false;
                    String args = tc.arguments().toUpperCase();
                    return args.contains("BTC");
                });
            });
    }

    @ParameterizedTest(name = "[{0}] {1} → list/get simulation")
    @MethodSource("chatVariantModelProvider")
    void shouldCallSimulationTool(String variant, String model) {
        runToolSelectionScenario(variant, model, "simulation_query",
            "How is my simulation doing?",
            result -> {
                assertThat(result.hasToolCalls()).as("should produce tool calls").isTrue();
                assertThat(result.toolCalls()).anyMatch(tc ->
                    tc.functionName().equals("list_simulations") ||
                    tc.functionName().equals("get_simulation"));
            });
    }

    @ParameterizedTest(name = "[{0}] {1} → create_strategy")
    @MethodSource("chatVariantModelProvider")
    void shouldCallCreateStrategy(String variant, String model) {
        runToolSelectionScenario(variant, model, "create_strategy",
            "Create a BTC strategy that buys when RSI is below 30 and sells when RSI is above 70",
            result -> {
                assertThat(result.hasToolCalls()).as("should produce tool calls").isTrue();
                assertThat(result.toolCalls()).anyMatch(tc -> tc.functionName().equals("create_strategy"));
            });
    }

    // ========================================================================
    // Chat Prompt — Text Response Scenarios (should NOT call a tool)
    // ========================================================================

    @ParameterizedTest(name = "[{0}] {1} → text (hello)")
    @MethodSource("chatVariantModelProvider")
    void shouldRespondWithTextForGreeting(String variant, String model) {
        runToolSelectionScenario(variant, model, "text_hello",
            "Hello",
            result -> {
                assertThat(result.content()).as("should have text content").isNotBlank();
                assertThat(result.hasToolCalls()).as("should NOT call any tool").isFalse();
            });
    }

    @ParameterizedTest(name = "[{0}] {1} → text (capabilities)")
    @MethodSource("chatVariantModelProvider")
    void shouldRespondWithTextForCapabilities(String variant, String model) {
        runToolSelectionScenario(variant, model, "text_capabilities",
            "What can you do?",
            result -> {
                assertThat(result.content()).as("should have text content").isNotBlank();
                assertThat(result.hasToolCalls()).as("should NOT call any tool").isFalse();
            });
    }

    // ========================================================================
    // Chat Prompt — Safety: hallucinated tool names
    // ========================================================================

    @ParameterizedTest(name = "[{0}] {1} → valid tool names only")
    @MethodSource("chatVariantModelProvider")
    void shouldOnlyCallValidToolNames(String variant, String model) {
        // Use a prompt that might tempt the model to hallucinate a tool
        runToolSelectionScenario(variant, model, "no_hallucinated_tools",
            "Send me an email alert when BTC drops below 50k",
            result -> {
                if (result.hasToolCalls()) {
                    for (ToolCall tc : result.toolCalls()) {
                        assertThat(VALID_TOOL_NAMES)
                            .as("tool '%s' must exist in ChatToolRegistry", tc.functionName())
                            .contains(tc.functionName());
                    }
                }
                // It's also acceptable to respond with text saying it can't do this
            });
    }

    // ========================================================================
    // Follow-up Suggestion Scenarios
    // ========================================================================

    @ParameterizedTest(name = "[{0}] {1} → valid follow-up JSON")
    @MethodSource("followupVariantModelProvider")
    void shouldGenerateValidFollowUpSuggestions(String variant, String model) {
        String scenario = "followup_valid_json";
        String prompt = FOLLOWUP_PROMPT_VARIANTS.get(variant);
        int passes = 0;

        for (int run = 1; run <= RUNS_PER_SCENARIO; run++) {
            try {
                List<ChatMessage> messages = List.of(
                    ChatMessage.system(prompt),
                    ChatMessage.user(buildFollowUpUserPrompt())
                );

                LLMConfig config = new LLMConfig(model, 0.3, 256, null, null, true);
                ChatCompletionResult result = llmPort.chatCompletion(messages, Collections.emptyList(), config);

                String content = result.content();
                assertThat(content).as("should have content").isNotBlank();

                // Extract JSON and parse
                String json = extractJson(content);
                List<Map<String, String>> suggestions = objectMapper.readValue(
                    json, new TypeReference<>() {});

                assertThat(suggestions).as("should have exactly 3 suggestions").hasSize(3);
                for (Map<String, String> s : suggestions) {
                    assertThat(s).containsKeys("label", "message");
                    assertThat(s.get("label")).as("label should be short").hasSizeLessThanOrEqualTo(30);
                    assertThat(s.get("message")).as("message should not be empty").isNotBlank();
                }

                passes++;
                logResult(true, variant, model, scenario, run, "Valid JSON with 3 suggestions");
            } catch (Exception | AssertionError e) {
                logResult(false, variant, model, scenario, run, e.getMessage());
            }

            sleepBetweenRuns();
        }

        recordResult(variant, model, scenario, passes);
    }

    @ParameterizedTest(name = "[{0}] {1} → user-phrased messages")
    @MethodSource("followupVariantModelProvider")
    void shouldPhraseMessagesAsUserRequests(String variant, String model) {
        String scenario = "followup_user_phrased";
        String prompt = FOLLOWUP_PROMPT_VARIANTS.get(variant);
        int passes = 0;

        for (int run = 1; run <= RUNS_PER_SCENARIO; run++) {
            try {
                List<ChatMessage> messages = List.of(
                    ChatMessage.system(prompt),
                    ChatMessage.user(buildFollowUpUserPrompt())
                );

                LLMConfig config = new LLMConfig(model, 0.3, 256, null, null, true);
                ChatCompletionResult result = llmPort.chatCompletion(messages, Collections.emptyList(), config);

                String json = extractJson(result.content());
                List<Map<String, String>> suggestions = objectMapper.readValue(
                    json, new TypeReference<>() {});

                boolean allUserPhrased = true;
                for (Map<String, String> s : suggestions) {
                    String msg = s.getOrDefault("message", "").toLowerCase();
                    // Check for AI-phrased patterns
                    if (msg.startsWith("would you like") || msg.startsWith("shall i") ||
                        msg.startsWith("do you want") || msg.startsWith("i can")) {
                        allUserPhrased = false;
                        logResult(false, variant, model, scenario, run,
                            "AI-phrased message: \"" + s.get("message") + "\"");
                        break;
                    }
                }

                if (allUserPhrased) {
                    passes++;
                    logResult(true, variant, model, scenario, run, "All messages user-phrased");
                }
            } catch (Exception | AssertionError e) {
                logResult(false, variant, model, scenario, run, e.getMessage());
            }

            sleepBetweenRuns();
        }

        recordResult(variant, model, scenario, passes);
    }

    // ========================================================================
    // Hero Flow — Multi-turn agentic conversation test
    // ========================================================================
    // Tests whether models can reproduce the hero section's multi-tool flow:
    //   backfill_price_data → get_indicators → create_strategy →
    //   create_simulation + start_simulation → query_portfolio_snapshots
    //
    // Pass criteria: model calls ≥ 3 of the 6 hero tools across the agentic loop.

    @Tag("prompt-engineering-expensive")
    @ParameterizedTest(name = "hero-flow [{0}] {1} → ≥3 hero tools")
    @MethodSource("heroFlowClaudeModelProvider")
    void shouldCompleteHeroFlowClaude(String variant, String model) {
        runHeroFlowScenario(variant, model);
    }

    @Tag("prompt-engineering")
    @ParameterizedTest(name = "hero-flow [{0}] {1} → ≥3 hero tools")
    @MethodSource("heroFlowCheapModelProvider")
    void shouldCompleteHeroFlowCheap(String variant, String model) {
        runHeroFlowScenario(variant, model);
    }

    private void runHeroFlowScenario(String variant, String model) {
        String scenario = "hero_flow_multi_turn";
        String systemPrompt = CHAT_PROMPT_VARIANTS.get(variant);
        List<ToolDefinition> tools = ChatToolRegistry.allTools();
        int passes = 0;

        for (int run = 1; run <= HERO_FLOW_RUNS; run++) {
            try {
                Set<String> calledTools = new LinkedHashSet<>();
                List<ChatMessage> messages = new ArrayList<>();
                messages.add(ChatMessage.system(systemPrompt));
                messages.add(ChatMessage.user(
                    "Backtest a BTC momentum strategy over the last 90 days"));

                for (int step = 0; step < HERO_FLOW_MAX_STEPS; step++) {
                    LLMConfig config = new LLMConfig(model, 0.1, 1024, null, null, true);
                    ChatCompletionResult result = llmPort.chatCompletion(messages, tools, config);

                    if (!result.hasToolCalls()) {
                        // Model produced a text response — end of agentic loop
                        System.out.printf("  [step %d] Text response: %s%n",
                            step + 1, truncate(result.content(), 120));
                        messages.add(ChatMessage.assistant(result.content()));
                        break;
                    }

                    // Process each tool call
                    messages.add(ChatMessage.assistantWithToolCalls(result.toolCalls()));
                    for (ToolCall tc : result.toolCalls()) {
                        calledTools.add(tc.functionName());
                        String mockResult = getMockToolResult(tc.functionName());
                        System.out.printf("  [step %d] %s(%s) → %s%n",
                            step + 1, tc.functionName(),
                            truncate(tc.arguments(), 60),
                            truncate(mockResult, 80));
                        messages.add(ChatMessage.toolResult(tc.id(), mockResult));
                    }

                    sleepBetweenRuns();
                }

                // Count how many hero tools were called
                long heroToolsHit = calledTools.stream()
                    .filter(HERO_FLOW_EXPECTED_TOOLS::contains)
                    .count();

                System.out.printf("  Tools called: %s (%d/%d hero tools)%n",
                    calledTools, heroToolsHit, HERO_FLOW_EXPECTED_TOOLS.size());

                assertThat(heroToolsHit)
                    .as("should call ≥3 of 6 hero tools, got: %s", calledTools)
                    .isGreaterThanOrEqualTo(3);

                passes++;
                logResult(true, variant, model, scenario, run,
                    String.format("%d/%d hero tools: %s", heroToolsHit,
                        HERO_FLOW_EXPECTED_TOOLS.size(), calledTools));
            } catch (Exception | AssertionError e) {
                logResult(false, variant, model, scenario, run, e.getMessage());
            }

            sleepBetweenRuns();
        }

        recordResult(variant, model, scenario, passes, HERO_FLOW_RUNS);
    }

    /**
     * Returns a mock tool result for the given tool name.
     * For unknown tools, returns a generic success JSON.
     */
    private String getMockToolResult(String toolName) {
        return HERO_TOOL_RESULTS.getOrDefault(toolName,
            "{\"status\":\"success\",\"message\":\"Operation completed\"}");
    }

    // ========================================================================
    // Parameter providers
    // ========================================================================

    static Stream<Arguments> chatVariantModelProvider() {
        return CHAT_PROMPT_VARIANTS.keySet().stream()
            .flatMap(v -> MODELS.stream().map(m -> Arguments.of(v, m)));
    }

    static Stream<Arguments> followupVariantModelProvider() {
        return FOLLOWUP_PROMPT_VARIANTS.keySet().stream()
            .flatMap(v -> MODELS.stream().map(m -> Arguments.of(v, m)));
    }

    static Stream<Arguments> heroFlowClaudeModelProvider() {
        return CHAT_PROMPT_VARIANTS.keySet().stream()
            .flatMap(v -> CLAUDE_MODELS.stream().map(m -> Arguments.of(v, m)));
    }

    static Stream<Arguments> heroFlowCheapModelProvider() {
        return CHAT_PROMPT_VARIANTS.keySet().stream()
            .flatMap(v -> MODELS.stream().map(m -> Arguments.of(v, m)));
    }

    // ========================================================================
    // Results summary
    // ========================================================================

    @AfterAll
    void printResults() {
        System.out.println("\n" + "=".repeat(100));
        System.out.println("=== PROMPT ENGINEERING RESULTS ===");
        System.out.println("=".repeat(100));
        System.out.printf("%-25s %-45s %-25s %s%n", "Variant", "Model", "Scenario", "Pass Rate");
        System.out.println("-".repeat(100));

        // Group by variant for summary
        Map<String, int[]> variantTotals = new LinkedHashMap<>();

        results.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(entry -> {
                String[] parts = entry.getKey().split("\\|");
                String variant = parts[0];
                String model = parts[1];
                String scenario = parts[2];
                int[] counts = entry.getValue();
                int pass = counts[0];
                int total = counts[1];
                String pct = total > 0 ? String.format("%d/%d (%d%%)", pass, total, pass * 100 / total) : "N/A";
                String status = (pass == total && total > 0) ? "[OK]" : "[FAIL]";

                System.out.printf("%-25s %-45s %-25s %s %s%n", variant, model, scenario, pct, status);

                variantTotals.computeIfAbsent(variant, k -> new int[2]);
                variantTotals.get(variant)[0] += pass;
                variantTotals.get(variant)[1] += total;
            });

        if (variantTotals.size() > 1) {
            System.out.println("\n" + "=".repeat(100));
            System.out.println("=== VARIANT COMPARISON (overall pass rate) ===");
            System.out.println("=".repeat(100));

            int bestPasses = -1;
            String bestVariant = null;

            for (Map.Entry<String, int[]> entry : variantTotals.entrySet()) {
                int pass = entry.getValue()[0];
                int total = entry.getValue()[1];
                if (total > 0 && pass > bestPasses) {
                    bestPasses = pass;
                    bestVariant = entry.getKey();
                }
            }

            for (Map.Entry<String, int[]> entry : variantTotals.entrySet()) {
                int pass = entry.getValue()[0];
                int total = entry.getValue()[1];
                String pct = total > 0 ? String.format("%d%% (%d/%d)", pass * 100 / total, pass, total) : "N/A";
                String marker = entry.getKey().equals(bestVariant) ? "  <-- WINNER" : "";
                System.out.printf("%-25s %s%s%n", entry.getKey(), pct, marker);
            }
        }

        System.out.println("=".repeat(100) + "\n");
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    private void runToolSelectionScenario(String variant, String model, String scenario,
                                          String userMessage, ResultAssertion assertion) {
        String prompt = CHAT_PROMPT_VARIANTS.get(variant);
        List<ToolDefinition> tools = ChatToolRegistry.allTools();
        int passes = 0;

        for (int run = 1; run <= RUNS_PER_SCENARIO; run++) {
            try {
                List<ChatMessage> messages = List.of(
                    ChatMessage.system(prompt),
                    ChatMessage.user(userMessage)
                );

                LLMConfig config = new LLMConfig(model, 0.1, 512, null, null, true);
                ChatCompletionResult result = llmPort.chatCompletion(messages, tools, config);

                assertion.assertResult(result);
                passes++;
                logResult(true, variant, model, scenario, run, summarizeResult(result));
            } catch (Exception | AssertionError e) {
                logResult(false, variant, model, scenario, run, e.getMessage());
            }

            sleepBetweenRuns();
        }

        recordResult(variant, model, scenario, passes);
    }

    private void recordResult(String variant, String model, String scenario, int passes) {
        recordResult(variant, model, scenario, passes, RUNS_PER_SCENARIO);
    }

    private void recordResult(String variant, String model, String scenario, int passes, int total) {
        String key = variant + "|" + model + "|" + scenario;
        results.put(key, new int[]{passes, total});
    }

    private void logResult(boolean pass, String variant, String model, String scenario, int run, String detail) {
        String status = pass ? "PASS" : "FAIL";
        System.out.printf("[%s] %s | %s | %s (run %d)%n  %s%n",
            status, variant, model, scenario, run,
            detail != null ? detail : "");
    }

    private String summarizeResult(ChatCompletionResult result) {
        if (result.hasToolCalls()) {
            StringBuilder sb = new StringBuilder("Tool calls: ");
            for (ToolCall tc : result.toolCalls()) {
                sb.append(tc.functionName()).append("(").append(truncate(tc.arguments(), 60)).append(") ");
            }
            return sb.toString();
        } else if (result.content() != null) {
            return "Text: " + truncate(result.content(), 100);
        }
        return "Empty response";
    }

    private String buildFollowUpUserPrompt() {
        return """
            ## Recent Conversation
            user: What are my strategies?

            ## Tools Called
            - list_strategies

            ## Tool Results (summarized)
            ### list_strategies
            [{"id":"abc-123","name":"BTC Mean Reversion","status":"ACTIVE"},{"id":"def-456","name":"ETH Momentum","status":"ACTIVE"}]

            ## Assistant's Final Response (IMPORTANT: base suggestions on what was offered here)
            You have 2 strategies:
            - **BTC Mean Reversion** — ACTIVE
            - **ETH Momentum** — ACTIVE

            Would you like to see the performance of either strategy, or create a backtest simulation?

            Generate 3 relevant follow-up suggestions as JSON.
            """;
    }

    private String extractJson(String content) {
        if (content == null) return "[]";
        String trimmed = content.trim();
        if (trimmed.startsWith("```json")) trimmed = trimmed.substring(7);
        else if (trimmed.startsWith("```")) trimmed = trimmed.substring(3);
        if (trimmed.endsWith("```")) trimmed = trimmed.substring(0, trimmed.length() - 3);
        trimmed = trimmed.trim();
        int start = trimmed.indexOf('[');
        int end = trimmed.lastIndexOf(']');
        if (start >= 0 && end > start) return trimmed.substring(start, end + 1);
        return trimmed;
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    private void sleepBetweenRuns() {
        try {
            Thread.sleep(DELAY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @FunctionalInterface
    interface ResultAssertion {
        void assertResult(ChatCompletionResult result);
    }
}
