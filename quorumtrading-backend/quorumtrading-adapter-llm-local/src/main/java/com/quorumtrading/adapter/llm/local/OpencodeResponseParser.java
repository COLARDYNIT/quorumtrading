package com.quorumtrading.adapter.llm.local;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quorumtrading.model.valueobject.LLMUsage;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Parses the newline-delimited JSON event stream from {@code opencode run --format json}.
 *
 * <p>Schema below is transcribed from real output captured on opencode 1.18.10 (2026-08-03), not
 * from documentation:</p>
 *
 * <pre>
 * {"type":"step_start","part":{...}}
 * {"type":"text","part":{"type":"text","text":"PONG",...}}
 * {"type":"step_finish","part":{"type":"step-finish",
 *      "tokens":{"total":13822,"input":13800,"output":12,"reasoning":10,
 *                "cache":{"write":0,"read":0}},"cost":0}}
 * </pre>
 *
 * <p>and on failure, with exit code 1 and an <b>empty stderr</b>:</p>
 *
 * <pre>
 * {"type":"error","error":{"name":"UnknownError","data":{"message":"...","ref":"err_20193095"}}}
 * </pre>
 *
 * <h2>Honesty rules</h2>
 *
 * <p>The same three that govern the claude bridge, for the same reason — a trading decision derived
 * from an invented answer is worse than no decision:</p>
 *
 * <ol>
 *   <li><b>An error is an error.</b> An {@code error} event throws, and never becomes response
 *       text. Empty output throws too: an LLM that said nothing has not said "no".</li>
 *   <li><b>Token counts only when genuinely present.</b> {@link #parseUsage} returns empty when the
 *       stream carried no {@code step_finish}, so the adapter records "unknown" rather than a
 *       measured-looking zero. Nothing is ever estimated.</li>
 *   <li><b>The answering model is not invented.</b> Verified: the run stream contains no model
 *       field at all — zero occurrences of the string in a full successful run. So
 *       {@link LLMUsage#model()} is left null rather than back-filled with the id that was
 *       <em>requested</em>. Echoing the request would launder a guess into a stored fact. The only
 *       place opencode records what actually answered is the persisted session
 *       ({@code opencode export <sessionID>} reports {@code providerID} + {@code modelID}), which
 *       costs a second process spawn per call and is deliberately not done here.</li>
 * </ol>
 */
@ApplicationScoped
public class OpencodeResponseParser implements ResponseParser {

    private static final Logger LOG = Logger.getLogger(OpencodeResponseParser.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String TOOL_NAME = "opencode";

    @Override
    public String parse(String rawOutput) throws CLIExecutionException {
        if (rawOutput == null || rawOutput.isBlank()) {
            // Not an empty answer — no answer. opencode produces zero bytes when it is killed
            // before it starts streaming, which is exactly what an unclosed stdin used to cause.
            throw new CLIExecutionException("Opencode returned no output");
        }

        StringBuilder text = new StringBuilder();
        for (String line : rawOutput.split("\\R")) {
            JsonNode event = readEvent(line);
            if (event == null) {
                continue;
            }
            failOnErrorEvent(event);
            appendText(event, text);
        }

        String answer = text.toString().trim();
        if (answer.isEmpty()) {
            throw new CLIExecutionException(
                    "Opencode produced no assistant text; refusing to synthesize a response");
        }
        return answer;
    }

    /**
     * Token counts, when opencode genuinely reported them.
     *
     * <p>{@code total} is taken as reported rather than recomputed: opencode counts reasoning
     * tokens in the total but not in {@code input} or {@code output} (13,800 + 12 + 10 = 13,822 in
     * the captured run), so a recomputed total would silently drop them.</p>
     */
    @Override
    public Optional<LLMUsage> parseUsage(String rawOutput) {
        if (rawOutput == null || rawOutput.isBlank()) {
            return Optional.empty();
        }

        Optional<LLMUsage> usage = Optional.empty();
        for (String line : rawOutput.split("\\R")) {
            JsonNode event = readEvent(line);
            if (event == null || !"step_finish".equals(event.path("type").asText())) {
                continue;
            }
            JsonNode part = event.path("part");
            JsonNode tokens = part.path("tokens");
            if (tokens.isMissingNode() || !tokens.has("input")) {
                continue;
            }
            int input = tokens.path("input").asInt();
            // Reasoning tokens are generated tokens; billing them as prompt would be wrong.
            int output = tokens.path("output").asInt() + tokens.path("reasoning").asInt();
            int total = tokens.path("total").asInt(input + output);
            BigDecimal cost = part.has("cost")
                    ? BigDecimal.valueOf(part.path("cost").asDouble())
                    : BigDecimal.ZERO;
            // model stays null: opencode never names the model that answered. See class javadoc.
            usage = Optional.of(new LLMUsage(null, input, output, total, cost));
        }
        return usage;
    }

    /** Parse one line, or null when it is not JSON — opencode may interleave non-event chatter. */
    private JsonNode readEvent(String line) {
        String trimmed = line.trim();
        if (trimmed.isEmpty() || trimmed.charAt(0) != '{') {
            return null;
        }
        try {
            return MAPPER.readTree(trimmed);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            LOG.debugf("Ignoring non-JSON line from opencode: %s", e.getMessage());
            return null;
        }
    }

    private void failOnErrorEvent(JsonNode event) throws CLIExecutionException {
        if (!"error".equals(event.path("type").asText())) {
            return;
        }
        JsonNode error = event.path("error");
        String name = error.path("name").asText("UnknownError");
        String message = error.path("data").path("message").asText("");
        String ref = error.path("data").path("ref").asText("");
        LOG.warnf("Opencode reported an error event: name=%s, message=%s, ref=%s", name, message, ref);
        throw new CLIExecutionException(String.format(
                "Opencode reported an error: %s%s%s",
                name,
                message.isEmpty() ? "" : " - " + message,
                ref.isEmpty() ? "" : " (ref " + ref + ")"));
    }

    private void appendText(JsonNode event, StringBuilder text) {
        if (!"text".equals(event.path("type").asText())) {
            return;
        }
        String chunk = event.path("part").path("text").asText("");
        if (!chunk.isEmpty()) {
            text.append(chunk);
        }
    }

    @Override
    public String getToolName() {
        return TOOL_NAME;
    }
}
