package com.quorumtrading.application.orchestration.agents;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.quorumtrading.model.entity.LLMDecision;
import com.quorumtrading.model.entity.LlmParseFailure;
import com.quorumtrading.model.enums.RecommendationType;
import com.quorumtrading.model.orchestration.AgentContext;
import com.quorumtrading.model.valueobject.Percentage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Shared parsing utilities for all standard agent LLM responses.
 * Handles markdown code-block stripping and JSON → LLMDecision conversion.
 */
public final class AgentResponseParser {

    private static final Logger logger = LoggerFactory.getLogger(AgentResponseParser.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private AgentResponseParser() {
    }

    /**
     * Build a JSON action string using Jackson for safe serialization.
     * Prevents JSON injection from ticker symbols, reasoning text, or any other field.
     */
    public static String buildActionJson(String action, String symbol, double quantity, double confidence, String reasoning) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("action", action);
        node.put("symbol", symbol);
        node.put("quantity", quantity);
        node.put("confidence", confidence);
        node.put("reasoning", reasoning);
        return node.toString();
    }

    /**
     * Shorthand for building a SKIP action JSON with quantity 0.
     */
    public static String buildSkipActionJson(String symbol, String reasoning) {
        return buildActionJson("SKIP", symbol, 0.0, 0.0, reasoning);
    }

    /**
     * Recover the complete top-level fields of a JSON object whose tail was lost.
     *
     * <p>A model that runs into its output-token limit stops mid-object. Everything it emitted
     * before that point is still its own literal JSON, so a decision rebuilt from those fields
     * is the model's own answer — not an inference about what it meant. Measured example, from
     * {@code llm_parse_failures} on the default self-hosted engine: a 916-character response
     * carrying a complete {@code action}, {@code symbol}, {@code confidence} and {@code reasoning}
     * ended in a dangling {@code ,"} and was discarded whole, costing the entire consensus round.
     *
     * <p>Nothing is inferred or defaulted here. Only fields whose value arrived intact are
     * returned; a field cut mid-value is simply absent, and the caller decides whether what
     * survived is enough.
     *
     * @return the fields that arrived complete, or {@code null} if the text is not a truncated
     *         JSON object at all (prose, for instance, which is deliberately not salvaged)
     */
    private static ObjectNode salvageTruncatedObject(String text) {
        if (text == null || text.isEmpty() || text.charAt(0) != '{') {
            return null;
        }
        ObjectNode recovered = objectMapper.createObjectNode();
        try (JsonParser parser = objectMapper.getFactory().createParser(text)) {
            if (parser.nextToken() != JsonToken.START_OBJECT) {
                return null;
            }
            while (parser.nextToken() == JsonToken.FIELD_NAME) {
                String field = parser.currentName();
                JsonToken value = parser.nextToken();
                if (value == null) {
                    break;
                }
                if (value.isStructStart()) {
                    // A nested value is not needed for a decision; skipChildren throws if it was
                    // the thing that got cut, which ends the walk exactly where it should.
                    parser.skipChildren();
                } else if (value != JsonToken.VALUE_NULL) {
                    recovered.put(field, parser.getValueAsString());
                }
            }
        } catch (IOException e) {
            // Hitting the truncation point is the expected way out of this loop, not an error.
            logger.debug("Salvage stopped at the truncation point: {}", e.getMessage());
        }
        return recovered.isEmpty() ? null : recovered;
    }

    /**
     * Rebuild a decision from a truncated response, or return {@code null} if what survived is
     * not enough to stand as the model's answer.
     *
     * <p>{@code action} and {@code confidence} are both required. Confidence gates the HOLD
     * abstain rule, so substituting a default would fabricate a vote weight — the same error as
     * reporting an uncomputable Sharpe ratio as 0.00.
     *
     * <p>Prose is deliberately not salvaged. Of the four measured failures three were prose, and
     * a keyword match cannot distinguish "the recommendation is to buy" from "I would not
     * recommend a buy". A wrong vote is worse than a skipped one.
     */
    private static LLMDecision recoverFromTruncation(
            String cleanedResponse, AgentContext context, String prompt,
            String modelName, String roleName, long executionTime) {
        ObjectNode salvaged = salvageTruncatedObject(cleanedResponse);
        if (salvaged == null || !salvaged.has("action") || !salvaged.has("confidence")) {
            return null;
        }
        try {
            LLMDecision decision = buildDecisionFromJson(
                    salvaged, salvaged.toString(), context, prompt, modelName, roleName, executionTime);
            logger.warn("[sim={}] {} agent response for {} was truncated at {} chars, but its"
                    + " recommendation and confidence arrived intact — recovered {} instead of"
                    + " discarding the round. The engine is likely hitting an output-token limit.",
                    context.getSimulationId().value().toString().substring(0, 8),
                    roleName, context.getTicker(), cleanedResponse.length(),
                    decision.getRecommendation());
            return decision;
        } catch (Exception e) {
            logger.debug("Truncated {} response could not be rebuilt: {}", roleName, e.getMessage());
            return null;
        }
    }

    /**
     * Populate a decision from a parsed response object.
     *
     * <p>Shared by the normal path and the truncation-recovery path on purpose: two copies of
     * this mapping would drift, and a recovered decision that differs in shape from a normal one
     * is a bug waiting to be found downstream.
     */
    private static LLMDecision buildDecisionFromJson(
            JsonNode json, String rawActionJson, AgentContext context, String prompt,
            String modelName, String roleName, long executionTime) {
        LLMDecision decision = new LLMDecision(
                context.getSimulationId(),
                context.getTimestamp(),
                UUID.randomUUID().toString(),
                roleName,
                modelName
        );

        decision.setPromptUsed(prompt);
        decision.recordRecommendation(RecommendationType.valueOf(json.get("action").asText().toUpperCase()));
        decision.recordConfidenceScore(new Percentage(new BigDecimal(json.get("confidence").asText())));
        // Reasoning is narrative: it does not affect the vote, so its absence must not cost one.
        decision.recordReasoning(json.has("reasoning") && !json.get("reasoning").isNull()
                ? json.get("reasoning").asText()
                : "(the model's reasoning did not survive its output limit; the recommendation"
                    + " and confidence above are its own)");
        decision.recordRecommendedAction(rawActionJson);
        decision.recordExecutionTime((int) executionTime);

        // Extract and validate ticker from LLM response
        String ticker = null;
        if (json.has("symbol") && json.get("symbol") != null && !json.get("symbol").isNull()) {
            ticker = json.get("symbol").asText();
        }

        // If LLM didn't provide a valid ticker, fall back to the context ticker
        if (ticker == null || ticker.isEmpty() || ticker.equals("null")) {
            logger.warn("LLM returned null/empty symbol, using ticker from context: {}", context.getTicker());
            ticker = context.getTicker();
        }

        decision.recordTicker(ticker);
        return decision;
    }

    /**
     * Strips markdown code block formatting from LLM responses.
     * Handles formats like:
     * <pre>
     * ```json\n{...}\n```
     * ```\n{...}\n```
     * </pre>
     * Plain JSON is returned as-is (trimmed).
     */
    public static String stripMarkdownCodeBlocks(String response) {
        if (response == null) {
            return null;
        }

        String trimmed = response.trim();

        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            if (firstNewline != -1) {
                int lastCodeFence = trimmed.lastIndexOf("```");
                if (lastCodeFence > firstNewline) {
                    trimmed = trimmed.substring(firstNewline + 1, lastCodeFence).trim();
                    logger.debug("Stripped markdown code blocks, new length: {} chars", trimmed.length());
                }
            }
        }

        return trimmed;
    }

    /**
     * Strips JavaScript-style comments ({@code //} and {@code /* * /}) from a JSON string,
     * preserving any comment-like sequences inside quoted strings. Also removes trailing
     * commas before {@code }} or {@code ]}.
     *
     * <p>LLMs sometimes return JSON with comments that Jackson's default parser rejects.
     */
    public static String stripJsonComments(String json) {
        if (json == null) {
            return null;
        }

        StringBuilder result = new StringBuilder(json.length());
        int i = 0;
        boolean inString = false;

        while (i < json.length()) {
            char c = json.charAt(i);

            if (inString) {
                result.append(c);
                if (c == '\\' && i + 1 < json.length()) {
                    // escaped character — copy next char unconditionally
                    result.append(json.charAt(i + 1));
                    i += 2;
                    continue;
                }
                if (c == '"') {
                    inString = false;
                }
                i++;
                continue;
            }

            // outside string
            if (c == '"') {
                inString = true;
                result.append(c);
                i++;
            } else if (c == '/' && i + 1 < json.length() && json.charAt(i + 1) == '/') {
                // single-line comment — skip to end of line
                i += 2;
                while (i < json.length() && json.charAt(i) != '\n') {
                    i++;
                }
            } else if (c == '/' && i + 1 < json.length() && json.charAt(i + 1) == '*') {
                // block comment — skip to closing */
                i += 2;
                while (i + 1 < json.length() && !(json.charAt(i) == '*' && json.charAt(i + 1) == '/')) {
                    i++;
                }
                if (i + 1 < json.length()) {
                    i += 2; // skip closing */
                }
            } else {
                result.append(c);
                i++;
            }
        }

        // Strip trailing commas before } or ]
        return result.toString().replaceAll(",\\s*([}\\]])", "$1");
    }

    /**
     * Parses a standard agent JSON response into an {@link LLMDecision}.
     * On parse failure, returns a SKIP decision with zero confidence.
     *
     * @param response      raw LLM response text (may contain markdown code blocks)
     * @param executionTime LLM call duration in milliseconds
     * @param context       agent context (supplies simulationId, timestamp, ticker)
     * @param prompt        the prompt that was sent to the LLM (stored on the decision for audit)
     * @param modelName     resolved model name returned by the LLM adapter
     * @param roleName      agent role name (e.g. "TECHNICAL", "FUNDAMENTAL")
     * @return a fully-populated LLMDecision
     */
    public static LLMDecision parseStandardResponse(String response, long executionTime,
                                                     AgentContext context, String prompt,
                                                     String modelName, String roleName) {
        return parseStandardResponse(response, executionTime, context, prompt, modelName, roleName, null);
    }

    /**
     * Parses a standard agent JSON response into an {@link LLMDecision}.
     * On parse failure, returns a SKIP decision with zero confidence and invokes the failure consumer.
     *
     * @param response        raw LLM response text (may contain markdown code blocks)
     * @param executionTime   LLM call duration in milliseconds
     * @param context         agent context (supplies simulationId, timestamp, ticker)
     * @param prompt          the prompt that was sent to the LLM (stored on the decision for audit)
     * @param modelName       resolved model name returned by the LLM adapter
     * @param roleName        agent role name (e.g. "TECHNICAL", "FUNDAMENTAL")
     * @param onParseFailure  optional consumer invoked with failure details when parsing fails
     * @return a fully-populated LLMDecision
     */
    public static LLMDecision parseStandardResponse(String response, long executionTime,
                                                     AgentContext context, String prompt,
                                                     String modelName, String roleName,
                                                     Consumer<LlmParseFailure> onParseFailure) {
        String cleanedResponse = stripJsonComments(stripMarkdownCodeBlocks(response));

        // Fallback: if response has preamble text before JSON, extract the JSON object
        if (cleanedResponse != null && !cleanedResponse.isEmpty()
                && cleanedResponse.charAt(0) != '{') {
            int jsonStart = cleanedResponse.indexOf('{');
            int jsonEnd = cleanedResponse.lastIndexOf('}');
            if (jsonStart >= 0 && jsonEnd > jsonStart) {
                logger.debug("Extracting JSON from mixed response (preamble at chars 0-{})", jsonStart);
                cleanedResponse = cleanedResponse.substring(jsonStart, jsonEnd + 1);
            }
        }

        try {
            JsonNode json = objectMapper.readTree(cleanedResponse);
            LLMDecision decision = buildDecisionFromJson(
                    json, cleanedResponse, context, prompt, modelName, roleName, executionTime);

            return decision;
        } catch (Exception e) {
            // Before writing the vote off, see whether the model actually delivered one. A
            // truncated object still carries its own literal values for every field that arrived,
            // and discarding those costs the whole consensus round — the measured cause of runs
            // that produce no decisions at all.
            LLMDecision recovered = recoverFromTruncation(
                    cleanedResponse, context, prompt, modelName, roleName, executionTime);
            if (recovered != null) {
                return recovered;
            }

            // Record the parse failure for troubleshooting
            if (onParseFailure != null) {
                try {
                    onParseFailure.accept(LlmParseFailure.capture(
                            context.getSimulationId(),
                            context.getTimestamp(),
                            context.getTicker(),
                            roleName,
                            modelName,
                            (int) executionTime,
                            prompt,
                            response,
                            e));
                } catch (Exception failureEx) {
                    logger.warn("Failed to record parse failure for {} agent: {}", roleName, failureEx.getMessage());
                }
            }

            LLMDecision decision = new LLMDecision(
                    context.getSimulationId(),
                    context.getTimestamp(),
                    UUID.randomUUID().toString(),
                    roleName,
                    "fallback"
            );

            decision.setPromptUsed(prompt);
            decision.recordRecommendation(RecommendationType.SKIP);
            decision.recordConfidenceScore(new Percentage(new BigDecimal("0.0")));
            decision.recordReasoning("Agent failed to provide analysis - response could not be parsed");
            decision.recordRecommendedAction(
                    buildSkipActionJson(context.getTicker(), roleName + " agent failed to provide analysis"));
            decision.recordTicker(context.getTicker());
            decision.recordExecutionTime((int) executionTime);
            logger.warn("{} agent parse failure for ticker {}: {}", roleName, context.getTicker(), e.getMessage());
            return decision;
        }
    }
}
