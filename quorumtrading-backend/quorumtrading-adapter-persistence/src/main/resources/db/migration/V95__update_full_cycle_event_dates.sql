-- Update full-cycle-5y dates: start from when SMA-200 is valid (200 trading
-- days after earliest QQQ data on 2020-07-27) and extend end date to Feb 2026.
UPDATE historical_events
SET start_date = '2021-06-01',
    end_date   = '2026-02-20',
    description = 'Complete market cycle covering post-COVID rally, meme stock mania, crypto boom and bust, aggressive rate hikes, 2022 bear market, banking crisis, and the AI rally. Starts after SMA-200 warm-up period for reliable moving average signals.',
    impact_summary = 'S&P 500: +50% | BTC: +400% | Multiple drawdowns >20% | 2022 bear & recovery'
WHERE slug = 'full-cycle-5y';
