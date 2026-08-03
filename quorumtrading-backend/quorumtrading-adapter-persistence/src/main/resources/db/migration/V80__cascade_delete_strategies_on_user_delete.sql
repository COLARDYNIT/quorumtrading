ALTER TABLE orchestrator_strategies
    DROP CONSTRAINT orchestrator_strategies_owner_id_fkey,
    ADD CONSTRAINT orchestrator_strategies_owner_id_fkey
        FOREIGN KEY (owner_id) REFERENCES users(id) ON DELETE CASCADE;
