package com.quorumtrading.application.service;

import com.quorumtrading.application.chat.ChatToolExecutor;
import com.quorumtrading.application.chat.ResearchToolRegistry;
import com.quorumtrading.application.event.ResearchAgentCompletedEvent;
import com.quorumtrading.application.event.ResearchAgentStartedEvent;
import com.quorumtrading.application.event.ResearchExecutionCompleted;
import com.quorumtrading.application.orchestration.agents.LLMConfig;
import com.quorumtrading.application.orchestration.agents.LLMQueryResult;
import com.quorumtrading.application.port.out.ConversationalLLMPort;
import com.quorumtrading.application.port.out.EventPublisher;
import com.quorumtrading.application.port.out.LLMPort;
import com.quorumtrading.application.port.out.MarketEventRepository;
import com.quorumtrading.application.port.out.ResearchExecutionRepository;
import com.quorumtrading.application.port.out.StrategyRepository;
import com.quorumtrading.application.usecase.CreateStrategyUseCase;
import com.quorumtrading.application.usecase.command.CreateStrategyCommand;
import com.quorumtrading.model.marketdata.MarketEvent;
import com.quorumtrading.model.entity.MarketResearch;
import com.quorumtrading.model.enums.EventType;
import com.quorumtrading.model.entity.ResearchExecution;
import com.quorumtrading.model.entity.Strategy;
import com.quorumtrading.model.enums.InvestmentType;
import com.quorumtrading.model.enums.ResearchDecisionType;
import com.quorumtrading.model.orchestration.StrategyConfig;
import com.quorumtrading.model.orchestration.StrategyConfigParser;
import com.quorumtrading.model.strategy.MarketContext;
import com.quorumtrading.model.strategy.StrategyTemplate;
import com.quorumtrading.model.strategy.StrategyTemplateType;
import com.quorumtrading.model.strategy.TemplateParameters;
import com.quorumtrading.model.valueobject.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jboss.logging.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.concurrent.Executor;

/**
 * Orchestrator for market research execution.
 * Coordinates LLM agents to analyze markets, design strategies, and assess viability.
 *
 * <p><b>Strategy Template Integration:</b></p>
 * When indicators are enabled, the orchestrator can use pre-coded strategy templates
 * instead of LLM-generated strategies. The template selector LLM analyzes market conditions
 * and recommends either a specific template or falls back to LLM generation.
 *
 * Framework-agnostic, uses constructor injection.
 */
public class MarketResearchOrchestrator {

    private static final Logger LOG = Logger.getLogger(MarketResearchOrchestrator.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final int MAX_TOOL_ITERATIONS = 10;

    private final LLMPort llmPort;
    private final StrategyRepository strategyRepository;
    private final ResearchExecutionRepository executionRepository;
    private final EventPublisher<ResearchExecutionCompleted> executionCompletedPublisher;
    private final EventPublisher<ResearchAgentStartedEvent> agentStartedPublisher;
    private final EventPublisher<ResearchAgentCompletedEvent> agentCompletedPublisher;
    private final String researchModel;
    private final String researchModelOnline;

    /**
     * Per-research-agent model resolution on the shared chain. Nullable: lightweight tests still
     * construct this orchestrator with a flat default, which is the pre-unification behaviour.
     */
    private ResearchModelResolver researchModelResolver;
    private final double temperature;
    private final int maxTokens;

    // Template-based strategy generation dependencies (optional)
    private final StrategyTemplateRegistry templateRegistry;
    private final boolean indicatorsEnabled;
    private final int templateConfidenceThreshold;

    // Optional: route strategy creation through CreateStrategyUseCase for broker validation
    private final CreateStrategyUseCase createStrategyUseCase;

    // Stored news data for enriching research agent prompts (optional)
    private final MarketEventRepository marketEventRepository;

    // Tool-calling support for research agents (optional)
    private final ConversationalLLMPort conversationalLLMPort;
    private final ChatToolExecutor chatToolExecutor;
    private final Executor agentExecutor;

    // Notification dispatcher (optional, wired post-construct by the bootstrap).
    // The 3 constructor overloads are already crowded; keep this as a setter to
    // avoid yet another permutation and let lightweight tests skip it.
    private com.quorumtrading.application.usecase.notification.DispatchNotificationUseCase dispatchNotificationUseCase;

    /**
     * Wires per-agent model resolution. Set post-construct for the same reason as the notification
     * dispatcher: the constructor overloads are already crowded.
     */
    public void setResearchModelResolver(ResearchModelResolver researchModelResolver) {
        this.researchModelResolver = researchModelResolver;
    }

    public void setDispatchNotificationUseCase(
            com.quorumtrading.application.usecase.notification.DispatchNotificationUseCase dispatcher) {
        this.dispatchNotificationUseCase = dispatcher;
    }

    /**
     * Full constructor with template-based strategy generation, agent progress events,
     * optional CreateStrategyUseCase for broker-validated strategy creation,
     * and optional tool-calling support for research agents.
     */
    public MarketResearchOrchestrator(
        LLMPort llmPort,
        StrategyRepository strategyRepository,
        ResearchExecutionRepository executionRepository,
        EventPublisher<ResearchExecutionCompleted> executionCompletedPublisher,
        EventPublisher<ResearchAgentStartedEvent> agentStartedPublisher,
        EventPublisher<ResearchAgentCompletedEvent> agentCompletedPublisher,
        String researchModel,
        String researchModelOnline,
        double temperature,
        int maxTokens,
        StrategyTemplateRegistry templateRegistry,
        boolean indicatorsEnabled,
        int templateConfidenceThreshold,
        CreateStrategyUseCase createStrategyUseCase,
        ConversationalLLMPort conversationalLLMPort,
        ChatToolExecutor chatToolExecutor,
        Executor agentExecutor,
        MarketEventRepository marketEventRepository
    ) {
        this.llmPort = llmPort;
        this.strategyRepository = strategyRepository;
        this.executionRepository = executionRepository;
        this.executionCompletedPublisher = executionCompletedPublisher;
        this.agentStartedPublisher = agentStartedPublisher;
        this.agentCompletedPublisher = agentCompletedPublisher;
        this.researchModel = researchModel;
        this.researchModelOnline = researchModelOnline;
        this.temperature = temperature;
        this.maxTokens = maxTokens;
        this.templateRegistry = templateRegistry;
        this.indicatorsEnabled = indicatorsEnabled;
        this.templateConfidenceThreshold = templateConfidenceThreshold;
        this.createStrategyUseCase = createStrategyUseCase;
        this.conversationalLLMPort = conversationalLLMPort;
        this.chatToolExecutor = chatToolExecutor;
        this.agentExecutor = agentExecutor;
        this.marketEventRepository = marketEventRepository;
    }

    /**
     * Execute research with a pre-generated execution ID.
     * <p>
     * This method fires progress events at each agent step when event publishers are available:
     * - ResearchAgentStartedEvent before each agent call
     * - ResearchAgentCompletedEvent after each agent call (with duration)
     * - ResearchExecutionCompleted when execution finishes (success or failure)
     * </p>
     *
     * @param research    The market research configuration to execute
     * @param executionId The pre-generated execution ID for event correlation
     * @return The research execution result
     */
    public ResearchExecution executeResearch(MarketResearch research, UUID executionId) {
        return executeResearch(research, executionId, null);
    }

    /**
     * Execute research with a pre-generated execution ID and optional user model preference.
     * <p>
     * This method fires progress events at each agent step when event publishers are available:
     * - ResearchAgentStartedEvent before each agent call
     * - ResearchAgentCompletedEvent after each agent call (with duration)
     * - ResearchExecutionCompleted when execution finishes (success or failure)
     * </p>
     *
     * @param research         The market research configuration to execute
     * @param executionId      The pre-generated execution ID for event correlation
     * @param userModelOverride Optional user-preferred model (if null, uses system default)
     * @return The research execution result
     */
    public ResearchExecution executeResearch(MarketResearch research, UUID executionId, String userModelOverride) {
        LOG.infof("Executing market research: %s (%s) with execution ID: %s",
            research.getName(), research.getId(), executionId);

        // Determine effective model - user override if provided, otherwise system default
        String effectiveModel = getEffectiveModel(userModelOverride, research.getId());

        // Declare agent results outside try block so catch block can persist them on failure
        String newsAnalysis = null;
        LLMUsage newsUsage = null;
        String viabilityAssessment = null;
        LLMUsage viabilityUsage = null;
        String viabilityToolCalls = null;
        String strategyMarkdown = null;
        LLMUsage strategyUsage = null;
        String strategyToolCalls = null;

        try {
            // ─── Step 1: News Analysis + Viability Assessment in PARALLEL ───
            // These are independent: news discovers opportunities via web search,
            // viability checks market conditions and asset tradability via tools.
            fireAgentStarted(research.getId(), executionId, ResearchAgentType.NEWS_SENTIMENT_ANALYST);
            fireAgentStarted(research.getId(), executionId, ResearchAgentType.VIABILITY_ASSESSOR);
            long parallelStartTime = System.currentTimeMillis();

            // Run news analyst in background thread (must use agentExecutor for Quarkus CDI context)
            Executor exec = agentExecutor != null ? agentExecutor : Runnable::run;
            CompletableFuture<LLMQueryResult> newsFuture = CompletableFuture.supplyAsync(() ->
                queryAgentWithUsage(
                    "news-sentiment-analyst",
                    buildContext(research, null, null),
                    configFor(research, ResearchAgentType.NEWS_SENTIMENT_ANALYST,
                            effectiveModel, userModelOverride, true)
                ),
                exec
            ).exceptionally(ex -> {
                LOG.warnf(ex, "News analyst failed for research %s, continuing with empty analysis", research.getId());
                return LLMQueryResult.of("News analysis unavailable due to agent failure.", com.quorumtrading.model.valueobject.LLMUsage.ZERO, effectiveModel);
            });

            // Run viability assessor in background thread (with tools)
            CompletableFuture<AgentResult> viabilityFuture = CompletableFuture.supplyAsync(() ->
                queryAgentWithToolsOrFallback(
                    "viability-assessor",
                    buildContext(research, null, null),
                    configFor(research, ResearchAgentType.VIABILITY_ASSESSOR,
                            effectiveModel, userModelOverride, true),
                    research.getOwnerId()
                ),
                exec
            ).exceptionally(ex -> {
                // Logged at ERROR, not WARN: the run completes, but its verdict is not a market
                // judgement. Score 0 sits below every threshold, so downstream this reads as "the
                // AI assessed the market and rejected it" — the reasoning must say plainly that no
                // assessment happened, or users rewrite their instructions forever chasing a
                // verdict the model never produced.
                LOG.errorf(ex, "[research=%s] Viability assessor did not run; recording a failed"
                    + " assessment (this is not a market judgement)", research.getId());
                String reason = ex.getMessage() != null ? ex.getMessage().replace("\"", "'") : "unknown error";
                return AgentResult.of("{\"viabilityScore\": 0, \"recommendation\": \"REJECT\","
                    + " \"reasoning\": \"ASSESSMENT FAILED - this is not a verdict on the market."
                    + " The viability agent could not be reached, so no opportunity analysis was"
                    + " performed and strategy design was skipped. Check the configured LLM engine"
                    + " rather than the research instructions. Underlying error: "
                    + reason + "\"}", com.quorumtrading.model.valueobject.LLMUsage.ZERO);
            });

            // Wait for both to complete
            CompletableFuture.allOf(newsFuture, viabilityFuture).join();

            LLMQueryResult newsResult = newsFuture.get();
            newsAnalysis = newsResult.content();
            newsUsage = newsResult.usage();

            long newsDuration = System.currentTimeMillis() - parallelStartTime;
            fireAgentCompleted(research.getId(), executionId, ResearchAgentType.NEWS_SENTIMENT_ANALYST, newsDuration);
            LOG.infof("[research=%s] News analysis complete: %d chars in %dms (cost: $%.6f)", research.getId(), newsAnalysis.length(), newsDuration, newsUsage.rawCost());

            AgentResult viabilityAgentResult = viabilityFuture.get();
            viabilityAssessment = viabilityAgentResult.content();
            viabilityUsage = viabilityAgentResult.usage();
            viabilityToolCalls = viabilityAgentResult.toolCallsJson();

            long viabilityDuration = System.currentTimeMillis() - parallelStartTime;
            fireAgentCompleted(research.getId(), executionId, ResearchAgentType.VIABILITY_ASSESSOR, viabilityDuration);
            LOG.infof("[research=%s] Viability assessment complete: %d chars in %dms (cost: $%.6f)", research.getId(), viabilityAssessment.length(), viabilityDuration, viabilityUsage.rawCost());

            // Parse viability assessment JSON to extract score
            int viabilityScore;
            String viabilityRecommendation;
            // Distinguishes "the assessor scored this market at 0" from "we could not read the
            // assessor's answer". Both stop the run, but only one is an assessment — recording
            // an unreadable response as a hard zero reports a judgement that was never made,
            // and a mixed or even positive assessment ends up filed as zero-viability.
            boolean viabilityUnreadable = false;
            try {
                String cleanedViability = cleanJsonResponse(viabilityAssessment);
                JsonNode viabilityJson = parseJsonWithContext(cleanedViability, "viability-assessor");
                viabilityScore = viabilityJson.get("viabilityScore").asInt();
                viabilityRecommendation = viabilityJson.get("recommendation").asText();
            } catch (IllegalStateException e) {
                LOG.errorf("[research=%s] Viability assessor returned an unparseable response; "
                        + "halting this run rather than recording a fabricated score of 0: %s",
                    research.getId(), e.getMessage());
                viabilityScore = 0;
                viabilityRecommendation = "UNKNOWN";
                viabilityUnreadable = true;
            }

            LOG.infof("[research=%s] Parsed viability score: %d, recommendation: %s", research.getId(), viabilityScore, viabilityRecommendation);

            // ─── Step 2: Viability gate — skip strategy design if not viable ───
            if (viabilityUnreadable || viabilityScore < 70 || "REJECT".equals(viabilityRecommendation)) {
                LOG.infof("[research=%s] Stopping before strategy design (score=%d, recommendation=%s, readable=%s)",
                    research.getId(), viabilityScore, viabilityRecommendation, !viabilityUnreadable);

                String reasoning = viabilityUnreadable
                    ? "The viability assessor's response could not be parsed, so market viability "
                      + "could not be determined. Strategy design was skipped. This is a failure to "
                      + "read the assessment, NOT a judgement that the market lacks opportunity — "
                      + "re-run the research to get an actual assessment."
                    : String.format(
                        "Market viability score of %d is below the threshold of 70. Recommendation: %s. "
                        + "Strategy design was skipped to save resources.",
                        viabilityScore, viabilityRecommendation);

                ResearchExecution execution = new ResearchExecution(
                    executionId,
                    research.getId(),
                    ResearchOutcome.NO_OPPORTUNITY,
                    reasoning,
                    // Null, not zero: no score was produced. A stored 0 is indistinguishable
                    // from a real zero-viability verdict in every downstream reader.
                    viabilityUnreadable ? null : new ViabilityScore(BigDecimal.valueOf(viabilityScore)),
                    null,
                    null
                );

                // Save agent decisions collected so far
                execution.addAgentDecision(ResearchAgentType.NEWS_SENTIMENT_ANALYST, newsAnalysis, "News and sentiment analysis", newsUsage);
                execution.addAgentDecision(ResearchAgentType.VIABILITY_ASSESSOR, viabilityAssessment, "Viability assessment", viabilityUsage, viabilityToolCalls);
                execution.addLLMUsage(newsUsage);
                execution.addLLMUsage(viabilityUsage);

                execution = executionRepository.save(execution);

                ResearchExecutionCompleted event = ResearchExecutionCompleted.of(
                    execution.getId(), research.getId(), null, research.isEnableAutoBacktest());
                executionCompletedPublisher.publish(event);

                LOG.infof("Research execution complete (no opportunity): %s", execution.getId());
                return execution;
            }

            // ─── Step 3: Strategy Design (only if viability approved) ───
            fireAgentStarted(research.getId(), executionId, ResearchAgentType.STRATEGY_DESIGNER);
            long strategyStartTime = System.currentTimeMillis();

            try {
                StrategyGenerationResult strategyResult = generateStrategyWithUsage(research, newsAnalysis,
                        modelFor(research, ResearchAgentType.STRATEGY_DESIGNER, effectiveModel, userModelOverride));
                strategyMarkdown = strategyResult.markdown();
                strategyUsage = strategyResult.usage();
                strategyToolCalls = strategyResult.toolCallsJson();

                long strategyDuration = System.currentTimeMillis() - strategyStartTime;
                fireAgentCompleted(research.getId(), executionId, ResearchAgentType.STRATEGY_DESIGNER, strategyDuration);
                LOG.infof("Strategy design complete: %d chars in %dms (cost: $%.6f)", strategyMarkdown.length(), strategyDuration, strategyUsage.rawCost());
            } catch (Exception e) {
                long strategyDuration = System.currentTimeMillis() - strategyStartTime;
                LOG.warnf(e, "Strategy designer failed for research %s after %dms, marking as NO_OPPORTUNITY", research.getId(), strategyDuration);

                // Save partial results as NO_OPPORTUNITY since we can't generate a strategy
                ResearchExecution partialExecution = new ResearchExecution(
                    executionId, research.getId(),
                    ResearchOutcome.NO_OPPORTUNITY,
                    "Strategy design failed: " + e.getMessage(),
                    new com.quorumtrading.model.valueobject.ViabilityScore(java.math.BigDecimal.valueOf(viabilityScore)),
                    null, null
                );
                partialExecution.addAgentDecision(ResearchAgentType.NEWS_SENTIMENT_ANALYST, newsAnalysis, null, newsUsage);
                partialExecution.addAgentDecision(ResearchAgentType.VIABILITY_ASSESSOR, viabilityAssessment, null, viabilityUsage, viabilityToolCalls);
                if (newsUsage != null) partialExecution.addLLMUsage(newsUsage);
                if (viabilityUsage != null) partialExecution.addLLMUsage(viabilityUsage);
                partialExecution = executionRepository.save(partialExecution);
                executionCompletedPublisher.publish(ResearchExecutionCompleted.of(
                    partialExecution.getId(), research.getId(), null, false));
                return partialExecution;
            }

            // ─── Step 4: Coordinator Decision ───
            String coordTargetAssets = research.getTargetAssets().isEmpty()
                ? "(discovered from research)"
                : research.getTargetAssets().stream()
                    .map(Symbol::ticker)
                    .collect(Collectors.joining(", "));
            Map<String, String> coordinatorContext = Map.of(
                "targetAssets", coordTargetAssets,
                "interval", research.getInterval().toString(),
                "currentDate", java.time.Instant.now().toString(),
                "newsAnalysis", newsAnalysis,
                "strategyDesign", strategyMarkdown,
                "viabilityAssessment", viabilityAssessment,
                "viabilityScore", String.valueOf(viabilityScore)
            );

            String coordinatorResponse = queryAgent(
                "researcher-coordinator",
                coordinatorContext,
                noWebSearch(effectiveModel)
            );
            LOG.infof("[research=%s] researcher-coordinator assessment complete: %d chars", research.getId(), coordinatorResponse.length());

            // Parse coordinator decision
            String cleanedResponse = cleanJsonResponse(coordinatorResponse);
            JsonNode decision = parseJsonWithContext(cleanedResponse, "researcher-coordinator");

            ResearchDecisionType decisionType;
            try {
                decisionType = ResearchDecisionType.valueOf(decision.get("decision").asText());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(
                    "Invalid decision type from LLM: " + decision.get("decision").asText() +
                    ". Expected: CREATE_STRATEGY or NO_OPPORTUNITY", e);
            }

            String reasoning = decision.get("reasoning").asText();

            // Create execution record
            ResearchExecution execution;
            UUID strategyId = null;

            if (decisionType == ResearchDecisionType.CREATE_STRATEGY) {
                // Strip any conversational wrapper the designer put around the document before
                // anything tries to parse it as frontmatter.
                String finalStrategyMarkdown = stripNonFrontmatterPreamble(strategyMarkdown, research.getId());

                // Patch missing evaluation timing fields (LLM sometimes omits these)
                finalStrategyMarkdown = patchMissingEvaluationTiming(finalStrategyMarkdown, research.getId());

                // Validate strategy markdown format BEFORE creating Strategy entity
                validateStrategyMarkdown(finalStrategyMarkdown);

                UserId strategyOwnerId = research.getOwnerId();
                String strategyName = extractStrategyName(finalStrategyMarkdown);

                if (createStrategyUseCase != null) {
                    try {
                        StrategyId createdId = createStrategyUseCase.execute(
                            new CreateStrategyCommand(strategyName, finalStrategyMarkdown, strategyOwnerId)
                        );
                        strategyId = createdId.value();
                        LOG.infof("[research=%s] Strategy created via CreateStrategyUseCase (broker-validated): %s", research.getId(), createdId);
                    } catch (IllegalArgumentException e) {
                        LOG.warnf("Broker validation failed, saving without validation: %s", e.getMessage());
                        Strategy strategy = new Strategy(
                            new StrategyId(UUID.randomUUID()),
                            strategyName,
                            finalStrategyMarkdown,
                            strategyOwnerId
                        );
                        strategy.validate();
                        strategy = strategyRepository.save(strategy);
                        strategyId = strategy.getId().value();
                    }
                } else {
                    Strategy strategy = new Strategy(
                        new StrategyId(UUID.randomUUID()),
                        strategyName,
                        finalStrategyMarkdown,
                        strategyOwnerId
                    );
                    strategy.validate();
                    strategy = strategyRepository.save(strategy);
                    strategyId = strategy.getId().value();
                }

                execution = new ResearchExecution(
                    executionId,
                    research.getId(),
                    ResearchOutcome.STRATEGY_CREATED,
                    reasoning,
                    new ViabilityScore(BigDecimal.valueOf(viabilityScore)),
                    strategyId,
                    null
                );

                LOG.infof("[research=%s] Strategy created: %s (viability: %d)", research.getId(), strategyName, viabilityScore);

                // MARKET_RESEARCH_STRATEGY_CREATED — best-effort. A failed dispatch must
                // not roll back the strategy creation. Both creation branches above
                // converge here, so this single call covers them.
                if (dispatchNotificationUseCase != null && strategyId != null) {
                    try {
                        java.util.Map<String, Object> payload = new java.util.HashMap<>();
                        payload.put("marketResearchId", research.getId().toString());
                        payload.put("executionId", executionId.toString());
                        payload.put("strategyId", strategyId.toString());
                        payload.put("strategyName", strategyName);
                        payload.put("viabilityScore", String.valueOf(viabilityScore));
                        String title = "New strategy from research: " + strategyName;
                        String body = "Your market research produced a new strategy"
                                + " (viability " + viabilityScore + ").";
                        dispatchNotificationUseCase.execute(
                            new com.quorumtrading.application.usecase.command.DispatchNotificationCommand(
                                strategyOwnerId,
                                com.quorumtrading.model.enums.NotificationType.MARKET_RESEARCH_STRATEGY_CREATED,
                                title,
                                body,
                                payload,
                                strategyId
                            )
                        );
                    } catch (Exception notifyEx) {
                        LOG.warnf("Failed to dispatch MARKET_RESEARCH_STRATEGY_CREATED for research %s strategy %s: %s",
                            research.getId(), strategyId, notifyEx.getMessage());
                    }
                }
            } else {
                execution = new ResearchExecution(
                    executionId,
                    research.getId(),
                    ResearchOutcome.NO_OPPORTUNITY,
                    reasoning,
                    new ViabilityScore(BigDecimal.valueOf(viabilityScore)),
                    null,
                    null
                );

                LOG.infof("[research=%s] No opportunity found (viability: %d): %s", research.getId(), viabilityScore, reasoning);
            }

            // Save agent decisions with LLM usage + tool calls tracking
            execution.addAgentDecision(ResearchAgentType.NEWS_SENTIMENT_ANALYST, newsAnalysis, "News and sentiment analysis", newsUsage);
            execution.addAgentDecision(ResearchAgentType.VIABILITY_ASSESSOR, viabilityAssessment, "Viability assessment", viabilityUsage, viabilityToolCalls);
            execution.addAgentDecision(ResearchAgentType.STRATEGY_DESIGNER, strategyMarkdown, "Strategy design", strategyUsage, strategyToolCalls);

            execution.addLLMUsage(newsUsage);
            execution.addLLMUsage(viabilityUsage);
            execution.addLLMUsage(strategyUsage);

            execution = executionRepository.save(execution);

            ResearchExecutionCompleted event = ResearchExecutionCompleted.of(
                execution.getId(),
                research.getId(),
                strategyId,
                research.isEnableAutoBacktest()
            );
            executionCompletedPublisher.publish(event);

            LOG.infof("Research execution complete: %s (outcome: %s)", execution.getId(), execution.getOutcome());
            return execution;

        } catch (Exception e) {
            LOG.errorf(e, "Research execution failed for: %s", research.getId());

            ResearchExecution execution = new ResearchExecution(
                executionId,
                research.getId(),
                ResearchOutcome.FAILED,
                "Execution failed: " + e.getMessage(),
                null,
                null,
                null
            );

            // Persist any agent decisions collected before the failure
            if (newsAnalysis != null) {
                execution.addAgentDecision(ResearchAgentType.NEWS_SENTIMENT_ANALYST, newsAnalysis, "News and sentiment analysis", newsUsage);
                if (newsUsage != null) execution.addLLMUsage(newsUsage);
            }
            if (viabilityAssessment != null) {
                execution.addAgentDecision(ResearchAgentType.VIABILITY_ASSESSOR, viabilityAssessment, "Viability assessment", viabilityUsage, viabilityToolCalls);
                if (viabilityUsage != null) execution.addLLMUsage(viabilityUsage);
            }
            if (strategyMarkdown != null) {
                execution.addAgentDecision(ResearchAgentType.STRATEGY_DESIGNER, strategyMarkdown, "Strategy design", strategyUsage, strategyToolCalls);
                if (strategyUsage != null) execution.addLLMUsage(strategyUsage);
            }

            execution = executionRepository.save(execution);

            ResearchExecutionCompleted event = ResearchExecutionCompleted.of(
                execution.getId(),
                research.getId(),
                null,
                research.isEnableAutoBacktest()
            );
            executionCompletedPublisher.publish(event);

            return execution;
        }
    }

    /**
     * Fire agent started event if publisher is available.
     */
    private void fireAgentStarted(UUID researchId, UUID executionId, ResearchAgentType agentType) {
        if (agentStartedPublisher != null) {
            agentStartedPublisher.publish(
                ResearchAgentStartedEvent.of(researchId, executionId, agentType)
            );
            LOG.debugf("Published ResearchAgentStartedEvent: %s for execution %s", agentType, executionId);
        }
    }

    /**
     * Fire agent completed event if publisher is available.
     */
    private void fireAgentCompleted(UUID researchId, UUID executionId, ResearchAgentType agentType, long durationMs) {
        if (agentCompletedPublisher != null) {
            agentCompletedPublisher.publish(
                ResearchAgentCompletedEvent.of(researchId, executionId, agentType, durationMs)
            );
            LOG.debugf("Published ResearchAgentCompletedEvent: %s completed in %dms for execution %s",
                agentType, durationMs, executionId);
        }
    }

    /**
     * Result of strategy generation with usage metrics.
     */
    private record StrategyGenerationResult(String markdown, LLMUsage usage, String toolCallsJson) {
        static StrategyGenerationResult of(String markdown, LLMUsage usage) {
            return new StrategyGenerationResult(markdown, usage != null ? usage : LLMUsage.ZERO, null);
        }
        static StrategyGenerationResult of(String markdown, LLMUsage usage, String toolCallsJson) {
            return new StrategyGenerationResult(markdown, usage != null ? usage : LLMUsage.ZERO, toolCallsJson);
        }
    }

    /**
     * Generate strategy using template-based or LLM-based approach with usage tracking.
     * Template-based generation is attempted first when indicators are enabled.
     *
     * @param effectiveModel The model to use for LLM calls
     */
    private StrategyGenerationResult generateStrategyWithUsage(MarketResearch research, String newsAnalysis, String effectiveModel) {
        if (indicatorsEnabled && templateRegistry != null) {
            try {
                return generateStrategyWithTemplateFallbackAndUsage(research, newsAnalysis, effectiveModel);
            } catch (Exception e) {
                LOG.warnf(e, "Template-based strategy generation failed, falling back to LLM: %s", e.getMessage());
            }
        }

        // Fallback: Pure LLM generation
        return generateLLMStrategyWithUsage(research, newsAnalysis, effectiveModel);
    }

    /**
     * Generate strategy using template-based or LLM-based approach (legacy).
     * Template-based generation is attempted first when indicators are enabled.
     */
    private String generateStrategy(MarketResearch research, String newsAnalysis, String effectiveModel) {
        return generateStrategyWithUsage(research, newsAnalysis, effectiveModel).markdown();
    }

    /**
     * Attempt template-based strategy generation with fallback to LLM (with usage tracking).
     *
     * @param effectiveModel The model to use for LLM calls
     */
    private StrategyGenerationResult generateStrategyWithTemplateFallbackAndUsage(MarketResearch research, String newsAnalysis, String effectiveModel) {
        // Step 1: Call template selector LLM to determine best approach
        String selectorTargetAssets = research.getTargetAssets().isEmpty()
            ? "(to be discovered)"
            : research.getTargetAssets().stream()
                .map(Symbol::ticker)
                .collect(Collectors.joining(", "));
        Map<String, String> selectorContext = Map.of(
            "targetAssets", selectorTargetAssets,
            "interval", research.getInterval().toString(),
            "newsAnalysis", newsAnalysis,
            "rationale", research.getMarkdownInstructions() != null ? research.getMarkdownInstructions() : "Market opportunity identified"
        );

        LLMQueryResult selectorResult = queryAgentWithUsage(
            "template-selector",
            selectorContext,
            noWebSearch(effectiveModel)
        );
        String selectorResponse = selectorResult.content();
        LLMUsage selectorUsage = selectorResult.usage();

        LOG.infof("[research=%s] Template selector response: %s (cost: $%.6f)", research.getId(), selectorResponse, selectorUsage.rawCost());

        // Step 2: Parse selector decision
        String cleanedResponse = cleanJsonResponse(selectorResponse);
        JsonNode selectorDecision;
        try {
            selectorDecision = parseJsonWithContext(cleanedResponse, "template-selector");
        } catch (Exception e) {
            LOG.warnf("Failed to parse template selector response, falling back to LLM: %s", e.getMessage());
            StrategyGenerationResult llmResult = generateLLMStrategyWithUsage(research, newsAnalysis, effectiveModel);
            return StrategyGenerationResult.of(llmResult.markdown(), selectorUsage.add(llmResult.usage()));
        }

        String recommendation = selectorDecision.has("recommendation")
            ? selectorDecision.get("recommendation").asText()
            : "USE_LLM";
        int confidence = selectorDecision.has("confidence")
            ? selectorDecision.get("confidence").asInt()
            : 0;

        LOG.infof("[research=%s] Template selector decision: %s (confidence: %d, threshold: %d)",
            research.getId(), recommendation, confidence, templateConfidenceThreshold);

        // Step 3: Check if template should be used
        if ("USE_TEMPLATE".equals(recommendation) && confidence >= templateConfidenceThreshold) {
            String templateTypeName = selectorDecision.has("templateType")
                ? selectorDecision.get("templateType").asText()
                : null;

            if (templateTypeName != null) {
                try {
                    StrategyTemplateType templateType = StrategyTemplateType.valueOf(templateTypeName);
                    Optional<StrategyTemplate> template = templateRegistry.getTemplate(templateType);

                    if (template.isPresent()) {
                        String marketContextRationale = selectorDecision.has("marketContext")
                            ? selectorDecision.get("marketContext").asText()
                            : newsAnalysis;

                        MarketContext marketContext = buildMarketContext(research, marketContextRationale);
                        TemplateParameters params = TemplateParameters.builder().build();

                        String strategyMarkdown = template.get().generateStrategyMarkdown(marketContext, params);
                        LOG.infof("Generated strategy using template: %s", templateType);
                        // Template-based generation only incurs selector cost
                        return StrategyGenerationResult.of(strategyMarkdown, selectorUsage);
                    }
                } catch (IllegalArgumentException e) {
                    LOG.warnf("Invalid template type from LLM: %s, falling back to LLM generation", templateTypeName);
                }
            }
        }

        // Fallback: LLM generation
        LOG.infof("[research=%s] Using LLM generation (recommendation=%s, confidence=%d)", research.getId(), recommendation, confidence);
        StrategyGenerationResult llmResult = generateLLMStrategyWithUsage(research, newsAnalysis, effectiveModel);
        return StrategyGenerationResult.of(llmResult.markdown(), selectorUsage.add(llmResult.usage()));
    }

    /**
     * Attempt template-based strategy generation with fallback to LLM (legacy).
     */
    private String generateStrategyWithTemplateFallback(MarketResearch research, String newsAnalysis, String effectiveModel) {
        return generateStrategyWithTemplateFallbackAndUsage(research, newsAnalysis, effectiveModel).markdown();
    }

    /**
     * Generate strategy using pure LLM with usage tracking.
     * When tool-calling is available, the Strategy Designer can query technical indicators
     * and ticker data to ground its strategy in real numbers.
     *
     * @param effectiveModel The model to use for LLM calls
     */
    private StrategyGenerationResult generateLLMStrategyWithUsage(MarketResearch research, String newsAnalysis, String effectiveModel) {
        Map<String, String> strategyContext = buildContext(research, newsAnalysis, null);
        AgentResult result = queryAgentWithToolsOrFallback(
            "strategy-designer",
            strategyContext,
            noWebSearch(effectiveModel),
            research.getOwnerId()
        );
        return StrategyGenerationResult.of(result.content(), result.usage(), result.toolCallsJson());
    }

    /**
     * Generate strategy using pure LLM (legacy).
     */
    private String generateLLMStrategy(MarketResearch research, String newsAnalysis, String effectiveModel) {
        return generateLLMStrategyWithUsage(research, newsAnalysis, effectiveModel).markdown();
    }

    /**
     * Build MarketContext for template-based strategy generation.
     * When no target assets are specified, creates context with empty ticker sets
     * and a placeholder primary ticker - the LLM will discover assets from rationale.
     */
    private MarketContext buildMarketContext(MarketResearch research, String rationale) {
        Set<Symbol> stockTickers = new HashSet<>();
        Set<Symbol> cryptoTickers = new HashSet<>();
        Symbol primaryTicker = null;

        for (Symbol symbol : research.getTargetAssets()) {
            if (primaryTicker == null) {
                primaryTicker = symbol;
            }
            // Classify based on ticker format (crypto typically has -USD suffix)
            if (symbol.ticker().contains("-USD") || symbol.ticker().contains("-EUR")) {
                cryptoTickers.add(symbol);
            } else {
                stockTickers.add(symbol);
            }
        }

        // If no target assets provided, use DISCOVERY as placeholder
        // The LLM will determine actual tickers from the research context
        if (primaryTicker == null) {
            primaryTicker = new Symbol("DISCO", InvestmentType.STOCK);
        }

        return new MarketContext(primaryTicker, stockTickers, cryptoTickers, rationale);
    }

    private String queryAgent(String templateName, Map<String, String> context, LLMConfig config) {
        return queryAgentWithUsage(templateName, context, config).content();
    }

    private LLMQueryResult queryAgentWithUsage(String templateName, Map<String, String> context, LLMConfig config) {
        String prompt = loadTemplate(templateName, context);
        return llmPort.queryWithUsage(prompt, config);
    }

    /**
     * Result of an agent call that may have used tool calling.
     */
    private record AgentResult(String content, LLMUsage usage, String toolCallsJson) {
        static AgentResult of(String content, LLMUsage usage) {
            return new AgentResult(content, usage != null ? usage : LLMUsage.ZERO, null);
        }
        static AgentResult of(String content, LLMUsage usage, String toolCallsJson) {
            return new AgentResult(content, usage != null ? usage : LLMUsage.ZERO, toolCallsJson);
        }
    }

    /**
     * Query an agent with tool-calling support when available, otherwise fall back to plain text.
     * <p>
     * When ConversationalLLMPort and ChatToolExecutor are available, the agent can call
     * platform tools (get_indicators, find_ticker, etc.) during its reasoning process.
     * The agentic loop runs up to {@link #MAX_TOOL_ITERATIONS} iterations.
     *
     * @param templateName Name of the prompt template
     * @param context      Template context variables
     * @param config       LLM config (model, temperature, etc.)
     * @param ownerId      User ID for tool execution authorization
     * @return AgentResult with final text content and aggregated usage
     */
    private AgentResult queryAgentWithToolsOrFallback(
            String templateName, Map<String, String> context, LLMConfig config, UserId ownerId) {

        // The availability check matters as much as the null checks: the conversational adapter is
        // present as a bean even with no API key, so on a keyless install every research agent took
        // this path and died on a 401 — which the caller then recorded as a legitimate "REJECT".
        if (conversationalLLMPort == null || chatToolExecutor == null || !conversationalLLMPort.isAvailable()) {
            LOG.debugf("Tool-calling unavailable for '%s'; using the plain-text path on the selected engine",
                templateName);
            LLMQueryResult result = queryAgentWithUsage(templateName, context, config);
            return AgentResult.of(result.content(), result.usage());
        }

        String prompt = loadTemplate(templateName, context);
        List<ToolDefinition> tools = ResearchToolRegistry.researchTools();
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.system(prompt));
        messages.add(ChatMessage.user("Execute your analysis. Use the available tools to gather real market data before forming your conclusions. Note: tools may return empty data for tickers without backfilled price history — if so, rely on web search and proceed with your analysis. Always produce the required output format regardless of tool results."));

        LLMUsage aggregatedUsage = LLMUsage.ZERO;
        // Track tool calls for persistence
        List<Map<String, Object>> toolCallRecords = new ArrayList<>();

        for (int iteration = 0; iteration < MAX_TOOL_ITERATIONS; iteration++) {
            LOG.infof("Research agent '%s' tool-calling iteration %d, messageCount=%d",
                templateName, iteration, messages.size());

            ChatCompletionResult result = conversationalLLMPort.chatCompletion(messages, tools, config);
            aggregatedUsage = aggregatedUsage.add(result.usage());

            if (result.hasToolCalls()) {
                // Record assistant message with tool calls
                messages.add(ChatMessage.assistantWithToolCalls(result.toolCalls()));

                // Execute each tool call
                for (ToolCall toolCall : result.toolCalls()) {
                    LOG.infof("Research agent '%s' calling tool: %s(%s)",
                        templateName, toolCall.functionName(), toolCall.arguments());

                    String toolResult = chatToolExecutor.execute(
                        toolCall.functionName(), toolCall.arguments(), ownerId);

                    messages.add(ChatMessage.toolResult(toolCall.id(), toolResult));

                    // Record tool call for persistence
                    toolCallRecords.add(Map.of(
                        "tool", toolCall.functionName(),
                        "args", toolCall.arguments(),
                        "result", truncateToolResult(toolResult, 500)
                    ));
                }
                // Continue loop — LLM needs to process tool results
            } else {
                // Text response — agent is done
                String content = result.content() != null ? result.content() : "";
                LOG.infof("Research agent '%s' completed after %d iterations, %d chars, %d tool calls",
                    templateName, iteration + 1, content.length(), toolCallRecords.size());
                return AgentResult.of(content, aggregatedUsage, serializeToolCalls(toolCallRecords));
            }
        }

        // Max iterations reached — force completion
        LOG.warnf("Research agent '%s' hit max tool iterations (%d), requesting final response",
            templateName, MAX_TOOL_ITERATIONS);

        messages.add(ChatMessage.user(
            "You have used all available tool calls. Please provide your final analysis now based on the data you have gathered."));
        ChatCompletionResult finalResult = conversationalLLMPort.chatCompletion(messages, List.of(), config);
        aggregatedUsage = aggregatedUsage.add(finalResult.usage());

        String content = finalResult.content() != null ? finalResult.content() : "";
        return AgentResult.of(content, aggregatedUsage, serializeToolCalls(toolCallRecords));
    }

    private String loadTemplate(String templateName, Map<String, String> context) {
        try {
            InputStream is = getClass().getResourceAsStream("/prompts/" + templateName + ".md");
            if (is == null) {
                throw new IllegalStateException("Template not found: " + templateName);
            }

            String template = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))
                .lines()
                .collect(Collectors.joining("\n"));

            // Replace placeholders
            for (Map.Entry<String, String> entry : context.entrySet()) {
                template = template.replace("{{" + entry.getKey() + "}}", entry.getValue());
            }

            return template;

        } catch (Exception e) {
            throw new RuntimeException("Failed to load template: " + templateName, e);
        }
    }

    private Map<String, String> buildContext(MarketResearch research, String newsAnalysis, String strategyDesign) {
        Map<String, String> context = new HashMap<>();

        // Target assets are optional - provide empty string or "to be discovered" if not specified
        String targetAssetsStr = research.getTargetAssets().isEmpty()
            ? "(to be discovered from research instructions)"
            : research.getTargetAssets().stream()
                .map(Symbol::ticker)
                .collect(Collectors.joining(", "));
        context.put("targetAssets", targetAssetsStr);
        context.put("interval", research.getInterval().toString());
        context.put("currentDate", java.time.Instant.now().toString());

        // Include market research instructions (default to generic if not provided)
        String instructions = research.getMarkdownInstructions();
        if (instructions == null || instructions.isBlank()) {
            instructions = "Analyze market conditions for trading opportunities. Discover relevant assets based on current market trends.";
        }
        context.put("marketResearchInstructions", instructions);

        if (newsAnalysis != null) {
            context.put("newsAnalysis", newsAnalysis);
        }
        if (strategyDesign != null) {
            context.put("strategyDesign", strategyDesign);
        }

        // Inject stored news data from database for target tickers
        context.put("storedNewsData", buildStoredNewsSection(research.getTargetAssets()));

        return context;
    }

    /**
     * Query the database for recent NEWS events related to target tickers and
     * format them as structured text for injection into research agent prompts.
     * Returns up to 20 articles from the last 7 days with headline + summary.
     */
    private String buildStoredNewsSection(Set<Symbol> targetAssets) {
        if (marketEventRepository == null || targetAssets.isEmpty()) {
            return "No stored news data available.";
        }

        try {
            Instant now = Instant.now();
            Instant sevenDaysAgo = now.minus(Duration.ofDays(7));
            int maxArticles = 20;

            // Pass all tickers to both crypto and stock sets because the REST resource
            // may default all assets to CRYPTO regardless of actual type.
            Set<String> allTickers = targetAssets.stream()
                .map(Symbol::ticker)
                .collect(Collectors.toSet());

            List<MarketEvent> newsEvents = marketEventRepository.findByTickersAndTimeRange(
                allTickers, allTickers, sevenDaysAgo, now, EventType.NEWS, maxArticles);

            if (newsEvents.isEmpty()) {
                return "No stored news data available for the target tickers in the last 7 days.";
            }

            StringBuilder sb = new StringBuilder();
            for (MarketEvent event : newsEvents) {
                Map<String, Object> data = event.rawData();
                String headline = (String) data.getOrDefault("headline",
                    data.getOrDefault("title", "No headline"));
                String source = event.source() != null ? event.source() : event.symbol();
                sb.append("- [").append(event.timestamp())
                    .append("] [").append(source)
                    .append("] ").append(headline).append('\n');
                String summary = (String) data.get("summary");
                if (summary != null && !summary.isBlank()) {
                    String truncated = summary.length() > 400
                        ? summary.substring(0, 400) + "..."
                        : summary;
                    sb.append("  ").append(truncated).append('\n');
                }
            }
            LOG.infof("Injected %d stored news articles into research context for tickers: %s",
                newsEvents.size(), targetAssets.stream().map(Symbol::ticker).collect(Collectors.joining(", ")));
            return sb.toString();

        } catch (Exception e) {
            LOG.warnf("Failed to query stored news for research context: %s", e.getMessage());
            return "No stored news data available (query failed).";
        }
    }

    private String serializeToolCalls(List<Map<String, Object>> toolCallRecords) {
        if (toolCallRecords == null || toolCallRecords.isEmpty()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(toolCallRecords);
        } catch (Exception e) {
            LOG.warnf("Failed to serialize tool calls: %s", e.getMessage());
            return null;
        }
    }

    private String truncateToolResult(String result, int maxLength) {
        if (result == null) return "";
        if (result.length() <= maxLength) return result;
        return result.substring(0, maxLength) + "...[truncated]";
    }

    /**
     * Create LLM config with web search enabled (appends :online suffix).
     *
     * @param effectiveModel The base model to use (user override or system default)
     */
    private LLMConfig enableWebSearch(String effectiveModel) {
        String modelWithOnline = appendOnlineSuffix(effectiveModel);
        return new LLMConfig(modelWithOnline, temperature, maxTokens);
    }

    /**
     * Create LLM config without web search.
     *
     * @param effectiveModel The model to use (user override or system default)
     */
    private LLMConfig noWebSearch(String effectiveModel) {
        return new LLMConfig(effectiveModel, temperature, maxTokens);
    }

    /**
     * Append :online suffix to model ID for web search, avoiding duplicates.
     */
    private String appendOnlineSuffix(String model) {
        if (model == null || model.isBlank()) {
            return researchModelOnline; // Fallback to default
        }
        // If already has :online suffix, return as-is
        if (model.toLowerCase().endsWith(":online")) {
            return model;
        }
        return model + ":online";
    }

    /**
     * Get effective model - user override if provided, otherwise system default.
     *
     * <p>Only used when no {@link ResearchModelResolver} is wired. With one, every research agent
     * resolves separately through the shared chain — see {@link #modelFor}.</p>
     */
    private String getEffectiveModel(String userModelOverride, UUID researchId) {
        if (userModelOverride != null && !userModelOverride.isBlank()) {
            LOG.infof("[research=%s] Using user model override: %s", researchId, userModelOverride);
            return userModelOverride;
        }
        return researchModel;
    }

    /**
     * The model for one named research agent.
     *
     * <p>Research applied a single model to every agent, which is why a per-agent research
     * preference was storable and inert. Resolving per agent is what makes that preference mean
     * something — and what makes {@code defaults.research.orchestrator} visibly dead rather than
     * plausibly live.</p>
     *
     * @param research  the research being executed (owner supplies the preferences)
     * @param agent     which research agent is about to run
     * @param fallback  the flat effective model, used when no resolver is wired
     */
    private String modelFor(MarketResearch research, ResearchAgentType agent, String fallback,
                            String userModelOverride) {
        return resolutionFor(research, agent, fallback, userModelOverride).model();
    }

    /** Model plus reasoning effort for one research agent. */
    private record AgentModelChoice(String model, com.quorumtrading.model.valueobject.ModelEffort effort) {}

    private AgentModelChoice resolutionFor(MarketResearch research, ResearchAgentType agent, String fallback,
                                           String userModelOverride) {
        if (researchModelResolver == null) {
            return new AgentModelChoice(fallback, null);
        }
        try {
            com.quorumtrading.model.valueobject.ModelResolution resolution =
                    researchModelResolver.resolve(research.getOwnerId(), agent, userModelOverride);
            if (!resolution.isServable()) {
                throw new IllegalStateException(String.format(
                        "Research agent %s cannot run: model '%s' (chosen at tier %s) is not servable "
                        + "by the active LLM engine %s. Change the preference or select an engine that has it.",
                        agent, resolution.requested(), resolution.decidedByTier(), resolution.engine()));
            }
            if (resolution.wasSubstituted()) {
                LOG.infof("[research=%s] %s: '%s' substituted by '%s' (%s)", research.getId(), agent,
                        resolution.requested(), resolution.effective(), resolution.substitutionReason());
            }
            return new AgentModelChoice(resolution.effective(), resolution.effort());
        } catch (IllegalStateException e) {
            throw e;
        } catch (RuntimeException e) {
            LOG.warnf("[research=%s] %s: could not resolve a model (%s); falling back to %s",
                    research.getId(), agent, e.getMessage(), fallback);
            return new AgentModelChoice(fallback, null);
        }
    }

    /**
     * LLM config for one research agent, carrying its reasoning effort.
     *
     * <p>Effort is applied here rather than folded into the model id, so an engine with no effort
     * concept simply sees a null and omits the flag.</p>
     */
    private LLMConfig configFor(MarketResearch research, ResearchAgentType agent, String fallback,
                                String userModelOverride, boolean webSearch) {
        AgentModelChoice choice = resolutionFor(research, agent, fallback, userModelOverride);
        LLMConfig config = webSearch ? enableWebSearch(choice.model()) : noWebSearch(choice.model());
        return choice.effort() == null ? config : config.withEffort(choice.effort());
    }

    /**
     * Pure technique / connector vocabulary. A name built ONLY from these tokens
     * (after stripping a trailing "strategy") is a bare indicator label like
     * "Moving Average Crossover Strategy" — undistinctive across different baskets.
     */
    private static final java.util.Set<String> TECHNIQUE_TOKENS = java.util.Set.of(
            "moving", "average", "crossover", "cross", "macd", "signal", "rsi",
            "divergence", "bollinger", "band", "bands", "breakout", "golden", "death",
            "ema", "sma", "wma", "vwap", "mean", "reversion", "momentum", "stochastic",
            "ichimoku", "trend", "following", "oscillator", "swing", "scalp", "scalping",
            "atr", "adx", "obv", "pivot", "support", "resistance", "of", "the", "and",
            "-", "–", "—", "/", "&");

    // Package-private for unit testing of the naming convention guard.
    String extractStrategyName(String markdown) {
        // Extract name from YAML frontmatter or markdown H1 header, append month/year
        try {
            String baseName = null;
            boolean fromFrontmatter = false;

            // First try YAML frontmatter
            if (markdown.startsWith("---")) {
                String[] lines = markdown.split("\n");
                for (String line : lines) {
                    if (line.startsWith("name:")) {
                        baseName = line.substring(5).trim().replace("\"", "");
                        fromFrontmatter = true;
                        break;
                    }
                }
            }

            // If not found in frontmatter, try first H1 markdown header
            if (baseName == null) {
                String[] lines = markdown.split("\n");
                for (String line : lines) {
                    String trimmed = line.trim();
                    if (trimmed.startsWith("# ")) {
                        baseName = trimmed.substring(2).trim();
                        break;
                    }
                }
            }

            // Defense-in-depth: if the model ignored the naming convention and emitted a
            // bare technique label in the H1 (e.g. "Moving Average Crossover Strategy"),
            // prepend the lead tickers so distinct baskets don't collapse to one name.
            // Never override an explicit frontmatter name: — only a generic H1.
            if (baseName != null && !fromFrontmatter && isGenericTechniqueName(baseName)) {
                java.util.List<String> tickers = extractLeadTickers(markdown, 3);
                if (!tickers.isEmpty()) {
                    String prefix = String.join("/", tickers);
                    LOG.infof("Enriched generic strategy name '%s' with tickers '%s'", baseName, prefix);
                    baseName = prefix + " " + baseName;
                }
            }

            // If we found a name, append month and year
            if (baseName != null && !baseName.isBlank()) {
                java.time.Instant now = java.time.Instant.now();
                java.time.ZonedDateTime zonedDateTime = now.atZone(java.time.ZoneId.systemDefault());
                java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("MMM yyyy");
                String monthYear = zonedDateTime.format(formatter);
                return baseName + " " + monthYear;
            }
        } catch (Exception e) {
            LOG.warnf("Failed to extract strategy name from markdown: %s", e.getMessage());
        }

        // Fallback to default with readable timestamp
        java.time.Instant now = java.time.Instant.now();
        java.time.ZonedDateTime zonedDateTime = now.atZone(java.time.ZoneId.systemDefault());
        java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("MMM yyyy");
        return "Research Strategy " + zonedDateTime.format(formatter);
    }

    /**
     * True when a name is a bare indicator label — every token (after dropping a
     * trailing "strategy") is drawn from {@link #TECHNIQUE_TOKENS}. Conservative by
     * design: any distinctive word (a ticker, sector, or thesis term) makes it false,
     * so a legitimately named strategy ending in "Strategy" is never clobbered.
     */
    private boolean isGenericTechniqueName(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        String normalized = name.toLowerCase(java.util.Locale.ROOT).trim();
        if (normalized.endsWith(" strategy")) {
            normalized = normalized.substring(0, normalized.length() - " strategy".length()).trim();
        } else if (normalized.equals("strategy")) {
            return true;
        }
        if (normalized.isBlank()) {
            return true;
        }
        for (String token : normalized.split("\\s+")) {
            if (!TECHNIQUE_TOKENS.contains(token)) {
                return false; // a distinctive (non-technique) word ⇒ not generic
            }
        }
        return true;
    }

    /**
     * Best-effort extraction of the first {@code max} tickers from the YAML
     * frontmatter (stock_tickers / crypto_tickers), supporting both block list
     * ({@code - AAPL}) and inline ({@code [AAPL, MSFT]}) forms.
     */
    private java.util.List<String> extractLeadTickers(String markdown, int max) {
        java.util.List<String> tickers = new java.util.ArrayList<>();
        try {
            int firstDelim = markdown.indexOf("---");
            if (firstDelim < 0) {
                return tickers;
            }
            int secondDelim = markdown.indexOf("\n---", firstDelim + 3);
            String frontmatter = secondDelim > 0 ? markdown.substring(firstDelim, secondDelim) : markdown;

            boolean inTickerList = false;
            for (String raw : frontmatter.split("\n")) {
                if (tickers.size() >= max) {
                    break;
                }
                String line = raw.strip();
                boolean isTickerKey = line.startsWith("stock_tickers:") || line.startsWith("crypto_tickers:");
                if (isTickerKey) {
                    int lb = line.indexOf('[');
                    int rb = line.indexOf(']');
                    if (lb >= 0 && rb > lb) { // inline array form
                        for (String t : line.substring(lb + 1, rb).split(",")) {
                            addTicker(tickers, t, max);
                        }
                        inTickerList = false;
                    } else {
                        inTickerList = true; // block list follows
                    }
                    continue;
                }
                if (inTickerList) {
                    if (line.startsWith("- ")) {
                        addTicker(tickers, line.substring(2), max);
                    } else if (!line.isEmpty()) {
                        inTickerList = false; // reached the next key
                    }
                }
            }
        } catch (Exception e) {
            // best-effort: return whatever we gathered
        }
        return tickers;
    }

    private void addTicker(java.util.List<String> tickers, String raw, int max) {
        if (tickers.size() >= max) {
            return;
        }
        String ticker = raw.replace("\"", "").replace("'", "").strip();
        if (!ticker.isEmpty() && !tickers.contains(ticker)) {
            tickers.add(ticker);
        }
    }

    /**
     * Parse JSON response with better error context.
     * Provides actionable error messages when LLM returns prose instead of JSON.
     */
    private JsonNode parseJsonWithContext(String json, String agentName) {
        if (json == null || json.isBlank()) {
            throw new IllegalStateException(
                String.format("[%s] LLM returned empty response instead of JSON", agentName));
        }

        // Detect common non-JSON patterns (LLM refusals/apologies)
        String trimmed = json.trim();
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
            // Extract first 200 chars for error context
            String preview = trimmed.length() > 200
                ? trimmed.substring(0, 200) + "..."
                : trimmed;
            throw new IllegalStateException(
                String.format("[%s] LLM returned prose instead of JSON. Response starts with: %s",
                    agentName, preview));
        }

        try {
            return OBJECT_MAPPER.readTree(json);
        } catch (Exception e) {
            // Extract first 300 chars for debugging
            String preview = json.length() > 300
                ? json.substring(0, 300) + "..."
                : json;
            throw new IllegalStateException(
                String.format("[%s] Failed to parse LLM response as JSON: %s. Response: %s",
                    agentName, e.getMessage(), preview), e);
        }
    }

    /**
     * Clean JSON response from LLM by removing markdown code block wrappers
     * and fixing common JSON formatting issues.
     */
    private String cleanJsonResponse(String response) {
        if (response == null || response.isBlank()) {
            return response;
        }

        String cleaned = response.trim();

        // Step 1: Remove markdown code block wrappers (```json ... ``` or ``` ... ```)
        if (cleaned.startsWith("```")) {
            // Find first newline after opening ```
            int firstNewline = cleaned.indexOf('\n');
            if (firstNewline > 0) {
                // Remove opening ``` and language identifier
                cleaned = cleaned.substring(firstNewline + 1);
            } else {
                // No newline, just remove opening ```
                cleaned = cleaned.substring(3);
            }

            // Remove closing ```
            if (cleaned.endsWith("```")) {
                cleaned = cleaned.substring(0, cleaned.length() - 3);
            }

            cleaned = cleaned.trim();
        }

        // Step 2: Try to parse as-is first
        try {
            OBJECT_MAPPER.readTree(cleaned);
            return cleaned; // Already valid JSON
        } catch (Exception e) {
            LOG.debugf("JSON needs cleaning: %s", e.getMessage());
        }

        // Step 3: Fix unescaped control characters in string values
        StringBuilder fixed = new StringBuilder(cleaned.length() * 2);
        boolean inString = false;
        boolean escaped = false;

        for (int i = 0; i < cleaned.length(); i++) {
            char c = cleaned.charAt(i);

            if (escaped) {
                fixed.append(c);
                escaped = false;
                continue;
            }

            if (c == '\\') {
                escaped = true;
                fixed.append(c);
                continue;
            }

            if (c == '"') {
                inString = !inString;
                fixed.append(c);
                continue;
            }

            if (inString) {
                switch (c) {
                    case '\n':
                        fixed.append("\\n");
                        break;
                    case '\r':
                        fixed.append("\\r");
                        break;
                    case '\t':
                        fixed.append("\\t");
                        break;
                    case '\b':
                        fixed.append("\\b");
                        break;
                    case '\f':
                        fixed.append("\\f");
                        break;
                    default:
                        fixed.append(c);
                }
            } else {
                fixed.append(c);
            }
        }

        return fixed.toString();
    }

    /**
     * Patches missing schedule fields in strategy markdown.
     * If the LLM omits live_schedule/backtest_schedule,
     * this method adds sensible defaults based on the ticker types present.
     *
     * @param markdown The strategy markdown to patch
     * @param researchId The market research id, for log context
     * @return The patched markdown with schedule fields
     */
    /**
     * Remove anything the LLM wrapped around the strategy document before its frontmatter.
     *
     * <p>The designer is asked for a document that starts with {@code ---}, and usually obliges,
     * but a model that prefaces it with "Here is the strategy:" or wraps the whole thing in a
     * fenced code block produced markdown that failed validation outright. The research run had
     * already been paid for at that point and no strategy was saved — the entire execution was
     * wasted on a formatting habit.</p>
     *
     * <p>Only leading prose and fences are removed; the document itself is untouched. If no
     * frontmatter delimiter is found the input is returned unchanged so validation can report
     * the real problem.</p>
     */
    private String stripNonFrontmatterPreamble(String markdown, UUID researchId) {
        if (markdown == null || markdown.isBlank()) {
            return markdown;
        }

        String working = markdown.strip();

        // Unwrap a fenced block: ```markdown ... ``` or ``` ... ```
        if (working.startsWith("```")) {
            int firstLineEnd = working.indexOf('\n');
            if (firstLineEnd > 0) {
                String withoutOpeningFence = working.substring(firstLineEnd + 1);
                int closingFence = withoutOpeningFence.lastIndexOf("```");
                working = (closingFence >= 0
                    ? withoutOpeningFence.substring(0, closingFence)
                    : withoutOpeningFence).strip();
            }
        }

        if (working.startsWith("---")) {
            return working;
        }

        // Find the first line that is exactly the frontmatter delimiter and start there.
        String[] lines = working.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].strip().equals("---")) {
                String recovered = String.join("\n", java.util.Arrays.copyOfRange(lines, i, lines.length));
                LOG.warnf("[research=%s] Strategy markdown had %d line(s) of preamble before its "
                        + "frontmatter; stripped them rather than failing the run", researchId, i);
                return recovered.strip();
            }
        }

        // No frontmatter at all — hand it back untouched so validation reports the real fault.
        return working;
    }

    private String patchMissingEvaluationTiming(String markdown, UUID researchId) {
        if (markdown == null || markdown.isBlank()) {
            return markdown;
        }

        String trimmed = markdown.trim();
        if (!trimmed.startsWith("---")) {
            return markdown;
        }

        String[] parts = trimmed.split("---", 3);
        if (parts.length < 3) {
            return markdown;
        }

        String frontmatter = parts[1];

        // Check if schedule is already present. Legacy fields (market_hours /
        // evaluation_interval_seconds) are NOT patched around: leave the markdown
        // untouched so validateStrategyMarkdown rejects them with a clear error.
        boolean hasLiveSchedule = frontmatter.contains("live_schedule:");
        boolean hasLegacyScheduleFields = frontmatter.contains("evaluation_interval_seconds:")
            || frontmatter.contains("market_hours:");

        if (hasLiveSchedule || hasLegacyScheduleFields) {
            return markdown; // Already has scheduling (or will be rejected as legacy)
        }

        LOG.infof("[research=%s] Strategy markdown missing schedule - patching with defaults", researchId);

        // Determine mode: if stock_tickers has values, use stock mode; otherwise crypto mode
        boolean hasStockTickers = frontmatter.contains("stock_tickers:") &&
            !frontmatter.matches("(?s).*stock_tickers:\\s*\\[\\s*\\].*");

        // Find the position to insert - before position_limits or reserve_cash_percentage
        String patchedFrontmatter;
        if (hasStockTickers) {
            // Stock mode: add live_schedule + backtest_schedule
            String scheduleBlock = "\nlive_schedule:\n" +
                "  timezone: \"America/New_York\"\n" +
                "  trading_days: [MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY]\n" +
                "  evaluation_times: [\"10:00\", \"14:00\"]\n" +
                "  market_open: \"09:30\"\n" +
                "  market_close: \"16:00\"\n" +
                "backtest_schedule:\n" +
                "  timezone: \"America/New_York\"\n" +
                "  trading_days: [WEDNESDAY]\n" +
                "  evaluation_times: [\"10:00\"]\n" +
                "  market_open: \"09:30\"\n" +
                "  market_close: \"16:00\"";

            patchedFrontmatter = insertBeforeField(frontmatter, scheduleBlock);
            LOG.infof("Added live_schedule + backtest_schedule for stock mode");
        } else {
            // Crypto mode: add live_schedule + backtest_schedule
            String scheduleBlock = "\nlive_schedule:\n" +
                "  interval: 1h\n" +
                "backtest_schedule:\n" +
                "  interval: 1w";

            patchedFrontmatter = insertBeforeField(frontmatter, scheduleBlock);
            LOG.infof("Added live_schedule (1h) + backtest_schedule (1w) for crypto mode");
        }

        return "---" + patchedFrontmatter + "\n---" + parts[2];
    }

    /**
     * Insert a block before position_limits or reserve_cash_percentage, or at end of frontmatter.
     */
    private String insertBeforeField(String frontmatter, String block) {
        if (frontmatter.contains("position_limits:")) {
            return frontmatter.replace("position_limits:",
                block + "\n\nposition_limits:");
        } else if (frontmatter.contains("reserve_cash_percentage:")) {
            return frontmatter.replace("reserve_cash_percentage:",
                block + "\n\nreserve_cash_percentage:");
        } else {
            return frontmatter + block;
        }
    }

    /**
     * Validates strategy markdown frontmatter structure.
     */
    private void validateStrategyMarkdown(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            throw new IllegalArgumentException("Strategy markdown cannot be blank");
        }

        String trimmed = markdown.trim();

        // Check for YAML frontmatter
        if (!trimmed.startsWith("---")) {
            throw new IllegalArgumentException(
                "Strategy markdown must start with YAML frontmatter (---)"
            );
        }

        // Split frontmatter from body
        String[] parts = trimmed.split("---", 3);
        if (parts.length < 3) {
            throw new IllegalArgumentException(
                "Invalid YAML frontmatter structure. Expected: ---\\n...yaml...\\n---\\n...markdown..."
            );
        }

        String frontmatter = parts[1].trim();
        if (frontmatter.isEmpty()) {
            throw new IllegalArgumentException("YAML frontmatter cannot be empty");
        }

        // Check for required fields (basic text-based validation)
        String[] requiredFields = {
            "version:",
            "consensus_method:",
            "consensus_threshold:"
        };

        for (String field : requiredFields) {
            if (!frontmatter.contains(field)) {
                throw new IllegalArgumentException(
                    "Strategy markdown missing required field: " + field.replace(":", "")
                );
            }
        }

        // At least one of stock_tickers or crypto_tickers must be present
        boolean hasStockTickers = frontmatter.contains("stock_tickers:");
        boolean hasCryptoTickers = frontmatter.contains("crypto_tickers:");
        if (!hasStockTickers && !hasCryptoTickers) {
            throw new IllegalArgumentException(
                "Strategy markdown must contain at least one of: stock_tickers, crypto_tickers"
            );
        }

        // Reject removed legacy schedule fields with an actionable error
        boolean hasLegacyScheduleFields = frontmatter.contains("evaluation_interval_seconds:")
            || frontmatter.contains("market_hours:");
        if (hasLegacyScheduleFields) {
            throw new IllegalArgumentException(
                "Legacy schedule fields 'market_hours' / 'evaluation_interval_seconds' are no longer supported. "
                + "Use 'live_schedule' instead (see database migration V122__Migrate_legacy_schedule_frontmatter)."
            );
        }

        // Check for evaluation timing: live_schedule is required
        boolean hasLiveSchedule = frontmatter.contains("live_schedule:");
        if (!hasLiveSchedule) {
            throw new IllegalArgumentException(
                "Strategy markdown requires 'live_schedule'"
            );
        }

        // Check for common mistakes
        if (frontmatter.contains("ticker:") && !frontmatter.contains("tickers:")) {
            throw new IllegalArgumentException(
                "Invalid field 'ticker'. Use 'stock_tickers' and 'crypto_tickers' arrays instead."
            );
        }

        if (frontmatter.contains("mode:")) {
            throw new IllegalArgumentException(
                "Invalid field 'mode'. Trading mode is inferred from tickers."
            );
        }

        LOG.debugf("Strategy markdown validation passed");
    }

}
