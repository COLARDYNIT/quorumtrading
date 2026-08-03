package com.quorumtrading.adapter.mcp.mapper;

import com.quorumtrading.adapter.mcp.dto.*;
import com.quorumtrading.application.orchestration.agents.ConsensusActionParser;
import com.quorumtrading.application.usecase.BackFillResult;
import com.quorumtrading.application.usecase.SimulationEvaluationResult;
import com.quorumtrading.application.usecase.dto.ConsensusWithAgentsResponse;
import com.quorumtrading.application.usecase.dto.BenchmarkLeaderboard;
import com.quorumtrading.application.usecase.dto.MarketResearchDetails;
import com.quorumtrading.application.usecase.dto.MarketResearchSummary;
import com.quorumtrading.model.entity.*;
import com.quorumtrading.model.entity.LlmParseFailure;
import com.quorumtrading.model.enums.ConsensusActionType;
import com.quorumtrading.model.enums.EventType;
import com.quorumtrading.model.enums.SimulationStatus;
import com.quorumtrading.model.marketdata.BackfillProgress;
import com.quorumtrading.model.marketdata.MarketEvent;
import com.quorumtrading.model.valueobject.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.yaml.snakeyaml.Yaml;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Maps domain entities to MCP-specific DTOs.
 * Manual mapping (no MapStruct) for full control over what LLM clients see.
 */
@ApplicationScoped
public class McpDtoMapper {

    @Inject
    ObjectMapper objectMapper;

    @Inject
    UiLinks uiLinks;

    private static final Pattern FRONTMATTER_PATTERN = Pattern.compile("^---\\s*\\n(.*?)\\n---", Pattern.DOTALL);

    public McpSimulationDto toSimulationDto(Simulation sim) {
        return toSimulationDto(sim, null, null, null, null, null);
    }

    public McpSimulationDto toSimulationDto(Simulation sim, String strategyName, String tradingMode) {
        return toSimulationDto(sim, strategyName, tradingMode, null, null, null);
    }

    public McpSimulationDto toSimulationDto(Simulation sim, String strategyName, String tradingMode,
                                             List<String> symbols, String currentValue, String returnPercent) {
        return toSimulationDto(sim, strategyName, tradingMode, symbols, currentValue, returnPercent,
                null, null, null, null);
    }

    /**
     * The broker account id to report for a simulation, or null when the caller never
     * attached one.
     *
     * <p>A backtest created without a {@code brokerAccountId} gets an inline mock account
     * created for it behind the scenes. Reporting that id back implies the user chose a
     * broker they never chose, and sends them looking for an account that does not appear in
     * {@code list_broker_accounts}. The trading mode already says BACKTEST; the id adds
     * nothing but confusion.</p>
     */
    /**
     * Whether this run can move real money, derived from the resolved trading mode.
     *
     * <p>Kept separate from {@code status} because "LIVE" names two unrelated things — a lifecycle
     * state and real-money trading — and a mock-broker run reports {@code status:"LIVE"} with
     * {@code tradingMode:"BACKTEST"}. Null when the trading mode was not resolved, because "we did
     * not look" and "no real money" are different answers to a question about money.</p>
     */
    private static Boolean realMoney(String tradingMode) {
        return tradingMode == null ? null : "LIVE".equals(tradingMode);
    }

    /**
     * The paused flag as a caller should read it: "do I need to call resume_simulation?".
     *
     * <p>A simulation is internally flagged paused from creation until it starts, and the domain
     * only treats pausing as meaningful in SIMULATING/LIVE. Reporting the raw flag made a healthy
     * new run announce {@code paused:true} with a null reason moments after create_simulation said
     * it starts by itself.</p>
     */
    private static boolean reportablePaused(Simulation sim) {
        boolean pausable = sim.getStatus() == SimulationStatus.SIMULATING
                || sim.getStatus() == SimulationStatus.LIVE;
        return pausable && (sim.isPaused() || sim.isPausedForExternalReason());
    }

    private String reportableBrokerAccountId(Simulation sim, boolean inlineBroker) {
        if (sim.getBrokerAccountId() == null || inlineBroker) {
            return null;
        }
        return sim.getBrokerAccountId().value().toString();
    }

    /**
     * Enriched simulation DTO including total invested basis, TWR/MWR, and
     * contributions list. Pass null for the recurring-investment fields when
     * not yet computed (list endpoints).
     */
    public McpSimulationDto toSimulationDto(Simulation sim, String strategyName, String tradingMode,
                                             List<String> symbols, String currentValue, String returnPercent,
                                             String totalInvested, String twrPercent, String mwrPercent,
                                             List<com.quorumtrading.adapter.mcp.dto.McpContributionDto> contributions) {
        return toSimulationDto(sim, strategyName, tradingMode, symbols, currentValue, returnPercent,
                totalInvested, twrPercent, mwrPercent, contributions, false);
    }

    /**
     * Enriched simulation DTO, told whether the simulation's broker account was created
     * inline for it. Inline accounts are not reported as the simulation's broker — see
     * {@link #reportableBrokerAccountId(Simulation, boolean)}.
     */
    public McpSimulationDto toSimulationDto(Simulation sim, String strategyName, String tradingMode,
                                             List<String> symbols, String currentValue, String returnPercent,
                                             String totalInvested, String twrPercent, String mwrPercent,
                                             List<com.quorumtrading.adapter.mcp.dto.McpContributionDto> contributions,
                                             boolean inlineBroker) {
        return toSimulationDto(sim, strategyName, tradingMode, symbols, currentValue, returnPercent,
                totalInvested, twrPercent, mwrPercent, contributions, inlineBroker, null);
    }

    /**
     * Full simulation DTO, including why a finished run decided nothing (null when it did).
     */
    public McpSimulationDto toSimulationDto(Simulation sim, String strategyName, String tradingMode,
                                             List<String> symbols, String currentValue, String returnPercent,
                                             String totalInvested, String twrPercent, String mwrPercent,
                                             List<com.quorumtrading.adapter.mcp.dto.McpContributionDto> contributions,
                                             boolean inlineBroker, String noDecisionsReason) {
        return toSimulationDto(sim, strategyName, tradingMode, symbols, currentValue, returnPercent,
                totalInvested, twrPercent, mwrPercent, contributions, inlineBroker, noDecisionsReason,
                null, null);
    }

    /**
     * Full simulation DTO, additionally carrying how many decisions wanted to trade but
     * nothing executed (null when not computed — list rows and non-terminal runs), and the
     * live execution-health block (detail view only — list rows pass null and the field is
     * omitted from their JSON).
     */
    public McpSimulationDto toSimulationDto(Simulation sim, String strategyName, String tradingMode,
                                             List<String> symbols, String currentValue, String returnPercent,
                                             String totalInvested, String twrPercent, String mwrPercent,
                                             List<com.quorumtrading.adapter.mcp.dto.McpContributionDto> contributions,
                                             boolean inlineBroker, String noDecisionsReason,
                                             Long decisionsNotExecuted,
                                             com.quorumtrading.adapter.mcp.dto.McpSimulationHealthDto health) {
        return new McpSimulationDto(
            sim.getSimulationId().value().toString(),
            sim.getName(),
            sim.getStrategyId().value().toString(),
            strategyName,
            sim.getStatus().name(),
            reportablePaused(sim),
            sim.isPausedForExternalReason() ? sim.getExternalPauseReason() : null,
            sim.getFailureReason(),
            sim.getProgress(),
            formatInstant(sim.getStartDate()),
            formatInstant(sim.getEndDate()),
            formatMoney(sim.getInitialCash()),
            sim.getInitialCash() != null ? sim.getInitialCash().currency().name() : null,
            reportableBrokerAccountId(sim, inlineBroker),
            tradingMode,
            realMoney(tradingMode),
            symbols,
            currentValue,
            returnPercent,
            formatInstant(sim.getStartedAt()),
            formatInstant(sim.getCompletedAt()),
            formatInstant(sim.getCreatedAt()),
            totalInvested,
            twrPercent,
            mwrPercent,
            contributions,
            noDecisionsReason,
            decisionsNotExecuted,
            health,
            uiLinks.simulation(sim.getSimulationId().value().toString())
        );
    }

    /** Adapter projection of the application-layer health read model. Null-in, null-out. */
    public com.quorumtrading.adapter.mcp.dto.McpSimulationHealthDto toHealthDto(
            com.quorumtrading.application.usecase.health.SimulationExecutionHealth health) {
        if (health == null) {
            return null;
        }
        return new com.quorumtrading.adapter.mcp.dto.McpSimulationHealthDto(
                health.verdict().name(),
                health.detail(),
                formatInstant(health.lastDecisionAt()),
                formatInstant(health.lastRoundAt()),
                health.recentRows(),
                health.recentSkippedRows(),
                health.consecutiveSkippedRows(),
                health.totalEvaluations(),
                health.totalSkippedEvaluations(),
                health.topSkipReasons().stream()
                        .map(r -> new com.quorumtrading.adapter.mcp.dto.McpSimulationHealthDto.McpSkipReasonDto(
                                r.reason(), r.count()))
                        .toList()
        );
    }

    public com.quorumtrading.adapter.mcp.dto.McpContributionDto toContributionDto(
            com.quorumtrading.model.entity.Contribution c) {
        return new com.quorumtrading.adapter.mcp.dto.McpContributionDto(
                c.id().value().toString(),
                c.amount().amount().toPlainString(),
                c.amount().currency().name(),
                formatInstant(c.contributionDate()),
                c.sourcePlanId() != null ? c.sourcePlanId().toString() : null,
                c.sourceAllocationId() != null ? c.sourceAllocationId().toString() : null,
                formatInstant(c.createdAt())
        );
    }

    public McpStrategyDto toStrategyDto(Strategy strategy) {
        return toStrategyDto(strategy, true, null);
    }

    public McpStrategyDto toStrategyDto(Strategy strategy, boolean includeContent) {
        return toStrategyDto(strategy, includeContent, null);
    }

    public McpStrategyDto toStrategyDto(Strategy strategy, boolean includeContent, Integer simulationCount) {
        List<String> stockTickers = null;
        List<String> cryptoTickers = null;
        String consensusMethod = null;
        String tradingMode = null;
        List<String> effectiveAgents = null;
        String agentAvailabilityNote = null;

        try {
            Map<String, Object> frontmatter = extractFrontmatter(strategy.getContent());
            if (frontmatter != null) {
                Object cm = frontmatter.get("consensus_method");
                // An absent key is not "nothing configured" — it selects ORCHESTRATOR_OVERRIDE
                // at runtime (StrategyConfigParser applies the same default). Report the value
                // that will actually govern the run, the way effectiveAgents already does.
                consensusMethod = cm != null
                        ? normalizeConsensusMethod(cm.toString())
                        : com.quorumtrading.model.enums.ConsensusMethod.ORCHESTRATOR_OVERRIDE.name();
                effectiveAgents = extractEffectiveAgents(frontmatter);
                stockTickers = extractTickerList(frontmatter, "stock_tickers");
                cryptoTickers = extractTickerList(frontmatter, "crypto_tickers");
                // Derive trading mode from tickers
                boolean hasCrypto = cryptoTickers != null && !cryptoTickers.isEmpty();
                boolean hasStock = stockTickers != null && !stockTickers.isEmpty();
                if (hasCrypto && !hasStock) tradingMode = "CRYPTO";
                else if (hasStock && !hasCrypto) tradingMode = "STOCK";
                else if (hasStock) tradingMode = "STOCK";
                agentAvailabilityNote = agentAvailabilityNote(effectiveAgents);
            }
        } catch (Exception e) {
            // Graceful degradation: metadata fields stay null on parse failure
        }

        return new McpStrategyDto(
            strategy.getId().value().toString(),
            strategy.getName(),
            strategy.getDescription(),
            strategy.getStatus().name(),
            strategy.isEnabled(),
            stockTickers,
            cryptoTickers,
            consensusMethod,
            effectiveAgents,
            agentAvailabilityNote,
            tradingMode,
            simulationCount,
            includeContent ? strategy.getContent() : null,
            formatInstant(strategy.getCreatedAt()),
            formatInstant(strategy.getUpdatedAt()),
            uiLinks.strategy(strategy.getId().value().toString())
        );
    }

    /**
     * Extracts combined symbols (stock + crypto tickers) from a strategy's YAML frontmatter.
     */
    public List<String> extractSymbols(Strategy strategy) {
        try {
            Map<String, Object> frontmatter = extractFrontmatter(strategy.getContent());
            if (frontmatter == null) return List.of();
            List<String> all = new ArrayList<>();
            List<String> stocks = extractTickerList(frontmatter, "stock_tickers");
            List<String> crypto = extractTickerList(frontmatter, "crypto_tickers");
            if (stocks != null) all.addAll(stocks);
            if (crypto != null) all.addAll(crypto);
            return all.isEmpty() ? List.of() : all;
        } catch (Exception e) {
            return List.of();
        }
    }

    public McpConsensusDecisionDto toConsensusDecisionDto(ConsensusDecision cd) {
        return toConsensusDecisionDto(cd, false);
    }

    public McpConsensusDecisionDto toConsensusDecisionDto(ConsensusDecision cd, boolean includeReasoning) {
        return new McpConsensusDecisionDto(
            cd.getId(),
            cd.getSimulationId() != null ? cd.getSimulationId().value().toString() : null,
            formatInstant(cd.getDecisionTimestamp()),
            cd.getConsensusMethod() != null ? cd.getConsensusMethod().name() : null,
            toActionDto(cd.getAction(), includeReasoning),
            cd.isExecuted(),
            cd.getExecutionOutcome() != null ? cd.getExecutionOutcome().name() : null,
            formatMoney(cd.getExecutionPrice()),
            cd.getQuantity() != null ? cd.getQuantity().toPlainString() : null,
            cd.getSymbol() != null ? cd.getSymbol().ticker() : null,
            cd.getInvestmentType() != null ? cd.getInvestmentType().name() : null,
            formatMoney(cd.getRawCost()),
            cd.getNotes()
        );
    }

    public McpAgentDecisionDto toAgentDecisionDto(LLMDecision ld) {
        return toAgentDecisionDto(ld, true);
    }

    public McpAgentDecisionDto toAgentDecisionDto(LLMDecision ld, boolean includeReasoning) {
        return toAgentDecisionDto(ld, includeReasoning, null);
    }

    public McpAgentDecisionDto toAgentDecisionDto(LLMDecision ld, boolean includeReasoning, String fallbackTicker) {
        String ticker = ld.getTicker() != null ? ld.getTicker() : fallbackTicker;
        ConsensusAction action = ConsensusActionParser.parse(ld.getRecommendedAction());

        // includeReasoning=false used to null ONLY this clean field while shipping the raw
        // recommendedAction JSON — which carries the same reasoning — unconditionally. The
        // documented "compact" mode therefore stripped nothing and saved no tokens. Both
        // carriers are now gated by the one flag.
        //
        // When reasoning IS requested it is emitted exactly once, here: the sibling action
        // payload always suppresses it (see toAgentActionDto) rather than repeating the
        // same paragraph a second time inside the same object.
        String reasoning = null;
        if (includeReasoning) {
            reasoning = ld.getReasoning() != null ? ld.getReasoning() : action.reasoning();
        }

        return new McpAgentDecisionDto(
            ld.getId(),
            ld.getAgentId(),
            ld.getRole(),
            ticker,
            ld.getRecommendation() != null ? ld.getRecommendation().name() : null,
            ld.getConfidenceScore() != null ? ld.getConfidenceScore().value().toPlainString() : null,
            reasoning,
            toAgentActionDto(action),
            ld.getLlmModel(),
            ld.getExecutionTimeMs()
        );
    }

    /**
     * Maps a parsed action to its wire form.
     *
     * @param includeReasoning when false, the reasoning text is omitted entirely
     */
    private McpActionDto toActionDto(ConsensusAction action, boolean includeReasoning) {
        if (action == null) {
            return null;
        }
        boolean parsed = action.type() != ConsensusActionType.UNKNOWN;
        return new McpActionDto(
            parsed ? action.type().name() : null,
            action.symbol(),
            action.quantity() != null ? action.quantity().toPlainString() : null,
            action.confidence() != null ? action.confidence().toPlainString() : null,
            includeReasoning ? action.reasoning() : null,
            // Only surface the unparsed text when there is nothing structured to show for it.
            parsed ? null : action.raw()
        );
    }

    /**
     * An agent's action payload never carries reasoning: the decision's own {@code reasoning}
     * field is the single place that text appears, so a caller reads it once instead of
     * finding the same paragraph in two sibling fields.
     */
    private McpActionDto toAgentActionDto(ConsensusAction action) {
        return toActionDto(action, false);
    }

    /**
     * Short stand-in for an agent reasoning that is byte-identical to the consensus reasoning.
     * Deliberately not a bare {@code null}: which agent's recommendation <i>became</i> the
     * consensus is genuinely useful, and silently blanking the field would throw that away.
     */
    static final String REASONING_SAME_AS_CONSENSUS =
        "(identical to consensus finalAction.reasoning — this agent's recommendation became the consensus)";

    /**
     * Collapses the one duplication the per-record dedup structurally cannot see.
     *
     * <p>Under MAJORITY the consensus action is built from the winning agent's own action, and
     * under ORCHESTRATOR_OVERRIDE the orchestrator's action is stored as the consensus action —
     * so the same paragraph genuinely occupies two rows, and {@link #toAgentDecisionDto} has no
     * view of the consensus to notice. At {@code limit=50} that doubled the largest text field
     * fifty times over.</p>
     */
    private McpAgentDecisionDto dedupeAgainstConsensus(McpAgentDecisionDto agent,
                                                       McpConsensusDecisionDto consensus) {
        if (agent.reasoning() == null
            || consensus.finalAction() == null
            || !agent.reasoning().equals(consensus.finalAction().reasoning())) {
            return agent;
        }
        return new McpAgentDecisionDto(
            agent.id(),
            agent.agentId(),
            agent.role(),
            agent.ticker(),
            agent.recommendation(),
            agent.confidenceScore(),
            REASONING_SAME_AS_CONSENSUS,
            agent.recommendedAction(),
            agent.llmModel(),
            agent.executionTimeMs()
        );
    }

    public McpConsensusWithAgentsDto toConsensusWithAgentsDto(ConsensusWithAgentsResponse response) {
        return toConsensusWithAgentsDto(response, true);
    }

    public McpConsensusWithAgentsDto toConsensusWithAgentsDto(ConsensusWithAgentsResponse response, boolean includeReasoning) {
        McpConsensusDecisionDto consensus = toConsensusDecisionDto(response.getConsensusDecision(), includeReasoning);

        // Fall back to the consensus symbol when agent ticker is null (not persisted in DB)
        String fallbackTicker = null;
        if (response.getConsensusDecision().getSymbol() != null) {
            fallbackTicker = response.getConsensusDecision().getSymbol().ticker();
        }

        String ft = fallbackTicker;
        List<McpAgentDecisionDto> agents = response.getAgentDecisions().stream()
            .map(ld -> toAgentDecisionDto(ld, includeReasoning, ft))
            .map(agent -> dedupeAgainstConsensus(agent, consensus))
            .toList();

        List<McpParseFailureDto> failureDtos = response.getParseFailures().isEmpty()
            ? null
            : response.getParseFailures().stream().map(this::toParseFailureDto).toList();

        return new McpConsensusWithAgentsDto(consensus, agents, failureDtos);
    }

    public McpParseFailureDto toParseFailureDto(LlmParseFailure failure) {
        return new McpParseFailureDto(
            formatInstant(failure.decisionTimestamp()),
            failure.ticker(),
            failure.agentRole(),
            failure.requestedModel(),
            failure.actualModel(),
            failure.temperature(),
            failure.executionTimeMs(),
            failure.rawResponse(),
            failure.errorMessage(),
            failure.errorType()
        );
    }

    /** A metric as a string, or null when there was nothing to measure. Never "0" for "unknown". */
    private static String plainOrNull(BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }

    public McpStrategyPerformanceDto toStrategyPerformanceDto(StrategyId strategyId, String strategyName, StrategyPerformance perf) {
        return new McpStrategyPerformanceDto(
            strategyId.value().toString(),
            strategyName,
            perf.totalSimulations(),
            perf.completedSimulations(),
            // Every metric may be null, meaning "no measurable run", not zero — see
            // StrategyPerformance. A strategy whose runs all decided nothing used to render an
            // avgReturn of 0.00% and a winRate of 0%, which on compare_strategies reads as a
            // measured loss rather than an absence of measurement.
            plainOrNull(perf.avgReturn()),
            plainOrNull(perf.winRate()),
            plainOrNull(perf.avgSharpeRatio()),
            plainOrNull(perf.bestReturn()),
            plainOrNull(perf.worstReturn()),
            perf.recentTrend().stream().map(BigDecimal::toPlainString).toList(),
            perf.emptyRuns()
        );
    }

    public McpPortfolioSnapshotDto toPortfolioSnapshotDto(PortfolioSnapshot snap) {
        List<McpPortfolioSnapshotDto.McpPositionDto> positions = new ArrayList<>();
        Map<Symbol, Position> posMap = snap.getPositions();
        if (posMap != null) {
            for (Map.Entry<Symbol, Position> entry : posMap.entrySet()) {
                Position pos = entry.getValue();
                String marketValue = null;
                String unrealizedPnl = null;
                if (pos.currentPrice() != null && pos.quantity() != null) {
                    BigDecimal mv = pos.quantity().multiply(pos.currentPrice().amount());
                    marketValue = mv.toPlainString();
                    if (pos.averagePrice() != null) {
                        BigDecimal pnl = pos.currentPrice().amount().subtract(pos.averagePrice().amount())
                            .multiply(pos.quantity());
                        unrealizedPnl = pnl.toPlainString();
                    }
                }
                positions.add(new McpPortfolioSnapshotDto.McpPositionDto(
                    pos.symbol().ticker(),
                    pos.symbol().type().name(),
                    pos.quantity().toPlainString(),
                    formatMoney(pos.averagePrice()),
                    formatMoney(pos.currentPrice()),
                    marketValue,
                    unrealizedPnl
                ));
            }
        }

        return new McpPortfolioSnapshotDto(
            snap.getSimulationId() != null ? snap.getSimulationId().value().toString() : null,
            formatInstant(snap.getTimestamp()),
            formatMoney(snap.getTotalValue()),
            formatMoney(snap.getCashAvailable()),
            snap.getTotalValue() != null ? snap.getTotalValue().currency().name() : null,
            positions
        );
    }

    // --- Market Research mappings ---

    public McpMarketResearchDto toMarketResearchDto(MarketResearchSummary summary) {
        return toMarketResearchDto(summary.research(), summary.strategyCount(), null, null);
    }

    public McpMarketResearchDto toMarketResearchDto(MarketResearch research, int strategyCount) {
        return toMarketResearchDto(research, strategyCount, null, null);
    }

    public McpMarketResearchDto toMarketResearchDto(MarketResearch research, int strategyCount,
                                                      Integer totalExecutions, String lastOutcome) {
        List<String> targetAssets = research.getTargetAssets() != null
            ? research.getTargetAssets().stream()
                .map(s -> s.ticker() + ":" + s.type().name())
                .toList()
            : List.of();

        ResearchInterval interval = research.getInterval();
        BacktestConfiguration btConfig = research.getBacktestConfig();

        return new McpMarketResearchDto(
            research.getId().toString(),
            research.getName(),
            research.getStatus().name(),
            targetAssets,
            interval.amount(),
            interval.unit().name(),
            research.isEnableAutoBacktest(),
            btConfig != null ? (int) btConfig.backtestLength().toDays() : null,
            btConfig != null ? formatInstant(btConfig.periodStart()) : null,
            btConfig != null ? formatInstant(btConfig.periodEnd()) : null,
            formatInstant(research.getNextScheduledRun()),
            formatInstant(research.getLastExecutedAt()),
            formatInstant(research.getCreatedAt()),
            strategyCount,
            research.getMarkdownInstructions(),
            totalExecutions,
            lastOutcome,
            uiLinks.marketResearch(research.getId().toString())
        );
    }

    public McpResearchExecutionDto toResearchExecutionDto(ResearchExecution exec) {
        return toResearchExecutionDto(exec, true);
    }

    public McpResearchExecutionDto toResearchExecutionDto(ResearchExecution exec, boolean includeAgentDecisions) {
        List<McpResearchAgentDecisionDto> agentDtos = null;
        if (includeAgentDecisions && exec.getAgentDecisions() != null) {
            agentDtos = exec.getAgentDecisions().stream()
                .map(this::toResearchAgentDecisionDto)
                .toList();
        }

        return new McpResearchExecutionDto(
            exec.getId().toString(),
            exec.getMarketResearchId().toString(),
            resolveOutcomeDisplay(exec),
            exec.getResearcherConclusion(),
            exec.getViabilityScore() != null ? exec.getViabilityScore().value().toPlainString() : null,
            exec.getStrategyId() != null ? exec.getStrategyId().toString() : null,
            exec.getSimulationId() != null ? exec.getSimulationId().toString() : null,
            formatInstant(exec.getExecutedAt()),
            agentDtos,
            exec.getRawCost() != null ? formatMoney(exec.getRawCost()) : null
        );
    }

    /**
     * Returns the outcome string to expose over MCP. When the underlying strategy has been deleted
     * (FK nulled by ON DELETE SET NULL on research_executions.created_strategy_id) we surface
     * STRATEGY_CREATED_DELETED so callers can distinguish "still linked" from "linked-then-deleted".
     */
    public static String resolveOutcomeDisplay(ResearchExecution exec) {
        if (exec == null || exec.getOutcome() == null) {
            return null;
        }
        if (exec.getOutcome() == ResearchOutcome.STRATEGY_CREATED && exec.getStrategyId() == null) {
            return "STRATEGY_CREATED_DELETED";
        }
        return exec.getOutcome().name();
    }

    public McpResearchAgentDecisionDto toResearchAgentDecisionDto(ResearchAgentDecision decision) {
        return new McpResearchAgentDecisionDto(
            decision.getId().toString(),
            decision.getAgentType() != null ? decision.getAgentType().name() : null,
            decision.getAdvice(),
            decision.getReasoning(),
            formatInstant(decision.getDecidedAt()),
            decision.getModelUsed(),
            decision.getTotalTokens(),
            decision.getRawCostUsd() != null ? decision.getRawCostUsd().toPlainString() : null
        );
    }

    public McpMarketResearchDetailsDto toMarketResearchDetailsDto(MarketResearchDetails details) {
        return toMarketResearchDetailsDto(details, true, true, Integer.MAX_VALUE);
    }

    public McpMarketResearchDetailsDto toMarketResearchDetailsDto(
            MarketResearchDetails details, boolean includeExecutions,
            boolean includeAgentDecisions, int executionLimit) {
        McpMarketResearchDto researchDto = toMarketResearchDto(details.research(), 0);

        List<McpResearchExecutionDto> executionDtos;
        if (includeExecutions) {
            executionDtos = details.executions().stream()
                .limit(executionLimit)
                .map(exec -> toResearchExecutionDto(exec, includeAgentDecisions))
                .toList();
        } else {
            executionDtos = null;
        }

        McpResearchExecutionDto latestDto = details.latestExecution()
            .map(exec -> toResearchExecutionDto(exec, includeAgentDecisions))
            .orElse(null);

        return new McpMarketResearchDetailsDto(
            researchDto,
            executionDtos,
            latestDto,
            details.totalExecutions(),
            details.successfulExecutions()
        );
    }

    // --- Benchmark mappings ---

    public McpBenchmarkDto toBenchmarkDto(Benchmark benchmark, List<Simulation> simulations) {
        return toBenchmarkDto(benchmark, simulations, null);
    }

    public McpBenchmarkDto toBenchmarkDto(Benchmark benchmark, List<Simulation> simulations, List<String> strategyNames) {
        List<String> simulationIds = simulations.stream()
            .map(s -> s.getSimulationId().value().toString())
            .toList();

        int total = simulations.size();
        int completed = (int) simulations.stream()
            .filter(s -> s.getStatus() == SimulationStatus.COMPLETED)
            .count();

        return new McpBenchmarkDto(
            benchmark.getBenchmarkId().value().toString(),
            benchmark.getName(),
            formatInstant(benchmark.getStartDate()),
            formatInstant(benchmark.getEndDate()),
            benchmark.isLive(),
            simulationIds,
            strategyNames,
            total,
            completed,
            formatInstant(benchmark.getCreatedAt()),
            uiLinks.benchmark(benchmark.getBenchmarkId().value().toString())
        );
    }

    // --- Market Data mappings ---

    public McpMarketEventDto toMarketEventDto(MarketEvent event) {
        return toMarketEventDto(event, false);
    }

    public McpMarketEventDto toMarketEventDto(MarketEvent event, boolean excludeRawData) {
        String dataJson = null;
        if (!excludeRawData && event.rawData() != null) {
            try {
                dataJson = objectMapper.writeValueAsString(event.rawData());
            } catch (JsonProcessingException e) {
                dataJson = "{}";
            }
        }

        // Extract close price for PRICE events regardless of excludeRawData
        String closePrice = null;
        if (event.eventType() == EventType.PRICE && event.rawData() != null) {
            Object close = event.rawData().get("close");
            if (close != null) {
                closePrice = close.toString();
            }
        }

        // Extract headline for NEWS/SOCIAL events regardless of excludeRawData
        String headline = null;
        if ((event.eventType() == EventType.NEWS || event.eventType() == EventType.SOCIAL) && event.rawData() != null) {
            Object h = event.rawData().get("headline");
            if (h == null) {
                h = event.rawData().get("title");
            }
            if (h != null) {
                headline = h.toString();
            }
        }

        return new McpMarketEventDto(
            event.id(),
            formatInstant(event.timestamp()),
            event.eventType() != null ? event.eventType().name() : null,
            event.source(),
            event.symbol(),
            dataJson,
            closePrice,
            headline,
            formatInstant(event.ingestedAt())
        );
    }

    /**
     * A backtest's projected cost, for pricing a run before creating one.
     *
     * <p>The note is part of the answer: an estimate quoted without its basis gets repeated to a
     * user as a fact, and this one is a projection over average token counts, not a quote.</p>
     */
    public com.quorumtrading.adapter.mcp.dto.McpCostEstimateDto toCostEstimateDto(
            com.quorumtrading.model.valueobject.CostEstimate estimate) {
        return toCostEstimateDto(estimate, null, null);
    }

    /** Cost estimate plus the wall-clock projection — on a free engine, time IS the cost. */
    public com.quorumtrading.adapter.mcp.dto.McpCostEstimateDto toCostEstimateDto(
            com.quorumtrading.model.valueobject.CostEstimate estimate,
            Long estimatedWallClockSeconds, String timeBasis) {
        return new com.quorumtrading.adapter.mcp.dto.McpCostEstimateDto(
                estimate.tradingDays(),
                estimate.totalConsensusRounds(),
                estimate.totalLLMCalls(),
                estimate.agentsPerRound(),
                estimate.tickerCount(),
                estimate.estimatedCost().toPlainString(),
                estimate.lowerBound().toPlainString(),
                estimate.upperBound().toPlainString(),
                estimate.costPerCall().toPlainString(),
                estimate.modelId(),
                estimate.modelName(),
                "Projection from average token counts per consensus call, priced against "
                        + estimate.modelName() + ". Actual spend varies with prompt and reply "
                        + "length; treat the range as the honest figure rather than the point "
                        + "estimate. No simulation was created and nothing has been charged.",
                estimatedWallClockSeconds,
                timeBasis,
                estimate.dataCoverageWarning());
    }

    public McpIndicatorSnapshotDto toIndicatorSnapshotDto(IndicatorSnapshot snapshot) {
        return toIndicatorSnapshotDto(snapshot, null, null, null);
    }

    public McpIndicatorSnapshotDto toIndicatorSnapshotDto(IndicatorSnapshot snapshot, String autoBackfillNote) {
        return toIndicatorSnapshotDto(snapshot, autoBackfillNote, null, null);
    }

    public McpIndicatorSnapshotDto toIndicatorSnapshotDto(IndicatorSnapshot snapshot, String autoBackfillNote,
                                                           String latestClosePrice, String latestPriceDate) {
        McpIndicatorSnapshotDto.McpMovingAverageDto maDto = null;
        McpIndicatorSnapshotDto.McpRSIDto rsiDto = null;
        McpIndicatorSnapshotDto.McpMACDDto macdDto = null;
        McpIndicatorSnapshotDto.McpBollingerDto bollingerDto = null;

        Map<IndicatorType, IndicatorValue> values = snapshot.values();
        if (values != null) {
            IndicatorValue maValue = values.get(IndicatorType.MOVING_AVERAGE);
            if (maValue instanceof IndicatorValue.MovingAverageValue ma) {
                maDto = new McpIndicatorSnapshotDto.McpMovingAverageDto(
                    ma.sma50() != null ? ma.sma50().toPlainString() : null,
                    ma.sma200() != null ? ma.sma200().toPlainString() : null,
                    ma.ema50() != null ? ma.ema50().toPlainString() : null,
                    ma.ema200() != null ? ma.ema200().toPlainString() : null
                );
            }

            IndicatorValue rsiValue = values.get(IndicatorType.RSI);
            if (rsiValue instanceof IndicatorValue.RSIValue rsi) {
                rsiDto = new McpIndicatorSnapshotDto.McpRSIDto(
                    rsi.rsi14() != null ? rsi.rsi14().toPlainString() : null
                );
            }

            IndicatorValue macdValue = values.get(IndicatorType.MACD);
            if (macdValue instanceof IndicatorValue.MACDValue macd) {
                macdDto = new McpIndicatorSnapshotDto.McpMACDDto(
                    macd.macdLine() != null ? macd.macdLine().toPlainString() : null,
                    macd.signalLine() != null ? macd.signalLine().toPlainString() : null,
                    macd.histogram() != null ? macd.histogram().toPlainString() : null
                );
            }

            IndicatorValue bbValue = values.get(IndicatorType.BOLLINGER_BANDS);
            if (bbValue instanceof IndicatorValue.BollingerValue bb) {
                bollingerDto = new McpIndicatorSnapshotDto.McpBollingerDto(
                    bb.upper() != null ? bb.upper().toPlainString() : null,
                    bb.middle() != null ? bb.middle().toPlainString() : null,
                    bb.lower() != null ? bb.lower().toPlainString() : null
                );
            }
        }

        // One reason per indicator, not the same sentence four times. IndicatorSnapshot.empty
        // stamps a single reason onto every IndicatorType, and flattening the map to a list
        // reproduced it verbatim per type with nothing saying which indicator each concerned —
        // so "No price data available" ×4 read as four separate findings and named none of them.
        // Where the reason really is shared, say it once and name who it applies to.
        List<String> warnings = summariseWarnings(snapshot.warnings());

        return new McpIndicatorSnapshotDto(
            // canonical(), not ticker(): labelling a venue-qualified instrument with its bare
            // root reports it as a different instrument — the US listing of the same company.
            snapshot.ticker() != null ? snapshot.ticker().canonical() : null,
            formatInstant(snapshot.calculatedAt()),
            latestClosePrice,
            latestPriceDate,
            maDto,
            rsiDto,
            macdDto,
            bollingerDto,
            warnings,
            autoBackfillNote
        );
    }

    /**
     * Group indicator warnings by reason and attribute each to the indicators it affects.
     *
     * <p>A cause shared by several indicators is emitted once, prefixed with the indicators it
     * affects ("MOVING_AVERAGE, RSI, MACD, BOLLINGER_BANDS: No price data available") instead of
     * four anonymous repetitions of the same sentence. A warning that concerns a single indicator
     * is already unambiguous and passes through unchanged, so nothing gains a redundant prefix.</p>
     */
    private List<String> summariseWarnings(Map<IndicatorType, String> warnings) {
        if (warnings == null || warnings.isEmpty()) {
            return List.of();
        }
        Map<String, List<String>> byReason = new java.util.LinkedHashMap<>();
        warnings.forEach((type, reason) -> byReason
            .computeIfAbsent(reason == null ? "Unavailable" : reason, r -> new ArrayList<>())
            .add(type.name()));
        return byReason.entrySet().stream()
            .map(e -> e.getValue().size() == 1
                ? e.getKey()
                : String.join(", ", e.getValue()) + ": " + e.getKey())
            .toList();
    }

    public McpIndicatorSnapshotDto toIndicatorSnapshotDto(IndicatorSnapshot snapshot, String autoBackfillNote,
                                                               String latestClosePrice, String latestPriceDate,
                                                               com.quorumtrading.model.valueobject.DividendInfo dividendInfo) {
        McpIndicatorSnapshotDto base = toIndicatorSnapshotDto(snapshot, autoBackfillNote, latestClosePrice, latestPriceDate);
        if (dividendInfo == null) {
            return base;
        }
        return new McpIndicatorSnapshotDto(
            base.ticker(), base.calculatedAt(), base.latestClosePrice(), base.latestPriceDate(),
            base.movingAverage(), base.rsi(), base.macd(), base.bollingerBands(),
            base.warnings(), base.autoBackfillNote(),
            dividendInfo.paysDividends(),
            dividendInfo.annualYieldEstimate() != null ? dividendInfo.annualYieldEstimate().toPlainString() : null,
            dividendInfo.dividendCountLastYear()
        );
    }

    // --- Backfill mappings ---

    public McpBackFillResultDto toBackFillResultDto(BackFillResult result) {
        return toBackFillResultDto(result, null, null, null);
    }

    public McpBackFillResultDto toBackFillResultDto(BackFillResult result, String startDate, String endDate, String granularity) {
        return new McpBackFillResultDto(
            result.ticker(),
            result.assetType(),
            startDate,
            endDate,
            granularity,
            result.recordsInserted(),
            result.recordsSkipped(),
            result.recordsFailed(),
            result.newsRecordsInserted(),
            result.newsRecordsSkipped(),
            result.success(),
            result.errorMessage()
        );
    }

    public McpPriceCoverageDto toPriceCoverageDto(com.quorumtrading.model.valueobject.PriceCoverage coverage) {
        return new McpPriceCoverageDto(
            coverage.ticker(),
            coverage.barCount(),
            coverage.distinctDays(),
            coverage.firstBar() != null ? coverage.firstBar().toString() : null,
            coverage.lastBar() != null ? coverage.lastBar().toString() : null,
            coverage.sources(),
            coverageNote(coverage),
            toCoverageWindowDto(coverage.window())
        );
    }

    /**
     * The note carries the one thing the numbers cannot: what to do next.
     *
     * <p>An unqualified "run backfill_price_data first" was returned for a ticker that had simply
     * not been fetched, for a typo, and for {@code BTC-USD} — a form no backfill can ever satisfy.
     * Form problems no longer reach here at all (see {@code TickerForm}); a venue-qualified symbol
     * still can, and needs its own answer, because backfilling it depends on a provider that is
     * off by default.</p>
     */
    private String coverageNote(com.quorumtrading.model.valueobject.PriceCoverage coverage) {
        var window = coverage.window();
        if (coverage.isEmpty()) {
            String base = "No price bars stored for this ticker — run backfill_price_data first.";
            return coverage.ticker() != null && coverage.ticker().contains(":")
                ? base + " This is a venue-qualified (non-US) symbol; check get_market_data_status"
                    + " for a provider that covers that venue before assuming the backfill can succeed."
                : base;
        }
        if (window != null && !window.isComplete()) {
            return "Stored bars do not cover the requested window: " + window.coveredDays() + " of "
                + window.expectedDays() + " expected days present, " + window.gaps().size()
                + " gap(s). Backfill the missing ranges before using this window for a backtest.";
        }
        if (coverage.barCount() > coverage.distinctDays()) {
            return "Bar count exceeds distinct days — duplicate or intraday bars present.";
        }
        return null;
    }

    private McpPriceCoverageDto.McpCoverageWindowDto toCoverageWindowDto(
            com.quorumtrading.model.valueobject.PriceCoverage.Window window) {
        if (window == null) {
            return null;
        }
        return new McpPriceCoverageDto.McpCoverageWindowDto(
            window.from().toString(),
            window.to().toString(),
            window.expectedDays(),
            window.coveredDays(),
            window.missingDays(),
            window.calendar(),
            window.gaps().stream()
                .map(g -> new McpPriceCoverageDto.McpCoverageGapDto(
                    g.from().toString(), g.to().toString(), g.days()))
                .toList()
        );
    }

    public McpBackfillProgressDto toBackfillProgressDto(BackfillProgress progress) {
        return toBackfillProgressDto(progress, null, null);
    }

    public McpBackfillProgressDto toBackfillProgressDto(BackfillProgress progress, String ticker, String assetType) {
        return new McpBackfillProgressDto(
            progress.jobId() != null ? progress.jobId().toString() : null,
            ticker,
            assetType,
            progress.status() != null ? progress.status().name() : null,
            progress.progressPercentage(),
            formatInstant(progress.startedAt()),
            formatInstant(progress.completedAt()),
            progress.errorMessage(),
            progress.totalRecordsExpected(),
            progress.recordsProcessed()
        );
    }

    // --- Broker Account mappings ---

    public McpBrokerAccountDto toBrokerAccountDto(BrokerAccount account) {
        Money total = account.getTotalBalance();
        Money equity = account.getEquity();
        Money reserved = account.getReservedBalance();
        Money available = account.getAvailableBalance();

        return new McpBrokerAccountDto(
            account.getId().value().toString(),
            account.getName(),
            account.getBrokerType().name(),
            account.getStatus().name(),
            formatMoney(total),
            equity != null ? formatMoney(equity) : null,
            formatMoney(reserved),
            formatMoney(available),
            total != null ? total.currency().name() : null,
            formatInstant(account.getCreatedAt()),
            formatInstant(account.getUpdatedAt())
        );
    }

    // --- Portfolio Evaluation mapping ---

    public McpPortfolioEvaluationDto toPortfolioEvaluationDto(SimulationEvaluationResult result) {
        return toPortfolioEvaluationDto(result, null, null);
    }

    public McpPortfolioEvaluationDto toPortfolioEvaluationDto(
            SimulationEvaluationResult result,
            McpPortfolioSnapshotDto latestSnapshot,
            String returnPercent) {
        return new McpPortfolioEvaluationDto(
            result.simulationId() != null ? result.simulationId().value().toString() : null,
            result.success(),
            result.skipped(),
            result.errorMessage(),
            latestSnapshot,
            returnPercent
        );
    }

    @SuppressWarnings("unchecked")
    Map<String, Object> extractFrontmatter(String content) {
        if (content == null) return null;
        Matcher matcher = FRONTMATTER_PATTERN.matcher(content);
        if (!matcher.find()) return null;
        String yamlStr = matcher.group(1).trim();
        Yaml yaml = new Yaml();
        Object parsed = yaml.load(yamlStr);
        return parsed instanceof Map ? (Map<String, Object>) parsed : null;
    }

    @SuppressWarnings("unchecked")
    /**
     * Renders a frontmatter ticker list as plain symbols.
     *
     * <p>An entry may be a bare token or the documented map form
     * ({@code - ticker: SIEGY / currency: USD}). Calling {@code toString()} on the
     * latter yields {@code "{ticker=SIEGY, currency=USD}"} — a Java map's debug
     * representation presented to the user as the ticker itself. It appeared in
     * {@code list_strategies}, in {@code get_strategy}, and in the symbol list a
     * simulation reports, which made a correctly-parsed strategy look corrupt and
     * led auditors to conclude the map form was bypassing validation. It is not:
     * the domain parser reads the map properly and validates the ticker inside it.
     * Only this rendering was wrong.</p>
     *
     * <p>Key precedence matches the domain parser ({@code ticker}, then
     * {@code symbol}), so the two never disagree about which field is the symbol.</p>
     */
    List<String> extractTickerList(Map<String, Object> frontmatter, String key) {
        Object value = frontmatter.get(key);
        if (value instanceof List<?> list) {
            return list.stream()
                .map(McpDtoMapper::tickerTokenOf)
                .filter(t -> t != null && !t.isBlank())
                .sorted()
                .toList();
        }
        return null;
    }

    /** Extracts the symbol from a bare token or a {@code {ticker:, currency:}} entry. */
    private static String tickerTokenOf(Object entry) {
        if (entry == null) {
            return null;
        }
        if (entry instanceof Map<?, ?> map) {
            Object ticker = map.get("ticker");
            if (ticker == null) {
                ticker = map.get("symbol");
            }
            return ticker == null ? null : ticker.toString().trim();
        }
        return entry.toString().trim();
    }

    /**
     * Normalize consensus method strings, mapping removed values to their replacements.
     * WEIGHTED, CONFIDENCE_BASED → MAJORITY.
     */
    String normalizeConsensusMethod(String raw) {
        if (raw == null) return null;
        String upper = raw.toUpperCase().trim();
        return switch (upper) {
            case "WEIGHTED", "WEIGHTED_AVERAGE", "CONFIDENCE_BASED", "MAJORITY_VOTE" -> "MAJORITY";
            default -> upper;
        };
    }

    /**
     * The agents that will actually vote for this strategy.
     *
     * <p>No MCP tool exposed this: {@code get_strategy} echoed the raw markdown back and nothing
     * reported the interpreted set, so an author could not tell how many agents a run would use —
     * and therefore what it would cost — without paying for a run and reading a decision's notes.
     * An omitted or empty {@code agents} key means all three.</p>
     */
    private List<String> extractEffectiveAgents(Map<String, Object> frontmatter) {
        List<String> all = List.of("TECHNICAL", "FUNDAMENTAL", "SENTIMENT");
        Object raw = frontmatter.get("agents");
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            return all;
        }
        List<String> named = new java.util.ArrayList<>();
        for (Object item : list) {
            String name;
            if (item instanceof Map<?, ?> map) {
                Object type = map.get("TYPE");
                if (type == null) {
                    type = map.get("type");
                }
                name = type == null ? null : String.valueOf(type);
            } else {
                name = String.valueOf(item);
            }
            if (name == null) continue;
            String upper = name.toUpperCase().trim();
            if (all.contains(upper) && !named.contains(upper)) {
                named.add(upper);
            }
        }
        return named.isEmpty() ? all : named;
    }

    /**
     * Warn that a configured agent may not actually vote.
     *
     * <p>FUNDAMENTAL needs at least 2 stored news events for the period and SENTIMENT at least 3;
     * below that they abstain silently. A crypto backtest usually has neither, so a strategy
     * configured for three-agent consensus can run its whole course on TECHNICAL alone with the
     * orchestrator largely restating it — visible previously only in a decision's notes text.</p>
     */
    private String agentAvailabilityNote(List<String> effectiveAgents) {
        if (effectiveAgents == null) {
            return null;
        }
        boolean fundamental = effectiveAgents.contains("FUNDAMENTAL");
        boolean sentiment = effectiveAgents.contains("SENTIMENT");
        if (!fundamental && !sentiment) {
            return null;
        }
        StringBuilder note = new StringBuilder();
        if (fundamental && sentiment) {
            note.append("FUNDAMENTAL and SENTIMENT abstain unless at least 2 and 3 news events "
                      + "respectively are stored for the evaluated period.");
        } else if (fundamental) {
            note.append("FUNDAMENTAL abstains unless at least 2 news events are stored for the "
                      + "evaluated period.");
        } else {
            note.append("SENTIMENT abstains unless at least 3 news events are stored for the "
                      + "evaluated period.");
        }
        note.append(" Backtests over periods with no stored news — crypto especially — will run on "
                  + "the remaining agents. Check a decision's notes with get_consensus_with_agents "
                  + "to see who actually voted.");
        return note.toString();
    }

    private String formatInstant(Instant instant) {
        return instant != null ? instant.toString() : null;
    }

    /**
     * Renders one already-ranked benchmark entry. The entry carries every field the DTO needs,
     * so this does not re-look-up the simulation — the previous overload took a {@code
     * Simulation} the caller had to keep in a side map, which was a null waiting to happen if
     * the two queries ever disagreed.
     */
    public McpBenchmarkEntryDto toBenchmarkEntryDto(BenchmarkLeaderboard.Entry entry) {
        return new McpBenchmarkEntryDto(
            entry.simulationId().value().toString(),
            entry.simulationName(),
            entry.strategyName(),
            formatMoney(entry.initialCash()),
            entry.tradingMode(),
            formatMoney(entry.currentValue()),
            entry.returnPercent() != null ? entry.returnPercent().toPlainString() : null,
            entry.status().name(),
            entry.executedTradeCount()
        );
    }

    private String formatMoney(Money money) {
        return money != null ? money.amount().toPlainString() : null;
    }
}
