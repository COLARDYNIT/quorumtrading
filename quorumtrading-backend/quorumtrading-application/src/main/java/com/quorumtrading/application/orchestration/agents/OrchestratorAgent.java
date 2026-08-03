package com.quorumtrading.application.orchestration.agents;

import com.quorumtrading.application.config.TradingLimitsConfig;
import com.quorumtrading.application.port.out.LLMPort;
import com.quorumtrading.application.port.out.LlmParseFailureRepository;
import com.quorumtrading.model.entity.LLMDecision;
import com.quorumtrading.model.enums.AgentRole;
import com.quorumtrading.model.entity.LlmParseFailure;
import com.quorumtrading.model.enums.RecommendationType;
import com.quorumtrading.model.orchestration.AgentContext;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.Percentage;
import com.quorumtrading.model.valueobject.Symbol;
import com.quorumtrading.model.enums.InvestmentType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.quorumtrading.application.service.ModelResolutionService;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Orchestrator agent that makes final trading decisions by considering
 * recommendations from all specialized agents (Technical, Fundamental, Sentiment).
 *
 * This agent acts as the final decision-maker, reviewing all agent inputs and
 * determining the best course of action based on consensus, risk assessment, and
 * overall market conditions.
 */
public class OrchestratorAgent implements Agent {

    private static final Logger logger = LoggerFactory.getLogger(OrchestratorAgent.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static final String A2UI_INSTRUCTION = """

            ## Visual Analysis (A2UI)
            After your JSON response, ALWAYS append a visual summary in <a2ui>...</a2ui> tags.
            You MUST include exactly these 3 components:
            1. **ActionBadge** — your final BUY/SELL/HOLD decision
            2. **VoteSummary** — all agent votes as JSON string array
            3. **ConfidenceGauge** — your confidence as 0-100 integer

            Format: each component needs id, type, childIds (empty []), properties (BoundValue wrappers).
            BoundValue: {"literalString":"text"} or {"literalNumber":42}

            EXACT template (fill in your values):
            <a2ui>{"surfaceUpdate":{"components":[{"id":"a1","type":"ActionBadge","childIds":[],"properties":{"action":{"literalString":"BUY"}}},{"id":"v1","type":"VoteSummary","childIds":[],"properties":{"votes":{"literalString":"[{\"agent\":\"TECHNICAL\",\"action\":\"BUY\",\"confidence\":0.85},{\"agent\":\"FUNDAMENTAL\",\"action\":\"HOLD\",\"confidence\":0.6},{\"agent\":\"SENTIMENT\",\"action\":\"BUY\",\"confidence\":0.7}]"}}},{"id":"c1","type":"ConfidenceGauge","childIds":[],"properties":{"value":{"literalNumber":75},"label":{"literalString":"Orchestrator Confidence"}}}]}}</a2ui>
            """;

    private final LLMPort llmPort;
    private final List<LLMDecision> agentDecisions;
    private final TradingLimitsConfig tradingLimitsConfig;
    private final ModelResolutionService modelResolutionService;
    private final boolean a2uiEnabled;
    private final LlmParseFailureRepository parseFailureRepository;

    public OrchestratorAgent(LLMPort llmPort, List<LLMDecision> agentDecisions, TradingLimitsConfig tradingLimitsConfig, ModelResolutionService modelResolutionService, boolean a2uiEnabled, LlmParseFailureRepository parseFailureRepository) {
        this.llmPort = llmPort;
        this.agentDecisions = agentDecisions;
        this.tradingLimitsConfig = tradingLimitsConfig;
        this.modelResolutionService = modelResolutionService;
        this.a2uiEnabled = a2uiEnabled;
        this.parseFailureRepository = parseFailureRepository;
    }

    @Override
    public LLMDecision analyze(AgentContext context) {
        try {
            logger.debug("Starting orchestrator analysis for simulation: {}", context.getSimulationId());

            String prompt = buildPrompt(context, agentDecisions);

            // Log the exact prompt being sent to the LLM
            logger.debug("=== ORCHESTRATOR PROMPT START ===");
            logger.debug(prompt);
            logger.debug("=== ORCHESTRATOR PROMPT END ===");

            double temperature = context.getStrategyTemperature() != null ? context.getStrategyTemperature() : 0.7;
            com.quorumtrading.model.valueobject.ModelResolution resolution =
                modelResolutionService.requireServableModel(context, getRole());
            String model = resolution.effective();
            boolean noFallback = modelResolutionService.shouldDisableFallback(context, getRole());
            LLMConfig config = new LLMConfig(model, temperature, 800);
            if (noFallback) {
                config = config.withNoFallback();
            }

            long startTime = System.currentTimeMillis();
            LLMQueryResult llmResponse = llmPort.queryWithUsage(prompt, config);
            long executionTime = System.currentTimeMillis() - startTime;

            // Log raw LLM response for debugging response parsing issues
            logger.debug("=== ORCHESTRATOR AGENT RAW LLM RESPONSE START ===");
            logger.debug("Model: {}", llmResponse.model());
            logger.debug("Response length: {} chars", llmResponse.content().length());
            logger.debug("Tokens: {} prompt, {} completion, {} total",
                llmResponse.usage().promptTokens(),
                llmResponse.usage().completionTokens(),
                llmResponse.usage().totalTokens());
            logger.debug("Cost: raw=${}, billed=${}",
                llmResponse.usage().rawCost(),
                llmResponse.usage().billedCost());
            logger.debug("Response:\n{}", llmResponse.content());
            logger.debug("=== ORCHESTRATOR AGENT RAW LLM RESPONSE END ===");

            LLMDecision decision = parseResponse(llmResponse.content(), executionTime, context, prompt, llmResponse.model());
            if (resolution.wasSubstituted()) {
                decision.recordRequestedModel(resolution.requested());
            }

            // Record LLM usage for cost tracking
            decision.recordLLMUsage(llmResponse.usage());

            logger.info("Orchestrator → {}: {} (confidence: {}, {}ms)",
                context.getSimulationId().value().toString().substring(0, 8),
                decision.getRecommendation(),
                String.format("%.2f", decision.getConfidenceScore().value()),
                executionTime);

            return decision;
        } catch (Exception e) {
            logger.error("Orchestrator analysis failed for simulation: {}", context.getSimulationId(), e);
            throw new RuntimeException("Orchestrator analysis failed", e);
        }
    }

    @Override
    public AgentRole getRole() {
        return AgentRole.ORCHESTRATOR;
    }

    /**
     * Longest agent reasoning included per agent in the orchestrator prompt.
     *
     * <p>Bounded so that four verbose agents cannot crowd out the strategy, portfolio and
     * indicator sections that follow them, while still carrying the substance of the argument
     * rather than only its conclusion.</p>
     */
    private static final int MAX_AGENT_REASONING_CHARS = 900;

    private static String truncateReasoning(String reasoning) {
        String collapsed = reasoning.strip().replaceAll("\\s+", " ");
        if (collapsed.length() <= MAX_AGENT_REASONING_CHARS) {
            return collapsed;
        }
        return collapsed.substring(0, MAX_AGENT_REASONING_CHARS) + "… (truncated)";
    }

    private String buildPrompt(AgentContext context, List<LLMDecision> agentDecisions) {
        // Give the orchestrator the agents' actual analysis, not just their votes. This block
        // used to render "- TECHNICAL: BUY" and nothing else, discarding the confidence value
        // and the whole reasoning that each agent had already produced and that was sitting in
        // llm_decisions. The orchestrator was asked to adjudicate between specialists while
        // being told only what they concluded, never why — so a high-conviction, well-argued
        // call and a coin-flip looked identical apart from a STRONG prefix.
        StringBuilder agentSummary = new StringBuilder();
        for (LLMDecision decision : agentDecisions) {
            agentSummary.append(String.format("- %s: %s",
                decision.getRole(),
                decision.getRecommendation()));

            if (decision.getConfidenceScore() != null) {
                agentSummary.append(String.format(" (confidence %.2f)",
                    decision.getConfidenceScore().value().doubleValue()));
            }
            agentSummary.append("\n");

            String reasoning = decision.getReasoning();
            if (reasoning != null && !reasoning.isBlank()) {
                agentSummary.append("  Reasoning: ")
                    .append(truncateReasoning(reasoning))
                    .append("\n");
            }
        }

        if (agentSummary.isEmpty()) {
            // Say so explicitly rather than leaving a blank section the model has to interpret.
            agentSummary.append("- (no agent recommendations were produced for this evaluation)\n");
        }

        // Get current holdings for the ticker being analyzed
        // Resolve the real asset class. Symbol equality is over (canonical, type), and the
        // portfolio keys crypto positions as CRYPTO, so a hardcoded STOCK could never match
        // one: every agent was told "0 units (no position)" while holding a crypto position,
        // and position-aware strategy rules could not be honoured.
        Symbol tickerSymbol =
                new Symbol(context.getTicker(), InvestmentType.fromTicker(context.getTicker()));
        com.quorumtrading.model.entity.Position currentPosition = context.getPortfolio().positions().get(tickerSymbol);
        String positionInfo;
        if (currentPosition != null && currentPosition.quantity().compareTo(BigDecimal.ZERO) > 0) {
            positionInfo = String.format("- Current Holdings of %s: %s units (avg cost: $%s/unit)",
                context.getTicker(),
                currentPosition.quantity(),
                currentPosition.averagePrice().amount()
            );
        } else {
            positionInfo = String.format("- Current Holdings of %s: 0 units (no position)", context.getTicker());
        }

        // Get strategy prompt if available
        String strategyGuidance = "";
        if (context.getStrategy() != null && context.getStrategy().getStrategyPrompt() != null) {
            strategyGuidance = String.format("""

                STRATEGY GUIDANCE:
                %s

                """, context.getStrategy().getStrategyPrompt());
        }

        // Note which agents were consulted (if not all 4)
        String agentSelectionNote = "";
        if (context.getStrategy() != null && context.getStrategy().getEnabledAgents() != null
                && context.getStrategy().getEnabledAgents().size() < 4) {
            String enabledNames = context.getStrategy().getEnabledAgents().stream()
                .map(Enum::name)
                .sorted()
                .collect(java.util.stream.Collectors.joining(", "));
            agentSelectionNote = String.format("""

                NOTE: Only the following agents were consulted for this decision: %s.
                Other agents were not enabled for this strategy. Base your decision only on the available recommendations.
                """, enabledNames);
        }

        // Include technical indicators if available (stocks only)
        String indicatorSection = "";
        if (context.getIndicators() != null && !context.getIndicators().tickerIndicators().isEmpty()) {
            indicatorSection = "\n\n" + context.getIndicators().toPromptText();
        }

        // Compute concrete minimum quantity from current price so the LLM doesn't have to do math
        String minQuantityInstruction = "";
        Money currentPrice = context.getCurrentPrices().get(tickerSymbol);
        if (currentPrice != null && currentPrice.amount().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal minTradeValue = tickerSymbol.type() == com.quorumtrading.model.enums.InvestmentType.CRYPTO
                    ? tradingLimitsConfig.crypto()
                    : tradingLimitsConfig.stock();
            BigDecimal minQty = minTradeValue.divide(currentPrice.amount(), 2, java.math.RoundingMode.UP);
            minQuantityInstruction = String.format(
                    "\n              * At current price $%s, the MINIMUM quantity is %s (worth $%s)",
                    currentPrice.amount().toPlainString(), minQty.toPlainString(), minTradeValue);
        }

        // Include recent trade history for context
        String tradeHistorySection = "";
        if (context.getRecentTradeHistory() != null) {
            tradeHistorySection = String.format("""

                RECENT TRADE HISTORY (for context only — the strategy rules above take precedence):
                %s
                """, context.getRecentTradeHistory());
        }

        return String.format("""
            You are the Lead Orchestrator for an AI trading system.

            CRITICAL INSTRUCTIONS:
            - This is a TRADING DECISION task, NOT a code-writing or code-investigation task
            - Make your decision ONLY based on the information provided below
            - Do NOT suggest writing code, investigating codebases, or modifying systems
            - ONLY respond with a trading decision in the required JSON format
            - You MUST include the symbol '%s' in your response

            QUANTITY (OPTIONAL — the system handles position sizing by default):
            - You may OMIT the "quantity" field entirely. The system will automatically calculate the correct size:
              * BUY → uses all available cash (respecting strategy limits and reserve cash rules)
              * SELL → sells the entire position
            - Only specify "quantity" if the strategy calls for PARTIAL entries/exits (e.g., scale in 50%%)
            - For HOLD decisions: no quantity needed
            - MINIMUM TRADE SIZE: $%s for STOCK, $%s for CRYPTO%s
              * If the portfolio cannot afford the minimum, choose HOLD instead of BUY

            You review recommendations from specialized agents and make the final trading decision.
            %s
            TICKER/SYMBOL BEING ANALYZED: %s

            CURRENT PORTFOLIO:
            - Cash Available: $%s
            %s
            %s
            AGENT RECOMMENDATIONS:
            %s%s%s

            TASK:
            Based on the strategy guidance, portfolio state, agent recommendations, and technical indicators, make a final trading decision for %s: BUY / SELL / HOLD

            Provide your confidence score (0-1) and reasoning for your TRADING DECISION.

            IMPORTANT:
            - The "symbol" field in your response MUST be exactly "%s" (not null, not empty)
            - You may omit "quantity" to let the system auto-size, or include it for partial positions
            """,
            context.getTicker(), // 1st: symbol in CRITICAL INSTRUCTIONS
            tradingLimitsConfig.stock(), // 2nd: minimum trade size for stocks
            tradingLimitsConfig.crypto(), // 3rd: minimum trade size for crypto
            minQuantityInstruction, // 4th: concrete minimum quantity (empty if no price)
            strategyGuidance, // 5th: strategy guidance
            context.getTicker(), // 6th: TICKER/SYMBOL BEING ANALYZED
            context.getPortfolio().cashAvailable().amount(), // 7th: Cash Available in portfolio
            positionInfo, // 8th: Current holdings of ticker being analyzed
            tradeHistorySection, // 9th: trade history section (empty if no trades)
            agentSummary.toString(), // 10th: agent recommendations
            indicatorSection, // 11th: technical indicators
            agentSelectionNote, // 12th: agent selection note (empty if all 4 enabled)
            context.getTicker(), // 13th: ticker in TASK
            context.getTicker() // 14th: ticker in IMPORTANT
        ) + AgentResponseFormat.JSON_RESPONSE_INSTRUCTION + (a2uiEnabled ? A2UI_INSTRUCTION : "");
    }

    /**
     * Extract an optional A2UI payload from the LLM response.
     * Looks for content between &lt;a2ui&gt; and &lt;/a2ui&gt; tags.
     */
    private String extractA2UIPayload(String response) {
        if (response == null) return null;
        int start = response.indexOf("<a2ui>");
        int end = response.indexOf("</a2ui>");
        if (start >= 0 && end > start) {
            String payload = response.substring(start + "<a2ui>".length(), end).trim();
            return payload.isEmpty() ? null : payload;
        }
        return null;
    }

    /**
     * Strip A2UI tags from the response so JSON parsing is not confused.
     */
    private String stripA2UIBlock(String response) {
        if (response == null) return null;
        return response.replaceAll("<a2ui>[\\s\\S]*?</a2ui>", "").trim();
    }

    private LLMDecision parseResponse(String response, long executionTime, AgentContext context, String prompt, String modelName) {
        try {
            // Extract optional A2UI payload before stripping
            String uiPayload = extractA2UIPayload(response);

            // Strip A2UI block, markdown code blocks, and JSON comments before parsing
            String cleanedResponse = AgentResponseParser.stripJsonComments(
                AgentResponseParser.stripMarkdownCodeBlocks(stripA2UIBlock(response)));

            // Fallback: if response has preamble text before JSON, extract the JSON object
            if (cleanedResponse != null && !cleanedResponse.isEmpty()
                    && cleanedResponse.charAt(0) != '{') {
                int jsonStart = cleanedResponse.indexOf('{');
                int jsonEnd = cleanedResponse.lastIndexOf('}');
                if (jsonStart >= 0 && jsonEnd > jsonStart) {
                    logger.debug("Extracting JSON from mixed response (preamble at chars 0-{})", jsonStart);
                    cleanedResponse = cleanedResponse.substring(jsonStart, jsonEnd + 1);
                }
            }

            JsonNode json = objectMapper.readTree(cleanedResponse);

            LLMDecision decision = new LLMDecision(
                context.getSimulationId(),
                context.getTimestamp(),
                UUID.randomUUID().toString(),
                getRole().name(),
                modelName
            );

            decision.setPromptUsed(prompt);
            decision.recordRecommendation(RecommendationType.valueOf(json.get("action").asText().toUpperCase()));
            decision.recordConfidenceScore(new Percentage(new BigDecimal(json.get("confidence").asText())));
            decision.recordReasoning(json.get("reasoning").asText());
            decision.recordRecommendedAction(cleanedResponse); // Store cleaned JSON response (markdown stripped)
            decision.recordExecutionTime((int) executionTime);

            // Extract quantity from LLM response if present
            if (json.has("quantity") && json.get("quantity") != null && !json.get("quantity").isNull()) {
                try {
                    BigDecimal quantity = new BigDecimal(json.get("quantity").asText());
                    decision.setTickerQuantity(quantity);
                    logger.info("Orchestrator extracted quantity: {}", quantity);
                } catch (Exception e) {
                    logger.warn("Failed to parse quantity from orchestrator response: {}", json.get("quantity"), e);
                }
            }

            // Extract and validate ticker from LLM response
            String ticker = null;
            if (json.has("symbol") && json.get("symbol") != null && !json.get("symbol").isNull()) {
                ticker = json.get("symbol").asText();
            }

            // CRITICAL: If LLM didn't provide a valid ticker, use the one from context
            // This ensures orchestrator decisions always have a ticker
            if (ticker == null || ticker.isEmpty() || ticker.equals("null")) {
                logger.warn("LLM returned null/empty symbol, using ticker from context: {}", context.getTicker());
                ticker = context.getTicker();
            }

            decision.recordTicker(ticker);
            decision.recordUiPayload(uiPayload);
            logger.info("Orchestrator decision ticker: {} (from context: {}), a2ui: {}",
                ticker, context.getTicker(), uiPayload != null ? "present" : "absent");

            return decision;
        } catch (Exception e) {
            // Record parse failure for troubleshooting
            if (parseFailureRepository != null) {
                try {
                    parseFailureRepository.save(LlmParseFailure.capture(
                            context.getSimulationId(),
                            context.getTimestamp(),
                            context.getTicker(),
                            getRole().name(),
                            modelName,
                            (int) executionTime,
                            prompt,
                            response,
                            e));
                } catch (Exception failureEx) {
                    logger.warn("Failed to record orchestrator parse failure: {}", failureEx.getMessage());
                }
            }

            // Fallback parsing - default to SKIP if parsing fails
            LLMDecision decision = new LLMDecision(
                context.getSimulationId(),
                context.getTimestamp(),
                UUID.randomUUID().toString(),
                getRole().name(),
                "fallback"
            );

            decision.setPromptUsed(prompt);
            decision.recordRecommendation(RecommendationType.SKIP);
            decision.recordConfidenceScore(new Percentage(new BigDecimal("0.0")));
            decision.recordReasoning("Agent failed to provide analysis - response could not be parsed");
            decision.recordRecommendedAction(
                AgentResponseParser.buildSkipActionJson(context.getTicker(), "Orchestrator failed to provide analysis"));
            decision.recordTicker(context.getTicker()); // Use ticker from context as fallback
            decision.setTickerQuantity(BigDecimal.ZERO); // Fallback to zero quantity for SKIP
            decision.recordExecutionTime((int) executionTime);
            String responseSnippet = response != null && response.length() > 200
                ? response.substring(0, 200) + "..." : response;
            logger.warn("Orchestrator parse failure for ticker {} (sim {}): {} | Response: {}",
                context.getTicker(), context.getSimulationId(), e.getMessage(), responseSnippet);
            return decision;
        }
    }

}
