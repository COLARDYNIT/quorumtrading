-- V91: Correct lifetime_spend_amount using ALL cost sources.
-- V90 only used llm_usage_log (chat costs). Simulations and research
-- record costs via consensus_decisions and research_executions respectively.
-- Markup rate: 1.50x (LLMUsage.DEFAULT_MARKUP_RATE)

UPDATE user_budgets ub
SET lifetime_spend_amount = (
    -- 1) Simulation consensus costs (via strategy owner)
    COALESCE((
        SELECT SUM(cd.raw_cost_usd) * 1.50
        FROM consensus_decisions cd
        JOIN simulations s ON s.id = cd.simulation_id
        JOIN orchestrator_strategies os ON os.id = s.strategy_id
        WHERE os.owner_id = ub.user_id
    ), 0)
    +
    -- 2) Market research execution costs (via research owner)
    COALESCE((
        SELECT SUM(re.raw_cost_usd) * 1.50
        FROM research_executions re
        JOIN market_research mr ON mr.id = re.market_research_id
        WHERE mr.owner_id = ub.user_id
    ), 0)
    +
    -- 3) Chat / ticker-find costs (logged directly)
    COALESCE((
        SELECT SUM(l.raw_cost_usd) * 1.50
        FROM llm_usage_log l
        WHERE l.user_id = ub.user_id
    ), 0)
);
