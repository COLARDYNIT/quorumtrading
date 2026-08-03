-- Remove sharing and notifications feature (strategy sharing was removed from the UI)
-- Both tables were exclusively used for strategy sharing functionality

DROP TABLE IF EXISTS strategy_shares CASCADE;
DROP TABLE IF EXISTS notifications CASCADE;
