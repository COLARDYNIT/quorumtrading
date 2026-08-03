-- V43__increase_strategy_name_length.sql
-- Increase strategy name column from VARCHAR(50) to VARCHAR(255) to accommodate AI-generated strategy names
-- Example: "GLP-1 Oral Breakthrough & Amylin Acceleration Play" (52 chars) was failing

ALTER TABLE orchestrator_strategies ALTER COLUMN name TYPE VARCHAR(255);
