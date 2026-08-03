-- Historical market events for backtesting period selection and AI-driven discovery
CREATE TABLE historical_events (
    id              BIGSERIAL    PRIMARY KEY,
    slug            VARCHAR(80)  NOT NULL UNIQUE,
    name            VARCHAR(120) NOT NULL,
    start_date      DATE         NOT NULL,
    end_date        DATE         NOT NULL,
    category        VARCHAR(30)  NOT NULL,
    asset_class     VARCHAR(20)  NOT NULL,
    sentiment       VARCHAR(20)  NOT NULL,
    description     VARCHAR(500) NOT NULL,
    impact_summary  VARCHAR(200) NOT NULL,
    suggested_assets VARCHAR(200),
    keywords        VARCHAR(500)
);

CREATE INDEX idx_historical_events_category ON historical_events(category);
CREATE INDEX idx_historical_events_slug ON historical_events(slug);

-- Seed 12 historic market events with keywords for AI matching
INSERT INTO historical_events (slug, name, start_date, end_date, category, asset_class, sentiment, description, impact_summary, suggested_assets, keywords) VALUES
('covid-crash', 'COVID Crash', '2020-02-20', '2020-04-20', 'CRISIS', 'BOTH', 'BEARISH',
 'Black swan pandemic crash. Markets dropped 35% in 33 trading days as COVID-19 spread globally.',
 'S&P 500: -34% | BTC: -50% | VIX: 82',
 'SPY,BTC,ETH,GLD',
 'covid,pandemic,coronavirus,lockdown,black swan,march 2020,covid crash'),

('covid-recovery', 'COVID Recovery Rally', '2020-04-01', '2020-08-31', 'BULL_RUN', 'BOTH', 'BULLISH',
 'V-shaped recovery fueled by unprecedented Fed stimulus and fiscal support.',
 'S&P 500: +51% | BTC: +75% | NASDAQ: +61%',
 'SPY,QQQ,BTC,TSLA',
 'covid recovery,stimulus,fed,v-shaped,recovery rally,quantitative easing'),

('gamestop-squeeze', 'GameStop Squeeze', '2021-01-25', '2021-02-05', 'VOLATILITY', 'STOCK', 'MIXED',
 'Retail traders vs. hedge funds. GME surged 1,600% in days, triggering trading halts.',
 'GME: +1,600% | AMC: +300% | Volatility spike',
 'GME,AMC,BB,NOK',
 'gamestop,gme,squeeze,short squeeze,wallstreetbets,reddit,meme stocks,retail traders'),

('crypto-bull-2021', '2021 Crypto Bull Run', '2021-01-01', '2021-11-10', 'BULL_RUN', 'CRYPTO', 'BULLISH',
 'Historic crypto rally. Bitcoin reached $69K all-time high, Ethereum broke $4,800.',
 'BTC: +130% | ETH: +550% | SOL: +14,000%',
 'BTC,ETH,SOL,ADA',
 'crypto bull,bitcoin ath,ethereum,defi summer,nft,altcoin season,69k'),

('evergrande-crisis', 'Evergrande Crisis', '2021-09-13', '2021-10-15', 'CRISIS', 'BOTH', 'BEARISH',
 'Chinese real estate giant default fears sparked global contagion concerns.',
 'Hang Seng: -15% | S&P 500: -5% | Emerging markets selloff',
 'SPY,EEM,FXI,BTC',
 'evergrande,china,real estate,default,contagion,emerging markets,hang seng'),

('rate-hike-selloff', '2022 Rate Hike Selloff', '2022-01-01', '2022-06-30', 'BEAR_MARKET', 'BOTH', 'BEARISH',
 'Fed pivot to aggressive rate hikes crushed growth stocks and risk assets.',
 'NASDAQ: -32% | BTC: -58% | ARK: -55%',
 'QQQ,ARKK,BTC,TLT',
 'rate hike,fed pivot,inflation,interest rates,growth selloff,2022 bear market,hawkish'),

('luna-collapse', 'LUNA/Terra Collapse', '2022-05-07', '2022-05-15', 'CRISIS', 'CRYPTO', 'BEARISH',
 'Algorithmic stablecoin death spiral. $40B wiped out in days, triggering crypto contagion.',
 'LUNA: -99.99% | UST depeg | BTC: -25%',
 'BTC,ETH,SOL,AVAX',
 'luna,terra,ust,stablecoin,depeg,death spiral,do kwon,algorithmic stablecoin'),

('ftx-collapse', 'FTX Collapse', '2022-11-06', '2022-11-22', 'CRISIS', 'CRYPTO', 'BEARISH',
 '$32B crypto exchange collapsed in 10 days. Contagion fears across all crypto assets.',
 'FTT: -97% | BTC: -25% | SOL: -60%',
 'BTC,ETH,SOL,BNB',
 'ftx,sbf,sam bankman-fried,exchange collapse,fraud,contagion,ftt,alameda'),

('banking-crisis-2023', '2023 Banking Crisis', '2023-03-08', '2023-03-20', 'CRISIS', 'BOTH', 'BEARISH',
 'SVB, Signature Bank failures triggered regional banking panic and flight to safety.',
 'KRE (Regional Banks): -30% | BTC: +40% | Gold: +8%',
 'KRE,BTC,GLD,TLT',
 'svb,silicon valley bank,signature bank,banking crisis,bank run,regional banks,fdic'),

('ai-boom-2023', 'AI Boom / NVDA Rally', '2023-01-01', '2023-12-31', 'BULL_RUN', 'STOCK', 'BULLISH',
 'ChatGPT sparked AI gold rush. NVIDIA became the AI infrastructure leader.',
 'NVDA: +240% | META: +194% | MSFT: +57%',
 'NVDA,MSFT,META,GOOGL',
 'ai,artificial intelligence,chatgpt,nvidia,gpu,ai boom,magnificent seven,tech rally'),

('btc-etf-approval', 'Bitcoin ETF Approval', '2024-01-10', '2024-02-28', 'CATALYST', 'CRYPTO', 'BULLISH',
 'SEC approved spot Bitcoin ETFs. Institutional access unlocked, billions flowed in.',
 'BTC: +45% | ETF inflows: $10B+ | Crypto sentiment surge',
 'BTC,ETH,COIN,MSTR',
 'bitcoin etf,spot etf,sec approval,institutional,blackrock,etf inflows,ibit'),

('election-volatility-2024', '2024 Election Volatility', '2024-10-15', '2024-11-15', 'VOLATILITY', 'STOCK', 'MIXED',
 'US presidential election created significant pre and post-election market swings.',
 'VIX spike | Sector rotation | Policy uncertainty',
 'SPY,QQQ,XLF,XLE',
 'election,presidential,trump,policy uncertainty,sector rotation,vix,political risk');
