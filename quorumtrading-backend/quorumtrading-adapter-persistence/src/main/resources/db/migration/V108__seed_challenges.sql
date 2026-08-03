-- Seed challenges: historical events + live competitions
-- Uses system user (00000000-0000-0000-0000-000000000000) as creator

-- ============================================================================
-- HISTORICAL EVENT CHALLENGES
-- ============================================================================

-- 1. COVID Crash MSFT Survivor (Feb-Apr 2020)
INSERT INTO challenges (id, name, description, ticker, initial_cash, simulation_start_date, simulation_end_date, participation_open, participation_close, created_by, strategies_public)
VALUES (
  'a0000001-0000-0000-0000-000000000001',
  'COVID Crash MSFT Survivor',
  E'The COVID-19 crash of February–April 2020 was one of the fastest market drops in history. MSFT fell from $190 to $132 in three weeks, then recovered to $180 by end of April.\n\nCan your strategy survive the crash **and** catch the recovery? You start with $100,000 on Feb 1, 2020. The simulation ends Apr 30, 2020. Ranked by total portfolio value.\n\n## The challenge\n- **Don''t panic sell at the bottom** — many strategies sell too late and miss the recovery\n- **Don''t hold blindly** — the drop was 30%, a stop-loss could save you\n- **Timing the re-entry** is what separates good strategies from great ones',
  'MSFT', 100000,
  '2020-02-01T00:00:00Z', '2020-04-30T23:59:59Z',
  '2026-03-26T00:00:00Z', '2026-04-10T23:59:59Z',
  '00000000-0000-0000-0000-000000000000', false
);

-- 2. GameStop: The Squeeze (Jan-Mar 2021)
INSERT INTO challenges (id, name, description, ticker, initial_cash, simulation_start_date, simulation_end_date, participation_open, participation_close, created_by, strategies_public)
VALUES (
  'a0000001-0000-0000-0000-000000000002',
  'GameStop: The Squeeze',
  E'January 2021. A band of retail traders on Reddit''s r/WallStreetBets took on billion-dollar hedge funds — and won. GME surged from $17 to $483 in two weeks before crashing back.\n\n## The challenge\n- **The early bird** — getting in before the squeeze required reading the sentiment shift\n- **Diamond hands vs paper hands** — selling too early means missing the peak, selling too late means riding it down\n- **The aftermath** — GME didn''t go to zero. It stabilized around $50. Did your strategy adapt?\n\nThis is a test of **sentiment reading** and **exit timing**. Buy & hold would have worked great — can your strategy beat it?',
  'GME', 50000,
  '2021-01-04T00:00:00Z', '2021-03-05T23:59:59Z',
  '2026-03-26T00:00:00Z', '2026-04-15T23:59:59Z',
  '00000000-0000-0000-0000-000000000000', true
);

-- 3. 2022 Tech Bear Market (Jan-Jun 2022)
INSERT INTO challenges (id, name, description, ticker, initial_cash, simulation_start_date, simulation_end_date, participation_open, participation_close, created_by, strategies_public)
VALUES (
  'a0000001-0000-0000-0000-000000000003',
  '2022 Tech Bear Market',
  E'January 2022. The Fed signaled aggressive rate hikes. Growth stocks that had soared during COVID were about to face gravity. QQQ dropped 33% over six months.\n\n## The challenge\n- **Defensive positioning** — the smart move was to sell early and sit in cash. But when do you sell?\n- **Bear market rallies** — QQQ had multiple 5-10% bounces that trapped bulls. Did your strategy fall for the fakeouts?\n- **Bottom fishing** — QQQ hit its low in June. Getting back in required courage\n\nThis is a test of **risk management** and **patience**. Buy & hold lost 33%. Can you lose less — or even profit?',
  'QQQ', 100000,
  '2022-01-03T00:00:00Z', '2022-06-30T23:59:59Z',
  '2026-03-26T00:00:00Z', '2026-04-20T23:59:59Z',
  '00000000-0000-0000-0000-000000000000', true
);

-- 4. NVDA: The AI Explosion (May-Aug 2023)
INSERT INTO challenges (id, name, description, ticker, initial_cash, simulation_start_date, simulation_end_date, participation_open, participation_close, created_by, strategies_public)
VALUES (
  'a0000001-0000-0000-0000-000000000004',
  'NVDA: The AI Explosion',
  E'May 2023. NVIDIA reported earnings that stunned Wall Street — revenue guidance 50% above consensus. The stock doubled in three months as the AI narrative took hold.\n\n## The challenge\n- **Timing the entry** — NVDA gapped up 25% after earnings on May 24. Did your strategy catch it, or was it already in?\n- **Riding the wave** — after the gap, NVDA kept climbing. Taking profits too early meant leaving money on the table\n- **Pullback discipline** — even moonshots have 10% pullbacks. Did your strategy panic or hold?\n\nThis is a test of **momentum riding** and **conviction**. The trend was your friend — if you trusted it.',
  'NVDA', 100000,
  '2023-05-01T00:00:00Z', '2023-08-31T23:59:59Z',
  '2026-03-26T00:00:00Z', '2026-04-20T23:59:59Z',
  '00000000-0000-0000-0000-000000000000', false
);

-- 5. Bitcoin: ETF Approval (Jan-Mar 2024)
INSERT INTO challenges (id, name, description, ticker, initial_cash, simulation_start_date, simulation_end_date, participation_open, participation_close, created_by, strategies_public)
VALUES (
  'a0000001-0000-0000-0000-000000000005',
  'Bitcoin: ETF Approval',
  E'January 10, 2024. The SEC approved spot Bitcoin ETFs after years of rejections. Billions in institutional money flooded into BTC. But the classic "buy the rumor, sell the news" played out first.\n\n## The challenge\n- **The sell-the-news trap** — BTC actually *dipped* after ETF approval from $49K to $38K before rallying to $63K\n- **Institutional vs retail** — the dynamics shifted. This was no longer a retail-driven market\n- **Momentum recognition** — once the dip was bought, the trend was relentless\n\nThis is a test of **contrarian thinking** and **trend following**. The obvious trade was wrong on day one.',
  'BTC', 100000,
  '2024-01-02T00:00:00Z', '2024-03-15T23:59:59Z',
  '2026-03-26T00:00:00Z', '2026-04-15T23:59:59Z',
  '00000000-0000-0000-0000-000000000000', true
);

-- 6. Election Night 2024 (Oct-Nov 2024)
INSERT INTO challenges (id, name, description, ticker, initial_cash, simulation_start_date, simulation_end_date, participation_open, participation_close, created_by, strategies_public)
VALUES (
  'a0000001-0000-0000-0000-000000000006',
  'Election Night 2024',
  E'October 2024. Markets were pricing in uncertainty as the US presidential election approached. Polls were tight, policy implications were enormous, and volatility was elevated.\n\n## The challenge\n- **Pre-election positioning** — do you hedge, go to cash, or bet on an outcome?\n- **Election night** — SPY futures swung wildly overnight on Nov 5. Positioning mattered\n- **Post-election rally** — the market picked a direction fast. Were you on the right side?\n\nThis is a test of **event-driven trading** and **volatility management**. Sitting in cash was safe but meant missing the post-election surge.',
  'SPY', 100000,
  '2024-10-01T00:00:00Z', '2024-11-30T23:59:59Z',
  '2026-03-26T00:00:00Z', '2026-04-15T23:59:59Z',
  '00000000-0000-0000-0000-000000000000', false
);

-- ============================================================================
-- LIVE CHALLENGES (starting April 1, 2026)
-- ============================================================================

-- 7. TSLA: Q2 Earnings Season (Apr-May 2026)
INSERT INTO challenges (id, name, description, ticker, initial_cash, simulation_start_date, simulation_end_date, participation_open, participation_close, created_by, strategies_public)
VALUES (
  'a0000001-0000-0000-0000-000000000007',
  'TSLA: Q2 Earnings Season',
  E'Tesla reports Q1 earnings in late April. Between Elon''s distractions, robotaxi promises, and margin pressure, TSLA never fails to deliver volatility.\n\n## The challenge\n- Navigate the **pre-earnings drift** — does TSLA run up or sell off into earnings?\n- Survive the **earnings reaction** — TSLA routinely moves 10%+ on earnings day\n- Manage the **post-earnings trend** — the market digests the numbers over weeks, not minutes\n\nTwo months of Tesla. May the boldest strategy win.',
  'TSLA', 100000,
  '2026-04-01T00:00:00Z', '2026-05-31T23:59:59Z',
  '2026-03-27T00:00:00Z', '2026-05-31T23:59:59Z',
  '00000000-0000-0000-0000-000000000000', true
);

-- 8. ETH: April Sprint (Apr 2026)
INSERT INTO challenges (id, name, description, ticker, initial_cash, simulation_start_date, simulation_end_date, participation_open, participation_close, created_by, strategies_public)
VALUES (
  'a0000001-0000-0000-0000-000000000008',
  'ETH: April Sprint',
  E'One month. One ticker. Ethereum trades 24/7, so your strategy never sleeps.\n\n## The challenge\n- Crypto moves fast — **hourly evaluation** means your strategy reacts to intraday swings\n- April historically brings volatility to crypto as Q1 closes and institutional rebalancing kicks in\n- ETH correlates with BTC but has its own catalysts — staking yields, L2 activity, protocol upgrades\n\nShort, intense, no breaks. Your strategy runs around the clock for 30 days.',
  'ETH', 50000,
  '2026-04-01T00:00:00Z', '2026-04-30T23:59:59Z',
  '2026-03-27T00:00:00Z', '2026-04-30T23:59:59Z',
  '00000000-0000-0000-0000-000000000000', false
);
