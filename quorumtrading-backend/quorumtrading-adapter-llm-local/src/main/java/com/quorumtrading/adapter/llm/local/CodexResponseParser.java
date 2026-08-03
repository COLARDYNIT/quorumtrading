package com.quorumtrading.adapter.llm.local;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Parser for Codex CLI output format.
 * Codex outputs JSON on stdout: {"response": "actual LLM response", "metadata": {...}}
 * This parser extracts just the "response" field.
 */
@ApplicationScoped
public class CodexResponseParser implements ResponseParser {

    private static final Logger log = LoggerFactory.getLogger(CodexResponseParser.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String parse(String rawOutput) throws CLIExecutionException {
        if (rawOutput == null || rawOutput.trim().isEmpty()) {
            throw new CLIExecutionException("Empty or null output from Codex CLI");
        }

        try {
            log.debug("Parsing Codex response (length: {} chars)", rawOutput.length());

            // Try to parse as JSON first
            try {
                JsonNode root = objectMapper.readTree(rawOutput);

                if (root.has("response")) {
                    JsonNode responseNode = root.get("response");
                    if (!responseNode.isNull()) {
                        String response = responseNode.asText();
                        log.debug("Extracted Codex response from JSON: {}", response);
                        return response;
                    }
                }

            } catch (Exception jsonParseError) {
                log.debug("Failed to parse as JSON, trying text extraction: {}", jsonParseError.getMessage());
            }

            // Fallback: Extract response from text format
            // Codex outputs in format:
            // OpenAI Codex v0.53.0 (research preview)
            // --------
            // ... metadata ...
            // --------
            // user
            // <prompt>
            // codex
            // <actual response>
            // tokens used
            // <token count>
            // <actual response again>

            String cleaned = stripCodexHeader(rawOutput);

            if (cleaned != null && !cleaned.trim().isEmpty()) {
                log.debug("Extracted response from text format: {}", cleaned);
                return cleaned;
            }

            // Final fallback: return raw output
            log.warn("Could not extract response from Codex output, using raw output");
            return rawOutput.trim();

        } catch (Exception e) {
            // Final fallback: return raw output
            log.error("Failed to parse Codex output, returning raw output: {}", e.getMessage());
            return rawOutput.trim();
        }
    }

    /**
     * Strips the Codex CLI header and metadata to extract just the response.
     *
     * Codex output format:
     * <pre>
     * OpenAI Codex v0.53.0 (research preview)
     * --------
     * workdir: /path
     * model: gpt-5
     * ...
     * --------
     * user
     * <prompt>
     * codex
     * <response>
     * tokens used
     * <count>
     * <response>  (repeated at end)
     * </pre>
     *
     * @param rawOutput The raw output from Codex CLI
     * @return The extracted response, or null if extraction failed
     */
    private String stripCodexHeader(String rawOutput) {
        if (rawOutput == null) {
            return null;
        }

        String[] lines = rawOutput.split("\n");
        StringBuilder response = new StringBuilder();
        boolean foundCodexLabel = false;
        boolean inResponseSection = false;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();

            // Skip empty lines at the start
            if (!foundCodexLabel && line.isEmpty()) {
                continue;
            }

            // Look for "codex" label (marks start of response)
            if (!foundCodexLabel && line.equals("codex")) {
                foundCodexLabel = true;
                inResponseSection = true;
                continue;  // Skip the "codex" label itself
            }

            // If we found "codex" label, collect response lines
            if (foundCodexLabel) {
                // Stop at "tokens used" which marks end of response
                if (line.equals("tokens used") || line.startsWith("tokens used")) {
                    break;
                }

                // Add response line
                if (inResponseSection) {
                    response.append(line).append("\n");
                }
            }
        }

        String extracted = response.toString().trim();

        // If extraction failed, try to find content after the last "--------" divider
        if (extracted.isEmpty()) {
            int lastDividerIndex = -1;
            for (int i = 0; i < lines.length; i++) {
                if (lines[i].trim().equals("--------")) {
                    lastDividerIndex = i;
                }
            }

            if (lastDividerIndex >= 0 && lastDividerIndex < lines.length - 1) {
                StringBuilder fallback = new StringBuilder();
                for (int i = lastDividerIndex + 1; i < lines.length; i++) {
                    String line = lines[i].trim();
                    if (!line.isEmpty() && !line.equals("user") && !line.equals("codex") && !line.equals("tokens used")) {
                        fallback.append(line).append("\n");
                    }
                }
                extracted = fallback.toString().trim();
            }
        }

        return extracted.isEmpty() ? null : extracted;
    }

    @Override
    public String getToolName() {
        return "codex";
    }
}
