-- Cash contributions injected into LIVE simulations after their initial cash
-- (recurring-investment plan top-ups + manual contributions).
--
-- Append-only stream. UNIQUE (source_plan_id, contribution_date) prevents
-- scheduler retries from double-funding the same plan + day.

CREATE TABLE simulation_contributions (
    id                    UUID PRIMARY KEY,
    simulation_id         UUID NOT NULL REFERENCES simulations(id) ON DELETE CASCADE,
    amount                NUMERIC(20, 8) NOT NULL CHECK (amount > 0),
    currency              VARCHAR(8)     NOT NULL DEFAULT 'USD',
    contribution_date     TIMESTAMP WITH TIME ZONE NOT NULL,
    source_plan_id        UUID,
    source_allocation_id  UUID,
    created_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_simulation_contributions_simulation
    ON simulation_contributions(simulation_id, contribution_date);

-- Idempotency for scheduler retries — only enforced when sourced from a plan.
CREATE UNIQUE INDEX idx_simulation_contributions_plan_date
    ON simulation_contributions(source_plan_id, contribution_date)
    WHERE source_plan_id IS NOT NULL;
