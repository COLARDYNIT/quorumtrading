-- Remove historical events that start before August 2020.
-- We don't have price data to support backtesting these periods.
DELETE FROM historical_events WHERE start_date < '2020-08-01';
