package com.quorumtrading.adapter.mcp.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.List;

@RegisterForReflection
public record McpPriceCoverageDto(
    String ticker,
    long barCount,
    long distinctDays,
    String firstBar,
    String lastBar,
    List<String> sources,
    String note,
    /** Present only when the caller named a window. */
    @JsonInclude(JsonInclude.Include.NON_NULL) McpCoverageWindowDto window
) {

    /** Backwards-compatible constructor for the no-window case. */
    public McpPriceCoverageDto(String ticker, long barCount, long distinctDays, String firstBar,
                                String lastBar, List<String> sources, String note) {
        this(ticker, barCount, distinctDays, firstBar, lastBar, sources, note, null);
    }

    /**
     * What is covered inside the requested window.
     *
     * @param from         first day of the window (inclusive)
     * @param to           last day of the window (inclusive)
     * @param expectedDays days the calendar says should carry a bar
     * @param coveredDays  days that do
     * @param missingDays  the difference
     * @param calendar     which day-count rule was applied — stated, because it is an assumption
     * @param gaps         runs of missing days, oldest first
     */
    @RegisterForReflection
    public record McpCoverageWindowDto(
        String from,
        String to,
        long expectedDays,
        long coveredDays,
        long missingDays,
        String calendar,
        List<McpCoverageGapDto> gaps
    ) {}

    @RegisterForReflection
    public record McpCoverageGapDto(String from, String to, long days) {}
}
