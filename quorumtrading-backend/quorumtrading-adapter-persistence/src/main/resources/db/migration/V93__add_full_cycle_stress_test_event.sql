-- 5-year full market cycle event for comprehensive stress testing
INSERT INTO historical_events (slug, name, start_date, end_date, category, asset_class, sentiment, description, impact_summary, suggested_assets, keywords) VALUES
('full-cycle-5y', '5-Year Full Market Cycle', '2020-08-01', '2025-01-31', 'VOLATILITY', 'BOTH', 'MIXED',
 'Complete market cycle covering COVID recovery, meme stock mania, crypto boom and bust, aggressive rate hikes, banking crisis, and the AI rally. The ultimate strategy stress test.',
 'S&P 500: +90% | BTC: +900% | Multiple drawdowns >20%',
 'SPY,QQQ,BTC,ETH,NVDA,TSLA',
 'stress test,full cycle,5 year,five year,comprehensive,all weather,robustness,multi-regime');
