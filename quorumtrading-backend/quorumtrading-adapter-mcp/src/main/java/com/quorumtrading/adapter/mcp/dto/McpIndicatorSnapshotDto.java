package com.quorumtrading.adapter.mcp.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.List;

/**
 * Unknown dividend state is <b>omitted</b> rather than rendered.
 *
 * <p>A null {@code paysDividends} means the dividend history could not be
 * established, and serialising that as {@code false} — or even as an explicit
 * {@code null} sitting beside a confident {@code dividendCountLastYear: 0} —
 * reads as a positive claim that the instrument pays nothing. Coca-Cola and
 * AstraZeneca, both long-standing payers, were reported that way. Leaving the
 * field out says what is true: we do not know.</p>
 */
@RegisterForReflection
public record McpIndicatorSnapshotDto(
    String ticker,
    String calculatedAt,
    String latestClosePrice,
    String latestPriceDate,
    McpMovingAverageDto movingAverage,
    McpRSIDto rsi,
    McpMACDDto macd,
    McpBollingerDto bollingerBands,
    List<String> warnings,
    String autoBackfillNote,

    // Omitted entirely when unknown — only these three, so the rest of the response
    // keeps its stable shape. A null here means the dividend history could not be
    // established, and there is no way to render that as a value without asserting
    // something: false claims the instrument pays nothing, and an explicit null beside
    // a confident count of 0 reads the same way.
    @JsonInclude(JsonInclude.Include.NON_NULL) Boolean paysDividends,
    @JsonInclude(JsonInclude.Include.NON_NULL) String annualDividendPerShare,
    @JsonInclude(JsonInclude.Include.NON_NULL) Integer dividendCountLastYear
) {

    /** Backwards-compatible constructor without dividend fields. */
    public McpIndicatorSnapshotDto(String ticker, String calculatedAt, String latestClosePrice,
                                    String latestPriceDate, McpMovingAverageDto movingAverage,
                                    McpRSIDto rsi, McpMACDDto macd, McpBollingerDto bollingerBands,
                                    List<String> warnings, String autoBackfillNote) {
        this(ticker, calculatedAt, latestClosePrice, latestPriceDate, movingAverage, rsi, macd,
             bollingerBands, warnings, autoBackfillNote, null, null, null);
    }

    @RegisterForReflection
    public record McpMovingAverageDto(String sma50, String sma200, String ema50, String ema200) {}

    @RegisterForReflection
    public record McpRSIDto(String rsi14) {}

    @RegisterForReflection
    public record McpMACDDto(String macdLine, String signalLine, String histogram) {}

    @RegisterForReflection
    public record McpBollingerDto(String upper, String middle, String lower) {}
}
