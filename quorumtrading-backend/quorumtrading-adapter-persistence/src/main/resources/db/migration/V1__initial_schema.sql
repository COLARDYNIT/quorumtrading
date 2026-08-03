-- V1__initial_schema.sql
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE orchestrator_strategies (
    id BIGSERIAL PRIMARY KEY,
    version VARCHAR(50) UNIQUE NOT NULL,
    markdown_content TEXT NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    created_by VARCHAR(100),
    notes TEXT
);

-- Insert default strategy
INSERT INTO orchestrator_strategies (version, markdown_content, notes)
VALUES ('v1.0-default', '# Default Strategy

## Core Directive
Paper trading only. Conservative position sizing.

## Position Sizing Rules
- Maximum 20% portfolio in any single asset
- Reserve 30% cash
- Risk per trade: max 5% of portfolio

## Revaluation Triggers
- Every 6 hours

## Decision Framework
Require 60% consensus from agents.
', 'Initial default strategy');
