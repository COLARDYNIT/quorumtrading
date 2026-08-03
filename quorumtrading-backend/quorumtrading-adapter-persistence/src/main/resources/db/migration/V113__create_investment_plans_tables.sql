-- Recurring-investment plans + per-execution audit trail (Phase 3).

CREATE TABLE investment_plans (
    id                  UUID PRIMARY KEY,
    user_id             UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    broker_account_id   UUID NOT NULL REFERENCES broker_accounts(id) ON DELETE RESTRICT,
    budget_mode         VARCHAR(30)  NOT NULL,
    amount              NUMERIC(20, 8),
    currency            VARCHAR(8)   NOT NULL DEFAULT 'USD',
    day_of_month        SMALLINT     NOT NULL CHECK (day_of_month BETWEEN 1 AND 28),
    picker_policy       VARCHAR(40)  NOT NULL,
    preferences         JSONB        NOT NULL DEFAULT '{}'::jsonb,
    status              VARCHAR(20)  NOT NULL,
    next_execution_at   TIMESTAMP WITH TIME ZONE,
    last_execution_at   TIMESTAMP WITH TIME ZONE,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_investment_plans_user_status
    ON investment_plans(user_id, status);

-- Daily scheduler probes for due ACTIVE plans.
CREATE INDEX idx_investment_plans_due
    ON investment_plans(next_execution_at)
    WHERE status = 'ACTIVE';


CREATE TABLE investment_allocations (
    id                       UUID PRIMARY KEY,
    plan_id                  UUID NOT NULL REFERENCES investment_plans(id) ON DELETE CASCADE,
    user_id                  UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    execution_date           TIMESTAMP WITH TIME ZONE NOT NULL,
    status                   VARCHAR(40) NOT NULL,
    intended_amount          NUMERIC(20, 8) NOT NULL,
    actual_amount            NUMERIC(20, 8),
    currency                 VARCHAR(8) NOT NULL DEFAULT 'USD',
    chosen_strategy_id       UUID,
    picker_reasoning         TEXT,
    picker_llm_cost_usd      NUMERIC(20, 8) NOT NULL DEFAULT 0,
    action                   VARCHAR(30),
    target_simulation_id     UUID,
    contribution_id          UUID REFERENCES simulation_contributions(id) ON DELETE SET NULL,
    broker_order_refs        JSONB NOT NULL DEFAULT '[]'::jsonb,
    failure_reason           TEXT,
    created_at               TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_investment_allocations_plan
    ON investment_allocations(plan_id, execution_date DESC);

CREATE INDEX idx_investment_allocations_user
    ON investment_allocations(user_id, execution_date DESC);
