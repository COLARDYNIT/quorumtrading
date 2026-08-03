-- V18__add_cascade_delete_constraints.sql
-- Add ON DELETE CASCADE to foreign keys for proper cascade deletion

-- Drop existing foreign key constraints and recreate with ON DELETE CASCADE

-- 1. Simulations table: strategy_id foreign key
ALTER TABLE simulations
    DROP CONSTRAINT IF EXISTS simulations_strategy_id_fkey;

ALTER TABLE simulations
    ADD CONSTRAINT simulations_strategy_id_fkey
        FOREIGN KEY (strategy_id)
        REFERENCES orchestrator_strategies(id)
        ON DELETE CASCADE;

-- 2. llm_decisions table: simulation_id foreign key
ALTER TABLE llm_decisions
    DROP CONSTRAINT IF EXISTS llm_decisions_simulation_id_fkey;

ALTER TABLE llm_decisions
    ADD CONSTRAINT llm_decisions_simulation_id_fkey
        FOREIGN KEY (simulation_id)
        REFERENCES simulations(id)
        ON DELETE CASCADE;

-- 3. consensus_decisions table: simulation_id foreign key
ALTER TABLE consensus_decisions
    DROP CONSTRAINT IF EXISTS consensus_decisions_simulation_id_fkey;

ALTER TABLE consensus_decisions
    ADD CONSTRAINT consensus_decisions_simulation_id_fkey
        FOREIGN KEY (simulation_id)
        REFERENCES simulations(id)
        ON DELETE CASCADE;

-- 4. portfolio_snapshots table: simulation_id foreign key
ALTER TABLE portfolio_snapshots
    DROP CONSTRAINT IF EXISTS portfolio_snapshots_simulation_id_fkey;

ALTER TABLE portfolio_snapshots
    ADD CONSTRAINT portfolio_snapshots_simulation_id_fkey
        FOREIGN KEY (simulation_id)
        REFERENCES simulations(id)
        ON DELETE CASCADE;

-- 5. llm_performance table: simulation_id foreign key
ALTER TABLE llm_performance
    DROP CONSTRAINT IF EXISTS llm_performance_simulation_id_fkey;

ALTER TABLE llm_performance
    ADD CONSTRAINT llm_performance_simulation_id_fkey
        FOREIGN KEY (simulation_id)
        REFERENCES simulations(id)
        ON DELETE CASCADE;

-- Now when a strategy is deleted:
-- 1. All simulations linked to that strategy will be automatically deleted
-- 2. All llm_decisions linked to those simulations will be automatically deleted
-- 3. All consensus_decisions linked to those simulations will be automatically deleted
-- 4. All portfolio_snapshots linked to those simulations will be automatically deleted
-- 5. All llm_performance records linked to those simulations will be automatically deleted
