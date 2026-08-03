package com.quorumtrading.adapter.mcp.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * What a backtest is expected to cost, before anything is created.
 *
 * <p>Amounts are strings so a caller reads the exact decimal rather than a float that has been
 * through a JSON parser — these are money figures a user is quoted.</p>
 *
 * @param tradingDays          evaluation days in the span, after the strategy's schedule is applied
 * @param totalConsensusRounds consensus rounds across those days
 * @param totalLLMCalls        individual model calls, the figure that drives spend
 * @param agentsPerRound       agents per round, including the orchestrator where one is used
 * @param estimatedCost        point estimate in USD
 * @param lowerBound           low end of the confidence range
 * @param upperBound           high end of the confidence range
 * @param modelId              the model the estimate is priced against
 * @param modelName            its human-readable name
 * @param note                 how to read the number
 * @param estimatedWallClockSeconds projected run duration; null when the active engine has no
 *                             recent latency observations. On a free local engine the dollar
 *                             figure reads $0.00 while the run costs hours — this is the number
 *                             that says so before the run is created.
 * @param timeBasis            what the wall-clock projection rests on (observed latency × calls,
 *                             serialization assumption), or why it is absent
 * @param dataCoverageWarning  set when stored price data does not cover the requested window —
 *                             the estimate prices the schedule, not the data, and a run over
 *                             missing days evaluates nothing there; null when coverage is fine
 */
@RegisterForReflection
public record McpCostEstimateDto(
        int tradingDays,
        long totalConsensusRounds,
        long totalLLMCalls,
        int agentsPerRound,
        int tickerCount,
        String estimatedCost,
        String lowerBound,
        String upperBound,
        String costPerCall,
        String modelId,
        String modelName,
        String note,
        Long estimatedWallClockSeconds,
        String timeBasis,
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
        String dataCoverageWarning
) {}
