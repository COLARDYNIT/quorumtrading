/**
 * Static ticker database for autocomplete and search in the strategy wizard.
 * Includes popular stocks, ETFs, and crypto assets tradable on Alpaca.
 */

export interface TickerInfo {
  symbol: string;
  name: string;
  type: 'STOCK' | 'CRYPTO' | 'ETF';
}

export const TICKER_DATABASE: TickerInfo[] = [
  // ── Crypto ──────────────────────────────────────────────
  { symbol: 'BTC', name: 'Bitcoin', type: 'CRYPTO' },
  { symbol: 'ETH', name: 'Ethereum', type: 'CRYPTO' },
  { symbol: 'SOL', name: 'Solana', type: 'CRYPTO' },
  { symbol: 'DOGE', name: 'Dogecoin', type: 'CRYPTO' },
  { symbol: 'XRP', name: 'Ripple XRP', type: 'CRYPTO' },
  { symbol: 'ADA', name: 'Cardano', type: 'CRYPTO' },
  { symbol: 'DOT', name: 'Polkadot', type: 'CRYPTO' },
  { symbol: 'LINK', name: 'Chainlink', type: 'CRYPTO' },
  { symbol: 'AVAX', name: 'Avalanche', type: 'CRYPTO' },
  { symbol: 'MATIC', name: 'Polygon', type: 'CRYPTO' },
  { symbol: 'ATOM', name: 'Cosmos', type: 'CRYPTO' },
  { symbol: 'UNI', name: 'Uniswap', type: 'CRYPTO' },
  { symbol: 'LTC', name: 'Litecoin', type: 'CRYPTO' },
  { symbol: 'BCH', name: 'Bitcoin Cash', type: 'CRYPTO' },
  { symbol: 'SHIB', name: 'Shiba Inu', type: 'CRYPTO' },
  { symbol: 'NEAR', name: 'NEAR Protocol', type: 'CRYPTO' },
  { symbol: 'APT', name: 'Aptos', type: 'CRYPTO' },
  { symbol: 'ARB', name: 'Arbitrum', type: 'CRYPTO' },
  { symbol: 'OP', name: 'Optimism', type: 'CRYPTO' },
  { symbol: 'FIL', name: 'Filecoin', type: 'CRYPTO' },
  { symbol: 'AAVE', name: 'Aave', type: 'CRYPTO' },
  { symbol: 'MKR', name: 'Maker', type: 'CRYPTO' },
  { symbol: 'GRT', name: 'The Graph', type: 'CRYPTO' },
  { symbol: 'RENDER', name: 'Render', type: 'CRYPTO' },
  { symbol: 'SUI', name: 'Sui', type: 'CRYPTO' },
  { symbol: 'SEI', name: 'Sei', type: 'CRYPTO' },
  { symbol: 'PEPE', name: 'Pepe', type: 'CRYPTO' },
  { symbol: 'FET', name: 'Fetch.ai', type: 'CRYPTO' },

  // ── Stocks (Mega-cap / Large-cap) ──────────────────────
  { symbol: 'AAPL', name: 'Apple', type: 'STOCK' },
  { symbol: 'MSFT', name: 'Microsoft', type: 'STOCK' },
  { symbol: 'NVDA', name: 'NVIDIA', type: 'STOCK' },
  { symbol: 'GOOGL', name: 'Alphabet (Google)', type: 'STOCK' },
  { symbol: 'AMZN', name: 'Amazon', type: 'STOCK' },
  { symbol: 'META', name: 'Meta Platforms (Facebook)', type: 'STOCK' },
  { symbol: 'TSLA', name: 'Tesla', type: 'STOCK' },
  { symbol: 'BRK.B', name: 'Berkshire Hathaway', type: 'STOCK' },
  { symbol: 'JPM', name: 'JPMorgan Chase', type: 'STOCK' },
  { symbol: 'V', name: 'Visa', type: 'STOCK' },
  { symbol: 'MA', name: 'Mastercard', type: 'STOCK' },
  { symbol: 'UNH', name: 'UnitedHealth Group', type: 'STOCK' },
  { symbol: 'JNJ', name: 'Johnson & Johnson', type: 'STOCK' },
  { symbol: 'WMT', name: 'Walmart', type: 'STOCK' },
  { symbol: 'PG', name: 'Procter & Gamble', type: 'STOCK' },
  { symbol: 'HD', name: 'Home Depot', type: 'STOCK' },
  { symbol: 'DIS', name: 'Walt Disney', type: 'STOCK' },
  { symbol: 'BAC', name: 'Bank of America', type: 'STOCK' },
  { symbol: 'NFLX', name: 'Netflix', type: 'STOCK' },
  { symbol: 'ADBE', name: 'Adobe', type: 'STOCK' },
  { symbol: 'CRM', name: 'Salesforce', type: 'STOCK' },
  { symbol: 'AMD', name: 'Advanced Micro Devices', type: 'STOCK' },
  { symbol: 'INTC', name: 'Intel', type: 'STOCK' },
  { symbol: 'PYPL', name: 'PayPal', type: 'STOCK' },
  { symbol: 'ORCL', name: 'Oracle', type: 'STOCK' },
  { symbol: 'CSCO', name: 'Cisco Systems', type: 'STOCK' },
  { symbol: 'ACN', name: 'Accenture', type: 'STOCK' },
  { symbol: 'IBM', name: 'IBM', type: 'STOCK' },
  { symbol: 'AVGO', name: 'Broadcom', type: 'STOCK' },
  { symbol: 'TXN', name: 'Texas Instruments', type: 'STOCK' },
  { symbol: 'QCOM', name: 'Qualcomm', type: 'STOCK' },
  { symbol: 'NOW', name: 'ServiceNow', type: 'STOCK' },
  { symbol: 'SHOP', name: 'Shopify', type: 'STOCK' },
  { symbol: 'SQ', name: 'Block (Square)', type: 'STOCK' },
  { symbol: 'COIN', name: 'Coinbase', type: 'STOCK' },
  { symbol: 'PLTR', name: 'Palantir Technologies', type: 'STOCK' },
  { symbol: 'UBER', name: 'Uber Technologies', type: 'STOCK' },
  { symbol: 'ABNB', name: 'Airbnb', type: 'STOCK' },
  { symbol: 'SNOW', name: 'Snowflake', type: 'STOCK' },
  { symbol: 'NET', name: 'Cloudflare', type: 'STOCK' },
  { symbol: 'CRWD', name: 'CrowdStrike', type: 'STOCK' },
  { symbol: 'ZS', name: 'Zscaler', type: 'STOCK' },
  { symbol: 'PANW', name: 'Palo Alto Networks', type: 'STOCK' },
  { symbol: 'DDOG', name: 'Datadog', type: 'STOCK' },
  { symbol: 'MDB', name: 'MongoDB', type: 'STOCK' },
  { symbol: 'TTD', name: 'The Trade Desk', type: 'STOCK' },
  { symbol: 'MELI', name: 'MercadoLibre', type: 'STOCK' },
  { symbol: 'SE', name: 'Sea Limited', type: 'STOCK' },
  { symbol: 'PDD', name: 'PDD Holdings (Temu)', type: 'STOCK' },
  { symbol: 'BABA', name: 'Alibaba', type: 'STOCK' },
  { symbol: 'NIO', name: 'NIO', type: 'STOCK' },
  { symbol: 'LI', name: 'Li Auto', type: 'STOCK' },
  { symbol: 'RIVN', name: 'Rivian Automotive', type: 'STOCK' },
  { symbol: 'LCID', name: 'Lucid Group', type: 'STOCK' },
  { symbol: 'F', name: 'Ford Motor', type: 'STOCK' },
  { symbol: 'GM', name: 'General Motors', type: 'STOCK' },
  { symbol: 'BA', name: 'Boeing', type: 'STOCK' },
  { symbol: 'CAT', name: 'Caterpillar', type: 'STOCK' },
  { symbol: 'DE', name: 'Deere & Company', type: 'STOCK' },
  { symbol: 'GE', name: 'GE Aerospace', type: 'STOCK' },
  { symbol: 'HON', name: 'Honeywell', type: 'STOCK' },
  { symbol: 'RTX', name: 'RTX (Raytheon)', type: 'STOCK' },
  { symbol: 'LMT', name: 'Lockheed Martin', type: 'STOCK' },
  { symbol: 'XOM', name: 'Exxon Mobil', type: 'STOCK' },
  { symbol: 'CVX', name: 'Chevron', type: 'STOCK' },
  { symbol: 'COP', name: 'ConocoPhillips', type: 'STOCK' },
  { symbol: 'NEE', name: 'NextEra Energy', type: 'STOCK' },
  { symbol: 'ENPH', name: 'Enphase Energy', type: 'STOCK' },
  { symbol: 'FSLR', name: 'First Solar', type: 'STOCK' },
  { symbol: 'LLY', name: 'Eli Lilly', type: 'STOCK' },
  { symbol: 'PFE', name: 'Pfizer', type: 'STOCK' },
  { symbol: 'ABBV', name: 'AbbVie', type: 'STOCK' },
  { symbol: 'MRK', name: 'Merck', type: 'STOCK' },
  { symbol: 'TMO', name: 'Thermo Fisher Scientific', type: 'STOCK' },
  { symbol: 'MRNA', name: 'Moderna', type: 'STOCK' },
  { symbol: 'ISRG', name: 'Intuitive Surgical', type: 'STOCK' },
  { symbol: 'GS', name: 'Goldman Sachs', type: 'STOCK' },
  { symbol: 'MS', name: 'Morgan Stanley', type: 'STOCK' },
  { symbol: 'C', name: 'Citigroup', type: 'STOCK' },
  { symbol: 'WFC', name: 'Wells Fargo', type: 'STOCK' },
  { symbol: 'BLK', name: 'BlackRock', type: 'STOCK' },
  { symbol: 'SCHW', name: 'Charles Schwab', type: 'STOCK' },
  { symbol: 'AXP', name: 'American Express', type: 'STOCK' },
  { symbol: 'KO', name: 'Coca-Cola', type: 'STOCK' },
  { symbol: 'PEP', name: 'PepsiCo', type: 'STOCK' },
  { symbol: 'MCD', name: 'McDonald\'s', type: 'STOCK' },
  { symbol: 'SBUX', name: 'Starbucks', type: 'STOCK' },
  { symbol: 'NKE', name: 'Nike', type: 'STOCK' },
  { symbol: 'COST', name: 'Costco', type: 'STOCK' },
  { symbol: 'TGT', name: 'Target', type: 'STOCK' },
  { symbol: 'LOW', name: 'Lowe\'s', type: 'STOCK' },
  { symbol: 'T', name: 'AT&T', type: 'STOCK' },
  { symbol: 'VZ', name: 'Verizon', type: 'STOCK' },
  { symbol: 'TMUS', name: 'T-Mobile US', type: 'STOCK' },
  { symbol: 'CMCSA', name: 'Comcast', type: 'STOCK' },
  { symbol: 'SPOT', name: 'Spotify', type: 'STOCK' },
  { symbol: 'RBLX', name: 'Roblox', type: 'STOCK' },
  { symbol: 'U', name: 'Unity Software', type: 'STOCK' },
  { symbol: 'EA', name: 'Electronic Arts', type: 'STOCK' },
  { symbol: 'ATVI', name: 'Activision Blizzard', type: 'STOCK' },
  { symbol: 'ARM', name: 'Arm Holdings', type: 'STOCK' },
  { symbol: 'SMCI', name: 'Super Micro Computer', type: 'STOCK' },
  { symbol: 'MRVL', name: 'Marvell Technology', type: 'STOCK' },
  { symbol: 'MU', name: 'Micron Technology', type: 'STOCK' },
  { symbol: 'LRCX', name: 'Lam Research', type: 'STOCK' },
  { symbol: 'AMAT', name: 'Applied Materials', type: 'STOCK' },
  { symbol: 'KLAC', name: 'KLA Corporation', type: 'STOCK' },
  { symbol: 'ASML', name: 'ASML Holding', type: 'STOCK' },
  { symbol: 'TSM', name: 'Taiwan Semiconductor (TSMC)', type: 'STOCK' },

  // ── ETFs (Index / Broad Market) ────────────────────────
  { symbol: 'SPY', name: 'SPDR S&P 500 ETF', type: 'ETF' },
  { symbol: 'QQQ', name: 'Invesco Nasdaq 100 ETF', type: 'ETF' },
  { symbol: 'DIA', name: 'SPDR Dow Jones Industrial ETF', type: 'ETF' },
  { symbol: 'IWM', name: 'iShares Russell 2000 Small-Cap ETF', type: 'ETF' },
  { symbol: 'VTI', name: 'Vanguard Total Stock Market ETF', type: 'ETF' },
  { symbol: 'VOO', name: 'Vanguard S&P 500 ETF', type: 'ETF' },
  { symbol: 'IVV', name: 'iShares Core S&P 500 ETF', type: 'ETF' },
  { symbol: 'IJR', name: 'iShares Core S&P Small-Cap ETF', type: 'ETF' },
  { symbol: 'IJH', name: 'iShares Core S&P Mid-Cap ETF', type: 'ETF' },
  { symbol: 'IWF', name: 'iShares Russell 1000 Growth ETF', type: 'ETF' },
  { symbol: 'IWD', name: 'iShares Russell 1000 Value ETF', type: 'ETF' },
  { symbol: 'RSP', name: 'Invesco S&P 500 Equal Weight ETF', type: 'ETF' },

  // ── ETFs (International) ───────────────────────────────
  { symbol: 'VEA', name: 'Vanguard Developed Markets ETF', type: 'ETF' },
  { symbol: 'VWO', name: 'Vanguard Emerging Markets ETF', type: 'ETF' },
  { symbol: 'EEM', name: 'iShares MSCI Emerging Markets ETF', type: 'ETF' },
  { symbol: 'EFA', name: 'iShares MSCI EAFE ETF', type: 'ETF' },
  { symbol: 'IEMG', name: 'iShares Core MSCI Emerging Markets ETF', type: 'ETF' },
  { symbol: 'IEFA', name: 'iShares Core MSCI EAFE ETF', type: 'ETF' },
  { symbol: 'VXUS', name: 'Vanguard Total International Stock ETF', type: 'ETF' },
  { symbol: 'FXI', name: 'iShares China Large-Cap ETF', type: 'ETF' },
  { symbol: 'KWEB', name: 'KraneShares China Internet ETF', type: 'ETF' },
  { symbol: 'EWJ', name: 'iShares MSCI Japan ETF', type: 'ETF' },

  // ── ETFs (Sector) ──────────────────────────────────────
  { symbol: 'XLK', name: 'Technology Select Sector SPDR', type: 'ETF' },
  { symbol: 'XLF', name: 'Financial Select Sector SPDR', type: 'ETF' },
  { symbol: 'XLE', name: 'Energy Select Sector SPDR', type: 'ETF' },
  { symbol: 'XLV', name: 'Health Care Select Sector SPDR', type: 'ETF' },
  { symbol: 'XLI', name: 'Industrial Select Sector SPDR', type: 'ETF' },
  { symbol: 'XLP', name: 'Consumer Staples Select Sector SPDR', type: 'ETF' },
  { symbol: 'XLY', name: 'Consumer Discretionary Select Sector SPDR', type: 'ETF' },
  { symbol: 'XLU', name: 'Utilities Select Sector SPDR', type: 'ETF' },
  { symbol: 'XLB', name: 'Materials Select Sector SPDR', type: 'ETF' },
  { symbol: 'XLRE', name: 'Real Estate Select Sector SPDR', type: 'ETF' },
  { symbol: 'XLC', name: 'Communication Services Select Sector SPDR', type: 'ETF' },
  { symbol: 'VGT', name: 'Vanguard Information Technology ETF', type: 'ETF' },
  { symbol: 'VHT', name: 'Vanguard Health Care ETF', type: 'ETF' },
  { symbol: 'VNQ', name: 'Vanguard Real Estate ETF', type: 'ETF' },
  { symbol: 'SMH', name: 'VanEck Semiconductor ETF', type: 'ETF' },
  { symbol: 'SOXX', name: 'iShares Semiconductor ETF', type: 'ETF' },
  { symbol: 'IBB', name: 'iShares Biotechnology ETF', type: 'ETF' },
  { symbol: 'XBI', name: 'SPDR S&P Biotech ETF', type: 'ETF' },
  { symbol: 'TAN', name: 'Invesco Solar ETF', type: 'ETF' },
  { symbol: 'ICLN', name: 'iShares Global Clean Energy ETF', type: 'ETF' },
  { symbol: 'HACK', name: 'ETFMG Prime Cyber Security ETF', type: 'ETF' },
  { symbol: 'BOTZ', name: 'Global X Robotics & AI ETF', type: 'ETF' },

  // ── ETFs (Thematic / Innovation) ───────────────────────
  { symbol: 'ARKK', name: 'ARK Innovation ETF', type: 'ETF' },
  { symbol: 'ARKW', name: 'ARK Next Generation Internet ETF', type: 'ETF' },
  { symbol: 'ARKF', name: 'ARK Fintech Innovation ETF', type: 'ETF' },
  { symbol: 'ARKG', name: 'ARK Genomic Revolution ETF', type: 'ETF' },
  { symbol: 'ARKQ', name: 'ARK Autonomous Technology & Robotics ETF', type: 'ETF' },

  // ── ETFs (Dividend / Income) ───────────────────────────
  { symbol: 'SCHD', name: 'Schwab US Dividend Equity ETF', type: 'ETF' },
  { symbol: 'VIG', name: 'Vanguard Dividend Appreciation ETF', type: 'ETF' },
  { symbol: 'VYM', name: 'Vanguard High Dividend Yield ETF', type: 'ETF' },
  { symbol: 'DVY', name: 'iShares Select Dividend ETF', type: 'ETF' },
  { symbol: 'HDV', name: 'iShares Core High Dividend ETF', type: 'ETF' },
  { symbol: 'NOBL', name: 'ProShares S&P 500 Dividend Aristocrats ETF', type: 'ETF' },

  // ── ETFs (Fixed Income) ────────────────────────────────
  { symbol: 'BND', name: 'Vanguard Total Bond Market ETF', type: 'ETF' },
  { symbol: 'AGG', name: 'iShares Core US Aggregate Bond ETF', type: 'ETF' },
  { symbol: 'TLT', name: 'iShares 20+ Year Treasury Bond ETF', type: 'ETF' },
  { symbol: 'IEF', name: 'iShares 7-10 Year Treasury Bond ETF', type: 'ETF' },
  { symbol: 'SHY', name: 'iShares 1-3 Year Treasury Bond ETF', type: 'ETF' },
  { symbol: 'HYG', name: 'iShares iBoxx High Yield Corporate Bond ETF', type: 'ETF' },
  { symbol: 'LQD', name: 'iShares iBoxx Investment Grade Corporate Bond ETF', type: 'ETF' },
  { symbol: 'TIP', name: 'iShares TIPS Bond ETF', type: 'ETF' },
  { symbol: 'VCIT', name: 'Vanguard Intermediate-Term Corporate Bond ETF', type: 'ETF' },

  // ── ETFs (Commodities) ─────────────────────────────────
  { symbol: 'GLD', name: 'SPDR Gold Shares ETF', type: 'ETF' },
  { symbol: 'SLV', name: 'iShares Silver Trust ETF', type: 'ETF' },
  { symbol: 'IAU', name: 'iShares Gold Trust ETF', type: 'ETF' },
  { symbol: 'USO', name: 'United States Oil Fund ETF', type: 'ETF' },
  { symbol: 'UNG', name: 'United States Natural Gas Fund ETF', type: 'ETF' },
  { symbol: 'DBA', name: 'Invesco DB Agriculture Fund ETF', type: 'ETF' },
  { symbol: 'DBC', name: 'Invesco DB Commodity Index Tracking Fund', type: 'ETF' },
  { symbol: 'PDBC', name: 'Invesco Optimum Yield Diversified Commodity ETF', type: 'ETF' },

  // ── ETFs (Leveraged / Inverse) ─────────────────────────
  { symbol: 'TQQQ', name: 'ProShares UltraPro QQQ (3x Nasdaq)', type: 'ETF' },
  { symbol: 'SQQQ', name: 'ProShares UltraPro Short QQQ (-3x Nasdaq)', type: 'ETF' },
  { symbol: 'SPXL', name: 'Direxion Daily S&P 500 Bull 3x', type: 'ETF' },
  { symbol: 'SPXS', name: 'Direxion Daily S&P 500 Bear 3x', type: 'ETF' },
  { symbol: 'UPRO', name: 'ProShares UltraPro S&P 500 (3x)', type: 'ETF' },
  { symbol: 'SOXL', name: 'Direxion Daily Semiconductor Bull 3x', type: 'ETF' },
  { symbol: 'SOXS', name: 'Direxion Daily Semiconductor Bear 3x', type: 'ETF' },
  { symbol: 'UVXY', name: 'ProShares Ultra VIX Short-Term Futures (1.5x)', type: 'ETF' },

  // ── ETFs (Volatility / Strategy) ───────────────────────
  { symbol: 'VXX', name: 'iPath Series B S&P 500 VIX ETN', type: 'ETF' },
  { symbol: 'VIXY', name: 'ProShares VIX Short-Term Futures ETF', type: 'ETF' },
  { symbol: 'JEPI', name: 'JPMorgan Equity Premium Income ETF', type: 'ETF' },
  { symbol: 'JEPQ', name: 'JPMorgan Nasdaq Equity Premium Income ETF', type: 'ETF' },
  { symbol: 'XYLD', name: 'Global X S&P 500 Covered Call ETF', type: 'ETF' },
  { symbol: 'QYLD', name: 'Global X Nasdaq 100 Covered Call ETF', type: 'ETF' },
];

/**
 * Search the ticker database by symbol or company name.
 * Returns matches sorted by relevance (exact symbol match first, then prefix, then contains).
 */
export function searchTickers(query: string, assetType: 'CRYPTO' | 'STOCK'): TickerInfo[] {
  const q = query.trim().toLowerCase();
  if (!q) return [];

  const typeFilter = assetType === 'CRYPTO' ? ['CRYPTO'] : ['STOCK', 'ETF'];

  const filtered = TICKER_DATABASE.filter(t => typeFilter.includes(t.type));

  const exactSymbol: TickerInfo[] = [];
  const prefixSymbol: TickerInfo[] = [];
  const prefixName: TickerInfo[] = [];
  const containsMatch: TickerInfo[] = [];

  for (const t of filtered) {
    const sym = t.symbol.toLowerCase();
    const name = t.name.toLowerCase();

    if (sym === q) {
      exactSymbol.push(t);
    } else if (sym.startsWith(q)) {
      prefixSymbol.push(t);
    } else if (name.startsWith(q)) {
      prefixName.push(t);
    } else if (name.includes(q) || sym.includes(q)) {
      containsMatch.push(t);
    }
  }

  return [...exactSymbol, ...prefixSymbol, ...prefixName, ...containsMatch].slice(0, 8);
}
