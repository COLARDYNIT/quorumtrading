-- V38__migrate_event_types.sql
-- Migrate old event_type values to match current EventType enum values
-- This fixes the "No enum constant ai.yoloquant.model.enums.EventType.PRICE_UPDATE" error

-- Update PRICE_UPDATE -> PRICE
UPDATE market_events
SET event_type = 'PRICE'
WHERE event_type = 'PRICE_UPDATE';

-- Update REDDIT_POST -> SOCIAL
UPDATE market_events
SET event_type = 'SOCIAL'
WHERE event_type = 'REDDIT_POST';

-- Note: NEWS remains unchanged as it matches the enum
