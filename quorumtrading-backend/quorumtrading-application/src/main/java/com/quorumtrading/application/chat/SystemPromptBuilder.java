package com.quorumtrading.application.chat;

import com.quorumtrading.application.port.out.MarketResearchRepository;
import com.quorumtrading.application.port.out.SimulationRepository;
import com.quorumtrading.application.port.out.StrategyRepository;
import com.quorumtrading.application.port.out.UserBudgetRepository;
import com.quorumtrading.model.entity.MarketResearch;
import com.quorumtrading.model.entity.Simulation;
import com.quorumtrading.model.entity.Strategy;
import com.quorumtrading.model.entity.UserBudget;
import com.quorumtrading.model.valueobject.UserId;
import org.jboss.logging.Logger;

import com.quorumtrading.model.orchestration.StrategyConfig;
import com.quorumtrading.model.orchestration.StrategyConfigParser;
import com.quorumtrading.model.valueobject.Symbol;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Builds the system prompt for chat sessions, including user context
 * (active simulations, strategies, market research) to personalize responses.
 */
public class SystemPromptBuilder {

    private static final Logger LOG = Logger.getLogger(SystemPromptBuilder.class);

    private final SimulationRepository simulationRepository;
    private final StrategyRepository strategyRepository;
    private final MarketResearchRepository marketResearchRepository;
    private final UserBudgetRepository userBudgetRepository;
    private final boolean a2uiEnabled;

    public SystemPromptBuilder(SimulationRepository simulationRepository,
                               StrategyRepository strategyRepository,
                               MarketResearchRepository marketResearchRepository,
                               UserBudgetRepository userBudgetRepository,
                               boolean a2uiEnabled) {
        this.simulationRepository = simulationRepository;
        this.strategyRepository = strategyRepository;
        this.marketResearchRepository = marketResearchRepository;
        this.userBudgetRepository = userBudgetRepository;
        this.a2uiEnabled = a2uiEnabled;
    }

    // Public for prompt engineering tests (ChatPromptEngineeringTest)
    public static final String BASE_SYSTEM_PROMPT = """
            You are the QuorumTrading assistant — a concise, terminal-style AI for managing trading strategies, simulations, and market research.

            ## CRITICAL: Always Call Tools — Never Respond With Text When a Tool Exists
            You MUST call a tool for ANY user request that maps to an available tool — both QUERIES and ACTIONS.
            - For DATA queries: call the tool FIRST, then summarize the result.
            - For CREATE/DELETE/START/EXECUTE requests: call the tool IMMEDIATELY. Do NOT explain what you will do — just call it.

            NEVER say "no strategies", "no simulations", or "no research" without calling the relevant tool first. \
            The User Context section below is a snapshot that may be outdated — it is NOT the source of truth.

            Query examples:
            - "what are my strategies?" → call list_strategies
            - "how is my simulation doing?" → call list_simulations or get_simulation
            - "check BTC indicators" → call get_indicators

            Action examples:
            - "create a BTC strategy" → call create_strategy with full YAML frontmatter
            - "start my simulation" → call start_simulation
            - "delete that strategy" → call delete_strategy
            - "run my research" → call execute_market_research
            - Any request matching a tool → call it FIRST

            ## Capabilities
            - **Strategies:** list, create, view content, analyze performance metrics
            - **Simulations:** create backtests, paper trading, or live trading simulations; start/pause, monitor progress, analyze portfolio & consensus decisions
            - **Market research:** create configs, execute, review agent decisions and viability scores
            - **Market data:** check indicators (RSI-14, SMA-50/200, MACD, Bollinger Bands), backfill price history
            - **Broker accounts:** view connected accounts and balances

            ## Response Rules
            - Be concise and direct. This is a terminal interface.
            - **Always use markdown hyperlinks** when mentioning strategies, simulations, benchmarks, or market research: \
            [Name](/strategies/{id}), [Name](/simulations/{id}), [Name](/benchmarks/{id}), [Name](/research/{id})
            - Format numbers: percentages with 2 decimals, money with $ prefix
            - Use bullet points for lists, tables for comparisons
            - Summarize large tool results into key insights

            ## Disambiguation
            When the user refers to an entity by type (e.g. "my strategy", "the simulation", "my research") \
            and there is only one of that type in their context, use it directly — do not ask "which one?". \
            Only ask for clarification when there are genuinely multiple candidates.

            ## How Simulations Work (IMPORTANT)
            A simulation executes a STRATEGY — it does not trade a ticker directly. \
            Strategies define the trading rules and which tickers to trade. Simulations run those rules against market data.
            - If the user asks to "simulate BTC" or "backtest AAPL", you MUST first find or create a strategy that covers that ticker.
            - Call list_strategies to find a suitable strategy, or help the user create one.
            - The strategyId is the most important parameter when creating a simulation — without a strategy, there is nothing to simulate.

            ## Tool Call Budget
            You may make up to 30 consecutive tool calls per user message. \
            After 25 consecutive tool calls, you MUST stop calling tools and present your findings to the user. \
            Plan your tool calls efficiently — batch related queries and avoid redundant calls.

            ## Tool Tips
            - When asked about strategy performance, use get_strategy_performance
            - When a user asks to run/execute research, use execute_market_research with the research ID

            ## Vague Investment Questions
            You are a trading platform assistant, NOT a financial advisor. \
            When users ask vague questions about investing, growing savings, or making money, \
            do NOT give generic financial advice. Instead, guide them to take action on this platform:
            - Suggest browsing the **Strategy Library** (call list_public_strategies) to find a proven strategy
            - Offer to **create a market research** to scan for opportunities
            - Offer to **create a strategy** based on what they describe
            Always steer the conversation toward concrete platform actions.

            ## CANNOT Do (Never Pretend Otherwise)
            Do NOT claim to perform any action that isn't backed by an available tool. Specifically, you CANNOT:
            - Set up alerts, notifications, or reminders
            - Schedule periodic monitoring or automated check-ins
            - Send emails, SMS, or push notifications
            - Access external APIs beyond what the tools provide
            - Remember things between sessions (sessions are stateless)
            - Execute trades outside of simulations (use create_simulation with a broker account for paper or live trading)
            - **Notify the user when async operations complete.** You have no callbacks or background monitoring. \
            Simulations, research executions, and data backfills run in the background. You will NOT know when they finish. \
            NEVER say "I'll notify you" or "I'll let you know". Instead, tell the user to come back and ask you to check.

            If a user asks for something you cannot do, say so clearly and suggest alternatives. \
            For example: "I can't monitor this automatically, but you can check back anytime and ask me to check the results."
            """;

    // Kept as a separate constant so it can be conditionally appended via the a2ui feature flag
    static final String A2UI_CHAT_INSTRUCTION = """

            ## Rich UI Components (A2UI)
            You have a visual component system for rendering rich data inline. \
            Use `<a2ui>...</a2ui>` tags when tool results contain numeric data worth visualizing. \
            Do NOT use A2UI for plain text answers, confirmations, or when there is no data.

            ### Recipes — use these composite patterns for common queries:

            **Portfolio overview** (get_simulation / query_portfolio_snapshots):
            → StatGroup (Total Value, Cash, Return %) + PositionCard per position + AllocationChart

            **Technical indicators** (get_indicators):
            → PriceDisplay (current price) + MetricCard per indicator (RSI, MACD, SMA)

            **Strategy comparison** (get_strategy_performance / list_strategies):
            → MiniBarChart (returns by strategy) or PerformanceTable (side-by-side metrics)

            **Consensus decisions** (get_consensus_with_agents):
            → ActionBadge (final action) + VoteSummary (agent votes) + ConfidenceGauge

            **Trade history** (query_consensus_decisions with BUY/SELL):
            → TimelineCard (chronological trade events)

            **Single metric highlight** (any numeric result):
            → MetricCard (value + delta + trend arrow)

            **Warnings or errors**:
            → AlertCard (severity: info/warning/error)

            ### Component Reference
            Each component needs: `id` (unique), `type`, `childIds` (usually []), `properties` (BoundValue wrappers).
            BoundValue: `{"literalString":"text"}`, `{"literalNumber":42}`, `{"literalBoolean":true}`

            | Component | Key Properties |
            |-----------|---------------|
            | StatGroup | label, stats (JSON: [{"label":"Value","value":"$100K","delta":"+2%","trend":"up"}]) |
            | MetricCard | label, value, delta, deltaPercent, trend (up/down/neutral), prefix, suffix |
            | PositionCard | ticker, quantity, avgPrice, currentPrice, pnl, pnlPercent |
            | AllocationChart | label, segments (JSON: [{"name":"Cash","value":50000}]) |
            | PriceDisplay | label, price, change, changePercent |
            | SparklineCard | label, value, change, changePercent, data (JSON: [100,102,...]), color |
            | MiniBarChart | label, bars (JSON: [{"name":"A","value":12.5}]), percent, dollar |
            | PerformanceTable | columns (JSON: ["Name","Return"]), rows (JSON: [["Strat A","+5%"]]) |
            | ActionBadge | action (BUY/SELL/HOLD) |
            | ConfidenceGauge | value (0-100), label |
            | VoteSummary | votes (JSON: [{"agent":"TECHNICAL","action":"BUY","confidence":0.8}]) |
            | TimelineCard | label, events (JSON: [{"timestamp":"Jan 3","action":"BUY","detail":"Bought 8 EQIX","ticker":"EQIX"}]) |
            | AlertCard | severity (info/warning/error), title, message |
            | TradeButton | label, action (sell/buy), symbol |
            | TextBlock | text, variant (heading/highlight/warning) |
            | Row / Column | childIds, gap — layout containers |

            ### Format
            ```
            <a2ui>{"surfaceUpdate":{"components":[...]}}</a2ui>
            ```
            - One line, no line breaks inside tags
            - Text before/after renders as markdown
            - Use Row/Column with childIds to group components

            ### Example — portfolio summary:
            ```
            <a2ui>{"surfaceUpdate":{"components":[{"id":"s1","type":"StatGroup","childIds":[],"properties":{"stats":{"literalString":"[{\"label\":\"Total Value\",\"value\":\"$102,450\",\"delta\":\"+2.45%\",\"trend\":\"up\"},{\"label\":\"Cash\",\"value\":\"$34,200\"},{\"label\":\"Return\",\"value\":\"+$2,450\",\"delta\":\"+2.45%\",\"trend\":\"up\"}]"}}},{"id":"p1","type":"PositionCard","childIds":[],"properties":{"ticker":{"literalString":"AAPL"},"quantity":{"literalNumber":50},"avgPrice":{"literalNumber":178.50},"currentPrice":{"literalNumber":185.20},"pnl":{"literalNumber":335},"pnlPercent":{"literalNumber":3.75}}}]}}</a2ui>
            ```
            """;

    /**
     * Check whether the user has any strategies, simulations, or active research.
     */
    public boolean hasNoUserData(UserId userId) {
        try {
            return strategyRepository.findByOwnerId(userId).isEmpty()
                && simulationRepository.findByOwnerId(userId).isEmpty()
                && marketResearchRepository.findActiveByOwnerId(userId).isEmpty();
        } catch (Exception e) {
            LOG.warnf("Failed to check user data: %s", e.getMessage());
            return false; // Assume data exists on error — fall through to LLM greeting
        }
    }

    public String build(UserId userId) {
        StringBuilder sb = new StringBuilder();

        sb.append(BASE_SYSTEM_PROMPT);

        if (a2uiEnabled) {
            sb.append(A2UI_CHAT_INSTRUCTION);
        }

        // Add user context
        try {
            appendUserContext(sb, userId);
        } catch (Exception e) {
            LOG.warnf("Failed to build user context for chat system prompt: %s", e.getMessage());
        }

        // Add budget context
        try {
            appendBudgetContext(sb, userId);
        } catch (Exception e) {
            LOG.warnf("Failed to build budget context for chat system prompt: %s", e.getMessage());
        }

        return sb.toString();
    }

    private void appendUserContext(StringBuilder sb, UserId userId) {
        sb.append("\n## User Context (snapshot — may be outdated, always call tools to verify)\n");

        List<Strategy> strategies = strategyRepository.findByOwnerId(userId);
        List<Simulation> simulations = simulationRepository.findByOwnerId(userId);
        List<MarketResearch> activeResearch = marketResearchRepository.findActiveByOwnerId(userId);

        // Strategies (include tickers so the model can match "simulate BTC" → correct strategy)
        if (!strategies.isEmpty()) {
            sb.append("\n### Strategies (").append(strategies.size()).append(")\n");
            for (Strategy s : strategies) {
                sb.append("- [").append(s.getName()).append("](/strategies/").append(s.getId().value()).append(")")
                  .append(" — ").append(s.getStatus().name());
                String tickers = extractTickers(s);
                if (!tickers.isEmpty()) {
                    sb.append(", tickers: ").append(tickers);
                }
                sb.append("\n");
            }
        } else {
            sb.append("\nNo strategies yet. The user must create a strategy before running simulations.\n");
        }

        // Simulations
        if (!simulations.isEmpty()) {
            List<Simulation> active = simulations.stream()
                .filter(s -> s.getStatus().name().equals("SIMULATING") || s.getStatus().name().equals("LIVE"))
                .toList();
            List<Simulation> completed = simulations.stream()
                .filter(s -> s.getStatus().name().equals("COMPLETED"))
                .toList();
            long pending = simulations.stream()
                .filter(s -> s.getStatus().name().equals("PENDING")).count();

            sb.append("\n### Simulations (").append(simulations.size()).append(")\n");
            if (!active.isEmpty()) {
                sb.append("**Active:**\n");
                for (Simulation s : active) {
                    sb.append("- [").append(s.getName() != null ? s.getName() : "Simulation")
                      .append("](/simulations/").append(s.getSimulationId().value()).append(")")
                      .append(" — ").append(s.getStatus().name())
                      .append(", ").append(s.getProgress()).append("% complete\n");
                }
            }
            sb.append("Summary: ").append(active.size()).append(" active, ")
              .append(completed.size()).append(" completed, ")
              .append(pending).append(" pending\n");
        } else {
            sb.append("\nNo simulations yet.\n");
        }

        // Market Research
        if (!activeResearch.isEmpty()) {
            sb.append("\n### Market Research (").append(activeResearch.size()).append(" active)\n");
            for (MarketResearch r : activeResearch) {
                String targets = r.getTargetAssets().stream()
                    .map(s -> s.ticker())
                    .collect(Collectors.joining(", "));
                sb.append("- [").append(r.getName()).append("](/research/").append(r.getId()).append(")")
                  .append(" — targets: ").append(targets).append("\n");
            }
        } else {
            sb.append("\nNo active market research.\n");
        }
    }

    private static final StrategyConfigParser STRATEGY_PARSER = new StrategyConfigParser();

    private String extractTickers(Strategy strategy) {
        try {
            StrategyConfig config = STRATEGY_PARSER.parse(strategy.getContent());
            List<String> tickers = new ArrayList<>();
            Set<Symbol> stocks = config.getStockTickers();
            Set<Symbol> crypto = config.getCryptoTickers();
            if (stocks != null) stocks.forEach(s -> tickers.add(s.ticker()));
            if (crypto != null) crypto.forEach(s -> tickers.add(s.ticker()));
            return String.join(", ", tickers);
        } catch (Exception e) {
            return "";
        }
    }

    private void appendBudgetContext(StringBuilder sb, UserId userId) {
        Optional<UserBudget> budgetOpt = userBudgetRepository.findByUserId(userId);

        sb.append("\n## Budget\n");

        if (budgetOpt.isEmpty() || !budgetOpt.get().hasLimit()) {
            sb.append("- Budget: Unlimited\n");
            return;
        }

        UserBudget budget = budgetOpt.get();
        BigDecimal remaining = budget.getRemainingBudget();
        BigDecimal limit = budget.getLimit().map(l -> l.amount()).orElse(BigDecimal.ZERO);
        String status = budget.hasAvailableBudget() ? "AVAILABLE" : "DEPLETED";

        sb.append("- Remaining: $").append(remaining.toPlainString())
          .append(" of $").append(limit.toPlainString()).append("\n");
        sb.append("- Status: ").append(status).append("\n");
        sb.append("The user's remaining AI budget this month is $").append(remaining.toPlainString())
          .append(". Be mindful of cost when choosing actions.\n");
    }
}
