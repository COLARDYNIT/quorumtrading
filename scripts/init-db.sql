-- Initial database setup for QuorumTrading

-- Enable required extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Create schemas
CREATE SCHEMA IF NOT EXISTS public;

-- Placeholder tables (will be replaced by Flyway migrations)
-- This file just ensures the database is ready for the application

-- Log initialization
DO $$
BEGIN
    RAISE NOTICE 'QuorumTrading database initialized successfully';
END $$;
