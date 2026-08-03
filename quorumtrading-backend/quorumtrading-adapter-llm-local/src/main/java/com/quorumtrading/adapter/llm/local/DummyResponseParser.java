package com.quorumtrading.adapter.llm.local;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Response parser for DummyExecutor.
 * <p>
 * Since DummyExecutor returns clean markdown content directly,
 * this parser simply passes through the response unchanged.
 * </p>
 */
@ApplicationScoped
public class DummyResponseParser implements ResponseParser {

    @Override
    public String parse(String rawOutput) throws CLIExecutionException {
        if (rawOutput == null || rawOutput.trim().isEmpty()) {
            throw new CLIExecutionException("Empty or null output from DummyExecutor");
        }
        return rawOutput.trim();
    }

    @Override
    public String getToolName() {
        return "dummy";
    }
}
