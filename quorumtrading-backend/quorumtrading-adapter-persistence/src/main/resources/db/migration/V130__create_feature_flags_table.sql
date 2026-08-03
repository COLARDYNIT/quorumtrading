-- V130: Runtime feature flags, evaluated by the Unleash SDK against this table.
--
-- No rows are inserted here. Seeding happens at boot from the per-profile defaults in
-- application.properties (see SeedFeatureFlagsService), because the starting position for a flag
-- differs per environment and a migration cannot know which profile it is running under.
-- Seeding is insert-if-absent, so an administrator's change survives redeploys.

CREATE TABLE feature_flags (
    -- The Unleash toggle name, matching Feature.flagName() in the domain registry.
    -- Rows whose name is not in that registry are ignored on read.
    name VARCHAR(64) PRIMARY KEY,

    -- The master switch. When false the flag is off regardless of strategies.
    enabled BOOLEAN NOT NULL DEFAULT FALSE,

    -- Unleash activation strategies as a JSON array, e.g.
    --   [{"name":"default","parameters":{}}]
    --   [{"name":"userWithId","parameters":{"userIds":"someone@example.com"}}]
    -- An empty array on an enabled flag is normalised to "default" when read.
    strategies TEXT NOT NULL DEFAULT '[]',

    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    -- Audit: which administrator last changed this. NULL for seeded rows.
    updated_by UUID REFERENCES users(id) ON DELETE SET NULL
);

-- The Unleash SDK polls for definitions; the poll probes MAX(updated_at) first and only
-- re-reads the rows when it moved.
CREATE INDEX idx_feature_flags_updated_at ON feature_flags(updated_at);
