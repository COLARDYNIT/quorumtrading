-- Remove COVID Crash MSFT Survivor challenge — no Alpaca price data before July 2020
-- Detach any simulations first, then delete the challenge
UPDATE simulations SET challenge_id = NULL WHERE challenge_id = 'a0000001-0000-0000-0000-000000000001';
DELETE FROM challenges WHERE id = 'a0000001-0000-0000-0000-000000000001';
