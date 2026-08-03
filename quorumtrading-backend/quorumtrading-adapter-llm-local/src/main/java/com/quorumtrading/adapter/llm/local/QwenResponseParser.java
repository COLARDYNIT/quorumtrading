package com.quorumtrading.adapter.llm.local;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser for Claude CLI output format.
 * Claude outputs JSON wrapped in markdown code blocks:
 * ```json
 * {"content": "actual LLM response"}
 * ```
 * This parser strips markdown blocks and extracts the "content" field.
 */
@ApplicationScoped
public class QwenResponseParser implements ResponseParser {

    private static final Logger log = LoggerFactory.getLogger(QwenResponseParser.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    // Pattern to match markdown code blocks with optional language specifier
    private static final Pattern CODE_BLOCK_PATTERN = Pattern.compile(
        "```(?:json)?\\s*\\n?([\\s\\S]*?)```",
        Pattern.MULTILINE
    );

    @Override
    public String parse(String rawOutput) throws CLIExecutionException {
        if (rawOutput == null || rawOutput.trim().isEmpty()) {
            throw new CLIExecutionException("Empty or null output from LLM");
        }

        try {
            log.debug("Parsing Claude response: {}", rawOutput);

            // Strip markdown code blocks if present
            String processedOutput = stripMarkdownCodeBlocks(rawOutput);

            // Try to parse as JSON first
            try {
                JsonNode root = objectMapper.readTree(processedOutput);

                if (root.has("content")) {
                    JsonNode contentNode = root.get("content");
                    if (!contentNode.isNull()) {
                        String content = contentNode.asText();
                        log.debug("Extracted Claude content from JSON: {}", content);
                        return content;
                    }
                }

                // If JSON parsed but no content field, try to extract JSON object embedded in text
                log.debug("JSON parsed but no 'content' field, attempting to extract embedded JSON");

            } catch (Exception jsonParseError) {
                // Not valid JSON, try to extract JSON from within the text
                log.debug("Failed to parse as JSON, attempting to extract embedded JSON: {}", jsonParseError.getMessage());
                String extractedJson = extractJsonFromText(processedOutput);
                if (extractedJson != null) {
                    try {
                        JsonNode root = objectMapper.readTree(extractedJson);
                        if (root.has("content")) {
                            String content = root.get("content").asText();
                            log.debug("Extracted content from embedded JSON: {}", content);
                            return content;
                        }
                    } catch (Exception e) {
                        log.debug("Failed to parse extracted JSON: {}", e.getMessage());
                    }
                }
            }

            // Fallback: treat entire output as plain text content
            log.warn("Could not parse as JSON, using raw output as plain text content");
            return processedOutput;

        } catch (Exception e) {
            // Final fallback: return raw output
            log.error("Failed to parse output, returning raw output: {}", e.getMessage());
            return rawOutput.trim();
        }
    }

    /**
     * Strips markdown code blocks from the output.
     * If no code blocks are found, returns the original output (assumes direct JSON).
     */
    private String stripMarkdownCodeBlocks(String output) {
        Matcher matcher = CODE_BLOCK_PATTERN.matcher(output);

        if (matcher.find()) {
            String extracted = matcher.group(1).trim();
            log.debug("Stripped markdown code blocks, extracted JSON: {}", extracted);
            return extracted;
        }

        // No markdown blocks found, assume direct JSON
        log.debug("No markdown code blocks found, using output as-is");
        return output.trim();
    }

    /**
     * Attempts to extract JSON object from within plain text.
     * Looks for patterns like {...} that might be valid JSON.
     */
    private String extractJsonFromText(String text) {
        // Look for JSON object pattern { ... }
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

    @Override
    public String getToolName() {
        return "claude";
    }
}
