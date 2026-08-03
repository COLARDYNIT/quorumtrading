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
 *
 * Supports two formats:
 * 1. Actual claude CLI with --output-format json:
 *    {"type":"result","subtype":"success",...,"result":"actual LLM response",...}
 *
 * 2. Legacy format (markdown-wrapped):
 *    ```json
 *    {"content": "actual LLM response"}
 *    ```
 *
 * This parser strips markdown blocks and extracts the "result" or "content" field.
 */
@ApplicationScoped
public class ClaudeResponseParser implements ResponseParser {

    private static final Logger log = LoggerFactory.getLogger(ClaudeResponseParser.class);
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

            // The CLI's --output-format json envelope is the OUTERMOST structure — parse it first
            // so the escapes inside the "result" string (\" \n) resolve through Jackson, THEN
            // strip any markdown fences the model put around its answer. Fence-stripping the raw
            // envelope instead regex-extracts still-escaped text that downstream JSON parsers
            // reject (seen live with find_ticker, 2026-06-10).
            try {
                String enveloped = extractResultOrContent(objectMapper.readTree(rawOutput.trim()));
                if (enveloped != null) {
                    log.debug("Extracted Claude result/content from CLI envelope");
                    return stripMarkdownCodeBlocks(enveloped);
                }
                log.debug("JSON parsed but no 'result' or 'content' field");
            } catch (Exception envelopeParseError) {
                log.debug("Output is not a bare JSON envelope: {}", envelopeParseError.getMessage());
            }

            // Legacy paths: fenced or free-form output. Strip fences, then look for the fields.
            String processedOutput = stripMarkdownCodeBlocks(rawOutput);

            try {
                String extracted = extractResultOrContent(objectMapper.readTree(processedOutput));
                if (extracted != null) {
                    log.debug("Extracted Claude result/content from fenced JSON");
                    return extracted;
                }
                log.debug("Fenced JSON parsed but no 'result' or 'content' field, attempting embedded JSON");
            } catch (Exception jsonParseError) {
                // Not valid JSON, try to extract JSON from within the text
                log.debug("Failed to parse as JSON, attempting to extract embedded JSON: {}", jsonParseError.getMessage());
                String extractedJson = extractJsonFromText(processedOutput);
                if (extractedJson != null) {
                    try {
                        String extracted = extractResultOrContent(objectMapper.readTree(extractedJson));
                        if (extracted != null) {
                            log.debug("Extracted Claude result/content from embedded JSON");
                            return extracted;
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
     * The model's answer from a parsed JSON node: the {@code result} field (actual claude CLI
     * {@code --output-format json}) or the legacy {@code content} field; {@code null} if neither
     * is present.
     */
    private static String extractResultOrContent(JsonNode root) {
        if (root.has("result") && !root.get("result").isNull()) {
            return root.get("result").asText();
        }
        if (root.has("content") && !root.get("content").isNull()) {
            return root.get("content").asText();
        }
        return null;
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
