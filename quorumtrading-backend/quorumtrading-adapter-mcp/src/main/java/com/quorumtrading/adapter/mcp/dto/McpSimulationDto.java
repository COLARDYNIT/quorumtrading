package com.quorumtrading.adapter.mcp.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.List;

/**
 * MCP DTO for simulation summary. Flattens domain entity for LLM consumption.
 */
@RegisterForReflection
public record McpSimulationDto(
    String id,
    String name,
    String strategyId,
    String strategyName,
    /**
     * Lifecycle state: PENDING, WAITING_FOR_DATA, SIMULATING, LIVE, COMPLETED, FAILED.
     *
     * <p><b>LIVE here is a state, not a statement about money.</b> It means the run has caught up
     * with the present and is evaluating forward in real time; a mock-broker run reaches it too.
     * Whether real money is at stake is {@link #realMoney()} — read that, never this.</p>
     */
    String status,
    /**
     * True when the simulation is paused and a caller must resume it to make progress.
     *
     * <p>Only reported for the states in which pausing is meaningful (SIMULATING, LIVE). A
     * simulation is internally flagged paused from the moment it is created until it starts, so
     * reporting that flag raw made every freshly created run look broken — {@code paused:true} with
     * a null reason, seconds after create_simulation promised the run starts on its own — and the
     * obvious response was to call resume_simulation on a run nobody had paused.</p>
     */
    boolean paused,
    /** Why the system paused it (e.g. BUDGET_DEPLETED); null when the user paused it or it is running. */
    String pauseReason,
    /**
     * Why the run failed. Null unless status is FAILED (and null for runs that failed before this
     * field existed). Without it a FAILED simulation reported nothing a caller could act on —
     * missing price data, a dead LLM provider and a broker rejection all looked identical.
     */
    String failureReason,
    int progress,
    String startDate,
    String endDate,
    String initialCash,
    String currency,
    String brokerAccountId,
    /** BACKTEST (no broker or a mock one), PAPER (ALPACA_PAPER/SAXO_SIM), LIVE (ALPACA_LIVE/SAXO_LIVE). */
    String tradingMode,
    /**
     * Whether this run can move real money — the single field to check before acting.
     *
     * <p>"LIVE" names two unrelated things: a lifecycle state, and real-money trading. A mock-broker
     * backtest that catches up with the present reports {@code status:"LIVE"} with
     * {@code tradingMode:"BACKTEST"}, so filtering simulations by status LIVE — which is exactly what
     * the setup_live_trading walkthrough used to prescribe for "find your live runs" — returns runs
     * that trade nothing. This flag is derived from the broker actually attached and is unambiguous:
     * true only for ALPACA_LIVE and SAXO_LIVE.</p>
     *
     * <p>Null when the trading mode was not resolved for this projection.</p>
     */
    Boolean realMoney,
    List<String> symbols,
    String currentValue,
    String returnPercent,
    String startedAt,
    String completedAt,
    String createdAt,
    // --- Recurring-investment fields ---
    /** Seed cash + Σ contributions — the basis {@code returnPercent} is measured against. */
    String totalInvested,
    /**
     * Time-weighted return. Computing it needs every snapshot of the run, so the list view omits
     * it rather than sending null — an absent key says "ask get_simulation", where a null said
     * "this run has no time-weighted return", which was not true.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    String twrPercent,
    /** Money-weighted return (annualised IRR). Omitted from list rows for the same reason as {@link #twrPercent()}. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    String mwrPercent,
    List<McpContributionDto> contributions,
    /**
     * Why a finished run decided nothing — null whenever it decided something.
     *
     * <p>A backtest that skipped every evaluation still completes: status COMPLETED,
     * progress 100, return 0.0000%. That is byte-identical to a run which evaluated
     * normally and chose to hold throughout, so a broken run reported itself as a
     * successful flat one and the only trace was a note buried on individual decision
     * rows. Any cause produces it — a wrong-timezone schedule, a botched backfill, a
     * window that is entirely market holidays — so the field states the count and the
     * reason rather than naming a single culprit.</p>
     *
     * <p>This is a post-mortem: it stays null while a run is in flight. For the live
     * question — "why is this run not progressing right now?" — read {@link #health()}.</p>
     */
    String noDecisionsReason,
    /**
     * How many decisions in this run wanted to trade but nothing executed — refused by the
     * strategy's own risk controls, below the minimum trade value, or rejected by the broker.
     *
     * <p>The complement of {@link #noDecisionsReason()}: that field catches a run that decided
     * nothing, this one catches a run that decided plenty and traded nothing. A headline
     * return over mostly-refused BUYs is not the strategy's return; a non-zero count here says
     * to read the decisions (query_consensus_decisions) before trusting it. Null on list rows
     * and non-terminal runs (a running simulation legitimately accumulates these).</p>
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    Long decisionsNotExecuted,
    /**
     * Live execution health — verdict, skip streak, dominant skip reasons. Present on
     * {@code get_simulation} only; list rows omit it (it costs a per-simulation query).
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    McpSimulationHealthDto health,
    /**
     * Web-app detail page for this simulation. Give this to the user when they want to see the
     * run in a browser. Omitted on headless instances (frontend disabled).
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    String uiUrl
) {}
