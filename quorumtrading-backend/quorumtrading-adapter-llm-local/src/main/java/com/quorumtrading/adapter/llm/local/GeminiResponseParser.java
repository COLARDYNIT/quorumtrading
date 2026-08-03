package com.quorumtrading.adapter.llm.local;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser for Gemini CLI output format.
 *
 * Supports two formats:
 * 1. Actual gemini CLI with --output-format json:
 *    {"response": "actual LLM response", "stats": {...}}
 *
 * 2. Legacy format (object with text field):
 *    {"response": {"text": "actual LLM response"}, "stats": {...}}
 *
 * This parser checks for direct string response first, then falls back to response.text.
 * Falls back to plain text if JSON parsing fails.
 */
@ApplicationScoped
public class GeminiResponseParser implements ResponseParser {

    private static final Logger log = LoggerFactory.getLogger(GeminiResponseParser.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String parse(String rawOutput) throws CLIExecutionException {
        if (rawOutput == null || rawOutput.trim().isEmpty()) {
            throw new CLIExecutionException("Empty or null output from Gemini CLI");
        }

        try {
            log.debug("Parsing Gemini response: {}", rawOutput);

            // Try to parse as JSON first
            try {
                JsonNode root = objectMapper.readTree(rawOutput);

                // Navigate to response field
                if (root.has("response")) {
                    JsonNode responseNode = root.get("response");
                    if (!responseNode.isNull()) {
                        // Check if response is a direct string (actual gemini CLI format)
                        if (responseNode.isTextual()) {
                            String text = responseNode.asText();
                            log.debug("Extracted Gemini response as direct string: {}", text);
                            return text;
                        }
                        // Fallback: check if response is an object with text field (legacy format)
                        else if (responseNode.has("text")) {
                            JsonNode textNode = responseNode.get("text");
                            if (!textNode.isNull()) {
                                String text = textNode.asText();
                                log.debug("Extracted Gemini text from response.text: {}", text);
                                return text;
                            }
                        }
                    }
                }

                log.debug("JSON parsed but missing expected structure, attempting to extract embedded JSON");

            } catch (Exception jsonParseError) {
                log.debug("Failed to parse as JSON, attempting to extract embedded JSON: {}", jsonParseError.getMessage());
                String extractedJson = extractJsonFromText(rawOutput);
                if (extractedJson != null) {
                    try {
                        JsonNode root = objectMapper.readTree(extractedJson);
                        if (root.has("response")) {
                            JsonNode responseNode = root.get("response");
                            if (!responseNode.isNull()) {
                                // Check if response is a direct string (actual gemini CLI format)
                                if (responseNode.isTextual()) {
                                    String text = responseNode.asText();
                                    log.debug("Extracted response as direct string from embedded JSON: {}", text);
                                    return text;
                                }
                                // Fallback: check if response is an object with text field (legacy format)
                                else if (responseNode.has("text")) {
                                    String text = responseNode.get("text").asText();
                                    log.debug("Extracted text from embedded JSON response.text: {}", text);
                                    return text;
                                }
                            }
                        }
                    } catch (Exception e) {
                        log.debug("Failed to parse extracted JSON: {}", e.getMessage());
                    }
                }
            }

            // Fallback: treat entire output as plain text
            log.warn("Could not parse Gemini output as JSON, using raw output as plain text");
            return rawOutput.trim();

        } catch (Exception e) {
            // Final fallback: return raw output
            log.error("Failed to parse Gemini output, returning raw output: {}", e.getMessage());
            return rawOutput.trim();
        }
    }

    /**
     * Attempts to extract JSON object from within plain text.
     * Handles:
     * 1. Plain JSON: {...}
     * 2. Markdown-wrapped JSON: ```json\n{...}\n``` or `{...}`
     */
    private String extractJsonFromText(String text) {
        // First, try to extract markdown-wrapped JSON (```json...``` or `...`)
        String unwrapped = unwrapMarkdownJson(text);
        if (unwrapped != null) {
            log.debug("Extracted JSON from markdown wrapper");
            return unwrapped;
        }

        // Fallback: Look for JSON object pattern { ... }
        Pattern jsonPattern = Pattern.compile("\\{[^{}]*(?:\\{[^{}]*\\}[^{}]*)*\\}", Pattern.DOTALL);
        Matcher matcher = jsonPattern.matcher(text);

        while (matcher.find()) {
            String candidate = matcher.group();
            try {
                // Verify it's valid JSON
                objectMapper.readTree(candidate);
                log.debug("Found embedded JSON: {}", candidate);
                return candidate;
            } catch (Exception e) {
                // Not valid JSON, continue searching
            }
        }

        log.debug("No embedded JSON found in text");
        return null;
    }

    /**
     * Unwraps JSON from markdown code blocks.
     * Handles patterns like:
     * - ```json\n{...}\n```
     * - `{...}`
     * - Just plain {...} with surrounding text
     */
    private String unwrapMarkdownJson(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }

        // Try to match triple backtick markdown: ```json\n{...}\n```
        Pattern tripleBacktickPattern = Pattern.compile("`{3}(?:json)?\\s*([\\s\\S]*?)`{3}");
        Matcher tripleBacktickMatcher = tripleBacktickPattern.matcher(text);
        if (tripleBacktickMatcher.find()) {
            String content = tripleBacktickMatcher.group(1).trim();
            // Find the JSON object within the markdown
            int openBrace = content.indexOf('{');
            int closeBrace = content.lastIndexOf('}');
            if (openBrace >= 0 && closeBrace > openBrace) {
                String json = content.substring(openBrace, closeBrace + 1).trim();
                try {
                    objectMapper.readTree(json);
                    log.debug("Extracted JSON from triple-backtick markdown");
                    return json;
                } catch (Exception e) {
                    log.debug("Failed to parse content from triple-backtick markdown: {}", e.getMessage());
                }
            }
        }

        // Try to match single backtick markdown: `{...}`
        Pattern singleBacktickPattern = Pattern.compile("`([\\s\\S]*?)`");
        Matcher singleBacktickMatcher = singleBacktickPattern.matcher(text);
        if (singleBacktickMatcher.find()) {
            String content = singleBacktickMatcher.group(1).trim();
            // Check if this looks like JSON
            if (content.startsWith("{") && content.endsWith("}")) {
                try {
                    objectMapper.readTree(content);
                    log.debug("Extracted JSON from single-backtick markdown");
                    return content;
                } catch (Exception e) {
                    log.debug("Failed to parse content from single-backtick markdown: {}", e.getMessage());
                }
            }
        }

        return null;
    }

    @Override
    public String getToolName() {
        return "gemini";
    }
}
