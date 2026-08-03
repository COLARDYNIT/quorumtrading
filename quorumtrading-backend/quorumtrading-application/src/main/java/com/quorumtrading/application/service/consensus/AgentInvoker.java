package com.quorumtrading.application.service.consensus;

import com.quorumtrading.application.config.TradingLimitsConfig;
import com.quorumtrading.application.orchestration.agents.Agent;
import com.quorumtrading.application.orchestration.agents.OrchestratorAgent;
import com.quorumtrading.application.port.out.LLMDecisionRepository;
import com.quorumtrading.application.port.out.LLMPort;
import com.quorumtrading.application.port.out.LlmParseFailureRepository;
import com.quorumtrading.application.service.ModelResolutionService;
import com.quorumtrading.model.entity.LLMDecision;
import com.quorumtrading.model.enums.AgentRole;
import com.quorumtrading.model.enums.RecommendationType;
import com.quorumtrading.model.orchestration.AgentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Invokes the specialized agents (in parallel, with cached-HOLD reuse) and the
 * orchestrator agent for a single ticker's consensus evaluation.
 *
 * <p>Plain POJO wired via {@code ApplicationConfig} (CDI convention: application
 * services are produced, not annotated). Extracted move-only from
 * {@code ReachConsensusService} — behavior preserved.</p>
 */
public class AgentInvoker {

    private static final Logger logger = LoggerFactory.getLogger(AgentInvoker.class);

    // Default agent roles used when strategy does not specify enabled agents
    private static final Set<AgentRole> DEFAULT_ENABLED_AGENTS = Set.of(
        AgentRole.TECHNICAL, AgentRole.FUNDAMENTAL, AgentRole.SENTIMENT
    );

    private final List<Agent> agents;
    private final ExecutorService executorService;
    private final LLMDecisionRepository llmDecisionRepository;
    private final LLMPort llmPort;
    private final TradingLimitsConfig tradingLimitsConfig;
    private final ModelResolutionService modelResolutionService;
    private final LlmParseFailureRepository llmParseFailureRepository;
    private final boolean a2uiEnabled;
    private final int agentTimeoutSeconds;

    /**
     * Root-cause message of the most recent failure per {@code ticker|role}, so
     * {@link #buildParticipationNote} can name WHY an invoked agent returned nothing instead of
     * saying only that it did.
     *
     * <p>Keyed by ticker because tickers are evaluated concurrently within a round; the entries
     * for a ticker are cleared as its round begins, so a stale reason from an earlier round can
     * never be attributed to a later one.</p>
     */
    private final Map<String, String> agentFailureReasons = new ConcurrentHashMap<>();

    /** Cause-chain frames to walk when extracting a root-cause message. */
    private static final int MAX_CAUSE_DEPTH = 10;

    public AgentInvoker(
            List<Agent> agents,
            ExecutorService executorService,
            LLMDecisionRepository llmDecisionRepository,
            LLMPort llmPort,
            TradingLimitsConfig tradingLimitsConfig,
            ModelResolutionService modelResolutionService,
            LlmParseFailureRepository llmParseFailureRepository,
            boolean a2uiEnabled) {
        this(agents, executorService, llmDecisionRepository, llmPort, tradingLimitsConfig,
            modelResolutionService, llmParseFailureRepository, a2uiEnabled, 30);
    }

    public AgentInvoker(
            List<Agent> agents,
            ExecutorService executorService,
            LLMDecisionRepository llmDecisionRepository,
            LLMPort llmPort,
            TradingLimitsConfig tradingLimitsConfig,
            ModelResolutionService modelResolutionService,
            LlmParseFailureRepository llmParseFailureRepository,
            boolean a2uiEnabled,
            int agentTimeoutSeconds) {
        this.agents = agents;
        this.executorService = executorService;
        this.llmDecisionRepository = llmDecisionRepository;
        this.llmPort = llmPort;
        this.tradingLimitsConfig = tradingLimitsConfig;
        this.modelResolutionService = modelResolutionService;
        this.llmParseFailureRepository = llmParseFailureRepository;
        this.a2uiEnabled = a2uiEnabled;
        this.agentTimeoutSeconds = agentTimeoutSeconds;
        logger.info("AgentInvoker initialized with {} agents, a2ui: {}, agent timeout: {}s",
            agents.size(), a2uiEnabled, agentTimeoutSeconds);
    }

    /**
     * Query all specialized agents in parallel (excluding orchestrator).
     * Each agent executes with active CDI request context to access Quarkus configuration.
     * Uses ExecutorService to ensure proper Quarkus context is available.
     */
    public List<LLMDecision> queryAgentsInParallel(AgentContext context) {
        // Determine which agent roles are enabled by the strategy
        Set<AgentRole> enabledAgents = context.getStrategy() != null
            ? context.getStrategy().getEffectiveEnabledAgents()
            : DEFAULT_ENABLED_AGENTS;

        Set<AgentRole> enabledNonOrchestratorAgents = enabledAgents.stream()
            .filter(role -> role != AgentRole.ORCHESTRATOR)
            .collect(Collectors.toSet());

        logger.debug("Gathering recommendations from agents: {}", enabledNonOrchestratorAgents);

        // Drop this ticker's reasons from any previous round before collecting fresh ones.
        String tickerPrefix = failureKeyPrefix(context.getTicker());
        agentFailureReasons.keySet().removeIf(key -> key.startsWith(tickerPrefix));

        List<CompletableFuture<LLMDecision>> futures = agents.stream()
            .filter(agent -> enabledNonOrchestratorAgents.contains(agent.getRole()))
            .filter(agent -> hasContextOrLogSkip(agent, context))
            .map(agent -> CompletableFuture.supplyAsync(() -> {
                try {
                    // Check if agent allows caching and if a cached HOLD decision exists
                    if (agent.mayUseCachedDecision(context)) {
                        Optional<LLMDecision> cachedDecision = llmDecisionRepository.getPreviousDecision(
                            agent.getRole(),
                            context.getSimulationId(),
                            context.getTimestamp()
                        );

                        // Only reuse a cached HOLD, and only while it is still current. The lookup
                        // returns the most recent prior decision with no age bound, so without this
                        // check a single HOLD was replayed verbatim for the rest of the run — three
                        // consecutive weekly sessions returning byte-identical reasoning at 0ms and
                        // 0 tokens, and a decision as much as two weeks stale being presented as
                        // this session's analysis.
                        if (cachedDecision.isPresent()
                                && cachedDecision.get().isHoldRecommendation()
                                && agent.isCachedDecisionStillFresh(cachedDecision.get(), context)) {
                            LLMDecision cached = cachedDecision.get();
                            logger.debug("[sim={}] Reusing cached HOLD decision for {} agent (original decision id: {}, timestamp: {})",
                                context.getSimulationId().value().toString().substring(0, 8),
                                agent.getRole(),
                                cached.getId(),
                                cached.getDecisionTimestamp());

                            // CRITICAL: Clone the cached decision to create a NEW entity
                            // The cached decision is a detached entity (already persisted with an ID).
                            // If we return it directly, Hibernate will throw EntityExistsException when
                            // trying to persist it again. By cloning, we create a fresh entity without an ID.
                            return cloneCachedDecision(cached, context);
                        }
                    }

                    // No cached decision available or agent doesn't allow caching - analyze fresh
                    return agent.analyze(context);
                } catch (Exception e) {
                    // Record the ROOT cause, not the wrapper. Each agent wraps its failure as
                    // "<Role> agent analysis failed", so logging e.getMessage() threw away the
                    // only sentence that said anything — the LLM router's own diagnosis, e.g.
                    // "Selected LLM provider LOCAL_HTTP cannot serve calls right now (no local
                    // model server responded at the configured URL)". Every user-visible surface
                    // then degraded to "all specialized agents failed", and an audit had to read
                    // container logs to discover the engine was simply down.
                    String cause = rootCauseMessage(e);
                    agentFailureReasons.put(failureKey(context.getTicker(), agent.getRole()), cause);
                    logger.warn("[sim={}] Agent {} failed: {}",
                        context.getSimulationId().value().toString().substring(0, 8),
                        agent.getRole(), cause);
                    return null;
                }
            }, executorService))
            .collect(Collectors.toList());

        try {
            // Wait for all agents to complete. Configurable (quorumtrading.consensus.
            // agent-timeout-seconds): local engines need minutes where hosted APIs need seconds.
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .get(agentTimeoutSeconds, TimeUnit.SECONDS);

            return futures.stream()
                .map(CompletableFuture::join)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        } catch (Exception e) {
            logger.warn("[sim={}] Some agents timed out, continuing with available recommendations",
                context.getSimulationId().value().toString().substring(0, 8));
            return futures.stream()
                .filter(f -> f.isDone() && !f.isCompletedExceptionally())
                .map(CompletableFuture::join)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        }
    }

    /**
     * Evaluate an agent's context, logging the reason once when it is skipped.
     * The check itself is side-effect free, so {@link #buildParticipationNote} can re-run it
     * later without producing a second log line.
     */
    private boolean hasContextOrLogSkip(Agent agent, AgentContext context) {
        Agent.ContextCheck check = agent.checkContext(context);
        if (!check.sufficient()) {
            logger.info("[sim={}] {} agent: skipping {} — {}",
                context.getSimulationId().value().toString().substring(0, 8),
                agent.getRole(), context.getTicker(), check.reason());
        }
        return check.sufficient();
    }

    /**
     * Explain which enabled agents produced no decision, and why — so a note like "1/1 agent votes"
     * is answerable from the stored decision alone instead of requiring backend logs.
     *
     * <p>Two causes are deliberately distinguished. An agent filtered out up front <em>abstained</em>
     * and reports the data gap that caused it; an agent that was invoked but returned nothing
     * <em>failed</em> (exception or timeout). Collapsing both into "insufficient data or agent
     * failure" told the reader nothing they could act on.</p>
     *
     * @return the note suffix, or an empty string when every enabled agent voted
     */
    public String buildParticipationNote(
            AgentContext context,
            Set<AgentRole> enabledAgents,
            List<LLMDecision> agentDecisions) {

        // A SKIP is not participation. A parse-failed agent gets a synthesized SKIP decision
        // (AgentResponseParser records it with model "fallback"), so counting any decision as
        // participation meant a round where every agent's output was unparseable produced an
        // EMPTY note — no agent named, no cause given. That was the common failure once the
        // engine was healthy: the model answers, but with garbage.
        Set<String> participated = agentDecisions.stream()
            .filter(d -> d.getRecommendation() != RecommendationType.SKIP)
            .map(LLMDecision::getRole)
            .collect(Collectors.toSet());

        Map<String, LLMDecision> skipped = agentDecisions.stream()
            .filter(d -> d.getRecommendation() == RecommendationType.SKIP)
            .collect(Collectors.toMap(LLMDecision::getRole, d -> d, (a, b) -> a));

        List<String> explanations = agents.stream()
            .filter(agent -> enabledAgents.contains(agent.getRole()))
            .filter(agent -> agent.getRole() != AgentRole.ORCHESTRATOR)
            .filter(agent -> !participated.contains(agent.getRole().name()))
            .sorted(Comparator.comparing(agent -> agent.getRole().name()))
            .map(agent -> {
                Agent.ContextCheck check = agent.checkContext(context);
                if (!check.sufficient()) {
                    return agent.getRole().name() + " abstained — " + check.reason();
                }
                // The agent answered, but with something unusable — the parser rewrote it as a
                // SKIP. Say so, rather than reporting nothing at all.
                LLMDecision skip = skipped.get(agent.getRole().name());
                if (skip != null) {
                    return agent.getRole().name()
                        + " — answered, but its output could not be parsed into a recommendation";
                }
                // The agent was invoked and came back with nothing. Name the cause when we
                // captured one: "no decision returned" is true and useless, and it was the only
                // thing a user saw when the whole LLM engine was unreachable.
                String cause = agentFailureReasons.get(failureKey(context.getTicker(), agent.getRole()));
                return cause == null || cause.isBlank()
                    ? agent.getRole().name() + " — no decision returned (timed out before answering)"
                    : agent.getRole().name() + " — no decision returned: " + cause;
            })
            .toList();

        return explanations.isEmpty() ? "" : " [" + String.join("; ", explanations) + "]";
    }

    private static String failureKeyPrefix(String ticker) {
        return ticker + "|";
    }

    private static String failureKey(String ticker, AgentRole role) {
        return failureKeyPrefix(ticker) + role.name();
    }

    /**
     * The deepest non-blank message in the cause chain.
     *
     * <p>Agents wrap their failures ({@code "Technical agent analysis failed"}), so the
     * outermost message is always the least informative one in the chain.</p>
     */
    private static String rootCauseMessage(Throwable t) {
        String deepest = null;
        Throwable current = t;
        // Bounded rather than "walk until null": a self-referencing cause chain is rare but does
        // occur in the wild, and this runs on a failure path where a hang would be the worst
        // possible outcome. Ten frames is far deeper than any wrapper stack here.
        for (int depth = 0; current != null && depth < MAX_CAUSE_DEPTH; depth++) {
            if (current.getMessage() != null && !current.getMessage().isBlank()) {
                deepest = current.getMessage();
            }
            current = current.getCause();
        }
        return deepest != null ? deepest : t.getClass().getSimpleName();
    }

    /**
     * Construct and invoke the orchestrator agent over the agent decisions.
     * Propagates any failure — the caller decides the fallback (majority vote).
     */
    public LLMDecision invokeOrchestrator(AgentContext context, List<LLMDecision> agentDecisions) {
        OrchestratorAgent orchestrator = new OrchestratorAgent(
            llmPort, agentDecisions, tradingLimitsConfig, modelResolutionService, a2uiEnabled, llmParseFailureRepository);
        return orchestrator.analyze(context);
    }

    /**
     * Clone a cached decision to create a new decision entity.
     * This prevents Hibernate "detached entity" errors by creating a fresh entity
     * without an ID, but reusing all the cached decision data (reasoning, confidence, etc.).
     *
     * @param cached The cached decision to clone
     * @param context Current agent context (for timestamp and simulation ID)
     * @return New LLMDecision entity with fresh ID and current timestamp
     */
    private LLMDecision cloneCachedDecision(LLMDecision cached, AgentContext context) {
        // Create new decision with current context (no ID, will be generated on persist)
        LLMDecision newDecision = new LLMDecision(
            context.getSimulationId(),
            context.getTimestamp(),
            java.util.UUID.randomUUID().toString(), // New agent ID
            cached.getRole(),
            cached.getLlmModel() // Use original model name as-is
        );

        // Copy all cached decision data
        // Use context ticker since the database column was dropped in V27 migration
        // (cached.getTicker() will always return null for decisions loaded from DB)
        String ticker = context.getTicker() != null ? context.getTicker() : cached.getTicker();
        if (ticker != null && !ticker.isEmpty()) {
            newDecision.recordTicker(ticker);
        }

        // Mark prompt as cached (prompt_used is TEXT, no length limit)
        // Cache is identifiable by: executionTimeMs=0, this marker, and timestamp difference
        String promptMarker = "\n\n[CACHED DECISION REUSED - Original timestamp: " +
                              cached.getDecisionTimestamp() + "]";
        newDecision.setPromptUsed(cached.getPromptUsed() + promptMarker);

        newDecision.recordReasoning(cached.getReasoning());
        newDecision.recordRecommendation(cached.getRecommendation());
        newDecision.recordRecommendedAction(cached.getRecommendedAction());
        newDecision.recordConfidenceScore(cached.getConfidenceScore());
        newDecision.recordExecutionTime(0); // No LLM execution time - indicates cached decision
        newDecision.recordQuantity(cached.getTickerQuantity());

        return newDecision;
    }
}
