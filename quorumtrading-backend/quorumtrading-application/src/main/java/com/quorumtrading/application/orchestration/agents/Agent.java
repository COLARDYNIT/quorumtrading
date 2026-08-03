package com.quorumtrading.application.orchestration.agents;

import com.quorumtrading.model.entity.LLMDecision;
import com.quorumtrading.model.enums.AgentRole;
import com.quorumtrading.model.orchestration.AgentContext;

public interface Agent {
    /**
     * Analyze the context and return a decision.
     * This method is synchronous - async execution is handled by the caller.
     */
    LLMDecision analyze(AgentContext context);

    AgentRole getRole();

    /**
     * Whether an agent has the data it needs, and if not, why not.
     *
     * <p>The reason is user-facing: it ends up in the consensus note so a reader can tell a
     * deliberate abstention ("no news for this ticker") apart from a malfunction ("the LLM call
     * failed") without digging through backend logs.</p>
     *
     * @param sufficient true when the agent can produce a meaningful decision
     * @param reason short explanation when {@code sufficient} is false; null otherwise
     */
    record ContextCheck(boolean sufficient, String reason) {

        private static final ContextCheck SUFFICIENT = new ContextCheck(true, null);

        /** Named {@code ok} rather than {@code sufficient} — the latter is the accessor. */
        public static ContextCheck ok() {
            return SUFFICIENT;
        }

        public static ContextCheck insufficient(String reason) {
            return new ContextCheck(false, reason);
        }
    }

    /**
     * Determine whether this agent has sufficient context data to produce a meaningful decision,
     * and explain the gap when it does not.
     * <p>
     * Agents that depend on specific external data (e.g., news for sentiment analysis) should
     * override this to return {@link ContextCheck#insufficient(String)} when that data is
     * unavailable — for example, during backtests with no historical news. An insufficient result
     * causes the agent to be skipped entirely for this consensus round, saving an LLM call that
     * would produce a low-quality guess.
     * <p>
     * Implementations must be side-effect free: the caller evaluates this both when filtering
     * agents and again when explaining who sat out, so logging here would double up.
     * <p>
     * Live simulations typically return sufficient because agents can search the web for current data.
     *
     * @param context the current agent context with market data, portfolio, etc.
     * @return whether the agent has enough data, plus the reason when it does not
     */
    default ContextCheck checkContext(AgentContext context) {
        return ContextCheck.ok();
    }

    /**
     * Convenience predicate over {@link #checkContext(AgentContext)}.
     *
     * @param context the current agent context with market data, portfolio, etc.
     * @return true if the agent has enough data to add value to the consensus
     */
    default boolean hasContextForDecision(AgentContext context) {
        return checkContext(context).sufficient();
    }

    /**
     * Determine if this agent may use a cached HOLD decision instead of calling the LLM.
     * Returns true if there is no fresh, relevant new data for this agent.
     * "Fresh" is configurable in application.properties per agent in minutes.
     *
     * @param context the current agent context with market data, news, etc.
     * @return true if cached decision may be used, false otherwise
     */
    default boolean mayUseCachedDecision(AgentContext context) {
        // Default implementation: always call LLM (no caching)
        // Individual agents can override this to implement caching logic
        return false;
    }

    /**
     * Whether a previously stored decision is recent enough to stand in for this evaluation.
     * <p>
     * Separate from {@link #mayUseCachedDecision(AgentContext)}: that answers "has anything changed
     * that would warrant fresh analysis", this answers "is the candidate still current". The
     * repository lookup returns the most recent prior decision regardless of age, so without this
     * bound a single HOLD is replayed for the remainder of a run and presented as each session's
     * analysis. Agents that configure a TTL should override.
     *
     * @param cached  the candidate decision from a previous evaluation
     * @param context the current agent context, whose timestamp is "now" for a backtest
     * @return true if the cached decision may still be reused
     */
    default boolean isCachedDecisionStillFresh(LLMDecision cached, AgentContext context) {
        // Default: no TTL notion, so freshness is decided entirely by mayUseCachedDecision.
        return true;
    }
}
