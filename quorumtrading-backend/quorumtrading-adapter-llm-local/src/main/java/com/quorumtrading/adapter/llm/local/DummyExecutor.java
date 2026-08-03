package com.quorumtrading.adapter.llm.local;

import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Dummy executor for E2E testing that returns canned responses from resource files.
 * <p>
 * This executor does not call any external CLI tools. Instead, it reads pre-defined
 * markdown responses from src/main/resources/dummy-responses/ based on prompt patterns.
 * </p>
 * <p>
 * This is useful for:
 * <ul>
 *   <li>E2E tests that need deterministic LLM responses</li>
 *   <li>Local development without API costs</li>
 *   <li>Demo environments</li>
 * </ul>
 * </p>
 *
 * @author QuorumTrading
 * @since 1.0.0
 */
@ApplicationScoped
public class DummyExecutor implements CLIExecutor {

    private static final Logger LOG = Logger.getLogger(DummyExecutor.class);
    private static final String TOOL_NAME = "dummy";
    private static final String RESOURCES_PATH = "dummy-responses/";

    /**
     * Marker appended to every agent-decision prompt by
     * {@code AgentResponseFormat.JSON_RESPONSE_INSTRUCTION} (application layer).
     * Its presence unambiguously identifies a Technical/Fundamental/Sentiment/Orchestrator
     * decision prompt, as opposed to research or free-form prompts.
     */
    private static final String JSON_DECISION_MARKER = "Respond in JSON format ONLY";

    /**
     * Holdings line every agent prompt embeds, e.g.
     * "- Current Holdings of BTC: 0 units (no position)".
     */
    private static final Pattern HOLDINGS_PATTERN = Pattern.compile("Current Holdings of (\\w+):");
    private static final String HELD_POSITION_MARKER = "units (avg cost";

    @Override
    public String execute(String prompt) throws CLIExecutionException {
        if (prompt == null || prompt.isEmpty()) {
            throw new CLIExecutionException("Prompt cannot be null or empty");
        }

        // Agent-decision prompts get a deterministic JSON decision: BUY when the portfolio is
        // flat, HOLD once a position exists. Checked BEFORE the keyword table, because a
        // strategy body may contain any of the keywords below (e.g. "backtest", "RSI").
        if (prompt.contains(JSON_DECISION_MARKER)) {
            String decision = buildDecisionJson(prompt);
            LOG.infof("DummyExecutor: agent-decision prompt detected, returning canned decision %s", decision);
            return decision;
        }

        String responseFile = selectResponseFile(prompt);
        LOG.infof("DummyExecutor: prompt matched '%s', loading response", responseFile);

        return loadResource(RESOURCES_PATH + responseFile);
    }

    /**
     * Builds a deterministic, stateless decision JSON from the prompt's portfolio state.
     * <p>
     * "One BUY then HOLD" emerges from the portfolio itself: the first evaluation runs with a
     * flat portfolio (BUY, full cash since quantity is omitted); every later evaluation sees the
     * open position and returns HOLD. No JVM state is kept between calls.
     * </p>
     */
    private String buildDecisionJson(String prompt) {
        Matcher matcher = HOLDINGS_PATTERN.matcher(prompt);
        String ticker = matcher.find() ? matcher.group(1) : "BTC";
        // Held positions render as "<qty> units (avg cost: $.../unit)"; a flat portfolio renders
        // "0 units (no position)". Default (no holdings line at all) is treated as flat → BUY.
        boolean flat = !prompt.contains(HELD_POSITION_MARKER);
        String action = flat ? "BUY" : "HOLD";
        String reasoning = flat
            ? "e2e canned decision: no position held, open a full-cash position"
            : "e2e canned decision: position already held, keep holding";
        return "{\"action\":\"" + action + "\",\"symbol\":\"" + ticker
            + "\",\"confidence\":0.9,\"reasoning\":\"" + reasoning + "\"}";
    }

    /**
     * Selects the appropriate response file based on prompt content.
     * <p>
     * Pattern matching is case-insensitive and checks for keywords
     * commonly associated with different agent types.
     * </p>
     *
     * @param prompt The input prompt to analyze
     * @return The filename of the appropriate response markdown
     */
    private String selectResponseFile(String prompt) {
        String lower = prompt.toLowerCase();

        // Strategy discovery patterns
        if (lower.contains("market research") || lower.contains("find strategy")
                || lower.contains("discover") || lower.contains("analyze market")) {
            return "strategy-discovery.md";
        }

        // Backtest analysis patterns
        if (lower.contains("backtest") || lower.contains("performance analysis")) {
            return "backtest-analysis.md";
        }

        // Technical agent patterns
        if (lower.contains("technical") || lower.contains("rsi") || lower.contains("macd")
                || lower.contains("indicator") || lower.contains("chart")) {
            return "technical-agent.md";
        }

        // Sentiment agent patterns
        if (lower.contains("sentiment") || lower.contains("news") || lower.contains("social")
                || lower.contains("twitter") || lower.contains("reddit")) {
            return "sentiment-agent.md";
        }

        // Fundamental agent patterns
        if (lower.contains("fundamental") || lower.contains("valuation") || lower.contains("earnings")
                || lower.contains("revenue") || lower.contains("balance sheet")) {
            return "fundamental-agent.md";
        }

        return "default-response.md";
    }

    /**
     * Loads a markdown resource file from the classpath.
     *
     * @param path The resource path relative to the classpath root
     * @return The file contents as a string
     * @throws CLIExecutionException if the resource cannot be found or read
     */
    private String loadResource(String path) throws CLIExecutionException {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(path)) {
            if (is == null) {
                throw new CLIExecutionException("Resource not found: " + path);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new CLIExecutionException("Failed to load resource: " + path, e);
        }
    }

    @Override
    public String getToolName() {
        return TOOL_NAME;
    }

    @Override
    public boolean isInstalled() {
        // Dummy executor is always "installed" since it has no external dependencies
        return true;
    }
}
